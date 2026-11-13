import crypto from 'node:crypto';
import { Readable } from 'node:stream';
import { config } from './config.js';
import { db } from './db.js';
import { hmacSign, isHttpUrl, safeEqual } from './util.js';

const SECRET = crypto.randomBytes(32);
const manifestCache = new Map();
const inFlight = new Map();
const cookieJars = new Map();

const HLS_TYPES = [
  'application/vnd.apple.mpegurl',
  'application/x-mpegurl',
  'audio/mpegurl',
  'audio/x-mpegurl',
  'application/mpegurl',
  'text/plain',
];

const PASS_HEADERS = [
  'content-type',
  'content-range',
  'accept-ranges',
  'cache-control',
  'expires',
  'last-modified',
  'etag',
];

export function getBaseUrl(req) {
  if (config.publicUrl) return config.publicUrl;
  const host = req?.headers?.host;
  if (host) {
    const proto = req.protocol || 'http';
    return `${proto}://${host}`;
  }
  return `http://localhost:${config.port}`;
}

export function signTarget(streamId, url) {
  const payload = Buffer.from(JSON.stringify({ s: streamId, u: url }), 'utf8').toString(
    'base64url',
  );
  const sig = hmacSign(payload, SECRET).slice(0, 43);
  return `${payload}.${sig}`;
}

export function verifyTarget(token) {
  const value = String(token || '');
  const dot = value.lastIndexOf('.');
  if (dot <= 0) return null;
  const payload = value.slice(0, dot);
  const sig = value.slice(dot + 1);
  if (!safeEqual(sig, hmacSign(payload, SECRET).slice(0, 43))) return null;
  try {
    const data = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (!data || typeof data.u !== 'string' || !isHttpUrl(data.u)) return null;
    if (typeof data.s !== 'string') return null;
    return { streamId: data.s, url: data.u };
  } catch {
    return null;
  }
}

function baseHeaders(stream) {
  const headers = {
    'user-agent': stream?.user_agent || config.userAgent,
    accept: '*/*',
    'accept-language': 'it-IT,it;q=0.9,en;q=0.8',
  };
  if (stream?.referrer) {
    headers.referer = stream.referrer;
    try {
      headers.origin = new URL(stream.referrer).origin;
    } catch {
      // ignore
    }
  }
  return headers;
}

function headerVariants(stream) {
  const configured = baseHeaders(stream);
  const variants = [configured];
  const fallback = { ...configured, 'user-agent': config.userAgent };
  if (fallback['user-agent'] !== configured['user-agent']) variants.push(fallback);
  const noReferrer = { ...fallback };
  delete noReferrer.referer;
  delete noReferrer.origin;
  if (stream?.referrer) variants.push(noReferrer);
  return variants;
}

function jarKey(streamId, host) {
  return `${streamId}|${host}`;
}

function getCookieHeader(streamId, url) {
  let host;
  try {
    host = new URL(url).host;
  } catch {
    return null;
  }
  const jar = cookieJars.get(jarKey(streamId, host));
  if (!jar || !jar.size) return null;
  return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
}

function storeCookies(streamId, url, response) {
  let host;
  try {
    host = new URL(url).host;
  } catch {
    return;
  }
  const setCookies =
    typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : [];
  if (!setCookies.length) return;
  const key = jarKey(streamId, host);
  let jar = cookieJars.get(key);
  if (!jar) {
    jar = new Map();
    cookieJars.set(key, jar);
  }
  for (const raw of setCookies) {
    const first = raw.split(';')[0];
    const eq = first.indexOf('=');
    if (eq <= 0) continue;
    const name = first.slice(0, eq).trim();
    const value = first.slice(eq + 1).trim();
    if (!name) continue;
    if (/max-age\s*=\s*0/i.test(raw)) jar.delete(name);
    else jar.set(name, value);
  }
  if (cookieJars.size > 200) {
    const oldest = cookieJars.keys().next().value;
    cookieJars.delete(oldest);
  }
}

