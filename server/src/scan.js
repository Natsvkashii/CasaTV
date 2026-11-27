import { config } from './config.js';
import { db, getSetting } from './db.js';
import { sleep } from './util.js';

export const scanState = {
  running: false,
  id: null,
  mode: null,
  total: 0,
  checked: 0,
  online: 0,
  offline: 0,
  startedAt: null,
  finishedAt: null,
  error: null,
  current: null,
  stopRequested: false,
};

let scanPromise = null;

function log(...args) {
  console.log('[scan]', ...args);
}

function headersFor(stream, { fallback = false } = {}) {
  const headers = {
    'user-agent': fallback ? config.userAgent : stream.user_agent || config.userAgent,
    accept: '*/*',
    'accept-language': 'it-IT,it;q=0.9,en;q=0.8',
  };
  if (!fallback && stream.referrer) {
    headers.referer = stream.referrer;
    try {
      headers.origin = new URL(stream.referrer).origin;
    } catch {
      // ignore
    }
  }
  return headers;
}

async function readHead(response, maxBytes = 32 * 1024) {
  if (!response.body) return Buffer.alloc(0);
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (total < maxBytes) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value) {
        const chunk = Buffer.from(value);
        chunks.push(chunk);
        total += chunk.length;
      }
    }
  } finally {
    await reader.cancel().catch(() => {});
  }
  return Buffer.concat(chunks).subarray(0, maxBytes);
}

async function tryFetch(stream, { range = 'bytes=0-32768', fallback = false } = {}) {
  const headers = headersFor(stream, { fallback });
  if (range) headers.range = range;
  return fetch(stream.url, {
    headers,
    redirect: 'follow',
    signal: AbortSignal.timeout(9_000),
  });
}

export async function checkStream(stream) {
  const started = Date.now();
  try {
    let response;
    try {
      response = await tryFetch(stream);
    } catch (error) {
      response = await tryFetch(stream, { fallback: true }).catch(() => {
        throw error;
      });
    }

    if (response.status === 416) {
      response = await tryFetch(stream, { range: null });
    }

    if (!response.ok && response.status !== 206) {
      if (response.status === 403) {
        response = await tryFetch(stream, { fallback: true });
      }
      if (!response.ok && response.status !== 206) {
        return {
          status: 'offline',
          ms: Date.now() - started,
          error: `HTTP ${response.status}`,
        };
      }
    }

    const type = String(response.headers.get('content-type') || '').toLowerCase();
    const body = await readHead(response);
    const text = body.toString('utf8');
    const isHls =
      type.includes('mpegurl') ||
      /\.m3u8?(?:$|\?)/i.test(stream.url) ||
      text.includes('#EXTM3U') ||
      text.includes('#EXT-X-');
    if (isHls) {
      if (text.includes('#EXTM3U') || text.includes('#EXT-X-')) {
        return { status: 'online', ms: Date.now() - started };
      }
      return {
        status: 'offline',
        ms: Date.now() - started,
        error: 'manifest HLS non valido',
      };
    }
    if (type.includes('text/html')) {
      return {
        status: 'offline',
        ms: Date.now() - started,
        error: 'risposta HTML invece del flusso',
      };
    }
    if (body.length === 0) {
      return {
        status: 'offline',
        ms: Date.now() - started,
        error: 'risposta vuota',
      };
    }
    return { status: 'online', ms: Date.now() - started };
  } catch (error) {
    return {
      status: 'offline',
      ms: Date.now() - started,
      error: error.name === 'TimeoutError' ? 'timeout' : error.message,
    };
  }
}

function updateStreamStatus(streamId, result) {
  const now = Math.floor(Date.now() / 1000);
  if (result.status === 'online') {
    db.prepare(
      `UPDATE streams SET status = 'online', status_checked_at = ?, status_ms = ?,
       status_error = NULL, last_ok_at = ? WHERE id = ?`,
    ).run(now, result.ms, now, streamId);
  } else {
    db.prepare(
      `UPDATE streams SET status = 'offline', status_checked_at = ?, status_ms = ?,
       status_error = ? WHERE id = ?`,
    ).run(now, result.ms, String(result.error || '').slice(0, 400), streamId);
  }
}

export function recomputeChannelStatuses() {
  db.exec(`
    UPDATE channels SET status = CASE
      WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channel_id = channels.id AND s.enabled = 1 AND s.status = 'online') THEN 'online'
      WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channel_id = channels.id AND s.enabled = 1 AND s.status = 'offline') THEN 'offline'
      ELSE 'unknown'
    END
    WHERE enabled = 1
  `);
}

export async function checkChannel(channelId) {
  const streams = db
    .prepare(
      `SELECT s.* FROM streams s
       WHERE s.channel_id = ? AND s.enabled = 1
       ORDER BY s.quality_rank DESC, s.priority ASC LIMIT 6`,
    )
    .all(channelId);
  if (!streams.length) return { checked: 0, online: 0 };
  let online = 0;
  for (const stream of streams) {
    const result = await checkStream(stream);
    updateStreamStatus(stream.id, result);
    if (result.status === 'online') online += 1;
    else if (online > 0) break;
  }
  recomputeChannelStatuses();
  return { checked: streams.length, online };
}

