import { safeJsonParse } from './util.js';
import { categoryName, countryFlag, countryName } from './db.js';

/**
 * Parse an extended M3U playlist into entries.
 * Handles EXTINF attributes, EXTVLCOPT headers, EXTGRP and pipe-suffixed headers.
 */
export function parseM3u(text) {
  const entries = [];
  let current = null;
  let pendingHeaders = {};

  const lines = String(text || '')
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/);

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
