import fs from 'node:fs/promises';
import path from 'node:path';
import { config, paths } from './config.js';
import {
  db,
  getPlaylist,
  getSetting,
  refreshMeta,
  samplePlaylistChannels,
  savePlaylist,
  setSetting,
  transaction,
  updatePlaylistStatus,
} from './db.js';
import { parseM3u } from './m3u.js';
import {
  categorySlug,
  channelPriority,
  cleanChannelName,
  parseQuality,
  qualityRank,
  safeJsonParse,
  sha1,
  slugify,
  splitPipeHeaders,
} from './util.js';

export const ingestState = {
  running: false,
  phase: 'idle',
  startedAt: null,
  finishedAt: null,
  error: null,
  stats: null,
  reason: null,
  source: null,
};

let ingestPromise = null;

function log(...args) {
  console.log('[catalog]', ...args);
}

async function readJson(file, fallback = null) {
  try {
    return JSON.parse(await fs.readFile(file, 'utf8'));
  } catch {
    return fallback;
  }
}

async function fetchCached(url, filename, { force = false, timeout = 120_000 } = {}) {
  const file = path.join(paths.cache, filename);
  const metaFile = `${file}.meta.json`;
  const cachedMeta = (await readJson(metaFile, {})) || {};
  const headers = { 'user-agent': config.userAgent, accept: '*/*' };
  if (!force && cachedMeta.etag) headers['if-none-match'] = cachedMeta.etag;
  if (!force && cachedMeta.lastModified) {
    headers['if-modified-since'] = cachedMeta.lastModified;
  }

  let response;
  try {
    response = await fetch(url, {
      headers,
      redirect: 'follow',
      signal: AbortSignal.timeout(timeout),
    });
  } catch (error) {
    const cached = await fs.readFile(file, 'utf8').catch(() => null);
    if (cached != null) {
      log(`download failed (${filename}), using cache: ${error.message}`);
      return { text: cached, fromCache: true, url };
    }
    throw new Error(`Impossibile scaricare ${url}: ${error.message}`);
  }

  if (response.status === 304) {
    const cached = await fs.readFile(file, 'utf8').catch(() => null);
    if (cached != null) {
      log(`${filename}: non modificato (304)`);
      return { text: cached, fromCache: true, url };
    }
  }

  if (!response.ok) {
    const cached = await fs.readFile(file, 'utf8').catch(() => null);
    if (cached != null) {
      log(`${filename}: HTTP ${response.status}, uso cache`);
      return { text: cached, fromCache: true, url };
    }
    throw new Error(`HTTP ${response.status} per ${url}`);
  }

  const text = await response.text();
  await fs.writeFile(file, text);
  await fs.writeFile(
    metaFile,
    JSON.stringify(
      {
        url,
        etag: response.headers.get('etag'),
        lastModified: response.headers.get('last-modified'),
        fetchedAt: Date.now(),
      },
      null,
      2,
    ),
  );
  return { text, fromCache: false, url };
}

async function fetchJson(url, filename, options) {
  const { text } = await fetchCached(url, filename, options);
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`JSON non valido da ${url}: ${error.message}`);
  }
}

function inferCountry(baseId, apiChannel, attrs, countries) {
  if (apiChannel?.country) return apiChannel.country.toUpperCase();
  const fromAttr = String(attrs['tvg-country'] || '')
    .split(/[,;]/)
    .map((value) => value.trim().toUpperCase())
    .find((value) => /^[A-Z]{2}$/.test(value));
  if (fromAttr) return fromAttr;
  const match = /\.([a-z]{2})$/i.exec(baseId);
  if (match) {
    const code = match[1].toUpperCase();
    if (countries.has(code)) return code;
  }
  return null;
}