export function getLastScan() {
  return db
    .prepare('SELECT * FROM scan_runs ORDER BY id DESC LIMIT 1')
    .get();
}

function streamsForScan(mode, limit) {
  const defaultCountry = (getSetting('default_country') || config.defaultCountry).toUpperCase();
  let where = 's.enabled = 1 AND c.enabled = 1';
  const params = [];
  if (mode === 'quick') {
    where += ' AND (c.favorite = 1 OR c.country = ?)';
    params.push(defaultCountry);
  } else if (mode === 'stale') {
    where +=
      " AND (s.status = 'unknown' OR s.status_checked_at IS NULL OR s.status_checked_at < ?)";
    params.push(Math.floor(Date.now() / 1000) - 24 * 3600);
  }
  const limitSql = limit ? ` LIMIT ${Math.max(1, Number(limit))}` : '';
  return db
    .prepare(
      `SELECT s.* FROM streams s JOIN channels c ON c.id = s.channel_id
       WHERE ${where}
       ORDER BY c.favorite DESC, s.quality_rank DESC, s.channel_id, s.priority ASC${limitSql}`,
    )
    .all(...params);
}

export function stopScan() {
  if (scanState.running) scanState.stopRequested = true;
}

export async function startScan({ mode = 'quick', limit = null } = {}) {
  if (scanPromise) return scanState;
  scanPromise = (async () => {
    const streams = streamsForScan(mode, limit);
    if (!streams.length) {
      return scanState;
    }
    const now = Math.floor(Date.now() / 1000);
    const insert = db
      .prepare(
        `INSERT INTO scan_runs (mode, started_at, total, status) VALUES (?, ?, ?, 'running')`,
      )
      .run(mode, now, streams.length);
    const runId = Number(insert.lastInsertRowid);

    Object.assign(scanState, {
      running: true,
      id: runId,
      mode,
      total: streams.length,
      checked: 0,
      online: 0,
      offline: 0,
      startedAt: now,
      finishedAt: null,
      error: null,
      current: null,
      stopRequested: false,
    });
    log(`avvio scansione ${mode}: ${streams.length} sorgenti`);

    const concurrency = Math.max(1, Number(getSetting('scan_concurrency', '12')) || 12);
    let index = 0;
    let persistedAt = 0;

    const worker = async () => {
      while (index < streams.length && !scanState.stopRequested) {
        const stream = streams[index];
        index += 1;
        scanState.current = stream.channel_id;
        const result = await checkStream(stream);
        updateStreamStatus(stream.id, result);
        scanState.checked += 1;
        if (result.status === 'online') scanState.online += 1;
        else scanState.offline += 1;

        const nowSec = Date.now();
        if (nowSec - persistedAt > 4000) {
          persistedAt = nowSec;
          db.prepare(
            `UPDATE scan_runs SET checked = ?, online = ?, offline = ? WHERE id = ?`,
          ).run(scanState.checked, scanState.online, scanState.offline, runId);
        }
      }
    };

    try {
      await Promise.all(
        Array.from({ length: Math.min(concurrency, streams.length) }, () => worker()),
      );
      recomputeChannelStatuses();
      const finished = Math.floor(Date.now() / 1000);
      const status = scanState.stopRequested ? 'stopped' : 'done';
      db.prepare(
        `UPDATE scan_runs SET finished_at = ?, checked = ?, online = ?, offline = ?, status = ? WHERE id = ?`,
      ).run(finished, scanState.checked, scanState.online, scanState.offline, status, runId);
      Object.assign(scanState, {
        running: false,
        finishedAt: finished,
        current: null,
      });
      log(
        `scansione ${status}: ${scanState.checked}/${scanState.total}, ${scanState.online} online, ${scanState.offline} offline`,
      );
    } catch (error) {
      const finished = Math.floor(Date.now() / 1000);
      db.prepare(
        `UPDATE scan_runs SET finished_at = ?, checked = ?, online = ?, offline = ?, status = 'error', error = ? WHERE id = ?`,
      ).run(
        finished,
        scanState.checked,
        scanState.online,
        scanState.offline,
        String(error.message),
        runId,
      );
      scanState.running = false;
      scanState.finishedAt = finished;
      scanState.error = error.message;
      log(`errore scansione: ${error.stack || error.message}`);
    } finally {
      scanState.current = null;
      scanPromise = null;
    }
    return scanState;
  })();

  return scanPromise;
}

export function startScanInBackground(options) {
  startScan(options).catch((error) => log(error.message));
  return scanState;
}

let scheduler = null;

export function startScanScheduler() {
  if (scheduler) return;
  scheduler = setInterval(async () => {
    if (scanState.running) return;
    if (getSetting('auto_scan', '0') !== '1') return;
    const intervalHours = Math.max(1, Number(getSetting('scan_interval_hours', '24')) || 24);
    const last = getLastScan();
    const lastAt = last?.finished_at || last?.started_at || 0;
    if (Date.now() / 1000 - lastAt < intervalHours * 3600) return;
    log('scansione automatica programmata');
    await startScan({ mode: 'stale' }).catch(() => {});
  }, 30 * 60 * 1000);
}

export async function waitForScanIdle() {
  if (scanPromise) await scanPromise;
}

export { sleep };
