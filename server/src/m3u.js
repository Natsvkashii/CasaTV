import { safeJsonParse } from './util.js';
import { categoryName, countryFlag, countryName } from './db.js';

/**
 * Parse an extended M3U playlist into entries.
 * Handles EXTINF attributes, EXTVLCOPT headers, EXTGRP and pipe-suffixed headers.
 */
function isHlsManifest(text) {
  const head = String(text || '').slice(0, 16384);
  const hasIptv = /#EXTINF:-1/i.test(head);
  if ((/#EXT-X-TARGETDURATION|#EXT-X-MEDIA-SEQUENCE/i.test(head)) && !hasIptv) {
    return true;
  }
  if (/#EXT-X-STREAM-INF/i.test(head) && !/#EXTINF:/i.test(head)) {
    return true;
  }
  return false;
}

function normalizePlsToM3u(text) {
  const head = String(text || '').trimStart().slice(0, 1024);
  if (!/^\[playlist\]/i.test(head) && !(head.includes('NumberOfEntries=') && /File1=/i.test(head))) {
    return text;
  }
  const files = new Map();
  const titles = new Map();
  for (const rawLine of String(text || '').split(/\r?\n/)) {
    const line = rawLine.trim();
    const fMatch = /^File(\d+)\s*=\s*(.+)$/i.exec(line);
    if (fMatch) {
      files.set(Number(fMatch[1]), fMatch[2].trim());
      continue;
    }
    const tMatch = /^Title(\d+)\s*=\s*(.+)$/i.exec(line);
    if (tMatch) titles.set(Number(tMatch[1]), tMatch[2].trim());
  }
  if (!files.size) return '';
  const out = ['#EXTM3U'];
  for (const [idx, url] of [...files.entries()].sort((a, b) => a[0] - b[0])) {
    const title = (titles.get(idx) || url.split('/').pop()?.split('?')[0] || `Channel ${idx}`).replace(/,/g, ' ');
    out.push(`#EXTINF:-1,${title}`, url);
  }
  return out.join('\n');
}

export function parseM3u(text) {
  const raw = normalizePlsToM3u(String(text || '').replace(/^\uFEFF/, ''));
  if (isHlsManifest(raw)) return [];
  const entries = [];
  let current = null;
  let pendingHeaders = {};

  const lines = raw.split(/\r?\n/);

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) continue;

    if (line.startsWith('#EXTINF:')) {
      if (current && current.url) {
        entries.push(current);
      }
      const { attrs, title, duration } = parseExtinf(line);
      current = {
        duration,
        attrs,
        title,
        headers: { ...pendingHeaders },
      };
      pendingHeaders = {};
      continue;
    }

    if (line.startsWith('#EXTVLCOPT:')) {
      const opt = line.slice('#EXTVLCOPT:'.length);
      const eq = opt.indexOf('=');
      if (eq !== -1) {
        const key = opt.slice(0, eq).trim().toLowerCase();
        const value = opt.slice(eq + 1).trim();
        if (key === 'http-user-agent') pendingHeaders.userAgent = value;
        if (key === 'http-referrer' || key === 'http-referer') {
          pendingHeaders.referrer = value;
        }
      }
      continue;
    }

    if (line.startsWith('#EXTGRP:')) {
      if (current) current.group = line.slice('#EXTGRP:'.length).trim();
      continue;
    }

    if (line.startsWith('#')) continue;

    if (current) {
      current.url = line;
      if (Object.keys(pendingHeaders).length) {
        current.headers = { ...current.headers, ...pendingHeaders };
        pendingHeaders = {};
      }
      entries.push(current);
      current = null;
    } else if (/^(https?|rtsp|rtmps?):\/\//i.test(line)) {
      const bareName = line.split('/').pop()?.split('?')[0]?.split('.')[0] || `Canale ${entries.length + 1}`;
      entries.push({
        duration: -1,
        attrs: {},
        title: bareName,
        headers: { ...pendingHeaders },
        url: line,
      });
      pendingHeaders = {};
    }
  }

  if (current && current.url) entries.push(current);
  return entries;
}

function parseExtinf(line) {
  const body = line.slice('#EXTINF:'.length);
  let comma = -1;
  let inQuotes = false;
  for (let i = 0; i < body.length; i += 1) {
    const ch = body[i];
    if (ch === '"') inQuotes = !inQuotes;
    else if (ch === ',' && !inQuotes) {
      comma = i;
      break;
    }
  }
  if (comma === -1) {
    return { duration: -1, attrs: parseAttrs(body), title: '' };
  }
  const head = body.slice(0, comma);
  const title = body.slice(comma + 1).trim();
  const durationMatch = /^(-?\d+(?:\.\d+)?)/.exec(head.trim());
  const duration = durationMatch ? Number(durationMatch[1]) : -1;
  const attrText = head.replace(/^-?\d+(?:\.\d+)?\s*/, '');
  return { duration, attrs: parseAttrs(attrText), title };
}

function parseAttrs(text) {
  const attrs = {};
  const re = /([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"/g;
  let match;
  while ((match = re.exec(text))) {
    attrs[match[1].toLowerCase()] = match[2];
  }
  return attrs;
}

function attr(value) {
  return String(value ?? '').replace(/"/g, "'").replace(/[\r\n]+/g, ' ');
}

export function playlistGroup(row, mode = 'country') {
  const categories = safeJsonParse(row.categories, []);
  const primary = categories.length ? categoryName(categories[0]) : 'Altro';
  const country = row.country || 'INT';
  const flag = countryFlag(country);
  const cname = countryName(country);
  switch (mode) {
    case 'category':
      return primary;
    case 'country-category':
      return `${flag} ${cname} · ${primary}`;
    case 'none':
      return 'CasaTV';
    case 'country':
    default:
      return `${flag} ${cname}`;
  }
}

/**
 * Generate an M3U playlist pointing every channel at the local stream proxy.
 */
export function generateM3u(rows, { baseUrl, groupBy = 'country', includeEpg = true } = {}) {
  const lines = [];
  const header = includeEpg
    ? `#EXTM3U x-tvg-url="${baseUrl}/epg.xml"`
    : '#EXTM3U';
  lines.push(header);
  for (const row of rows) {
    if (!row.enabled || row.hidden) continue;
    const logo = row.logo ? `${baseUrl}/logo/${encodeURIComponent(row.id)}` : '';
    const categories = safeJsonParse(row.categories, []);
    const languages = safeJsonParse(row.languages, []);
    const attrs = [
      'tvg-id="' + attr(row.id) + '"',
      logo ? 'tvg-logo="' + attr(logo) + '"' : null,
      row.number ? 'tvg-chno="' + row.number + '"' : null,
      'group-title="' + attr(playlistGroup(row, groupBy)) + '"',
      row.country ? 'tvg-country="' + attr(row.country) + '"' : null,
      languages.length ? 'tvg-language="' + attr(languages[0]) + '"' : null,
      'tvg-type="' + attr(categories[0] || 'general') + '"',
    ].filter(Boolean);
    lines.push(`#EXTINF:-1 ${attrs.join(' ')},${row.name}`);
    lines.push(`${baseUrl}/stream/${encodeURIComponent(row.id)}/playlist.m3u8`);
  }
  return `${lines.join('\n')}\n`;
}
