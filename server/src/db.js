import { DatabaseSync } from 'node:sqlite';
import { paths, config } from './config.js';
import {
  normalizeId,
  safeJsonParse,
  qualityRank,
  sha1,
  slugify,
} from './util.js';

export const db = new DatabaseSync(paths.db);

db.exec(`
  PRAGMA journal_mode = WAL;
  PRAGMA synchronous = NORMAL;
  PRAGMA foreign_keys = ON;
  PRAGMA busy_timeout = 5000;
`);

db.exec(`
  CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS countries (
    code TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    flag TEXT,
    languages TEXT NOT NULL DEFAULT '[]'
  );

  CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT
  );

  CREATE TABLE IF NOT EXISTS languages (
    code TEXT PRIMARY KEY,
    name TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS channels (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    alt_names TEXT NOT NULL DEFAULT '[]',
    country TEXT,
    group_title TEXT,
    categories TEXT NOT NULL DEFAULT '[]',
    languages TEXT NOT NULL DEFAULT '[]',
    logo TEXT,
    website TEXT,
    network TEXT,
    is_nsfw INTEGER NOT NULL DEFAULT 0,
    number INTEGER,
    enabled INTEGER NOT NULL DEFAULT 1,
    favorite INTEGER NOT NULL DEFAULT 0,
    hidden INTEGER NOT NULL DEFAULT 0,
    custom INTEGER NOT NULL DEFAULT 0,
    preferred_stream_id TEXT,
    status TEXT NOT NULL DEFAULT 'unknown',
    last_played_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_channels_number ON channels(number);
  CREATE INDEX IF NOT EXISTS idx_channels_country ON channels(country);
  CREATE INDEX IF NOT EXISTS idx_channels_status ON channels(status);
  CREATE INDEX IF NOT EXISTS idx_channels_favorite ON channels(favorite);
  CREATE INDEX IF NOT EXISTS idx_channels_last_played ON channels(last_played_at);

  CREATE TABLE IF NOT EXISTS streams (
    id TEXT PRIMARY KEY,
    channel_id TEXT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    feed TEXT,
    quality TEXT NOT NULL DEFAULT 'SD',
    quality_rank INTEGER NOT NULL DEFAULT 0,
    user_agent TEXT,
    referrer TEXT,
    priority INTEGER NOT NULL DEFAULT 100,
    enabled INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'unknown',
    status_checked_at INTEGER,
    status_ms INTEGER,
    status_error TEXT,
    last_ok_at INTEGER,
    UNIQUE(channel_id, url)
  );
  CREATE INDEX IF NOT EXISTS idx_streams_channel ON streams(channel_id);
  CREATE INDEX IF NOT EXISTS idx_streams_status ON streams(status);

  CREATE TABLE IF NOT EXISTS scan_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mode TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    finished_at INTEGER,
    total INTEGER NOT NULL DEFAULT 0,
    checked INTEGER NOT NULL DEFAULT 0,
    online INTEGER NOT NULL DEFAULT 0,
    offline INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'running',
    error TEXT
  );

  CREATE TABLE IF NOT EXISTS epg_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    url TEXT NOT NULL UNIQUE,
    enabled INTEGER NOT NULL DEFAULT 1,
    last_fetch INTEGER,
    last_status TEXT,
    channel_count INTEGER NOT NULL DEFAULT 0,
    program_count INTEGER NOT NULL DEFAULT 0
  );

  CREATE TABLE IF NOT EXISTS epg_programs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL REFERENCES epg_sources(id) ON DELETE CASCADE,
    channel_id TEXT NOT NULL,
    channel_norm TEXT NOT NULL,
    start INTEGER NOT NULL,
    stop INTEGER NOT NULL,
    title TEXT NOT NULL,
    subtitle TEXT,
    description TEXT,
    category TEXT,
    icon TEXT
  );
  CREATE INDEX IF NOT EXISTS idx_epg_norm_start ON epg_programs(channel_norm, start);
  CREATE INDEX IF NOT EXISTS idx_epg_start ON epg_programs(start);
  CREATE INDEX IF NOT EXISTS idx_epg_source ON epg_programs(source_id);

  CREATE TABLE IF NOT EXISTS profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    avatar TEXT NOT NULL DEFAULT '🦊',
    language TEXT NOT NULL DEFAULT 'it',
    country TEXT NOT NULL DEFAULT 'IT',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS profile_channels (
    profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    channel_id TEXT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    favorite INTEGER NOT NULL DEFAULT 0,
    hidden INTEGER NOT NULL DEFAULT 0,
    last_played_at INTEGER,
    watch_count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, channel_id)
  );
  CREATE INDEX IF NOT EXISTS idx_profile_channels_favorite
    ON profile_channels(profile_id, favorite);
  CREATE INDEX IF NOT EXISTS idx_profile_channels_recent
    ON profile_channels(profile_id, last_played_at);
  CREATE TABLE IF NOT EXISTS playlists (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    channel_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  );
`);

try {
  db.exec('ALTER TABLE profile_channels ADD COLUMN watch_count INTEGER NOT NULL DEFAULT 0');
} catch (_) {}

try {
  db.exec('ALTER TABLE channels ADD COLUMN playlist_id TEXT DEFAULT NULL');
} catch (_) {}

const defaultSettings = {
  server_name: config.serverName,
  default_country: config.defaultCountry,
  catalog_url: config.sourceIndexUrl,
  auto_scan: '0',
  scan_interval_hours: '24',
  epg_auto_refresh: '1',
  hide_nsfw: '1',
};

const insertSetting = db.prepare(
  'INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)',
);
for (const [key, value] of Object.entries(defaultSettings)) {
  insertSetting.run(key, value);
}

export function getSetting(key, fallback = null) {
  const row = db.prepare('SELECT value FROM settings WHERE key = ?').get(key);
  return row ? row.value : fallback;
}

