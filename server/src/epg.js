import zlib from 'node:zlib';
import { XMLParser } from 'fast-xml-parser';
import { config } from './config.js';
import {
  db,
  getSetting,
  getStats,
  setSetting,
  transaction,
} from './db.js';
import {
  escapeXml,
  normalizeId,
  parseXmltvDate,
  xmltvDate,
} from './util.js';

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  textNodeName: '#text',
  trimValues: true,
  parseTagValue: false,
  parseAttributeValue: false,
  isArray: (name) => ['programme', 'channel', 'display-name', 'icon', 'category'].includes(name),
});

export const epgState = {
  running: false,
  sourceId: null,
  phase: 'idle',
  startedAt: null,
  finishedAt: null,
  error: null,
  result: null,
};

let epgPromise = null;

function log(...args) {
  console.log('[epg]', ...args);
}

function textOf(value) {
  if (value == null) return '';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (Array.isArray(value)) return textOf(value[0]);
  if (typeof value === 'object') {
    if (value['#text'] != null) return String(value['#text']);
    if (value.__text != null) return String(value.__text);
  }
  return '';
}

function first(value) {
  return Array.isArray(value) ? value[0] : value;
}

export function listSources() {
  return db.prepare('SELECT * FROM epg_sources ORDER BY id ASC').all();
}

export function addSource(name, url) {
  const value = String(url || '').trim();
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error('URL EPG non valido');
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('L’EPG deve usare http o https');
  }
  const info = db
    .prepare(
      'INSERT INTO epg_sources (name, url) VALUES (?, ?) ON CONFLICT(url) DO UPDATE SET name = excluded.name',
    )
    .run(String(name || parsed.hostname).slice(0, 120), value);
  return db
    .prepare('SELECT * FROM epg_sources WHERE url = ?')
    .get(value);
}

export function removeSource(id) {
  db.prepare('DELETE FROM epg_sources WHERE id = ?').run(Number(id));
}

export function seedDefaultEpg() {
  if (getSetting('epg_seeded') === '1') return;
  setSetting('epg_seeded', '1');
  if (!config.defaultEpgUrl) return;
  const count = db.prepare('SELECT COUNT(*) AS n FROM epg_sources').get().n;
  if (count > 0) return;
  try {
    insertDefaultSource();
    log('sorgente EPG predefinita aggiunta');
  } catch {
    // ignore
  }
}

function insertDefaultSource() {
  db.prepare(
    'INSERT OR IGNORE INTO epg_sources (name, url) VALUES (?, ?)',
  ).run('EPG Predefinita', config.defaultEpgUrl);
}

