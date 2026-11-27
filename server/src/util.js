import crypto from 'node:crypto';

export function sha1(value) {
  return crypto.createHash('sha1').update(String(value)).digest('hex');
}

export function sha256(value) {
  return crypto.createHash('sha256').update(String(value)).digest('hex');
}

export function slugify(value) {
  return String(value || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80);
}

/** Stable id for fuzzy EPG matching: lowercase, letters+digits only. */
export function normalizeId(value) {
  return String(value || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
}

export function cleanChannelName(raw) {
  let name = String(raw || '').trim();
  name = name.replace(/\s*[\[(](?:not\s*24\/7|geo[- ]?blocked|1080p|720p|576p|480p|4k|uhd)[\])]\s*/gi, ' ');
  name = name.replace(/\s*\((?:\d{3,4}p)\)\s*$/i, '');
  name = name.replace(/\s*[-–—]\s*(?:HD|SD|FHD|UHD|4K)\s*$/i, '');
  name = name.replace(/\s+(?:HD|SD|FHD|UHD|4K)\s*$/i, '');
  name = name.replace(/\s{2,}/g, ' ').trim();
  return name || String(raw || '').trim();
}

export function parseQuality(...values) {
  for (const value of values) {
    if (!value) continue;
    const text = String(value);
    const explicit = text.match(/(\d{3,4})p\b/i);
    if (explicit) {
      const h = Number(explicit[1]);
      if (h >= 2160) return '4K';
      if (h >= 1080) return '1080p';
      if (h >= 720) return '720p';
      if (h >= 576) return '576p';
      if (h >= 480) return '480p';
      return `${h}p`;
    }
    if (/\b(?:uhd|4k)\b/i.test(text)) return '4K';
    if (/\bfhd\b/i.test(text)) return '1080p';
    if (/\bhd\b/i.test(text)) return '720p';
    if (/\bsd\b/i.test(text)) return 'SD';
  }
  return 'SD';
}

export function qualityRank(q) {
  return (
    {
      '4K': 50,
      '1080p': 40,
      '720p': 30,
      '576p': 20,
      '480p': 10,
      SD: 5,
    }[q] || 0
  );
}

const CATEGORY_ALIASES = {
  general: 'general',
  generalist: 'general',
  news: 'news',
  sport: 'sports',
  sports: 'sports',
  movie: 'movies',
  movies: 'movies',
  film: 'movies',
  kids: 'kids',
  children: 'kids',
  music: 'music',
  documentary: 'documentary',
  documentaries: 'documentary',
  entertainment: 'entertainment',
  comedy: 'comedy',
  series: 'series',
  education: 'education',
  culture: 'culture',
  lifestyle: 'lifestyle',
  religion: 'religious',
  religious: 'religious',
  shopping: 'shop',
  shop: 'shop',
  travel: 'travel',
  cooking: 'cooking',
  family: 'family',
  animation: 'animation',
  business: 'business',
  weather: 'weather',
  science: 'science',
  auto: 'auto',
  outdoor: 'outdoor',
  relax: 'relax',
  legislative: 'legislative',
  public: 'public',
  classic: 'classic',
  undefined: '',
};

export function categorySlug(value) {
  const slug = slugify(value);
  return CATEGORY_ALIASES[slug] ?? slug;
}

export function channelPriority(_name) {
  return 1000;
}

export function safeJsonParse(value, fallback) {
  try {
    const parsed = JSON.parse(value);
    return parsed ?? fallback;
  } catch {
    return fallback;
  }
}

export function hmacSign(data, secret) {
  return crypto.createHmac('sha256', secret).update(data).digest('base64url');
}

export function safeEqual(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

export function escapeXml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

export function xmltvDate(epochSeconds) {
  const d = new Date(epochSeconds * 1000);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}${p(
    d.getUTCHours(),
  )}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())} +0000`;
}

export function parseXmltvDate(value) {
  const m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{4})?/.exec(
    String(value || '').trim(),
  );
  if (!m) return null;
  const [, y, mo, d, h, mi = '00', s = '00', offset] = m;
  let epoch = Date.UTC(
    Number(y),
    Number(mo) - 1,
    Number(d),
    Number(h),
    Number(mi),
    Number(s),
  );
  if (offset) {
    const sign = offset.startsWith('-') ? -1 : 1;
    const hours = Number(offset.slice(1, 3));
    const minutes = Number(offset.slice(3, 5));
    epoch -= sign * (hours * 60 + minutes) * 60_000;
  }
  return Math.floor(epoch / 1000);
}

export function isHttpUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Parse IPTV URL suffix `url|User-Agent=...&Referer=...` into {url, headers}. */
export function splitPipeHeaders(rawUrl) {
  const value = String(rawUrl || '').trim();
  const index = value.indexOf('|');
  if (index === -1) return { url: value, headers: {} };
  const url = value.slice(0, index);
  const headers = {};
  for (const part of value.slice(index + 1).split('&')) {
    const eq = part.indexOf('=');
    if (eq === -1) continue;
    const key = part.slice(0, eq).trim().toLowerCase();
    const val = part.slice(eq + 1).trim();
    if (key === 'user-agent' || key === 'user_agent' || key === 'useragent') {
      headers.userAgent = val;
    } else if (key === 'referer' || key === 'referrer') {
      headers.referrer = val;
    } else if (key === 'origin') {
      headers.origin = val;
    }
  }
  return { url, headers };
}
