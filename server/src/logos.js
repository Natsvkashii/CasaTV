import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { config, paths } from './config.js';
import { db } from './db.js';
import { sha256 } from './util.js';

const memory = new Map(); // channelId -> { buffer, type, at }
const inflight = new Map();

function detectImage(buffer) {
  if (!buffer || buffer.length < 12) return null;
  if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47) {
    return 'image/png';
  }
  if (buffer[0] === 0xff && buffer[1] === 0xd8) return 'image/jpeg';
  if (buffer.slice(0, 4).toString('ascii') === 'GIF8') return 'image/gif';
  if (
    buffer.slice(0, 4).toString('ascii') === 'RIFF' &&
    buffer.slice(8, 12).toString('ascii') === 'WEBP'
  ) {
    return 'image/webp';
  }
  const head = buffer.slice(0, 400).toString('utf8').trim();
  if (head.startsWith('<svg') || head.includes('<svg')) return 'image/svg+xml';
  return null;
}

async function downloadLogo(url) {
  const response = await fetch(url, {
    headers: {
      'user-agent': config.userAgent,
      accept: 'image/avif,image/webp,image/png,image/jpeg,image/svg+xml,*/*',
      'accept-language': 'it-IT,it;q=0.9,en;q=0.8',
    },
    redirect: 'follow',
    signal: AbortSignal.timeout(12_000),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const buffer = Buffer.from(await response.arrayBuffer());
  if (buffer.length > 3 * 1024 * 1024) throw new Error('immagine troppo grande');
  const detected = detectImage(buffer) || response.headers.get('content-type') || 'image/png';
  if (!detected.startsWith('image/')) throw new Error('contenuto non immagine');
  return { buffer, type: detected.split(';')[0] };
}

export async function getLogo(req, reply, channelId) {
  const channel = db
    .prepare('SELECT id, logo FROM channels WHERE id = ?')
    .get(channelId);
  if (!channel) {
    reply.code(404);
    return reply.send('Canale non trovato');
  }
  if (!channel.logo) {
    reply.code(404);
    return reply.send('Nessun logo');
  }

  const cachedMem = memory.get(channel.id);
  if (cachedMem && cachedMem.url === channel.logo) {
    return sendImage(reply, cachedMem.buffer, cachedMem.type, channel.logo);
  }

  const key = sha256(channel.logo);
  const binFile = path.join(paths.logos, `${key}.bin`);
  const metaFile = path.join(paths.logos, `${key}.json`);

  try {
    const [buffer, metaRaw] = await Promise.all([
      fsp.readFile(binFile),
      fsp.readFile(metaFile, 'utf8'),
    ]);
    const meta = JSON.parse(metaRaw);
    if (Date.now() - Number(meta.at || 0) < 7 * 24 * 3600 * 1000) {
      memory.set(channel.id, { buffer, type: meta.type, at: Date.now(), url: channel.logo });
      if (memory.size > 300) memory.delete(memory.keys().next().value);
      return sendImage(reply, buffer, meta.type, channel.logo);
    }
  } catch {
    // cache miss
  }

  const taskKey = `${channel.id}|${channel.logo}`;
  if (inflight.has(taskKey)) {
    const result = await inflight.get(taskKey).catch(() => null);
    if (result) return sendImage(reply, result.buffer, result.type, channel.logo);
  }

  const task = downloadLogo(channel.logo);
  inflight.set(taskKey, task);
  try {
    const { buffer, type } = await task;
    await fsp.writeFile(binFile, buffer);
    await fsp.writeFile(metaFile, JSON.stringify({ at: Date.now(), type, url: channel.logo }));
    memory.set(channel.id, { buffer, type, at: Date.now(), url: channel.logo });
    if (memory.size > 300) memory.delete(memory.keys().next().value);
    return sendImage(reply, buffer, type, channel.logo);
  } catch {
    // Fall back to the original URL: the app's image loader may still reach it.
    reply.header('cache-control', 'public, max-age=3600');
    return reply.redirect(channel.logo);
  } finally {
    inflight.delete(taskKey);
  }
}

function sendImage(reply, buffer, type, url) {
  reply
    .header('content-type', type)
    .header('cache-control', 'public, max-age=604800, immutable')
    .header('access-control-allow-origin', '*')
    .header('etag', `"${sha256(url).slice(0, 24)}"`);
  return reply.send(buffer);
}

// Periodic cleanup of the on-disk logo cache (keep it under control).
export async function cleanLogoCache(maxAgeDays = 45) {
  try {
    const files = await fsp.readdir(paths.logos);
    const cutoff = Date.now() - maxAgeDays * 24 * 3600 * 1000;
    await Promise.all(
      files
        .filter((f) => f.endsWith('.bin'))
        .map(async (f) => {
          const full = path.join(paths.logos, f);
          const stat = await fs.promises.stat(full).catch(() => null);
          if (stat && stat.mtimeMs < cutoff) {
            await fsp.unlink(full).catch(() => {});
            await fsp.unlink(full.replace(/\.bin$/, '.json')).catch(() => {});
          }
        }),
    );
  } catch {
    // ignore
  }
}