async function downloadXml(url) {
  const response = await fetch(url, {
    headers: {
      'user-agent': config.userAgent,
      accept: 'application/xml,text/xml,application/gzip,*/*',
    },
    redirect: 'follow',
    signal: AbortSignal.timeout(120_000),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const contentLength = Number(response.headers.get('content-length') || 0);
  if (contentLength > 120 * 1024 * 1024) throw new Error('file EPG troppo grande');
  let buffer = Buffer.from(await response.arrayBuffer());
  if (buffer.length > 120 * 1024 * 1024) throw new Error('file EPG troppo grande');
  if (buffer[0] === 0x1f && buffer[1] === 0x8b) {
    buffer = zlib.gunzipSync(buffer, { maxOutputLength: 150 * 1024 * 1024 });
  }
  return buffer.toString('utf8').replace(/^\uFEFF/, '');
}

function parseXmltv(xml) {
  const doc = parser.parse(xml);
  const tv = doc.tv || doc;
  const channels = (tv.channel || []).map((channel) => ({
    id: String(channel['@_id'] || '').trim(),
    name: textOf(channel['display-name']),
    icon: channel.icon?.[0]?.['@_src'] || null,
  })).filter((c) => c.id);

  const programmes = [];
  for (const p of tv.programme || []) {
    const channelId = String(p['@_channel'] || '').trim();
    const start = parseXmltvDate(p['@_start']);
    const stop = parseXmltvDate(p['@_stop']);
    if (!channelId || !start || !stop || stop <= start) continue;
    const title = textOf(p.title);
    if (!title) continue;
    programmes.push({
      channelId,
      channelNorm: normalizeId(channelId),
      start,
      stop,
      title: title.slice(0, 300),
      subtitle: textOf(p['sub-title']).slice(0, 300) || null,
      description: textOf(p.desc).slice(0, 3000) || null,
      category: textOf(first(p.category)).slice(0, 100) || null,
      icon: first(p.icon)?.['@_src'] || null,
    });
  }
  return { channels, programmes };
}

async function refreshSourceInternal(source) {
  const started = Date.now();
  Object.assign(epgState, {
    running: true,
    sourceId: source.id,
    phase: 'download',
    startedAt: Math.floor(started / 1000),
    finishedAt: null,
    error: null,
    result: null,
  });
  log(`scarico ${source.name}`);
  try {
    const xml = await downloadXml(source.url);
    epgState.phase = 'parse';
    const { channels, programmes } = parseXmltv(xml);
    epgState.phase = 'save';

    transaction(() => {
      db.prepare('DELETE FROM epg_programs WHERE source_id = ?').run(source.id);
      const insert = db.prepare(`
        INSERT INTO epg_programs (
          source_id, channel_id, channel_norm, start, stop, title, subtitle,
          description, category, icon
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const p of programmes) {
        insert.run(
          source.id,
          p.channelId,
          p.channelNorm,
          p.start,
          p.stop,
          p.title,
          p.subtitle,
          p.description,
          p.category,
          p.icon,
        );
      }
      db.prepare(
        `UPDATE epg_sources SET last_fetch = ?, last_status = 'ok',
         channel_count = ?, program_count = ? WHERE id = ?`,
      ).run(
        Math.floor(Date.now() / 1000),
        channels.length,
        programmes.length,
        source.id,
      );
    });

    const result = {
      sourceId: source.id,
      name: source.name,
      channels: channels.length,
      programs: programmes.length,
      durationMs: Date.now() - started,
    };
    epgState.phase = 'done';
    epgState.running = false;
    epgState.finishedAt = Math.floor(Date.now() / 1000);
    epgState.result = result;
    log(`${source.name}: ${programmes.length} programmi`);
    return result;
  } catch (error) {
    db.prepare(
      "UPDATE epg_sources SET last_status = ?, last_fetch = ? WHERE id = ?",
    ).run(`error: ${String(error.message).slice(0, 200)}`, Math.floor(Date.now() / 1000), source.id);
    epgState.running = false;
    epgState.phase = 'error';
    epgState.error = error.message;
    epgState.finishedAt = Math.floor(Date.now() / 1000);
    log(`errore ${source.name}: ${error.message}`);
    throw error;
  }
}

export async function refreshSource(id) {
  const source = db.prepare('SELECT * FROM epg_sources WHERE id = ?').get(Number(id));
  if (!source) throw new Error('Sorgente EPG non trovata');
  if (epgPromise) return epgPromise;
  epgPromise = refreshSourceInternal(source).finally(() => {
    epgPromise = null;
  });
  return epgPromise;
}

export async function refreshAllSources({ force = false } = {}) {
  if (epgPromise) return epgPromise;
  const sources = db
    .prepare('SELECT * FROM epg_sources WHERE enabled = 1 ORDER BY id ASC')
    .all();
  const now = Math.floor(Date.now() / 1000);
  const due = sources.filter(
    (s) => force || !s.last_fetch || now - s.last_fetch > 12 * 3600,
  );
  if (!due.length) return [];
  epgPromise = (async () => {
    const results = [];
    for (const source of due) {
      try {
        results.push(await refreshSourceInternal(source));
      } catch (error) {
        results.push({ sourceId: source.id, name: source.name, error: error.message });
      }
    }
    return results;
  })().finally(() => {
    epgPromise = null;
  });
  return epgPromise;
}

export function hasFreshEpg() {
  const row = db
    .prepare(
      "SELECT COUNT(*) AS n FROM epg_sources WHERE enabled = 1 AND last_status = 'ok' AND program_count > 0",
    )
    .get();
  return row.n > 0;
}

export function epgChannel(channelId) {
  const norm = normalizeId(channelId);
  const now = Math.floor(Date.now() / 1000);
  const rows = db
    .prepare(
      `SELECT title, subtitle, description, category, start, stop, icon
       FROM epg_programs WHERE channel_norm = ? AND stop > ? AND start < ?
       ORDER BY start ASC LIMIT 24`,
    )
    .all(norm, now - 6 * 3600, now + 36 * 3600);
  const current = rows.find((p) => p.start <= now && p.stop > now) || null;
  const next = rows.find((p) => p.start > now) || null;
  return {
    channelId,
    now: current,
    next,
    upcoming: rows.filter((p) => p.start > now).slice(0, 8),
  };
}

let xmlCache = { key: null, xml: null, gz: null, at: 0 };

export function generateXmltv(baseUrl) {
  const now = Math.floor(Date.now() / 1000);
  const windowStart = now - 6 * 3600;
  const windowEnd = now + 48 * 3600;
  const cacheKey = `${baseUrl}|${Math.floor(now / 300)}`;
  if (xmlCache.key === cacheKey && xmlCache.xml) {
    return xmlCache;
  }

  const channels = db
    .prepare(
      'SELECT id, name, logo, country FROM channels WHERE enabled = 1 AND hidden = 0',
    )
    .all();
  const byNorm = new Map();
  for (const channel of channels) {
    const norm = normalizeId(channel.id);
    if (!byNorm.has(norm)) byNorm.set(norm, channel);
  }

  const programmes = db
    .prepare(
      `SELECT channel_norm, start, stop, title, subtitle, description, category
       FROM epg_programs WHERE stop > ? AND start < ? ORDER BY channel_norm, start ASC`,
    )
    .all(windowStart, windowEnd);

  const matched = [];
  const usedChannels = new Set();
  for (const p of programmes) {
    const channel = byNorm.get(p.channel_norm);
    if (!channel) continue;
    usedChannels.add(channel.id);
    matched.push({ ...p, channel });
  }
  if (!matched.length) {
    xmlCache = { key: cacheKey, xml: null, gz: null, at: Date.now() };
    return xmlCache;
  }

  const parts = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<!DOCTYPE tv SYSTEM "xmltv.dtd">',
    `<tv generator-info-name="CasaTV" generator-info-url="${escapeXml(baseUrl)}">`,
  ];
  for (const channel of channels) {
    if (!usedChannels.has(channel.id)) continue;
    const logo = channel.logo
      ? `${baseUrl}/logo/${encodeURIComponent(channel.id)}`
      : '';
    parts.push(
      `  <channel id="${escapeXml(channel.id)}"><display-name>${escapeXml(
        channel.name,
      )}</display-name>${logo ? `<icon src="${escapeXml(logo)}" />` : ''}</channel>`,
    );
  }
  for (const p of matched) {
    parts.push(
      `  <programme start="${xmltvDate(p.start)}" stop="${xmltvDate(p.stop)}" channel="${escapeXml(
        p.channel.id,
      )}"><title lang="it">${escapeXml(p.title)}</title>${
        p.subtitle ? `<sub-title lang="it">${escapeXml(p.subtitle)}</sub-title>` : ''
      }${
        p.description ? `<desc lang="it">${escapeXml(p.description)}</desc>` : ''
      }${p.category ? `<category lang="it">${escapeXml(p.category)}</category>` : ''}</programme>`,
    );
  }
  parts.push('</tv>');
  const xml = parts.join('\n');
  xmlCache = {
    key: cacheKey,
    xml,
    gz: zlib.gzipSync(Buffer.from(xml, 'utf8')),
    at: Date.now(),
  };
  return xmlCache;
}

export function getEpgStats() {
  const sources = listSources();
  const programs = db.prepare('SELECT COUNT(*) AS n FROM epg_programs').get().n;
  const matched = db
    .prepare(
      `SELECT COUNT(DISTINCT p.channel_norm) AS n FROM epg_programs p
       JOIN channels c ON replace(replace(lower(c.id), '.', ''), '-', '') = p.channel_norm`,
    )
    .get().n;
  return { sources, programs, matchedChannels: matched };
}

export function autoRefreshEpg() {
  if (getSetting('epg_auto_refresh', '1') !== '1') return;
  const now = Math.floor(Date.now() / 1000);
  const stale = db
    .prepare(
      'SELECT COUNT(*) AS n FROM epg_sources WHERE enabled = 1 AND (last_fetch IS NULL OR last_fetch < ?)',
    )
    .get(now - 12 * 3600).n;
  if (!stale) return;
  setTimeout(() => {
    refreshAllSources().catch((error) => log(error.message));
  }, 8000);
}

export function epgGuide({ country, hours = 12, limit = 200 } = {}) {
  const now = Math.floor(Date.now() / 1000);
  const end = now + Math.max(1, Math.min(Number(hours) || 12, 48)) * 3600;
  const params = [];
  let countrySql = '';
  if (country) {
    countrySql = ' AND c.country = ?';
    params.push(String(country).toUpperCase());
  }
  params.push(Math.max(1, Math.min(Number(limit) || 200, 500)));
  const channels = db
    .prepare(
      `SELECT c.id, c.name, c.number, c.logo, c.country
       FROM channels c WHERE c.enabled = 1 AND c.hidden = 0${countrySql}
       ORDER BY c.number IS NULL, c.number ASC, c.name LIMIT ?`,
    )
    .all(...params);
  const byNorm = new Map();
  for (const ch of channels) byNorm.set(normalizeId(ch.id), ch);
  const norms = [...byNorm.keys()];
  if (!norms.length) return { channels: [], programs: [] };
  const placeholders = norms.map(() => '?').join(',');
  const programs = db
    .prepare(
      `SELECT channel_norm, start, stop, title, subtitle, description, category
       FROM epg_programs WHERE channel_norm IN (${placeholders}) AND stop > ? AND start < ?
       ORDER BY start ASC`,
    )
    .all(...norms, now, end);
  return {
    from: now,
    to: end,
    channels: channels.map((c) => ({
      ...c,
      logo: c.logo ? `/logo/${encodeURIComponent(c.id)}` : null,
    })),
    programs: programs
      .map((p) => ({
        channelId: byNorm.get(p.channel_norm)?.id,
        start: p.start,
        stop: p.stop,
        title: p.title,
        subtitle: p.subtitle,
        description: p.description,
        category: p.category,
      }))
      .filter((p) => p.channelId),
  };
}

export { getStats };