const LANGUAGE_CODES = {
  italian: 'ita',
  english: 'eng',
  spanish: 'spa',
  french: 'fra',
  german: 'deu',
  portuguese: 'por',
  arabic: 'ara',
  russian: 'rus',
  chinese: 'zho',
  japanese: 'jpn',
  korean: 'kor',
  dutch: 'nld',
  polish: 'pol',
  turkish: 'tur',
  hindi: 'hin',
  swedish: 'swe',
  norwegian: 'nor',
  danish: 'dan',
  finnish: 'fin',
  greek: 'ell',
  czech: 'ces',
  romanian: 'ron',
  hungarian: 'hun',
  ukrainian: 'ukr',
  catalan: 'cat',
  galician: 'glg',
  basque: 'eus',
  albanian: 'sqi',
  serbian: 'srp',
  croatian: 'hrv',
  slovak: 'slk',
  slovene: 'slv',
  bulgarian: 'bul',
  lithuanian: 'lit',
  latvian: 'lav',
  estonian: 'est',
  icelandic: 'isl',
  irish: 'gle',
  welsh: 'cym',
  afrikaans: 'afr',
  swahili: 'swa',
  filipino: 'fil',
  bengali: 'ben',
  urdu: 'urd',
  persian: 'fas',
  hebrew: 'heb',
  thai: 'tha',
  vietnamese: 'vie',
  indonesian: 'ind',
  malay: 'msa',
};

function languageCode(value) {
  const raw = String(value || '').trim().toLowerCase();
  if (!raw) return null;
  if (LANGUAGE_CODES[raw]) return LANGUAGE_CODES[raw];
  if (/^[a-z]{2}$/.test(raw)) {
    const byTwo = Object.entries(LANGUAGE_CODES).find(([name]) =>
      name.startsWith(raw),
    );
    return byTwo ? byTwo[1] : raw;
  }
  if (/^[a-z]{3}$/.test(raw)) return raw;
  return null;
}

function languagesFromAttrs(attrs) {
  return [
    ...new Set(
      String(attrs['tvg-language'] || '')
        .split(/[,;/]/)
        .map(languageCode)
        .filter(Boolean),
    ),
  ];
}

function catalogSourceUrl() {
  return String(getSetting('catalog_url', config.sourceIndexUrl) || config.sourceIndexUrl)
    .trim();
}

function metaFromDatabase() {
  return {
    countries: db.prepare('SELECT * FROM countries').all(),
    categories: db.prepare('SELECT * FROM categories').all(),
    languages: db.prepare('SELECT * FROM languages').all(),
  };
}

function normalizeTvgId(value) {
  return String(value || '').trim().replace(/\s+/g, '');
}