export function setSetting(key, value) {
  db.prepare(
    'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
  ).run(key, String(value));
}

export function getAllSettings() {
  const out = {};
  for (const row of db.prepare('SELECT key, value FROM settings').all()) {
    out[row.key] = row.value;
  }
  return out;
}

/* ------------------------------------------------------------------ */
/* Profiles                                                            */
/* ------------------------------------------------------------------ */

export const PROFILE_AVATARS = [
  '🦊', '🐼', '🐯', '🐨', '🦁', '🐸', '🐵', '🐺', '🦄', '🐙',
  '🐳', '🦉', '🐻', '🐧', '🦖', '🐝', '🐬', '🦋', '🐰', '🐲',
];

export const PROFILE_LANGUAGES = [
  { code: 'it', name: 'Italiano' },
  { code: 'en', name: 'English' },
  { code: 'es', name: 'Español' },
];

const PROFILE_LANGUAGE_CODES = new Set(PROFILE_LANGUAGES.map((l) => l.code));

function normalizeProfileLanguage(value) {
  const code = String(value || 'it').toLowerCase().slice(0, 2);
  return PROFILE_LANGUAGE_CODES.has(code) ? code : 'it';
}

function normalizeProfileName(value) {
  return String(value || '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 28);
}

function makeProfileId(name) {
  const base = slugify(name).slice(0, 22) || 'profilo';
  return `${base}-${sha1(`${name}:${Date.now()}:${Math.random()}`).slice(0, 6)}`;
}

function ensureProfiles() {
  const count = db.prepare('SELECT COUNT(*) AS n FROM profiles').get().n;
  if (count > 0) return;
  const now = Math.floor(Date.now() / 1000);
  const country = (getSetting('default_country', config.defaultCountry) || config.defaultCountry)
    .toUpperCase()
    .slice(0, 2);
  const id = 'profilo-principale';
  db.prepare(
    `INSERT INTO profiles (id, name, avatar, language, country, created_at, updated_at)
     VALUES (?, ?, ?, 'it', ?, ?, ?)`,
  ).run(id, 'Principale', '🦊', country || 'IT', now, now);

  // Preserve the favorites/history that existed before multi-profile support.
  db.prepare(
    `INSERT OR IGNORE INTO profile_channels
       (profile_id, channel_id, favorite, hidden, last_played_at)
     SELECT ?, id, favorite, hidden, last_played_at
     FROM channels
     WHERE favorite = 1 OR hidden = 1 OR last_played_at IS NOT NULL`,
  ).run(id);
}

ensureProfiles();

export function profileDto(row, lang = null) {
  if (!row) return null;
  const language = normalizeProfileLanguage(row.language);
  const displayLang = lang || language;
  return {
    id: row.id,
    name: row.name,
    avatar: row.avatar || '🦊',
    language,
    languageName:
      PROFILE_LANGUAGES.find((l) => l.code === language)?.name || language,
    country: row.country || 'IT',
    countryName: countryName(row.country, displayLang),
    flag: countryFlag(row.country),
    isGuest: row.id === 'guest',
    createdAt: row.created_at || null,
    updatedAt: row.updated_at || null,
  };
}

export function listProfiles(lang = null) {
  return db
    .prepare('SELECT * FROM profiles ORDER BY created_at ASC, name COLLATE NOCASE ASC')
    .all()
    .map((row) => profileDto(row, lang));
}

export function getProfile(id) {
  if (!id) return null;
  return db.prepare('SELECT * FROM profiles WHERE id = ?').get(String(id)) || null;
}

export function getDefaultProfile() {
  return (
    db
      .prepare('SELECT * FROM profiles ORDER BY created_at ASC, name COLLATE NOCASE ASC LIMIT 1')
      .get() || null
  );
}

export function getDefaultProfileId() {
  return getDefaultProfile()?.id || 'profilo-principale';
}

export function createProfile({ name, avatar, language, country } = {}) {
  const cleanName = normalizeProfileName(name);
  if (!cleanName) {
    const error = new Error('Il nome del profilo è obbligatorio');
    error.status = 400;
    throw error;
  }
  const chosenAvatar = PROFILE_AVATARS.includes(avatar) ? avatar : PROFILE_AVATARS[0];
  const chosenLanguage = normalizeProfileLanguage(language);
  const chosenCountry = String(country || '').toUpperCase().slice(0, 2) || 'IT';
  if (!/^[A-Z]{2}$/.test(chosenCountry)) {
    const error = new Error('Paese del profilo non valido');
    error.status = 400;
    throw error;
  }
  const duplicate = db
    .prepare('SELECT id FROM profiles WHERE lower(name) = lower(?)')
    .get(cleanName);
  if (duplicate) {
    const error = new Error('Esiste già un profilo con questo nome');
    error.status = 409;
    throw error;
  }
  const now = Math.floor(Date.now() / 1000);
  let id = makeProfileId(cleanName);
  while (getProfile(id)) id = makeProfileId(cleanName);
  db.prepare(
    `INSERT INTO profiles (id, name, avatar, language, country, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  ).run(id, cleanName, chosenAvatar, chosenLanguage, chosenCountry, now, now);
  return getProfile(id);
}

export function updateProfile(id, { name, avatar, language, country } = {}) {
  const current = getProfile(id);
  if (!current) return null;
  const updates = [];
  const params = [];
  if (name !== undefined) {
    const cleanName = normalizeProfileName(name);
    if (!cleanName) {
      const error = new Error('Il nome del profilo è obbligatorio');
      error.status = 400;
      throw error;
    }
    const duplicate = db
      .prepare('SELECT id FROM profiles WHERE lower(name) = lower(?) AND id <> ?')
      .get(cleanName, id);
    if (duplicate) {
      const error = new Error('Esiste già un profilo con questo nome');
      error.status = 409;
      throw error;
    }
    updates.push('name = ?');
    params.push(cleanName);
  }
  if (avatar !== undefined) {
    updates.push('avatar = ?');
    params.push(PROFILE_AVATARS.includes(avatar) ? avatar : current.avatar);
  }
  if (language !== undefined) {
    updates.push('language = ?');
    params.push(normalizeProfileLanguage(language));
  }
  if (country !== undefined) {
    const value = String(country || '').toUpperCase().slice(0, 2);
    if (!/^[A-Z]{2}$/.test(value)) {
      const error = new Error('Paese del profilo non valido');
      error.status = 400;
      throw error;
    }
    updates.push('country = ?');
    params.push(value);
  }
  if (!updates.length) return current;
  updates.push('updated_at = ?');
  params.push(Math.floor(Date.now() / 1000), id);
  db.prepare(`UPDATE profiles SET ${updates.join(', ')} WHERE id = ?`).run(...params);
  return getProfile(id);
}

export function deleteProfile(id) {
  if (id === 'guest') {
    const error = new Error('Il profilo Ospite non può essere eliminato');
    error.status = 400;
    throw error;
  }
  const count = db.prepare('SELECT COUNT(*) AS n FROM profiles WHERE id <> ?').get('guest').n;
  if (count <= 1) {
    const error = new Error('Deve restare almeno un profilo');
    error.status = 400;
    throw error;
  }
  return db.prepare('DELETE FROM profiles WHERE id = ?').run(id).changes > 0;
}

export function ensureGuestProfile() {
  const existing = db.prepare("SELECT id FROM profiles WHERE id = 'guest'").get();
  if (!existing) {
    const now = Math.floor(Date.now() / 1000);
    db.prepare(`
      INSERT INTO profiles (id, name, avatar, language, country, created_at, updated_at)
      VALUES ('guest', 'Ospite', '👤', 'it', 'IT', ?, ?)
    `).run(now, now);
  }
}

export function clearGuestProfileData() {
  db.prepare("DELETE FROM profile_channels WHERE profile_id = 'guest'").run();
}

export function setProfileChannelState(profileId, channelId, updates = {}) {
  const pid = profileId || getDefaultProfileId();
  const exists = db.prepare('SELECT id FROM channels WHERE id = ?').get(channelId);
  if (!exists) return false;
  const current = db
    .prepare('SELECT * FROM profile_channels WHERE profile_id = ? AND channel_id = ?')
    .get(pid, channelId) || { favorite: 0, hidden: 0, last_played_at: null, watch_count: 0 };
  const favorite =
    updates.favorite !== undefined ? (updates.favorite ? 1 : 0) : current.favorite;
  const hidden = updates.hidden !== undefined ? (updates.hidden ? 1 : 0) : current.hidden;
  const lastPlayed =
    updates.lastPlayedAt !== undefined ? updates.lastPlayedAt : current.last_played_at;
  const watchInc = Number(updates.watchCount || 0);

  db.prepare(
    `INSERT INTO profile_channels (profile_id, channel_id, favorite, hidden, last_played_at, watch_count)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(profile_id, channel_id) DO UPDATE SET
       favorite = excluded.favorite,
       hidden = excluded.hidden,
       last_played_at = excluded.last_played_at,
       watch_count = COALESCE(profile_channels.watch_count, 0) + excluded.watch_count`,
  ).run(pid, channelId, favorite, hidden, lastPlayed, watchInc);
  return true;
}

export function markProfilePlayed(profileId, channelId) {
  return setProfileChannelState(profileId, channelId, {
    lastPlayedAt: Math.floor(Date.now() / 1000),
    watchCount: 1,
  });
}

export function transaction(fn) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (error) {
    try {
      db.exec('ROLLBACK');
    } catch {
      // ignore
    }
    throw error;
  }
}

/* ------------------------------------------------------------------ */
/* Metadata caches                                                     */
/* ------------------------------------------------------------------ */

let meta = {
  countries: new Map(),
  categories: new Map(),
  languages: new Map(),
};

const regionNamesByLang = new Map();

function langCode(lang) {
  const code = String(lang || 'it').toLowerCase().slice(0, 2);
  return code === 'en' || code === 'es' ? code : 'it';
}

function regionNames(lang) {
  const code = langCode(lang);
  if (!regionNamesByLang.has(code)) {
    regionNamesByLang.set(code, new Intl.DisplayNames([code], { type: 'region' }));
  }
  return regionNamesByLang.get(code);
}

const LANGUAGE_IT = {
  ita: 'Italiano',
  eng: 'Inglese',
  spa: 'Spagnolo',
  fra: 'Francese',
  deu: 'Tedesco',
  por: 'Portoghese',
  ara: 'Arabo',
  rus: 'Russo',
  zho: 'Cinese',
  jpn: 'Giapponese',
  kor: 'Coreano',
  nld: 'Olandese',
  pol: 'Polacco',
  tur: 'Turco',
  hin: 'Hindi',
  swe: 'Svedese',
  nor: 'Norvegese',
  dan: 'Danese',
  fin: 'Finlandese',
  ell: 'Greco',
  ces: 'Ceco',
  ron: 'Rumeno',
  hun: 'Ungherese',
  ukr: 'Ucraino',
  cat: 'Catalano',
  glg: 'Gallego',
  eus: 'Basco',
  sqi: 'Albanese',
  srp: 'Serbo',
  hrv: 'Croato',
  slk: 'Slovacco',
  slv: 'Sloveno',
  bul: 'Bulgaro',
  lit: 'Lituano',
  lav: 'Lettone',
  est: 'Estone',
  isl: 'Islandese',
  gle: 'Irlandese',
  cym: 'Gallese',
  afr: 'Afrikaans',
  swa: 'Swahili',
  fil: 'Filippino',
  ben: 'Bengalese',
  urd: 'Urdu',
  fas: 'Persiano',
  heb: 'Ebraico',
  tha: 'Thai',
  vie: 'Vietnamita',
  ind: 'Indonesiano',
  msa: 'Malese',
};

const CATEGORY_IT = {
  general: 'Generalisti',
  news: 'Notizie',
  sports: 'Sport',
  movies: 'Film',
  kids: 'Bambini',
  music: 'Musica',
  documentary: 'Documentari',
  entertainment: 'Intrattenimento',
  series: 'Serie TV',
  comedy: 'Commedia',
  education: 'Formazione',
  culture: 'Cultura',
  lifestyle: 'Lifestyle',
  religious: 'Religione',
  shop: 'Telecompras',
  travel: 'Viaggi',
  cooking: 'Cucina',
  family: 'Famiglia',
  animation: 'Animazione',
  business: 'Economia',
  weather: 'Meteo',
  science: 'Scienza',
  auto: 'Motori',
  outdoor: 'Outdoor',
  relax: 'Relax',
  legislative: 'Istituzionale',
  public: 'Servizio pubblico',
  classic: 'Classici',
  undefined: 'Altro',
};

const CATEGORY_EN = {
  general: 'General',
  news: 'News',
  sports: 'Sports',
  movies: 'Movies',
  kids: 'Kids',
  music: 'Music',
  documentary: 'Documentary',
  entertainment: 'Entertainment',
  series: 'Series',
  comedy: 'Comedy',
  education: 'Education',
  culture: 'Culture',
  lifestyle: 'Lifestyle',
  religious: 'Religious',
  shop: 'Shopping',
  travel: 'Travel',
  cooking: 'Cooking',
  family: 'Family',
  animation: 'Animation',
  business: 'Business',
  weather: 'Weather',
  science: 'Science',
  auto: 'Motoring',
  outdoor: 'Outdoor',
  relax: 'Relax',
  legislative: 'Legislative',
  public: 'Public service',
  classic: 'Classic',
  undefined: 'Other',
};

const CATEGORY_ES = {
  general: 'Generalistas',
  news: 'Noticias',
  sports: 'Deportes',
  movies: 'Películas',
  kids: 'Infantil',
  music: 'Música',
  documentary: 'Documentales',
  entertainment: 'Entretenimiento',
  series: 'Series',
  comedy: 'Comedia',
  education: 'Formación',
  culture: 'Cultura',
  lifestyle: 'Estilo de vida',
  religious: 'Religión',
  shop: 'Telecompras',
  travel: 'Viajes',
  cooking: 'Cocina',
  family: 'Familia',
  animation: 'Animación',
  business: 'Economía',
  weather: 'Meteorología',
  science: 'Ciencia',
  auto: 'Motor',
  outdoor: 'Aire libre',
  relax: 'Relax',
  legislative: 'Institucional',
  public: 'Servicio público',
  classic: 'Clásicos',
  undefined: 'Otros',
};

const CATEGORY_BY_LANG = {
  it: CATEGORY_IT,
  en: CATEGORY_EN,
  es: CATEGORY_ES,
};

const LANGUAGE_ES = {
  ita: 'Italiano',
  eng: 'Inglés',
  spa: 'Español',
  fra: 'Francés',
  deu: 'Alemán',
  por: 'Portugués',
  ara: 'Árabe',
  rus: 'Ruso',
  zho: 'Chino',
  jpn: 'Japonés',
  kor: 'Coreano',
  nld: 'Neerlandés',
  pol: 'Polaco',
  tur: 'Turco',
  hin: 'Hindi',
  swe: 'Sueco',
  nor: 'Noruego',
  dan: 'Danés',
  fin: 'Finlandés',
  ell: 'Griego',
  ces: 'Checo',
  ron: 'Rumano',
  hun: 'Húngaro',
  ukr: 'Ucraniano',
  cat: 'Catalán',
  glg: 'Gallego',
  eus: 'Vasco',
  sqi: 'Albanés',
  srp: 'Serbio',
  hrv: 'Croata',
  slk: 'Eslovaco',
  slv: 'Esloveno',
  bul: 'Búlgaro',
  lit: 'Lituano',
  lav: 'Letón',
  est: 'Estonio',
  isl: 'Islandés',
  gle: 'Irlandés',
  cym: 'Galés',
  afr: 'Afrikáans',
  swa: 'Suajili',
  fil: 'Filipino',
  ben: 'Bengalí',
  urd: 'Urdu',
  fas: 'Persa',
  heb: 'Hebreo',
  tha: 'Tailandés',
  vie: 'Vietnamita',
  ind: 'Indonesio',
  msa: 'Malayo',
};

const LANGUAGE_EN = {
  ita: 'Italian',
  eng: 'English',
  spa: 'Spanish',
  fra: 'French',
  deu: 'German',
  por: 'Portuguese',
  ara: 'Arabic',
  rus: 'Russian',
  zho: 'Chinese',
  jpn: 'Japanese',
  kor: 'Korean',
  nld: 'Dutch',
  pol: 'Polish',
  tur: 'Turkish',
  hin: 'Hindi',
  swe: 'Swedish',
  nor: 'Norwegian',
  dan: 'Danish',
  fin: 'Finnish',
  ell: 'Greek',
  ces: 'Czech',
  ron: 'Romanian',
  hun: 'Hungarian',
  ukr: 'Ukrainian',
  cat: 'Catalan',
  glg: 'Galician',
  eus: 'Basque',
  sqi: 'Albanian',
  srp: 'Serbian',
  hrv: 'Croatian',
  slk: 'Slovak',
  slv: 'Slovenian',
  bul: 'Bulgarian',
  lit: 'Lithuanian',
  lav: 'Latvian',
  est: 'Estonian',
  isl: 'Icelandic',
  gle: 'Irish',
  cym: 'Welsh',
  afr: 'Afrikaans',
  swa: 'Swahili',
  fil: 'Filipino',
  ben: 'Bengali',
  urd: 'Urdu',
  fas: 'Persian',
  heb: 'Hebrew',
  tha: 'Thai',
  vie: 'Vietnamese',
  ind: 'Indonesian',
  msa: 'Malay',
};

const LANGUAGE_BY_LANG = {
  it: LANGUAGE_IT,
  en: LANGUAGE_EN,
  es: LANGUAGE_ES,
};

const HOME_STRINGS = {
  it: {
    mostWatched: 'I tuoi più visti',
    mostWatchedSubtitle: 'I canali che guardi più spesso',
    recommended: 'Consigliati per te',
    recommendedSubtitle: 'Scelti per {country}',
    recent: 'Continua a guardare',
    favorites: 'Preferiti',
    countrySubtitle: 'Canali del tuo paese',
    allCountries: 'Internazionale',
  },
  en: {
    mostWatched: 'Most Watched',
    mostWatchedSubtitle: 'Channels you watch most often',
    recommended: 'Recommended for you',
    recommendedSubtitle: 'Picked for {country}',
    recent: 'Continue watching',
    favorites: 'Favorites',
    countrySubtitle: 'Channels from your country',
    allCountries: 'International',
  },
  es: {
    mostWatched: 'Más vistos',
    mostWatchedSubtitle: 'Los canales que ves con más frecuencia',
    recommended: 'Recomendado para ti',
    recommendedSubtitle: 'Elegido para {country}',
    recent: 'Seguir viendo',
    favorites: 'Favoritos',
    countrySubtitle: 'Canales de tu país',
    allCountries: 'Internacional',
  },
};

function isEnglish(lang) {
  return langCode(lang) === 'en';
}

function isSpanish(lang) {
  return langCode(lang) === 'es';
}

export function categoryName(id, lang = 'it') {
  return (
    CATEGORY_BY_LANG[langCode(lang)][id] ||
    meta.categories.get(id)?.name ||
    id
  );
}

export function countryName(code, lang = 'it') {
  if (!code) {
    if (isEnglish(lang)) return 'International';
    if (isSpanish(lang)) return 'Internacional';
    return 'Internazionale';
  }
  try {
    return regionNames(lang).of(code.toUpperCase()) || code;
  } catch {
    return meta.countries.get(code)?.name || code;
  }
}

export function countryFlag(code) {
  if (!code) return '🌍';
  const stored = meta.countries.get(code.toUpperCase())?.flag;
  if (stored) return stored;
  return String.fromCodePoint(
    ...code
      .toUpperCase()
      .split('')
      .map((c) => 0x1f1e6 + c.charCodeAt(0) - 65),
  );
}

export function languageName(code, lang = 'it') {
  const names = LANGUAGE_BY_LANG[langCode(lang)];
  if (langCode(lang) === 'it') return LANGUAGE_IT[code] || meta.languages.get(code)?.name || code;
  return names[code] || meta.languages.get(code)?.name || LANGUAGE_IT[code] || code;
}

export function refreshMeta() {
  meta = {
    countries: new Map(
      db.prepare('SELECT * FROM countries').all().map((r) => [r.code, r]),
    ),
    categories: new Map(
      db.prepare('SELECT * FROM categories').all().map((r) => [r.id, r]),
    ),
    languages: new Map(
      db.prepare('SELECT * FROM languages').all().map((r) => [r.code, r]),
    ),
  };
}

refreshMeta();

/* ------------------------------------------------------------------ */
/* Channel queries                                                     */
/* ------------------------------------------------------------------ */

const CHANNEL_SELECT = `
  SELECT c.*,
    COALESCE(pc.favorite, 0) AS profile_favorite,
    COALESCE(pc.hidden, 0) AS profile_hidden,
    pc.last_played_at AS profile_last_played_at,
    COALESCE(pc.watch_count, 0) AS profile_watch_count,
    (SELECT COUNT(*) FROM streams s WHERE s.channel_id = c.id AND s.enabled = 1) AS streams_count,
    (SELECT COUNT(*) FROM streams s WHERE s.channel_id = c.id AND s.enabled = 1 AND s.status = 'online') AS online_streams,
    COALESCE((
      SELECT s.quality FROM streams s
      WHERE s.channel_id = c.id AND s.enabled = 1
      ORDER BY s.quality_rank DESC, (s.status = 'online') DESC, s.priority ASC, s.id ASC
      LIMIT 1
    ), 'SD') AS quality
  FROM channels c
  LEFT JOIN profile_channels pc
    ON pc.channel_id = c.id AND pc.profile_id = ?
`;

const CHANNEL_COUNT_FROM = `
  FROM channels c
  LEFT JOIN profile_channels pc
    ON pc.channel_id = c.id AND pc.profile_id = ?
`;

function buildFilters(filters = {}) {
  const where = [];
  const params = [];

  const enabled = filters.enabled === undefined ? 1 : Number(filters.enabled);
  if (enabled !== -1) {
    where.push('c.enabled = ?');
    params.push(enabled ? 1 : 0);
  }
  where.push('(c.playlist_id IS NULL OR c.playlist_id IN (SELECT id FROM playlists WHERE enabled = 1))');

  if (filters.hidden === undefined || filters.hidden === '0' || filters.hidden === 0) {
    where.push('COALESCE(pc.hidden, 0) = 0');
  } else if (filters.hidden !== 'all' && filters.hidden !== -1) {
    where.push('pc.hidden = 1');
  }

  if (filters.favorite === 1 || filters.favorite === '1' || filters.favorite === true) {
    where.push('pc.favorite = 1');
  }

  if (filters.country) {
    const list = String(filters.country)
      .split(',')
      .map((v) => v.trim().toUpperCase())
      .filter(Boolean);
    if (list.length) {
      where.push(`c.country IN (${list.map(() => '?').join(',')})`);
      params.push(...list);
    }
  }

  if (filters.category) {
    const list = String(filters.category)
      .split(',')
      .map((v) => v.trim())
      .filter(Boolean);
    if (list.length) {
      where.push(
        `EXISTS (SELECT 1 FROM json_each(c.categories) j WHERE j.value IN (${list
          .map(() => '?')
          .join(',')}))`,
      );
      params.push(...list);
    }
  }

  if (filters.language) {
    const list = String(filters.language)
      .split(',')
      .map((v) => v.trim())
      .filter(Boolean);
    if (list.length) {
      where.push(
        `EXISTS (SELECT 1 FROM json_each(c.languages) j WHERE j.value IN (${list
          .map(() => '?')
          .join(',')}))`,
      );
      params.push(...list);
    }
  }

  if (filters.quality) {
    where.push(
      'EXISTS (SELECT 1 FROM streams s WHERE s.channel_id = c.id AND s.enabled = 1 AND s.quality = ?)',
    );
    params.push(String(filters.quality));
  }

  if (filters.status && filters.status !== 'all') {
    where.push('c.status = ?');
    params.push(String(filters.status));
  }

  if (filters.number !== undefined && filters.number !== null && filters.number !== '') {
    where.push('c.number = ?');
    params.push(Number(filters.number));
  }

  if (filters.q) {
    const q = `%${String(filters.q).trim()}%`;
    where.push('(c.name LIKE ? OR c.alt_names LIKE ? OR c.id LIKE ?)');
    params.push(q, q, q);
  }

  const sql = where.length ? `WHERE ${where.join(' AND ')}` : '';
  return { sql, params };
}

function sortClause(sort) {
  switch (sort) {
    case 'name':
      return 'ORDER BY c.name COLLATE NOCASE ASC, c.number IS NULL, c.number ASC';
    case 'country':
      return 'ORDER BY c.country ASC, c.number IS NULL, c.number ASC';
    case 'most-watched':
      return 'ORDER BY COALESCE(pc.watch_count, 0) DESC, pc.last_played_at IS NULL, pc.last_played_at DESC, c.number IS NULL, c.number ASC';
    case 'recent':
      return 'ORDER BY pc.last_played_at IS NULL, pc.last_played_at DESC, c.number ASC';
    case 'status':
      return "ORDER BY (c.status = 'online') DESC, c.number IS NULL, c.number ASC";
    case 'random':
      return 'ORDER BY RANDOM()';
    default:
      return 'ORDER BY c.number IS NULL, c.number ASC, c.name COLLATE NOCASE ASC';
  }
}

export function listChannelRows(filters = {}) {
  const profileId = filters.profileId || getDefaultProfileId();
  const { sql, params } = buildFilters(filters);
  const maxLimit = Math.min(Number(filters.maxLimit) || 500, 20000);
  const limit = Math.max(1, Math.min(Number(filters.limit) || 100, maxLimit));
  const offset = Math.max(0, Number(filters.offset) || 0);
  const order = sortClause(filters.sort);

  const rows = db
    .prepare(`${CHANNEL_SELECT} ${sql} ${order} LIMIT ? OFFSET ?`)
    .all(profileId, ...params, limit, offset);
  const total = db
    .prepare(`SELECT COUNT(*) AS n ${CHANNEL_COUNT_FROM} ${sql}`)
    .get(profileId, ...params).n;

  return { rows, total, limit, offset };
}

export function getChannelRow(id, profileId = null) {
  return db
    .prepare(`${CHANNEL_SELECT} WHERE c.id = ?`)
    .get(profileId || getDefaultProfileId(), id);
}

export function getChannelRowByNumber(number, profileId = null) {
  return db
    .prepare(`${CHANNEL_SELECT} WHERE c.number = ? AND c.enabled = 1`)
    .get(profileId || getDefaultProfileId(), Number(number));
}

export function getChannelRowByNorm(norm, profileId = null) {
  return db
    .prepare(
      `${CHANNEL_SELECT} WHERE replace(replace(lower(c.id), '.', ''), '-', '') = ?`,
    )
    .get(profileId || getDefaultProfileId(), norm);
}

/* ------------------------------------------------------------------ */
/* EPG                                                                 */
/* ------------------------------------------------------------------ */

export function getVisibleEpgSourceCount() {
  return db
    .prepare(
      'SELECT COUNT(*) AS n FROM epg_sources WHERE enabled = 1 AND program_count > 0',
    )
    .get().n;
}

export function attachNow(rows, now = Math.floor(Date.now() / 1000)) {
  if (!rows.length || !getVisibleEpgSourceCount()) {
    for (const row of rows) {
      row.now_title = null;
      row.now_start = null;
      row.now_stop = null;
      row.next_title = null;
      row.next_start = null;
    }
    return rows;
  }

  const norms = [...new Set(rows.map((r) => normalizeId(r.id)))];
  const placeholders = norms.map(() => '?').join(',');
  const programs = db
    .prepare(
      `SELECT channel_norm, title, start, stop
       FROM epg_programs
       WHERE channel_norm IN (${placeholders})
         AND stop > ? AND start < ?
       ORDER BY channel_norm, start ASC`,
    )
    .all(...norms, now - 1800, now + 6 * 3600);

  const byNorm = new Map();
  for (const program of programs) {
    if (!byNorm.has(program.channel_norm)) byNorm.set(program.channel_norm, []);
    byNorm.get(program.channel_norm).push(program);
  }

  for (const row of rows) {
    row.now_title = null;
    row.now_start = null;
    row.now_stop = null;
    row.next_title = null;
    row.next_start = null;
    const list = byNorm.get(normalizeId(row.id)) || [];
    const current = list.find((p) => p.start <= now && p.stop > now);
    const next = list.find((p) => p.start > now);
    if (current) {
      row.now_title = current.title;
      row.now_start = current.start;
      row.now_stop = current.stop;
    }
    if (next) {
      row.next_title = next.title;
      row.next_start = next.start;
    }
  }
  return rows;
}

export function getNowNext(channelId, now = Math.floor(Date.now() / 1000)) {
  const norm = normalizeId(channelId);
  const rows = db
    .prepare(
      `SELECT title, start, stop, description, category
       FROM epg_programs
       WHERE channel_norm = ? AND stop > ? AND start < ?
       ORDER BY start ASC LIMIT 12`,
    )
    .all(norm, now - 6 * 3600, now + 24 * 3600);
  const current = rows.find((p) => p.start <= now && p.stop > now) || null;
  const next = rows.find((p) => p.start > now) || null;
  return { now: current, next, upcoming: rows.filter((p) => p.start > now).slice(0, 6) };
}

/* ------------------------------------------------------------------ */
/* DTO                                                                 */
/* ------------------------------------------------------------------ */

export function channelDto(row, baseUrl, { withEpg = false, lang = 'it' } = {}) {
  if (!row) return null;
  const categories = safeJsonParse(row.categories, []);
  const languages = safeJsonParse(row.languages, []);
  const logo = row.logo
    ? `${baseUrl}/logo/${encodeURIComponent(row.id)}`
    : null;
  const favorite =
    row.profile_favorite !== undefined ? !!row.profile_favorite : !!row.favorite;
  const hidden =
    row.profile_hidden !== undefined ? !!row.profile_hidden : !!row.hidden;
  return {
    id: row.id,
    number: row.number,
    name: row.name,
    country: row.country || null,
    countryName: countryName(row.country, lang),
    flag: countryFlag(row.country),
    categories,
    categoryNames: categories.map((id) => categoryName(id, lang)),
    languages,
    languageNames: languages.slice(0, 3).map((code) => languageName(code, lang)),
    quality: row.quality || 'SD',
    logo,
    favorite,
    hidden,
    enabled: !!row.enabled,
    status: row.status || 'unknown',
    streamsCount: row.streams_count ?? 0,
    onlineStreams: row.online_streams ?? 0,
    isNsfw: !!row.is_nsfw,
    streamUrl: `${baseUrl}/stream/${encodeURIComponent(row.id)}/playlist.m3u8`,
    epgId: row.id,
    lastPlayedAt: row.profile_last_played_at ?? row.last_played_at ?? null,
    now: row.now_title
      ? { title: row.now_title, start: row.now_start, stop: row.now_stop }
      : null,
    next: row.next_title ? { title: row.next_title, start: row.next_start } : null,
    ...(withEpg ? { epg: getNowNext(row.id) } : {}),
  };
}

export function listChannels(filters, baseUrl, lang = 'it') {
  const result = listChannelRows(filters);
  attachNow(result.rows);
  return {
    items: result.rows.map((row) => channelDto(row, baseUrl, { lang })),
    total: result.total,
    limit: result.limit,
    offset: result.offset,
    hasMore: result.offset + result.rows.length < result.total,
  };
}

/* ------------------------------------------------------------------ */
/* Stats / metadata                                                    */
/* ------------------------------------------------------------------ */

export function getStats(profileId = null) {
  const channels = db
    .prepare('SELECT COUNT(*) AS n FROM channels WHERE enabled = 1')
    .get().n;
  const allChannels = db.prepare('SELECT COUNT(*) AS n FROM channels').get().n;
  const streams = db
    .prepare('SELECT COUNT(*) AS n FROM streams WHERE enabled = 1')
    .get().n;
  const onlineStreams = db
    .prepare(
      "SELECT COUNT(*) AS n FROM streams WHERE enabled = 1 AND status = 'online'",
    )
    .get().n;
  const pid = profileId || getDefaultProfileId();
  const favoriteRow = db
    .prepare(
      'SELECT COUNT(*) AS n FROM profile_channels WHERE profile_id = ? AND favorite = 1',
    )
    .get(pid);
  const hiddenRow = db
    .prepare(
      'SELECT COUNT(*) AS n FROM profile_channels WHERE profile_id = ? AND hidden = 1',
    )
    .get(pid);
  const epgPrograms = db.prepare('SELECT COUNT(*) AS n FROM epg_programs').get().n;
  const countries = db
    .prepare(
      'SELECT COUNT(DISTINCT country) AS n FROM channels WHERE enabled = 1 AND country IS NOT NULL',
    )
    .get().n;
  return {
    channels,
    allChannels,
    streams,
    onlineStreams,
    favorites: favoriteRow.n,
    hidden: hiddenRow.n,
    epgPrograms,
    countries,
  };
}

export function getFacets(lang = 'it') {
  const countries = db
    .prepare(
      `SELECT country AS code, COUNT(*) AS count
       FROM channels WHERE enabled = 1 AND country IS NOT NULL
       GROUP BY country ORDER BY count DESC`,
    )
    .all()
    .map((row) => ({
      code: row.code,
      count: row.count,
      name: countryName(row.code, lang),
      flag: countryFlag(row.code),
    }));

  const categories = db
    .prepare(
      `SELECT j.value AS id, COUNT(*) AS count
       FROM channels c, json_each(c.categories) j
       WHERE c.enabled = 1
       GROUP BY j.value ORDER BY count DESC`,
    )
    .all()
    .map((row) => ({
      id: row.id,
      count: row.count,
      name: categoryName(row.id, lang),
    }));

  const languages = db
    .prepare(
      `SELECT j.value AS code, COUNT(*) AS count
       FROM channels c, json_each(c.languages) j
       WHERE c.enabled = 1
       GROUP BY j.value ORDER BY count DESC LIMIT 60`,
    )
    .all()
    .map((row) => ({
      code: row.code,
      count: row.count,
      name: languageName(row.code, lang),
    }));

  return { countries, categories, languages };
}

export function getHomeRows(baseUrl, limit = 18, profile = null) {
  const settings = getAllSettings();
  const activeProfile = profile || getDefaultProfile();
  const profileId = activeProfile?.id || getDefaultProfileId();
  const lang = activeProfile?.language || 'it';
  const strings = HOME_STRINGS[langCode(lang)] || HOME_STRINGS.it;
  const country = (activeProfile?.country || settings.default_country || config.defaultCountry)
    .toUpperCase();
  const rows = [];
  const dto = (row) => channelDto(row, baseUrl, { lang });

  // Most watched channels row: recommended first on the Home page
  const mostWatched = listChannelRows({
    sort: 'most-watched',
    limit: Math.min(limit, 16),
    profileId,
  });
  const mostWatchedRows = attachNow(
    mostWatched.rows
      .filter((r) => (r.profile_watch_count || 0) > 0 || r.profile_last_played_at != null)
      .slice(0, 16),
  );
  if (mostWatchedRows.length) {
    rows.push({
      id: 'most-watched',
      title: strings.mostWatched || 'I tuoi più visti',
      subtitle: strings.mostWatchedSubtitle || 'I canali che guardi più spesso',
      type: 'channels',
      total: mostWatchedRows.length,
      channels: mostWatchedRows.map(dto),
    });
  }

  // Region-aware selection row
  let recommended = listChannelRows({
    country,
    profileId,
    limit: Math.min(limit, 16),
    sort: 'status',
  });
  if (!recommended.rows.length) {
    recommended = listChannelRows({
      profileId,
      limit: Math.min(limit, 16),
      sort: 'status',
    });
  }
  if (recommended.rows.length) {
    attachNow(recommended.rows);
    rows.push({
      id: 'recommended',
      title: strings.recommended,
      subtitle: strings.recommendedSubtitle.replace(
        '{country}',
        country ? countryName(country, lang) : strings.allCountries,
      ),
      type: 'channels',
      total: recommended.total,
      channels: recommended.rows.map(dto),
    });
  }

  const recent = listChannelRows({ sort: 'recent', limit, hidden: 0, profileId });
  const recentRows = attachNow(
    recent.rows
      .filter((r) => r.profile_last_played_at != null)
      .slice(0, 12),
  );
  if (recentRows.length) {
    rows.push({
      id: 'recent',
      title: strings.recent,
      type: 'channels',
      channels: recentRows.map(dto),
    });
  }

  const favorites = listChannelRows({
    favorite: 1,
    limit,
    sort: 'number',
    profileId,
  });
  if (favorites.rows.length) {
    attachNow(favorites.rows);
    rows.push({
      id: 'favorites',
      title: strings.favorites,
      type: 'channels',
      total: favorites.total,
      channels: favorites.rows.map(dto),
    });
  }

  const countryRows = listChannelRows({
    country,
    limit,
    sort: 'number',
    profileId,
  });
  if (countryRows.rows.length) {
    attachNow(countryRows.rows);
    rows.push({
      id: `country:${country}`,
      title: countryName(country, lang),
      subtitle: strings.countrySubtitle,
      type: 'channels',
      total: countryRows.total,
      channels: countryRows.rows.map(dto),
    });
  }

  const featuredCategories = [
    'news',
    'sports',
    'movies',
    'kids',
    'entertainment',
    'music',
    'documentary',
  ];
  for (const category of featuredCategories) {
    let result = listChannelRows({
      category,
      country,
      limit,
      sort: 'number',
      profileId,
    });
    if (!result.rows.length) {
      result = listChannelRows({ category, limit, sort: 'number', profileId });
    }
    if (!result.rows.length) continue;
    attachNow(result.rows);
    rows.push({
      id: `category:${category}`,
      title: categoryName(category, lang),
      type: 'channels',
      total: result.total,
      channels: result.rows.map(dto),
    });
  }

  return rows;
}

/* ------------------------------------------------------------------ */
/* Playlists (Multiple M3U sources)                                   */
/* ------------------------------------------------------------------ */

export function listPlaylists() {
  return db
    .prepare('SELECT * FROM playlists ORDER BY created_at ASC')
    .all()
    .map((p) => ({
      id: p.id,
      name: p.name,
      url: p.url,
      enabled: p.enabled === 1,
      channelCount: p.channel_count,
      createdAt: p.created_at,
      updatedAt: p.updated_at,
    }));
}

export function getPlaylist(id) {
  const p = db.prepare('SELECT * FROM playlists WHERE id = ?').get(id);
  if (!p) return null;
  return {
    id: p.id,
    name: p.name,
    url: p.url,
    enabled: p.enabled === 1,
    channelCount: p.channel_count,
    createdAt: p.created_at,
    updatedAt: p.updated_at,
  };
}

export function savePlaylist({ id, name, url, enabled = 1, channel_count = 0 }) {
  const now = Math.floor(Date.now() / 1000);
  const existing = db.prepare('SELECT id FROM playlists WHERE id = ?').get(id);
  if (existing) {
    db.prepare(`
      UPDATE playlists
      SET name = ?, url = ?, enabled = ?, channel_count = ?, updated_at = ?
      WHERE id = ?
    `).run(name, url, enabled ? 1 : 0, channel_count, now, id);
  } else {
    db.prepare(`
      INSERT INTO playlists (id, name, url, enabled, channel_count, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(id, name, url, enabled ? 1 : 0, channel_count, now, now);
  }
}

export function togglePlaylist(id, enabled) {
  const now = Math.floor(Date.now() / 1000);
  db.prepare('UPDATE playlists SET enabled = ?, updated_at = ? WHERE id = ?')
    .run(enabled ? 1 : 0, now, id);
}

export function deletePlaylist(id) {
  const delStreams = db.prepare(
    'DELETE FROM streams WHERE channel_id IN (SELECT id FROM channels WHERE playlist_id = ?)',
  );
  const delChannels = db.prepare('DELETE FROM channels WHERE playlist_id = ?');
  const delPlaylist = db.prepare('DELETE FROM playlists WHERE id = ?');
  transaction(() => {
    delStreams.run(id);
    delChannels.run(id);
    delPlaylist.run(id);
  });
}