async function upstreamFetch(streamId, url, { range, timeout = 20_000, variants } = {}) {
  const list = variants || headerVariants({ id: streamId });
  let lastError = null;
  for (const headers of list) {
    const cookie = getCookieHeader(streamId, url);
    if (cookie) headers.cookie = cookie;
    if (range) headers.range = range;
    try {
      const response = await fetch(url, {
        headers,
        redirect: 'follow',
        signal: AbortSignal.timeout(timeout),
      });
      storeCookies(streamId, response.url || url, response);
      return response;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError || new Error('richiesta upstream fallita');
}

function looksLikeHls(url, contentType) {
  const type = String(contentType || '').toLowerCase();
  if (HLS_TYPES.some((t) => type.includes(t))) return true;
  try {
    const pathname = new URL(url).pathname.toLowerCase();
    return pathname.endsWith('.m3u8') || pathname.endsWith('.m3u');
  } catch {
    return false;
  }
}

function proxyUrl(serverBase, streamId, target) {
  return `${serverBase}/p/${signTarget(streamId, target)}`;
}

export function rewritePlaylist(text, manifestUrl, streamId, serverBase) {
  const lines = String(text || '').split(/\r?\n/);
  const out = [];
  for (const line of lines) {
    if (!line) {
      out.push(line);
      continue;
    }
    if (line.startsWith('#')) {
      if (!line.includes('URI=')) {
        out.push(line);
        continue;
      }
      const rewritten = line.replace(/URI="([^"]+)"/g, (match, uri) => {
        try {
          const absolute = new URL(uri, manifestUrl).toString();
          if (!isHttpUrl(absolute)) return match;
          return `URI="${proxyUrl(serverBase, streamId, absolute)}"`;
        } catch {
          return match;
        }
      });
      out.push(rewritten);
      continue;
    }
    try {
      const absolute = new URL(line.trim(), manifestUrl).toString();
      if (!isHttpUrl(absolute)) {
        out.push(line);
        continue;
      }
      out.push(proxyUrl(serverBase, streamId, absolute));
    } catch {
      out.push(line);
    }
  }
  return out.join('\n');
}

function manifestTtl(text) {
  if (text.includes('#EXT-X-ENDLIST')) return 300_000;
  if (text.includes('#EXT-X-STREAM-INF') || text.includes('#EXT-X-I-FRAME-STREAM-INF')) {
    return 30_000;
  }
  return 700;
}

function storeManifest(key, text, manifestUrl) {
  if (manifestCache.size > 800) {
    const oldest = manifestCache.keys().next().value;
    manifestCache.delete(oldest);
  }
  manifestCache.set(key, {
    text,
    manifestUrl,
    expires: Date.now() + manifestTtl(text),
  });
}