function baseIdFromEntry(tvgId, title, url) {
  if (tvgId) {
    return tvgId.split('@')[0].trim();
  }
  const name = slugify(cleanChannelName(title)) || 'canale';
  return `x-${name}-${sha1(url).slice(0, 6)}`;
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

async function saveMetadata({ countries, categories, languages }) {
  transaction(() => {
    const upCountry = db.prepare(`
      INSERT INTO countries (code, name, flag, languages) VALUES (?, ?, ?, ?)
      ON CONFLICT(code) DO UPDATE SET
        name = excluded.name, flag = excluded.flag, languages = excluded.languages
    `);
    for (const c of countries) {
      upCountry.run(
        c.code,
        c.name,
        c.flag || '',
        JSON.stringify(c.languages || []),
      );
    }

    const upCategory = db.prepare(`
      INSERT INTO categories (id, name, description) VALUES (?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        name = excluded.name, description = COALESCE(excluded.description, categories.description)
    `);
    for (const c of categories) {
      upCategory.run(c.id, c.name, c.description || null);
    }

    const upLanguage = db.prepare(`
      INSERT INTO languages (code, name) VALUES (?, ?)
      ON CONFLICT(code) DO UPDATE SET name = excluded.name
    `);
    for (const l of languages) {
      upLanguage.run(l.code, l.name);
    }
  });
}

function buildLookups(countriesRaw, categoriesRaw) {
  const countries = new Map(
    countriesRaw.map((c) => [String(c.code).toUpperCase(), c]),
  );
  const categoryByName = new Map();
  const categoryById = new Map();
  for (const c of categoriesRaw) {
    categoryByName.set(c.name.toLowerCase(), c.id);
    categoryById.set(c.id, c);
  }
  return { countries, categoryByName, categoryById };
}

function resolveCategories(groupTitle, apiChannel, lookup, extraCategories) {
  const ids = new Set(apiChannel?.categories || []);
  const rawGroups = String(groupTitle || '')
    .split(/[;,]/)
    .map((v) => v.trim())
    .filter(Boolean);
  for (const raw of rawGroups) {
    if (/^undefined$/i.test(raw)) continue;
    const lower = raw.toLowerCase();
    let id = lookup.categoryByName.get(lower);
    if (!id) id = categorySlug(raw);
    if (!id) continue;
    if (!lookup.categoryById.has(id) && !extraCategories.has(id)) {
      extraCategories.set(id, raw);
    }
    ids.add(id);
  }
  if (!ids.size) ids.add('general');
  return [...ids];
}

function normalizeStreams(streamsRaw) {
  const byUrl = new Map();
  for (const stream of streamsRaw) {
    if (!stream?.url) continue;
    byUrl.set(stream.url, stream);
  }
  return byUrl;
}

function channelSort(a, b, defaultCountry) {
  const aDefault = a.country === defaultCountry ? 0 : 1;
  const bDefault = b.country === defaultCountry ? 0 : 1;
  if (aDefault !== bDefault) return aDefault - bDefault;
  const pa = channelPriority(a.name);
  const pb = channelPriority(b.name);
  if (pa !== pb) return pa - pb;
  return a.name.localeCompare(b.name, 'it', { sensitivity: 'base' });
}

async function commitCatalog({ grouped, extraCategories, countries, categories, languages }) {
  const now = Math.floor(Date.now() / 1000);
  const defaultCountry = (getSetting('default_country') || config.defaultCountry).toUpperCase();
  const hideNsfw = getSetting('hide_nsfw', '1') === '1';

  const existing = new Map(
    db.prepare('SELECT id, number FROM channels').all().map((r) => [r.id, r.number]),
  );
  const usedNumbers = new Set(
    [...existing.values()].filter((n) => Number.isFinite(n)),
  );
  const newChannels = [...grouped.values()].filter(
    (c) => !existing.has(c.id) || existing.get(c.id) == null,
  );
  newChannels.sort((a, b) => channelSort(a, b, defaultCountry));
  const firstIngest = existing.size === 0;

  // Respect the Italian LCN for the main channels (Rai 1 = 1, Canale 5 = 5, …).
  for (const channel of newChannels) {
    const desired = channelPriority(channel.name);
    if (channel.country === defaultCountry && desired < 1000 && !usedNumbers.has(desired)) {
      channel.number = desired;
      usedNumbers.add(desired);
    }
  }
  // Don't let random channels steal LCN gaps (e.g. number 4 when Rete 4 is
  // missing). On the first import everything else starts at 100; later
  // additions are appended so existing channel numbers never change.
  const maxUsed = Math.max(0, ...[...usedNumbers].filter(Number.isFinite));
  let nextNumber = firstIngest ? 100 : maxUsed + 1;
  for (const channel of newChannels) {
    if (channel.number != null) continue;
    while (usedNumbers.has(nextNumber)) nextNumber += 1;
    channel.number = nextNumber;
    usedNumbers.add(nextNumber);
  }
  for (const channel of grouped.values()) {
    if (existing.get(channel.id) != null) {
      channel.number = existing.get(channel.id);
    }
  }

  transaction(() => {
    // Everything sourced from the upstream catalog is disabled first, then
    // re-enabled as we upsert the current snapshot. Custom channels are untouched.
    db.exec('UPDATE streams SET enabled = 0 WHERE channel_id IN (SELECT id FROM channels WHERE custom = 0)');
    db.exec('UPDATE channels SET enabled = 0 WHERE custom = 0');

    const upsertChannel = db.prepare(`
      INSERT INTO channels (
        id, name, alt_names, country, group_title, categories, languages, logo,
        website, network, is_nsfw, number, enabled, favorite, hidden, custom,
        status, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, 0, 'unknown', ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        name = excluded.name,
        alt_names = excluded.alt_names,
        country = excluded.country,
        group_title = excluded.group_title,
        categories = excluded.categories,
        languages = excluded.languages,
        logo = excluded.logo,
        website = excluded.website,
        network = excluded.network,
        is_nsfw = excluded.is_nsfw,
        number = COALESCE(channels.number, excluded.number),
        enabled = 1,
        updated_at = excluded.updated_at
    `);

    const upsertStream = db.prepare(`
      INSERT INTO streams (
        id, channel_id, url, feed, quality, quality_rank, user_agent, referrer,
        priority, enabled, status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'unknown')
      ON CONFLICT(channel_id, url) DO UPDATE SET
        feed = excluded.feed,
        quality = excluded.quality,
        quality_rank = excluded.quality_rank,
        user_agent = excluded.user_agent,
        referrer = excluded.referrer,
        priority = excluded.priority,
        enabled = 1
    `);

    for (const channel of grouped.values()) {
      const hidden = channel.isNsfw && hideNsfw ? 1 : 0;
      upsertChannel.run(
        channel.id,
        channel.name,
        JSON.stringify(channel.altNames),
        channel.country,
        channel.groupTitle,
        JSON.stringify(channel.categories),
        JSON.stringify(channel.languages),
        channel.logo,
        channel.website,
        channel.network,
        channel.isNsfw ? 1 : 0,
        channel.number ?? null,
        hidden,
        channel.createdAt || now,
        now,
      );

      for (const stream of channel.streams.values()) {
        upsertStream.run(
          stream.id,
          channel.id,
          stream.url,
          stream.feed,
          stream.quality,
          qualityRank(stream.quality),
          stream.userAgent,
          stream.referrer,
          stream.priority,
        );
      }
    }

    // New upstream categories discovered through group-title.
    const insertCategory = db.prepare(
      'INSERT OR IGNORE INTO categories (id, name, description) VALUES (?, ?, NULL)',
    );
    for (const [id, name] of extraCategories) {
      insertCategory.run(id, name);
    }

    db.exec(`
      UPDATE channels SET status = CASE
        WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channel_id = channels.id AND s.enabled = 1 AND s.status = 'online') THEN 'online'
        WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channel_id = channels.id AND s.enabled = 1 AND s.status = 'offline') THEN 'offline'
        ELSE 'unknown'
      END
      WHERE custom = 0
    `);
  });

  return {
    channels: grouped.size,
    streams: [...grouped.values()].reduce((sum, c) => sum + c.streams.size, 0),
    newChannels: newChannels.length,
    countries: countries.length,
    categories: categories.length + extraCategories.size,
    languages: languages.length,
  };
}

export function needsRefresh() {
  const last = Number(getSetting('last_ingest', '0')) || 0;
  if (!last) return true;
  const ageHours = (Date.now() / 1000 - last) / 3600;
  return ageHours >= config.refreshHours;
}

export async function runIngest({ force = false, reason = 'manual' } = {}) {
  if (ingestPromise) return ingestPromise;
  if (!force && !needsRefresh()) {
    return {
      skipped: true,
      ...ingestState,
      lastIngest: Number(getSetting('last_ingest', '0')) || null,
    };
  }

  ingestPromise = (async () => {
    const started = Date.now();
    ingestState.running = true;
    ingestState.phase = 'download';
    ingestState.startedAt = Math.floor(started / 1000);
    ingestState.finishedAt = null;
    ingestState.error = null;
    ingestState.stats = null;
    ingestState.reason = reason;
    log(`avvio aggiornamento (${reason})`);

    try {
      const sourceUrl = catalogSourceUrl();
      const useIptvApi = sourceUrl === config.sourceIndexUrl;
      ingestState.source = sourceUrl;
      const [indexResult, channelsRaw, streamsRaw, categoriesRaw, countriesRaw, languagesRaw] =
        await Promise.all([
          fetchCached(sourceUrl, 'index.m3u', { force }),
          useIptvApi
            ? fetchJson(`${config.apiBase}/channels.json`, 'channels.json', { force })
            : Promise.resolve([]),
          useIptvApi
            ? fetchJson(`${config.apiBase}/streams.json`, 'streams.json', { force })
            : Promise.resolve([]),
          useIptvApi
            ? fetchJson(`${config.apiBase}/categories.json`, 'categories.json', { force })
            : Promise.resolve([]),
          useIptvApi
            ? fetchJson(`${config.apiBase}/countries.json`, 'countries.json', { force })
            : Promise.resolve([]),
          useIptvApi
            ? fetchJson(`${config.apiBase}/languages.json`, 'languages.json', { force })
            : Promise.resolve([]),
        ]);

      ingestState.phase = 'parse';
      const databaseMeta = metaFromDatabase();
      const countries = Array.isArray(countriesRaw) && countriesRaw.length
        ? countriesRaw
        : databaseMeta.countries;
      const categories = Array.isArray(categoriesRaw) && categoriesRaw.length
        ? categoriesRaw
        : databaseMeta.categories;
      const languages = Array.isArray(languagesRaw) && languagesRaw.length
        ? languagesRaw
        : databaseMeta.languages;
      const apiChannels = new Map();
      for (const channel of Array.isArray(channelsRaw) ? channelsRaw : []) {
        if (channel?.id) apiChannels.set(channel.id, channel);
      }
      const apiStreams = normalizeStreams(Array.isArray(streamsRaw) ? streamsRaw : []);
      const lookup = buildLookups(countries, categories);
      if (Array.isArray(countriesRaw) && countriesRaw.length) {
        await saveMetadata({ countries, categories, languages });
      }

      const entries = parseM3u(indexResult.text);
      log(`playlist: ${entries.length} voci, ${apiChannels.size} canali API (${sourceUrl})`);

      const grouped = new Map();
      const extraCategories = new Map();
      const now = Math.floor(Date.now() / 1000);

      for (const entry of entries) {
        const attrs = entry.attrs || {};
        const tvgId = normalizeTvgId(attrs['tvg-id']);
        const rawUrl = String(entry.url || '').trim();
        if (!rawUrl || rawUrl.startsWith('#')) continue;
        const withHeaders = splitPipeHeaders(rawUrl);
        if (!/^https?:\/\//i.test(withHeaders.url)) continue;

        const apiStream = apiStreams.get(withHeaders.url) || apiStreams.get(rawUrl) || null;
        const baseId = baseIdFromEntry(tvgId, entry.title, withHeaders.url);
        if (!baseId) continue;
        const apiChannel = apiChannels.get(baseId) || apiChannels.get(tvgId) || null;
        const country = inferCountry(baseId, apiChannel, attrs, lookup.countries);
        const countryMeta = country ? lookup.countries.get(country) : null;
        const name = cleanChannelName(
          apiChannel?.name || entry.title || attrs['tvg-name'] || baseId,
        );
        const groupTitle =
          entry.group || attrs['group-title'] || apiChannel?.categories?.[0] || '';
        const categoryIds = resolveCategories(
          groupTitle,
          apiChannel,
          lookup,
          extraCategories,
        );
        const countryLanguages = Array.isArray(countryMeta?.languages)
          ? countryMeta.languages
          : safeJsonParse(countryMeta?.languages, []);
        const languagesList = unique([
          ...languagesFromAttrs(attrs),
          ...(Array.isArray(apiChannel?.languages) && apiChannel.languages.length
            ? apiChannel.languages
            : countryLanguages),
        ]);
        const quality = parseQuality(apiStream?.quality, entry.title, attrs['tvg-name']);
        const userAgent =
          entry.headers?.userAgent ||
          attrs['http-user-agent'] ||
          apiStream?.user_agent ||
          null;
        const referrer =
          entry.headers?.referrer ||
          attrs['http-referrer'] ||
          attrs['http-referer'] ||
          apiStream?.referrer ||
          null;
        const feed = tvgId.includes('@')
          ? tvgId.split('@').slice(1).join('@')
          : apiStream?.feed || null;

        let channel = grouped.get(baseId);
        if (!channel) {
          channel = {
            id: baseId,
            name,
            altNames: new Set(),
            country,
            groupTitle,
            categories: new Set(),
            languages: new Set(languagesList),
            logo: null,
            website: apiChannel?.website || null,
            network: apiChannel?.network || null,
            isNsfw: !!apiChannel?.is_nsfw,
            streams: new Map(),
            createdAt: now,
          };
          grouped.set(baseId, channel);
        }

        if (entry.title) channel.altNames.add(entry.title.trim());
        if (entry.title && cleanChannelName(entry.title) !== name) {
          channel.altNames.add(cleanChannelName(entry.title));
        }
        for (const id of categoryIds) channel.categories.add(id);
        for (const lang of languagesList) channel.languages.add(lang);
        if (!channel.country && country) channel.country = country;
        if (!channel.logo && attrs['tvg-logo']) channel.logo = attrs['tvg-logo'];
        if (channel.logo?.startsWith('http://') && attrs['tvg-logo']?.startsWith('https://')) {
          channel.logo = attrs['tvg-logo'];
        }
        if (!channel.website && apiChannel?.website) channel.website = apiChannel.website;
        if (!channel.network && apiChannel?.network) channel.network = apiChannel.network;
        if (apiChannel?.is_nsfw) channel.isNsfw = true;

        const streamKey = sha1(withHeaders.url).slice(0, 12);
        const streamId = `${tvgId || baseId}#${streamKey}`;
        if (!channel.streams.has(streamId)) {
          channel.streams.set(streamId, {
            id: streamId,
            url: withHeaders.url,
            feed,
            quality,
            userAgent,
            referrer,
            priority: 100 - qualityRank(quality),
          });
        }
      }

      for (const channel of grouped.values()) {
        channel.categories = new Set(
          channel.categories.size ? channel.categories : ['general'],
        );
        channel.altNames = [...channel.altNames].filter(
          (n) => n && n !== channel.name,
        ).slice(0, 12);
        channel.categories = [...channel.categories];
        channel.languages = [...channel.languages];
        channel.name = channel.name || channel.id;
        channel.logo = channel.logo ? channel.logo.trim() : null;
      }

      ingestState.phase = 'save';
      const stats = await commitCatalog({
        grouped,
        extraCategories,
        countries,
        categories,
        languages,
      });
      refreshMeta();
      setSetting('last_ingest', now);

      const result = {
        ...stats,
        entries: entries.length,
        durationMs: Date.now() - started,
        fromCache: indexResult.fromCache,
        skipped: false,
      };
      ingestState.stats = result;
      ingestState.phase = 'done';
      ingestState.running = false;
      ingestState.finishedAt = Math.floor(Date.now() / 1000);
      log(
        `completato: ${result.channels} canali / ${result.streams} sorgenti (${result.newChannels} nuovi) in ${result.durationMs} ms`,
      );
      return result;
    } catch (error) {
      ingestState.phase = 'error';
      ingestState.running = false;
      ingestState.error = error.message;
      ingestState.finishedAt = Math.floor(Date.now() / 1000);
      log(`errore: ${error.stack || error.message}`);
      throw error;
    } finally {
      ingestPromise = null;
    }
  })();

  return ingestPromise;
}

async function probeSampleUrls(urls) {
  if (!urls.length) return { status: 'error', lastError: 'Nessun canale nella playlist' };
  const samples = urls.length <= 3
    ? urls
    : [urls[0], urls[Math.floor(urls.length / 2)], urls[urls.length - 1]];
  let lastFailure = '';
  for (const rawUrl of samples) {
    const clean = String(rawUrl || '').trim();
    if (!clean.startsWith('http://') && !clean.startsWith('https://')) {
      lastFailure = 'URL non HTTP';
      continue;
    }
    try {
      const headRes = await fetch(clean, {
        method: 'HEAD',
        headers: { 'User-Agent': 'Mozilla/5.0 CasaTV/1.0' },
        signal: AbortSignal.timeout(4000),
      });
      if (headRes.ok || (headRes.status >= 200 && headRes.status < 400)) {
        return { status: 'ok', lastError: '' };
      }
      const getRes = await fetch(clean, {
        method: 'GET',
        headers: { 'User-Agent': 'Mozilla/5.0 CasaTV/1.0', Range: 'bytes=0-1024' },
        signal: AbortSignal.timeout(4000),
      });
      await getRes.body?.cancel?.();
      if (getRes.ok || (getRes.status >= 200 && getRes.status < 400)) {
        return { status: 'ok', lastError: '' };
      }
      lastFailure = `HTTP ${getRes.status}`;
    } catch (err) {
      lastFailure = err?.name || err?.message || 'Errore connessione';
    }
  }
  return { status: 'error', lastError: `Flussi non raggiungibili (${lastFailure})` };
}

export async function checkPlaylist(id) {
  const p = getPlaylist(id);
  if (!p) return null;
  const samples = samplePlaylistChannels(id, 3);
  if (!samples.length || !p.channelCount) {
    updatePlaylistStatus(id, 'error', p.lastError || 'Nessun canale valido nella playlist');
    return getPlaylist(id);
  }
  const probe = await probeSampleUrls(samples.map((s) => s.stream_url));
  updatePlaylistStatus(id, probe.status, probe.lastError);
  return getPlaylist(id);
}

export async function importPlaylist({ name, url = '', text = null, id = null }) {
  const playlistId = id || 'pl_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const cleanUrl = String(url || '').trim();
  const playlistName = (name || '').trim() ||
    (cleanUrl ? cleanUrl.split('/').pop()?.split('?')[0] : '') ||
    'Playlist ' + playlistId.slice(-4);

  const failEntry = (reason) => {
    if (id) {
      updatePlaylistStatus(id, 'error', reason);
    } else {
      savePlaylist({
        id: playlistId,
        name: playlistName,
        url: cleanUrl,
        enabled: 1,
        channel_count: 0,
        status: 'error',
        last_error: reason,
      });
    }
    return getPlaylist(playlistId);
  };

  let m3uText = text;
  if (m3uText == null && cleanUrl) {
    if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
      return failEntry('Indirizzo non valido (deve iniziare con http:// o https://)');
    }
    try {
      const res = await fetch(cleanUrl, {
        headers: {
          'User-Agent': config.userAgent,
          'Accept': 'audio/x-mpegurl, application/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*',
        },
        signal: AbortSignal.timeout(30_000),
      });
      if (!res.ok) return failEntry(`Download fallito (HTTP ${res.status})`);
      m3uText = await res.text();
    } catch (err) {
      return failEntry(`Download fallito: ${err?.message || 'errore di rete'}`);
    }
  }
  if (!m3uText || !String(m3uText).trim()) {
    return failEntry('Playlist vuota o non leggibile');
  }

  const parsedEntries = parseM3u(m3uText);
  const validEntries = parsedEntries.filter((e) => e.url && /^https?:\/\//i.test(e.url));
  if (!validEntries.length) {
    return failEntry('Nessun canale IPTV valido trovato (richiesto #EXTM3U / #EXTINF o PLS con stream http/https)');
  }

  const probe = await probeSampleUrls(validEntries.slice(0, 20).map((e) => e.url));
  const now = Math.floor(Date.now() / 1000);
  const existingMaxNum = db.prepare('SELECT COALESCE(MAX(number), 0) AS max_num FROM channels').get().max_num;

  const insertChannel = db.prepare(`
    INSERT INTO channels (
      id, name, alt_names, country, group_title, categories, languages,
      logo, website, network, is_nsfw, number, enabled, favorite, hidden,
      custom, preferred_stream_id, status, last_played_at, created_at, updated_at, playlist_id
    ) VALUES (
      ?, ?, '[]', ?, ?, ?, ?,
      ?, NULL, NULL, 0, ?, 1, 0, 0,
      1, NULL, 'unknown', NULL, ?, ?, ?
    )
    ON CONFLICT(id) DO UPDATE SET
      name = excluded.name,
      country = excluded.country,
      group_title = excluded.group_title,
      categories = excluded.categories,
      languages = excluded.languages,
      logo = excluded.logo,
      updated_at = excluded.updated_at,
      playlist_id = excluded.playlist_id
  `);

  const insertStream = db.prepare(`
    INSERT INTO streams (
      id, channel_id, url, feed, quality, quality_rank, user_agent, referrer, priority, status, added_at, updated_at
    ) VALUES (
      ?, ?, ?, ?, ?, ?, ?, ?, 10, 'unknown', ?, ?
    )
    ON CONFLICT(id) DO UPDATE SET
      url = excluded.url,
      feed = excluded.feed,
      quality = excluded.quality,
      quality_rank = excluded.quality_rank,
      user_agent = excluded.user_agent,
      referrer = excluded.referrer,
      updated_at = excluded.updated_at
  `);

  transaction(() => {
    db.prepare('DELETE FROM streams WHERE channel_id IN (SELECT id FROM channels WHERE playlist_id = ?)').run(playlistId);
    db.prepare('DELETE FROM channels WHERE playlist_id = ?').run(playlistId);

    let num = existingMaxNum;
    for (let i = 0; i < validEntries.length; i++) {
      const e = validEntries[i];
      const cleanName = e.title || e.attrs['tvg-name'] || `Canale ${i + 1}`;
      const chId = 'm3u_' + playlistId + '_' + i;
      const category = e.group || e.attrs['group-title'] || 'Generale';
      const country = (e.attrs['tvg-country'] || '').toUpperCase().slice(0, 2) || config.defaultCountry;
      const logo = e.attrs['tvg-logo'] || null;
      const lang = (e.attrs['tvg-language'] || '').toLowerCase();
      num++;

      insertChannel.run(
        chId,
        cleanName,
        country,
        category,
        JSON.stringify([category]),
        JSON.stringify(lang ? [lang] : []),
        logo,
        num,
        now,
        now,
        playlistId
      );

      const streamId = 'st_' + chId;
      const quality = e.attrs['tvg-quality'] || 'HD';
      const rank = quality.includes('1080') || quality.includes('HD') ? 2 : 1;
      insertStream.run(
        streamId,
        chId,
        e.url,
        null,
        quality,
        rank,
        e.headers?.userAgent || null,
        e.headers?.referrer || null,
        now,
        now
      );
    }

    savePlaylist({
      id: playlistId,
      name: playlistName,
      url: cleanUrl,
      enabled: 1,
      channel_count: validEntries.length,
      status: probe.status,
      last_error: probe.lastError,
    });
  });

  return getPlaylist(playlistId);
}