async function getManifest(stream, url, { force = false } = {}) {
  const key = `${stream.id}|${url}`;
  if (!force) {
    const cached = manifestCache.get(key);
    if (cached && cached.expires > Date.now()) return cached;
  }
  if (inFlight.has(key)) return inFlight.get(key);

  const task = (async () => {
    const response = await upstreamFetch(stream.id, url, {
      timeout: 15_000,
      variants: headerVariants(stream),
    });
    if (!response.ok) {
      const error = new Error(`HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    const text = await response.text();
    if (!text.includes('#EXTM3U') && !text.includes('#EXT-X-')) {
      const error = new Error('risposta non HLS');
      error.status = 502;
      throw error;
    }
    const entry = {
      text,
      manifestUrl: response.url || url,
      expires: Date.now() + manifestTtl(text),
    };
    storeManifest(key, entry.text, entry.manifestUrl);
    return entry;
  })();

  inFlight.set(key, task);
  try {
    return await task;
  } finally {
    inFlight.delete(key);
  }
}

export function selectStreams(channelId, limit = 4, requestedStreamId = null) {
  const channel = db
    .prepare('SELECT id, preferred_stream_id FROM channels WHERE id = ?')
    .get(channelId);
  if (!channel) return { channel: null, streams: [] };
  const streams = db
    .prepare(
      `SELECT * FROM streams
       WHERE channel_id = ? AND enabled = 1
       ORDER BY (id = ?) DESC,
                (id = ?) DESC,
                (status = 'online') DESC,
                quality_rank DESC,
                priority ASC,
                COALESCE(last_ok_at, 0) DESC,
                id ASC
       LIMIT ?`,
    )
    .all(
      channelId,
      requestedStreamId || '',
      channel.preferred_stream_id || '',
      limit,
    );
  return { channel, streams };
}

function markStream(streamId, status, { ms, error } = {}) {
  const now = Math.floor(Date.now() / 1000);
  if (status === 'online') {
    db.prepare(
      `UPDATE streams SET status = 'online', status_checked_at = ?, status_ms = ?,
       status_error = NULL, last_ok_at = ? WHERE id = ?`,
    ).run(now, ms ?? null, now, streamId);
  } else {
    db.prepare(
      `UPDATE streams SET status = 'offline', status_checked_at = ?, status_ms = ?,
       status_error = ? WHERE id = ?`,
    ).run(now, ms ?? null, String(error || '').slice(0, 400), streamId);
  }
  db.prepare(
    `UPDATE channels SET status = CASE
       WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channel_id = channels.id AND s.enabled = 1 AND s.status = 'online') THEN 'online'
       WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channel_id = channels.id AND s.enabled = 1 AND s.status = 'offline') THEN 'offline'
       ELSE 'unknown' END
     WHERE id = (SELECT channel_id FROM streams WHERE id = ?)`,
  ).run(streamId);
}

function setCors(reply) {
  reply.header('access-control-allow-origin', '*');
  reply.header('access-control-expose-headers', 'Content-Length, Content-Range');
}

function copyHeaders(reply, response) {
  for (const name of PASS_HEADERS) {
    const value = response.headers.get(name);
    if (value) reply.header(name, value);
  }
  // fetch decompresses gzip automatically; upstream length/encoding no longer apply.
  reply.removeHeader('content-encoding');
  reply.removeHeader('content-length');
}

function abortOnDisconnect(req, reply) {
  const controller = new AbortController();
  reply.raw.on('close', () => {
    if (!reply.raw.writableEnded) controller.abort();
  });
  return controller.signal;
}

async function serveManifest(stream, url, req, reply, { force = false } = {}) {
  const started = Date.now();
  const entry = await getManifest(stream, url, { force });
  markStream(stream.id, 'online', { ms: Date.now() - started });
  const serverBase = getBaseUrl(req);
  const body = rewritePlaylist(entry.text, entry.manifestUrl, stream.id, serverBase);
  setCors(reply);
  reply
    .header('content-type', 'application/vnd.apple.mpegurl; charset=utf-8')
    .header('cache-control', 'no-cache, no-store, must-revalidate')
    .header('pragma', 'no-cache');
  return reply.send(body);
}

async function pipeBinary(response, req, reply) {
  setCors(reply);
  copyHeaders(reply, response);
  if (!reply.getHeader('content-type')) {
    reply.header('content-type', 'application/octet-stream');
  }
  reply.header('cache-control', 'no-cache');
  reply.code(response.status === 206 ? 206 : response.status || 200);
  return reply.send(Readable.fromWeb(response.body));
}

async function proxyBinary(stream, url, req, reply) {
  const signal = abortOnDisconnect(req, reply);
  try {
    const response = await upstreamFetch(stream.id, url, {
      range: req.headers.range,
      timeout: 30_000,
      variants: headerVariants(stream),
    });
    if (!response.ok && response.status !== 304) {
      reply.code(response.status === 403 ? 502 : response.status);
      setCors(reply);
      return reply.send(`Upstream HTTP ${response.status}`);
    }
    return pipeBinary(response, req, reply);
  } catch (error) {
    if (signal.aborted) return reply;
    reply.code(502);
    setCors(reply);
    return reply.send(`Errore upstream: ${error.message}`);
  }
}

export async function handleStreamRequest(req, reply, channelId) {
  const requestedSource = String(req.query?.source || '')
    .trim()
    .slice(0, 256);
  // The app can ask for a specific source and then walk the source list when
  // one of them fails. The server still tries the other streams as a safety net.
  const { channel, streams } = selectStreams(channelId, 6, requestedSource);
  if (!channel) {
    reply.code(404);
    return reply.send('Canale non trovato');
  }
  if (!streams.length) {
    reply.code(404);
    return reply.send('Nessuna sorgente disponibile per questo canale');
  }

  const errors = [];
  for (const stream of streams) {
    const started = Date.now();
    try {
      const response = await upstreamFetch(stream.id, stream.url, {
        range: req.headers.range,
        timeout: 15_000,
        variants: headerVariants(stream),
      });
      if (!response.ok) {
        errors.push(`HTTP ${response.status}`);
        markStream(stream.id, 'offline', {
          ms: Date.now() - started,
          error: `HTTP ${response.status}`,
        });
        continue;
      }

      const contentType = response.headers.get('content-type') || '';
      if (looksLikeHls(stream.url, contentType)) {
        // Put the body back into the shared manifest pipeline by re-fetching
        // is wasteful; parse this response directly and cache it.
        const text = await response.text();
        if (!text.includes('#EXTM3U') && !text.includes('#EXT-X-')) {
          errors.push('non HLS');
          markStream(stream.id, 'offline', {
            ms: Date.now() - started,
            error: 'risposta non HLS',
          });
          continue;
        }
        const manifestUrl = response.url || stream.url;
        storeManifest(`${stream.id}|${stream.url}`, text, manifestUrl);
        markStream(stream.id, 'online', { ms: Date.now() - started });
        const serverBase = getBaseUrl(req);
        const body = rewritePlaylist(text, manifestUrl, stream.id, serverBase);
        setCors(reply);
        reply
          .header('content-type', 'application/vnd.apple.mpegurl; charset=utf-8')
          .header('cache-control', 'no-cache, no-store, must-revalidate');
        return reply.send(body);
      }

      markStream(stream.id, 'online', { ms: Date.now() - started });
      return pipeBinary(response, req, reply);
    } catch (error) {
      errors.push(error.message);
      markStream(stream.id, 'offline', {
        ms: Date.now() - started,
        error: error.message,
      });
    }
  }

  reply.code(502);
  setCors(reply);
  return reply.send(
    `Nessuna sorgente raggiungibile per ${channel.id}. Tentativi: ${errors.join(' | ')}`,
  );
}

export async function handleProxyToken(req, reply) {
  const target = verifyTarget(req.params.token);
  if (!target) {
    reply.code(403);
    return reply.send('Token non valido');
  }
  const stream = db.prepare('SELECT * FROM streams WHERE id = ?').get(target.streamId);
  if (!stream) {
    reply.code(404);
    return reply.send('Sorgente non trovata');
  }

  const started = Date.now();
  try {
    const response = await upstreamFetch(stream.id, target.url, {
      range: req.headers.range,
      timeout: 30_000,
      variants: headerVariants(stream),
    });
    if (!response.ok) {
      reply.code(response.status === 403 ? 502 : response.status);
      setCors(reply);
      return reply.send(`Upstream HTTP ${response.status}`);
    }
    const contentType = response.headers.get('content-type') || '';
    if (looksLikeHls(target.url, contentType)) {
      const text = await response.text();
      if (text.includes('#EXTM3U') || text.includes('#EXT-X-')) {
        const manifestUrl = response.url || target.url;
        storeManifest(`${stream.id}|${target.url}`, text, manifestUrl);
        const serverBase = getBaseUrl(req);
        const body = rewritePlaylist(text, manifestUrl, stream.id, serverBase);
        setCors(reply);
        reply
          .header('content-type', 'application/vnd.apple.mpegurl; charset=utf-8')
          .header('cache-control', 'no-cache, no-store, must-revalidate');
        return reply.send(body);
      }
    }
    return pipeBinary(response, req, reply);
  } catch (error) {
    reply.code(502);
    setCors(reply);
    return reply.send(`Errore upstream: ${error.message}`);
  }
}

export async function refreshManifest(channelId) {
  const { streams } = selectStreams(channelId, 2);
  for (const stream of streams) {
    try {
      const response = await upstreamFetch(stream.id, stream.url, {
        timeout: 12_000,
        variants: headerVariants(stream),
      });
      if (!response.ok) continue;
      const text = await response.text();
      if (text.includes('#EXTM3U')) {
        storeManifest(`${stream.id}|${stream.url}`, text, response.url || stream.url);
        return true;
      }
    } catch {
      // ignore
    }
  }
  return false;
}

export { serveManifest };
