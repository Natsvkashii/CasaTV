/* CasaTV Web App — Unified PC, Tablet & Smartphone Client matching Android TV & Mobile */

import { WORLD_MAP_GRATICULE, WORLD_MAP_REGIONS } from './world-map-data.js?v=26.11.025';

const GITHUB_REPO = 'Natsvkashii/CasaTV';
const WEB_APP_VERSION = '26.11.025';
const CHANNEL_PAGE_SIZE = 84;
const PROFILE_AVATARS = [
  '🦊', '🐼', '🐯', '🐨', '🦁', '🐸', '🐵', '🐺', '🦄', '🐙',
  '🐳', '🦉', '🐻', '🐧', '🦖', '🐝', '🐬', '🦋', '🐰', '🐲',
];

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const esc = (v) =>
  String(v ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[c]);

function fmtNum(n) {
  return new Intl.NumberFormat(getLang() === 'en' ? 'en-US' : getLang() === 'es' ? 'es-ES' : 'it-IT').format(
    Number(n) || 0,
  );
}

function fmtBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return `${parseFloat((bytes / Math.pow(1024, i)).toFixed(1))} ${units[i]}`;
}

function initials(name) {
  const parts = String(name || 'TV').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return 'TV';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

function countryFlag(code) {
  const clean = String(code || '').trim().toUpperCase();
  const norm = clean === 'UK' ? 'GB' : clean;
  if (!/^[A-Z]{2}$/.test(norm)) return '🌍';
  return String.fromCodePoint(norm.charCodeAt(0) + 127397, norm.charCodeAt(1) + 127397);
}

const COUNTRY_TIMEZONES = {
  AD: 'Europe/Andorra', AE: 'Asia/Dubai', AF: 'Asia/Kabul', AG: 'America/Antigua', AL: 'Europe/Tirane',
  AM: 'Asia/Yerevan', AO: 'Africa/Luanda', AR: 'America/Argentina/Buenos_Aires', AT: 'Europe/Vienna',
  AU: 'Australia/Sydney', AW: 'America/Aruba', AZ: 'Asia/Baku', BA: 'Europe/Sarajevo', BB: 'America/Barbados',
  BD: 'Asia/Dhaka', BE: 'Europe/Brussels', BF: 'Africa/Ouagadougou', BG: 'Europe/Sofia', BH: 'Asia/Bahrain',
  BI: 'Africa/Bujumbura', BJ: 'Africa/Porto-Novo', BM: 'Atlantic/Bermuda', BN: 'Asia/Brunei', BO: 'America/La_Paz',
  BR: 'America/Sao_Paulo', BS: 'America/Nassau', BT: 'Asia/Thimphu', BW: 'Africa/Gaborone', BY: 'Europe/Minsk',
  BZ: 'America/Belize', CA: 'America/Toronto', CD: 'Africa/Kinshasa', CF: 'Africa/Bangui', CG: 'Africa/Brazzaville',
  CH: 'Europe/Zurich', CI: 'Africa/Abidjan', CL: 'America/Santiago', CM: 'Africa/Douala', CN: 'Asia/Shanghai',
  CO: 'America/Bogota', CR: 'America/Costa_Rica', CU: 'America/Havana', CV: 'Atlantic/Cape_Verde',
  CW: 'America/Curacao', CY: 'Asia/Nicosia', CZ: 'Europe/Prague', DE: 'Europe/Berlin', DJ: 'Africa/Djibouti',
  DK: 'Europe/Copenhagen', DM: 'America/Dominica', DO: 'America/Santo_Domingo', DZ: 'Africa/Algiers',
  EC: 'America/Guayaquil', EE: 'Europe/Tallinn', EG: 'Africa/Cairo', ER: 'Africa/Asmara', ES: 'Europe/Madrid',
  ET: 'Africa/Addis_Ababa', FI: 'Europe/Helsinki', FJ: 'Pacific/Fiji', FO: 'Atlantic/Faroe', FR: 'Europe/Paris',
  GA: 'Africa/Libreville', GB: 'Europe/London', UK: 'Europe/London', GD: 'America/Grenada', GE: 'Asia/Tbilisi',
  GF: 'America/Cayenne', GH: 'Africa/Accra', GI: 'Europe/Gibraltar', GL: 'America/Godthab', GM: 'Africa/Banjul',
  GN: 'Africa/Conakry', GP: 'America/Guadeloupe', GQ: 'Africa/Malabo', GR: 'Europe/Athens', GT: 'America/Guatemala',
  GU: 'Pacific/Guam', GW: 'Africa/Bissau', GY: 'America/Guyana', HK: 'Asia/Hong_Kong', HN: 'America/Tegucigalpa',
  HR: 'Europe/Zagreb', HT: 'America/Port-au-Prince', HU: 'Europe/Budapest', ID: 'Asia/Jakarta', IE: 'Europe/Dublin',
  IL: 'Asia/Jerusalem', IN: 'Asia/Kolkata', IQ: 'Asia/Baghdad', IR: 'Asia/Tehran', IS: 'Atlantic/Reykjavik',
  IT: 'Europe/Rome', JM: 'America/Jamaica', JO: 'Asia/Amman', JP: 'Asia/Tokyo', KE: 'Africa/Nairobi',
  KG: 'Asia/Bishkek', KH: 'Asia/Phnom_Penh', KM: 'Indian/Comoro', KN: 'America/St_Kitts', KP: 'Asia/Pyongyang',
  KR: 'Asia/Seoul', KW: 'Asia/Kuwait', KY: 'America/Cayman', KZ: 'Asia/Almaty', LA: 'Asia/Vientiane',
  LB: 'Asia/Beirut', LC: 'America/St_Lucia', LI: 'Europe/Vaduz', LK: 'Asia/Colombo', LR: 'Africa/Monrovia',
  LS: 'Africa/Maseru', LT: 'Europe/Vilnius', LU: 'Europe/Luxembourg', LV: 'Europe/Riga', LY: 'Africa/Tripoli',
  MA: 'Africa/Casablanca', MC: 'Europe/Monaco', MD: 'Europe/Chisinau', ME: 'Europe/Podgorica',
  MG: 'Indian/Antananarivo', MK: 'Europe/Skopje', ML: 'Africa/Bamako', MM: 'Asia/Yangon', MN: 'Asia/Ulaanbaatar',
  MO: 'Asia/Macau', MQ: 'America/Martinique', MR: 'Africa/Nouakchott', MT: 'Europe/Malta', MU: 'Indian/Mauritius',
  MV: 'Indian/Maldives', MW: 'Africa/Blantyre', MX: 'America/Mexico_City', MY: 'Asia/Kuala_Lumpur',
  MZ: 'Africa/Maputo', NA: 'Africa/Windhoek', NC: 'Pacific/Noumea', NE: 'Africa/Niamey', NG: 'Africa/Lagos',
  NI: 'America/Managua', NL: 'Europe/Amsterdam', NO: 'Europe/Oslo', NP: 'Asia/Kathmandu', NZ: 'Pacific/Auckland',
  OM: 'Asia/Muscat', PA: 'America/Panama', PE: 'America/Lima', PF: 'Pacific/Tahiti', PG: 'Pacific/Port_Moresby',
  PH: 'Asia/Manila', PK: 'Asia/Karachi', PL: 'Europe/Warsaw', PR: 'America/Puerto_Rico', PS: 'Asia/Gaza',
  PT: 'Europe/Lisbon', PY: 'America/Asuncion', QA: 'Asia/Qatar', RO: 'Europe/Bucharest', RS: 'Europe/Belgrade',
  RU: 'Europe/Moscow', RW: 'Africa/Kigali', SA: 'Asia/Riyadh', SD: 'Africa/Khartoum', SE: 'Europe/Stockholm',
  SG: 'Asia/Singapore', SI: 'Europe/Ljubljana', SK: 'Europe/Bratislava', SL: 'Africa/Freetown',
  SM: 'Europe/San_Marino', SN: 'Africa/Dakar', SO: 'Africa/Mogadishu', SR: 'America/Paramaribo',
  SS: 'Africa/Juba', SV: 'America/El_Salvador', SY: 'Asia/Damascus', SZ: 'Africa/Mbabane', TD: 'Africa/Ndjamena',
  TG: 'Africa/Lome', TH: 'Asia/Bangkok', TJ: 'Asia/Dushanbe', TL: 'Asia/Dili', TM: 'Asia/Ashgabat',
  TN: 'Africa/Tunis', TR: 'Europe/Istanbul', TT: 'America/Port_of_Spain', TW: 'Asia/Taipei',
  TZ: 'Africa/Dar_es_Salaam', UA: 'Europe/Kiev', UG: 'Africa/Kampala', US: 'America/New_York',
  UY: 'America/Montevideo', UZ: 'Asia/Tashkent', VA: 'Europe/Vatican', VC: 'America/St_Vincent',
  VE: 'America/Caracas', VG: 'America/Tortola', VI: 'America/St_Thomas', VN: 'Asia/Ho_Chi_Minh',
  WS: 'Pacific/Apia', XK: 'Europe/Belgrade', YE: 'Asia/Aden', ZA: 'Africa/Johannesburg',
  ZM: 'Africa/Lusaka', ZW: 'Africa/Harare',
};

function getLocalizedCountryName(code, fallback = '') {
  const clean = String(code || '').trim().toUpperCase();
  const norm = clean === 'UK' ? 'GB' : clean;
  if (/^[A-Z]{2}$/.test(norm)) {
    const facetMatch = state.config?.facets?.countries?.find((c) => {
      const fc = String(c.code || '').trim().toUpperCase();
      return (fc === 'UK' ? 'GB' : fc) === norm;
    });
    try {
      if (typeof Intl !== 'undefined' && Intl.DisplayNames) {
        const dn = new Intl.DisplayNames([getLang()], { type: 'region' });
        const resolved = dn.of(norm);
        if (resolved && resolved !== norm) return resolved;
      }
    } catch (_) {}
    if (facetMatch?.name) return facetMatch.name;
  }
  return String(fallback || clean || '').trim();
}

function getCountryTimeText(code) {
  const clean = String(code || '').trim().toUpperCase();
  const norm = clean === 'UK' ? 'GB' : clean;
  const tz = COUNTRY_TIMEZONES[norm] || COUNTRY_TIMEZONES[clean];
  if (!tz) return null;
  try {
    return new Intl.DateTimeFormat([], {
      timeZone: tz,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(new Date());
  } catch (_) {
    return null;
  }
}

const WEB_AUDIO_EXT_RE = /\.(mp3|aac|aacp|ogg|oga|opus|flac|m4a|wav|wma|pls)(\?|#|$)/i;
const WEB_AUDIO_EP_RE = /(\/stream(\?|\/|$)|;stream|\.(mp3|aac|ogg|opus|flac|m4a)(\?|$))/i;
const WEB_VIDEO_QUALITIES = new Set(['HD', 'FHD', '4K', 'UHD', '1080P', '720P']);
const WEB_VIDEO_NAME_RE = /(^|[\s\-_|/(\[.])(tv|television|televisión|televisió|tele|bfm|vision|visió|video|vídeo|canal|channel|radiotv|radio-tv|visual\s*radio|radiovision|hd|uhd|fhd|4k|1080p|720p|576p|480p|360p|240p)([\s\-_|/)\].]|$)/i;
const WEB_RADIO_NAME_RE = /(^|[\s\-_|/(\[])(fm|am|radio|rádio|rádió|irratia|dab\+?|webradio)([\s\-_|/)\].]|$)/i;

function isLikelyAudioOnlyChannel(ch) {
  if (!ch) return false;
  const url = String(ch.streamUrl || '').trim();
  const urlLower = url.toLowerCase();
  if (WEB_AUDIO_EXT_RE.test(urlLower)) return true;
  const isContainer = urlLower.includes('.m3u8') || urlLower.includes('.mpd') || urlLower.includes('.ts');
  if (!isContainer && WEB_AUDIO_EP_RE.test(urlLower)) return true;

  const cats = [...(ch.categories || []), ...(ch.categoryNames || [])];
  if (cats.some((raw) => {
    const c = String(raw || '').toLowerCase();
    return (c.includes('radio') || c.includes('irratia')) && !c.includes('tv') && !c.includes('video') && !c.includes('tele');
  })) {
    return true;
  }

  const q = String(ch.quality || '').trim().toUpperCase();
  const hasVideoHint = WEB_VIDEO_QUALITIES.has(q) || WEB_VIDEO_NAME_RE.test(String(ch.name || ''));
  return !hasVideoHint && WEB_RADIO_NAME_RE.test(String(ch.name || ''));
}

/* ---------------- Device Detection (Smartphone / Tablet / PC) ---------------- */
function detectDeviceProfile() {
  const ua = navigator.userAgent || '';
  const chMobile = navigator.userAgentData?.mobile;
  const maxTouch = navigator.maxTouchPoints || 0;
  const coarse = window.matchMedia?.('(pointer: coarse)')?.matches || false;
  const w = window.innerWidth || 1280;
  const h = window.innerHeight || 720;
  const minDim = Math.min(w, h);
  const maxDim = Math.max(w, h);

  if (
    /iPhone|iPod/i.test(ua) ||
    (/Android/i.test(ua) && /Mobile/i.test(ua)) ||
    (chMobile === true && minDim < 600) ||
    (coarse && minDim < 600 && w < 720) ||
    w < 640
  ) {
    return 'smartphone';
  }
  if (
    /iPad/i.test(ua) ||
    (/Macintosh/i.test(ua) && maxTouch > 1) ||
    (/Android/i.test(ua) && !/Mobile/i.test(ua)) ||
    /Tablet|PlayBook|Silk/i.test(ua) ||
    (coarse && minDim >= 600 && maxDim <= 1450) ||
    (maxTouch > 0 && w >= 640 && w <= 1180)
  ) {
    return 'tablet';
  }
  return 'pc';
}

/* ---------------- State ---------------- */
const state = {
  deviceId: getOrCreateDeviceId(),
  deviceType: detectDeviceProfile(),
  theme: localStorage.getItem('casatv_theme') === 'light' ? 'light' : 'dark',
  multiTvSync: localStorage.getItem('casatv_multi_tv_sync') !== 'false',
  liveSafetyBuffer: localStorage.getItem('casatv_live_safety_buffer') !== 'false',
  mixIncludeAudioOnly: localStorage.getItem('casatv_mix_include_audio_only') === 'true',
  showRemoteHintsBanner: localStorage.getItem('casatv_show_remote_hints') !== 'false',
  updateFrequency: localStorage.getItem('casatv_update_freq') || 'WEEKLY',
  page: 'channels', // 'channels' | 'favorites' | 'settings'
  settingsSection: 'profile', // 'profile' | 'sources' | 'server' | 'guide'
  serverUrl: window.location.origin,
  serverDisconnected: false,
  config: null,
  system: null,
  profiles: [],
  activeProfile: null,
  tvCountry: localStorage.getItem('casatv_tv_country') || 'IT',

  // Channel catalog
  channels: [],
  channelsTotal: 0,
  channelsLoading: false,
  channelsFallback: false,
  channelsRequestSeq: 0,
  localVisibleLimit: CHANNEL_PAGE_SIZE,
  channelsObserver: null,
  favorites: [],
  favoritesLoading: false,
  localOverrideChannels: null,
  localSourceLabel: '',
  localSourceError: '',
  searchQuery: '',
  searchResults: [],
  searchLoading: false,
  searchTimer: null,

  // Mix Mappamondo & Zapping State (mirrors MainViewModel)
  allWorldPool: [],
  mixPoolLoaded: false,
  mixPoolPromise: null,
  selectedMixRegions: new Set(['ALL_WORLD']),
  selectedMixLanguages: new Set(),
  selectedMixGenres: new Set(),
  mixFilterTab: 'languages',
  mixHistory: [],
  mixIndex: -1,
  deadMixIds: new Set(),
  runtimeAudioOnlyIds: new Set(),
  mixAudioSkipCount: 0,
  verifiedNextMix: null,
  mixVerifyAbort: null,
  hoveredMixRegion: 'EUROPE',

  // Player State
  playerOpen: false,
  playerChannel: null,
  playerQueue: [],
  playerIsMix: false,
  playerSources: [],
  playerSourceIdx: 0,
  hls: null,
  dashPlayer: null,
  mpegtsPlayer: null,
  cardPreviewHls: null,
  cardPreviewTimer: null,
  cardPreviewVideo: null,
  cardPreviewCard: null,
  hoveredPreviewCard: null,
  drawerOpen: false,
  drawerCursorIdx: 0,
  drawerPreviewHls: null,
  drawerPreviewTimer: null,
  drawerPreviewVideo: null,
  drawerPreviewItem: null,
  playbackState: 'idle',
  playbackChannel: '',
  idleTimer: null,
  hintsBannerTimer: null,
  audioCheckTimer: null,
  currentStreamAudioOnly: false,
  numBuffer: '',
  numTimer: null,
  syncTimer: null,
  syncActivePeers: 1,
  syncText: '',
  systemPollTimer: null,
  mixErrorRetryCount: 0,
  tuneSeq: 0,
  reconnectTimer: null,
  reconnectAttempt: 0,
  reconnectActive: false,
  hasPlayedCurrentChannel: false,
  cushionTimer: null,
  cushionBuilding: false,
  lastCushionRateMs: 0,
  lastStallTime: -1,
  lastStallWallMs: 0,
  bufferingSinceMs: 0,
  readySinceMs: 0,
  playerTuneFailTimer: null,

  // GitHub Updates
  updateInfo: null,
  profileManaging: false,
  profileEditing: null, // null | 'new' | profileObj
};

function getOrCreateDeviceId() {
  let id = localStorage.getItem('casatv_web_device_id');
  if (!id) {
    id = `web-${Math.random().toString(36).slice(2, 9)}-${Date.now().toString(36)}`;
    localStorage.setItem('casatv_web_device_id', id);
  }
  return id;
}

function getLang() {
  const pLang = state.activeProfile?.language;
  const nav = (navigator.language || 'it').slice(0, 2).toLowerCase();
  const code = (pLang || nav || 'it').slice(0, 2).toLowerCase();
  return code === 'en' || code === 'es' ? code : 'it';
}

function t(it, en, es = en) {
  const l = getLang();
  if (l === 'en') return en;
  if (l === 'es') return es;
  return it;
}

function deviceLabel(type = state.deviceType) {
  if (type === 'smartphone') return 'Smartphone Web';
  if (type === 'tablet') return 'Tablet Web';
  return 'PC Web';
}

function deviceShortBadge(type = state.deviceType) {
  if (type === 'smartphone') return `📱 ${t('Smartphone', 'Smartphone', 'Móvil')}`;
  if (type === 'tablet') return `📋 ${t('Tablet', 'Tablet', 'Tablet')}`;
  return `🖥 ${t('PC / Desktop', 'PC / Desktop', 'PC / Escritorio')}`;
}

function toast(msg, kind = 'ok', ms = 3200) {
  const stack = $('#toasts');
  if (!stack) return;
  const el = document.createElement('div');
  el.className = `toast ${kind}`;
  el.textContent = msg;
  stack.appendChild(el);
  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(8px)';
    setTimeout(() => el.remove(), 220);
  }, ms);
}

/* ---------------- Theme & Device Application ---------------- */
function applyTheme(mode) {
  state.theme = mode === 'light' ? 'light' : 'dark';
  localStorage.setItem('casatv_theme', state.theme);
  document.documentElement.dataset.theme = state.theme;
  const meta = $('#meta-theme-color');
  if (meta) meta.content = state.theme === 'light' ? '#F0F6FC' : '#060B14';
}

function applyDeviceClass() {
  const next = detectDeviceProfile();
  const changed = next !== state.deviceType;
  state.deviceType = next;
  document.documentElement.dataset.device = next;
  return changed;
}

applyTheme(state.theme);
applyDeviceClass();

window.addEventListener('resize', () => {
  if (applyDeviceClass()) {
    renderCurrentScreen();
  }
});
window.addEventListener('orientationchange', () => {
  setTimeout(() => {
    if (applyDeviceClass()) renderCurrentScreen();
  }, 120);
});

/* ---------------- API Client ---------------- */
async function api(path, opts = {}) {
  const headers = {
    Accept: 'application/json',
    'X-Device-Id': state.deviceId,
    'X-Device-Name': `${detectBrowserName()} (${deviceLabel()})`,
    'X-Device-Platform': deviceLabel(),
    'X-App-Version': WEB_APP_VERSION,
    'X-Playback-State': state.playbackState,
    'X-Playback-Channel': state.playbackChannel,
    ...(opts.headers || {}),
  };
  if (state.activeProfile?.id) {
    headers['X-Profile-Id'] = state.activeProfile.id;
  }
  let body = opts.body;
  if (body && typeof body === 'object' && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(body);
  }
  const base = state.serverUrl && state.serverUrl !== window.location.origin ? state.serverUrl : '';
  const url = path.startsWith('http') ? path : `${base}${path}`;
  const res = await fetch(url, { ...opts, body, headers });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const json = await res.json();
      if (json.error) msg = json.error;
    } catch (_) {}
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  const ct = res.headers.get('content-type') || '';
  return ct.includes('json') ? res.json() : res.text();
}

function detectBrowserName() {
  const ua = navigator.userAgent || '';
  if (/Edg\//i.test(ua)) return 'Edge';
  if (/Chrome|CriOS/i.test(ua)) return 'Chrome';
  if (/Firefox|FxiOS/i.test(ua)) return 'Firefox';
  if (/Safari/i.test(ua)) return 'Safari';
  return 'Browser';
}

setInterval(() => {
  api('/api/clients/heartbeat', {
    method: 'POST',
    body: {
      deviceId: state.deviceId,
      name: `${detectBrowserName()} (${deviceLabel()})`,
      platform: deviceLabel(),
      appVersion: WEB_APP_VERSION,
      profileId: state.activeProfile?.id || '',
      state: state.playbackState,
      channel: state.playbackChannel,
    },
  }).catch(() => {});
}, 22000);

/* ---------------- Mappamondo World Regions (Ported from MixRegions.kt) ---------------- */
const MIX_REGIONS = [
  {
    id: 'ALL_WORLD',
    titleIt: 'TUTTO IL MONDO',
    titleEn: 'ALL THE WORLD',
    titleEs: 'TODO EL MUNDO',
    subIt: 'Zapping globale senza confini',
    subEn: 'Global shuffle across every region',
    subEs: 'Zapping global sin fronteras',
    codes: [],
  },
  {
    id: 'EUROPE',
    titleIt: 'EUROPA',
    titleEn: 'EUROPE',
    titleEs: 'EUROPA',
    subIt: 'Mediterranea, Occidentale e Nordica',
    subEn: 'Western, Nordic & Mediterranean Europe',
    subEs: 'Europa Occidental, Nórdica y Mediterránea',
    codes: ['AL','AD','AT','BE','BA','BG','BY','CH','CY','CZ','DE','DK','EE','ES','FI','FR','GB','UK','GR','HR','HU','IE','IS','IT','LI','LT','LU','LV','MC','MD','ME','MK','MT','NL','NO','PL','PT','RO','RS','SE','SI','SK','SM','UA','VA','XK','FO','GI'],
    anchor: [0.515, 0.275],
    neighbors: { left: 'NORTH_AMERICA', right: 'EURASIA', down: 'NORTH_AFRICA' },
    polygons: [
      [[0.436,0.334],[0.442,0.308],[0.464,0.296],[0.470,0.274],[0.494,0.258],[0.506,0.234],[0.532,0.236],[0.558,0.220],[0.582,0.214],[0.596,0.252],[0.592,0.296],[0.574,0.328],[0.556,0.348],[0.548,0.378],[0.532,0.374],[0.526,0.344],[0.518,0.372],[0.502,0.378],[0.494,0.348],[0.484,0.326],[0.468,0.342],[0.456,0.362],[0.436,0.358]],
      [[0.492,0.208],[0.496,0.168],[0.516,0.128],[0.548,0.102],[0.580,0.114],[0.584,0.158],[0.576,0.202],[0.552,0.214],[0.534,0.194],[0.518,0.222],[0.498,0.226]],
      [[0.450,0.242],[0.458,0.205],[0.468,0.178],[0.478,0.196],[0.476,0.234],[0.484,0.258],[0.466,0.270],[0.448,0.266]],
      [[0.428,0.228],[0.438,0.214],[0.446,0.226],[0.444,0.254],[0.432,0.260],[0.426,0.246]],
      [[0.414,0.136],[0.432,0.122],[0.452,0.126],[0.454,0.146],[0.434,0.152],[0.416,0.146]],
    ],
  },
  {
    id: 'NORTH_AMERICA',
    titleIt: 'NORD AMERICA',
    titleEn: 'NORTH AMERICA',
    titleEs: 'NORTEAMÉRICA',
    subIt: 'Stati Uniti, Canada e Nord Atlantico',
    subEn: 'United States, Canada & North Atlantic',
    subEs: 'Estados Unidos, Canadá y Atlántico Norte',
    codes: ['US','CA','BM','GL','PM'],
    anchor: [0.21, 0.265],
    neighbors: { right: 'EUROPE', down: 'CENTRAL_AMERICA' },
    polygons: [
      [[0.030,0.142],[0.068,0.105],[0.118,0.098],[0.155,0.118],[0.195,0.102],[0.225,0.132],[0.250,0.120],[0.265,0.165],[0.242,0.195],[0.275,0.175],[0.305,0.155],[0.335,0.195],[0.350,0.228],[0.320,0.258],[0.302,0.285],[0.286,0.322],[0.276,0.365],[0.280,0.405],[0.264,0.398],[0.252,0.362],[0.224,0.358],[0.202,0.382],[0.186,0.376],[0.160,0.366],[0.142,0.336],[0.125,0.285],[0.118,0.232],[0.095,0.195],[0.062,0.202],[0.034,0.182]],
      [[0.315,0.068],[0.355,0.042],[0.405,0.036],[0.436,0.058],[0.425,0.102],[0.396,0.148],[0.372,0.198],[0.350,0.190],[0.338,0.142],[0.312,0.105]],
      [[0.208,0.055],[0.262,0.042],[0.298,0.056],[0.296,0.096],[0.262,0.105],[0.228,0.088],[0.198,0.082]],
    ],
  },
  {
    id: 'CENTRAL_AMERICA',
    titleIt: 'CENTRO AMERICA',
    titleEn: 'CENTRAL AMERICA',
    titleEs: 'CENTROAMÉRICA',
    subIt: 'Messico, Istmo Centrale e Caraibi',
    subEn: 'Mexico, Central Isthmus & Caribbean',
    subEs: 'México, Istmo Central y Caribe',
    codes: ['MX','BZ','CR','CU','DO','GT','HN','HT','JM','NI','PA','PR','SV','BS','BB','TT','AG','AW','CW','DM','GD','GP','KN','LC','MQ','SX','VC','VG','VI','KY','TC','BQ','AI','MS','BL','MF'],
    anchor: [0.22, 0.46],
    neighbors: { left: 'NORTH_AMERICA', right: 'NORTH_AFRICA', up: 'NORTH_AMERICA', down: 'SOUTH_AMERICA' },
    polygons: [
      [[0.160,0.374],[0.190,0.388],[0.205,0.395],[0.215,0.432],[0.242,0.432],[0.248,0.452],[0.236,0.470],[0.252,0.495],[0.268,0.518],[0.264,0.538],[0.244,0.528],[0.226,0.504],[0.204,0.482],[0.182,0.456],[0.166,0.422],[0.152,0.395]],
      [[0.244,0.426],[0.272,0.424],[0.302,0.438],[0.305,0.452],[0.282,0.454],[0.252,0.442]],
      [[0.306,0.454],[0.332,0.456],[0.348,0.466],[0.344,0.482],[0.320,0.482],[0.304,0.470]],
    ],
  },
  {
    id: 'SOUTH_AMERICA',
    titleIt: 'SUD AMERICA',
    titleEn: 'SOUTH AMERICA',
    titleEs: 'SUDAMÉRICA',
    subIt: 'Regione Andina, Brasile e Cono Sud',
    subEn: 'Andean Region, Brazil & Southern Cone',
    subEs: 'Región Andina, Brasil y Cono Sur',
    codes: ['AR','BO','BR','CL','CO','EC','FK','GF','GY','PE','PY','SR','UY','VE'],
    anchor: [0.325, 0.675],
    neighbors: { right: 'SOUTH_AFRICA', up: 'CENTRAL_AMERICA' },
    polygons: [
      [[0.266,0.538],[0.286,0.516],[0.314,0.514],[0.342,0.534],[0.376,0.562],[0.408,0.606],[0.414,0.648],[0.396,0.692],[0.378,0.738],[0.354,0.782],[0.336,0.828],[0.328,0.876],[0.314,0.916],[0.292,0.912],[0.284,0.862],[0.282,0.802],[0.276,0.735],[0.266,0.675],[0.250,0.625],[0.252,0.576]],
    ],
  },
  {
    id: 'NORTH_AFRICA',
    titleIt: 'NORD AFRICA',
    titleEn: 'NORTH AFRICA',
    titleEs: 'NORTE DE ÁFRICA',
    subIt: 'Maghreb, Egitto e Sud Mediterraneo',
    subEn: 'Maghreb, Nile Valley & South Med',
    subEs: 'Magreb, Egipto y Sur del Mediterráneo',
    codes: ['DZ','EG','LY','MA','SD','TN','EH'],
    anchor: [0.502, 0.448],
    neighbors: { left: 'CENTRAL_AMERICA', right: 'MIDDLE_EAST', up: 'EUROPE', down: 'SOUTH_AFRICA' },
    polygons: [
      [[0.438,0.390],[0.468,0.376],[0.498,0.380],[0.518,0.402],[0.548,0.408],[0.572,0.414],[0.582,0.456],[0.586,0.508],[0.545,0.516],[0.500,0.518],[0.455,0.512],[0.426,0.496],[0.420,0.452],[0.428,0.414]],
    ],
  },
  {
    id: 'SOUTH_AFRICA',
    titleIt: 'SUD AFRICA',
    titleEn: 'SOUTHERN & SUB-SAHARAN',
    titleEs: 'ÁFRICA AUSTRAL',
    subIt: 'Africa Australe, Orientale e Subsahariana',
    subEn: 'Southern, East & Sub-Saharan Africa',
    subEs: 'África Austral, Oriental y Subsahariana',
    codes: ['AO','BF','BI','BJ','BW','CD','CF','CG','CI','CM','CV','DJ','ER','ET','GA','GH','GM','GN','GQ','GW','KE','KM','LR','LS','MG','ML','MR','MU','MW','MZ','NA','NE','NG','RE','RW','SC','SL','SN','SO','SS','ST','SZ','TD','TG','TZ','UG','YT','ZA','ZM','ZW'],
    anchor: [0.538, 0.648],
    neighbors: { left: 'SOUTH_AMERICA', right: 'AUSTRALIA', up: 'NORTH_AFRICA' },
    polygons: [
      [[0.416,0.520],[0.456,0.516],[0.508,0.520],[0.562,0.518],[0.592,0.524],[0.618,0.548],[0.640,0.542],[0.626,0.595],[0.602,0.652],[0.588,0.714],[0.572,0.768],[0.544,0.812],[0.520,0.806],[0.504,0.752],[0.494,0.690],[0.486,0.628],[0.454,0.598],[0.418,0.574],[0.410,0.544]],
      [[0.604,0.742],[0.616,0.696],[0.630,0.674],[0.636,0.696],[0.628,0.742],[0.618,0.774],[0.604,0.768]],
    ],
  },
  {
    id: 'MIDDLE_EAST',
    titleIt: 'MEDIO ORIENTE',
    titleEn: 'MIDDLE EAST',
    titleEs: 'ORIENTE MEDIO',
    subIt: 'Levante, Penisola Arabica e Golfo',
    subEn: 'Levant, Arabian Peninsula & Gulf',
    subEs: 'Levante, Península Arábiga y Golfo',
    codes: ['AE','BH','IQ','IR','IL','JO','KW','LB','OM','PS','QA','SA','SY','YE'],
    anchor: [0.622, 0.440],
    neighbors: { left: 'NORTH_AFRICA', right: 'ASIA', up: 'EURASIA', down: 'SOUTH_AFRICA' },
    polygons: [
      [[0.576,0.366],[0.608,0.360],[0.642,0.366],[0.668,0.392],[0.670,0.438],[0.656,0.462],[0.664,0.494],[0.634,0.522],[0.606,0.532],[0.592,0.492],[0.580,0.448],[0.572,0.405]],
    ],
  },
  {
    id: 'EURASIA',
    titleIt: 'EURASIA & CAUCASO',
    titleEn: 'EURASIA & CAUCASUS',
    titleEs: 'EURASIA Y CÁUCASO',
    subIt: 'Turchia, Caucaso e Asia Centrale',
    subEn: 'Turkey, Caucasus & Central Asia',
    subEs: 'Turquía, Cáucaso y Asia Central',
    codes: ['AM','AZ','GE','KZ','KG','RU','TJ','TM','TR','UZ'],
    anchor: [0.728, 0.205],
    neighbors: { left: 'EUROPE', down: 'ASIA' },
    polygons: [
      [[0.556,0.330],[0.574,0.310],[0.602,0.312],[0.618,0.334],[0.608,0.356],[0.582,0.358],[0.558,0.352]],
      [[0.584,0.192],[0.596,0.140],[0.640,0.116],[0.700,0.104],[0.775,0.088],[0.848,0.105],[0.916,0.130],[0.952,0.162],[0.920,0.198],[0.896,0.246],[0.872,0.228],[0.842,0.256],[0.790,0.275],[0.735,0.296],[0.688,0.344],[0.648,0.356],[0.616,0.332],[0.600,0.278],[0.590,0.236]],
    ],
  },
  {
    id: 'ASIA',
    titleIt: 'ASIA',
    titleEn: 'ASIA',
    titleEs: 'ASIA',
    subIt: 'Estremo Oriente, India e Sud-Est',
    subEn: 'East Asia, India & Southeast Asia',
    subEs: 'Asia Oriental, India y Sudeste Asiático',
    codes: ['AF','BD','BN','BT','CN','HK','ID','IN','JP','KH','KP','KR','LA','LK','MM','MN','MO','MV','MY','NP','PH','PK','SG','TH','TL','TW','VN'],
    anchor: [0.768, 0.420],
    neighbors: { left: 'MIDDLE_EAST', right: 'OCEANIA', up: 'EURASIA', down: 'AUSTRALIA' },
    polygons: [
      [[0.666,0.365],[0.702,0.330],[0.752,0.295],[0.808,0.268],[0.848,0.278],[0.862,0.318],[0.848,0.362],[0.852,0.402],[0.836,0.448],[0.802,0.486],[0.804,0.542],[0.782,0.562],[0.764,0.520],[0.748,0.482],[0.722,0.506],[0.712,0.548],[0.694,0.512],[0.680,0.456],[0.666,0.418]],
      [[0.878,0.285],[0.900,0.282],[0.908,0.312],[0.898,0.355],[0.880,0.395],[0.866,0.390],[0.876,0.348],[0.872,0.314]],
      [[0.742,0.570],[0.766,0.578],[0.794,0.608],[0.818,0.625],[0.806,0.640],[0.770,0.625],[0.738,0.592]],
      [[0.816,0.566],[0.842,0.556],[0.862,0.576],[0.858,0.614],[0.834,0.618],[0.814,0.596]],
      [[0.864,0.474],[0.882,0.470],[0.892,0.502],[0.888,0.538],[0.870,0.538],[0.862,0.504]],
    ],
  },
  {
    id: 'AUSTRALIA',
    titleIt: 'AUSTRALIA',
    titleEn: 'AUSTRALIA',
    titleEs: 'AUSTRALIA',
    subIt: 'Australia e Nuova Zelanda',
    subEn: 'Australia & New Zealand',
    subEs: 'Australia y Nueva Zelanda',
    codes: ['AU','NZ'],
    anchor: [0.838, 0.735],
    neighbors: { left: 'SOUTH_AFRICA', right: 'OCEANIA', up: 'ASIA' },
    polygons: [
      [[0.766,0.715],[0.792,0.678],[0.822,0.654],[0.846,0.668],[0.866,0.642],[0.890,0.676],[0.904,0.728],[0.890,0.786],[0.862,0.822],[0.832,0.796],[0.798,0.794],[0.768,0.802],[0.760,0.758]],
      [[0.928,0.802],[0.946,0.774],[0.962,0.788],[0.954,0.836],[0.934,0.876],[0.916,0.868],[0.924,0.834]],
    ],
  },
  {
    id: 'OCEANIA',
    titleIt: 'OCEANIA',
    titleEn: 'OCEANIA',
    titleEs: 'OCEANÍA',
    subIt: 'Pacifico, Melanesia e Polinesia',
    subEn: 'Pacific Islands, Melanesia & Polynesia',
    subEs: 'Islas del Pacífico, Melanesia y Polinesia',
    codes: ['AS','CK','FJ','FM','GU','KI','MH','MP','NC','NF','NR','NU','PF','PG','PN','PW','SB','TK','TO','TV','VU','WF','WS'],
    anchor: [0.925, 0.612],
    neighbors: { left: 'AUSTRALIA', up: 'ASIA' },
    polygons: [
      [[0.864,0.592],[0.892,0.596],[0.918,0.616],[0.916,0.642],[0.892,0.640],[0.864,0.620]],
      [[0.926,0.562],[0.948,0.556],[0.964,0.576],[0.958,0.606],[0.934,0.608],[0.922,0.584]],
      [[0.950,0.632],[0.972,0.628],[0.988,0.650],[0.982,0.680],[0.958,0.684],[0.946,0.658]],
    ],
  },
];

const COUNTRY_TO_REGION = (() => {
  const map = new Map();
  MIX_REGIONS.forEach((r) => {
    if (r.id !== 'ALL_WORLD') {
      r.codes.forEach((c) => map.set(c, r.id));
    }
  });
  return map;
})();

function regionTitle(region) {
  return t(region.titleIt, region.titleEn, region.titleEs);
}

function regionSub(region) {
  return t(region.subIt, region.subEn, region.subEs);
}

function resolveMixRegion(rawCode) {
  const clean = String(rawCode || '').trim().toUpperCase();
  const mapped = clean === 'UK' ? 'GB' : clean;
  return COUNTRY_TO_REGION.get(mapped) || null;
}

const loadedVendorScripts = new Map();
function ensureVendorScript(src) {
  if (loadedVendorScripts.has(src)) return loadedVendorScripts.get(src);
  const p = new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = src;
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => {
      loadedVendorScripts.delete(src);
      reject(new Error(`Failed to load ${src}`));
    };
    document.head.appendChild(s);
  });
  loadedVendorScripts.set(src, p);
  return p;
}

/* ---------------- Mix Language (37) & Genre (29) Filters ---------------- */
const MIX_LANGUAGES = [
  { code: 'sqi', title: { it: 'Albanese', en: 'Albanian', es: 'Albanés' }, aliases: ['sqi', 'sq', 'alb', 'albanese', 'albanian', 'shqip'], defaultCountries: ['AL', 'XK'] },
  { code: 'ara', title: { it: 'Arabo', en: 'Arabic', es: 'Árabe' }, aliases: ['ara', 'ar', 'arabo', 'arabic', 'árabe'], defaultCountries: ['AE', 'BH', 'DZ', 'EG', 'IQ', 'JO', 'KW', 'LB', 'LY', 'MA', 'OM', 'PS', 'QA', 'SA', 'SD', 'SY', 'TN', 'YE', 'MR'] },
  { code: 'bul', title: { it: 'Bulgaro', en: 'Bulgarian', es: 'Búlgaro' }, aliases: ['bul', 'bg', 'bulgaro', 'bulgarian', 'búlgaro'], defaultCountries: ['BG'] },
  { code: 'ces', title: { it: 'Ceco', en: 'Czech', es: 'Checo' }, aliases: ['ces', 'cze', 'cs', 'ceco', 'czech', 'checo'], defaultCountries: ['CZ'] },
  { code: 'zho', title: { it: 'Cinese', en: 'Chinese', es: 'Chino' }, aliases: ['zho', 'chi', 'zh', 'cmn', 'yue', 'cinese', 'chinese', 'chino'], defaultCountries: ['CN', 'TW', 'HK', 'MO'] },
  { code: 'kor', title: { it: 'Coreano', en: 'Korean', es: 'Coreano' }, aliases: ['kor', 'ko', 'coreano', 'korean'], defaultCountries: ['KR', 'KP'] },
  { code: 'hrv', title: { it: 'Croato', en: 'Croatian', es: 'Croata' }, aliases: ['hrv', 'hr', 'scr', 'croato', 'croatian', 'croata'], defaultCountries: ['HR'] },
  { code: 'dan', title: { it: 'Danese', en: 'Danish', es: 'Danés' }, aliases: ['dan', 'da', 'danese', 'danish', 'danés'], defaultCountries: ['DK', 'FO', 'GL'] },
  { code: 'heb', title: { it: 'Ebraico', en: 'Hebrew', es: 'Hebreo' }, aliases: ['heb', 'he', 'iw', 'ebraico', 'hebrew', 'hebreo'], defaultCountries: ['IL'] },
  { code: 'tgl', title: { it: 'Filippino / Tagalog', en: 'Filipino / Tagalog', es: 'Filipino / Tagalo' }, aliases: ['tgl', 'tl', 'fil', 'tagalog', 'filipino', 'filippino'], defaultCountries: ['PH'] },
  { code: 'fin', title: { it: 'Finlandese', en: 'Finnish', es: 'Finés' }, aliases: ['fin', 'fi', 'finlandese', 'finnish', 'finés'], defaultCountries: ['FI'] },
  { code: 'fra', title: { it: 'Francese', en: 'French', es: 'Francés' }, aliases: ['fra', 'fre', 'fr', 'francese', 'french', 'francés'], defaultCountries: ['FR', 'MC', 'BF', 'CI', 'CM', 'CD', 'CG', 'GA', 'GN', 'ML', 'NE', 'SN', 'TG', 'PF', 'NC', 'GF', 'GP', 'MQ', 'HT'] },
  { code: 'kat', title: { it: 'Georgiano', en: 'Georgian', es: 'Georgiano' }, aliases: ['kat', 'geo', 'ka', 'georgiano', 'georgian'], defaultCountries: ['GE'] },
  { code: 'jpn', title: { it: 'Giapponese', en: 'Japanese', es: 'Japonés' }, aliases: ['jpn', 'ja', 'giapponese', 'japanese', 'japonés'], defaultCountries: ['JP'] },
  { code: 'ell', title: { it: 'Greco', en: 'Greek', es: 'Griego' }, aliases: ['ell', 'gre', 'el', 'modern greek', 'modern greek (1453-)', 'greco', 'greek', 'griego'], defaultCountries: ['GR', 'CY'] },
  { code: 'hin', title: { it: 'Hindi', en: 'Hindi', es: 'Hindi' }, aliases: ['hin', 'hi', 'hindi'], defaultCountries: ['IN'] },
  { code: 'ind', title: { it: 'Indonesiano', en: 'Indonesian', es: 'Indonesio' }, aliases: ['ind', 'id', 'in', 'indonesiano', 'indonesian', 'indonesio'], defaultCountries: ['ID'] },
  { code: 'eng', title: { it: 'Inglese', en: 'English', es: 'Inglés' }, aliases: ['eng', 'en', 'inglese', 'english', 'inglés'], defaultCountries: ['US', 'GB', 'UK', 'CA', 'AU', 'NZ', 'IE', 'JM', 'BS', 'BB', 'TT', 'BZ', 'GY', 'NG', 'GH', 'KE', 'ZA', 'SG', 'PR'] },
  { code: 'ita', title: { it: 'Italiano', en: 'Italian', es: 'Italiano' }, aliases: ['ita', 'it', 'italiano', 'italian'], defaultCountries: ['IT', 'SM', 'VA'] },
  { code: 'nor', title: { it: 'Norvegese', en: 'Norwegian', es: 'Noruego' }, aliases: ['nor', 'no', 'nob', 'nno', 'norvegese', 'norwegian', 'noruego'], defaultCountries: ['NO'] },
  { code: 'nld', title: { it: 'Olandese', en: 'Dutch', es: 'Neerlandés' }, aliases: ['nld', 'dut', 'nl', 'olandese', 'dutch', 'neerlandés', 'flemish'], defaultCountries: ['NL', 'AW', 'CW', 'SR'] },
  { code: 'fas', title: { it: 'Persiano', en: 'Persian', es: 'Persa' }, aliases: ['fas', 'per', 'fa', 'persiano', 'persian', 'persa', 'farsi'], defaultCountries: ['IR', 'AF', 'TJ'] },
  { code: 'pol', title: { it: 'Polacco', en: 'Polish', es: 'Polaco' }, aliases: ['pol', 'pl', 'polacco', 'polish', 'polaco'], defaultCountries: ['PL'] },
  { code: 'por', title: { it: 'Portoghese', en: 'Portuguese', es: 'Portugués' }, aliases: ['por', 'pt', 'portoghese', 'portuguese', 'portugués'], defaultCountries: ['PT', 'BR', 'AO', 'MZ', 'CV', 'GW', 'TL'] },
  { code: 'ron', title: { it: 'Rumeno', en: 'Romanian', es: 'Rumano' }, aliases: ['ron', 'rum', 'ro', 'rumeno', 'romanian', 'rumano'], defaultCountries: ['RO', 'MD'] },
  { code: 'rus', title: { it: 'Russo', en: 'Russian', es: 'Ruso' }, aliases: ['rus', 'ru', 'russo', 'russian', 'ruso'], defaultCountries: ['RU', 'BY', 'KZ', 'KG'] },
  { code: 'srp', title: { it: 'Serbo', en: 'Serbian', es: 'Serbio' }, aliases: ['srp', 'sr', 'scc', 'serbo', 'serbian', 'serbio'], defaultCountries: ['RS', 'ME', 'BA'] },
  { code: 'slk', title: { it: 'Slovacco', en: 'Slovak', es: 'Eslovaco' }, aliases: ['slk', 'slo', 'sk', 'slovacco', 'slovak', 'eslovaco'], defaultCountries: ['SK'] },
  { code: 'spa', title: { it: 'Spagnolo', en: 'Spanish', es: 'Español' }, aliases: ['spa', 'es', 'spagnolo', 'spanish', 'español', 'castellano'], defaultCountries: ['ES', 'MX', 'AR', 'CO', 'CL', 'PE', 'VE', 'EC', 'GT', 'CU', 'BO', 'DO', 'HN', 'PY', 'SV', 'NI', 'CR', 'PA', 'UY', 'PR'] },
  { code: 'swe', title: { it: 'Svedese', en: 'Swedish', es: 'Sueco' }, aliases: ['swe', 'sv', 'svedese', 'swedish', 'sueco'], defaultCountries: ['SE'] },
  { code: 'deu', title: { it: 'Tedesco', en: 'German', es: 'Alemán' }, aliases: ['deu', 'ger', 'de', 'tedesco', 'german', 'alemán'], defaultCountries: ['DE', 'AT', 'CH', 'LI', 'LU'] },
  { code: 'tha', title: { it: 'Thailandese', en: 'Thai', es: 'Tailandés' }, aliases: ['tha', 'th', 'thai', 'thailandese', 'tailandés'], defaultCountries: ['TH'] },
  { code: 'tur', title: { it: 'Turco', en: 'Turkish', es: 'Turco' }, aliases: ['tur', 'tr', 'turco', 'turkish'], defaultCountries: ['TR'] },
  { code: 'ukr', title: { it: 'Ucraino', en: 'Ukrainian', es: 'Ucraniano' }, aliases: ['ukr', 'uk', 'ucraino', 'ukrainian', 'ucraniano'], defaultCountries: ['UA'] },
  { code: 'hun', title: { it: 'Ungherese', en: 'Hungarian', es: 'Húngaro' }, aliases: ['hun', 'hu', 'ungherese', 'hungarian', 'húngaro'], defaultCountries: ['HU'] },
  { code: 'urd', title: { it: 'Urdu', en: 'Urdu', es: 'Urdu' }, aliases: ['urd', 'ur', 'urdu'], defaultCountries: ['PK'] },
  { code: 'vie', title: { it: 'Vietnamita', en: 'Vietnamese', es: 'Vietnamita' }, aliases: ['vie', 'vi', 'vietnamita', 'vietnamese'], defaultCountries: ['VN'] },
];

const MIX_GENRES = [
  { id: 'action', title: { it: 'Azione', en: 'Action', es: 'Acción' }, categoryKeywords: ['action', 'azione', 'acción', 'accion', 'martial', 'combat'], nameKeywords: ['action', 'azione'] },
  { id: 'adventure', title: { it: 'Avventura', en: 'Adventure', es: 'Aventuras' }, categoryKeywords: ['adventure', 'avventura', 'aventuras', 'aventura'], nameKeywords: ['adventure', 'avventura'] },
  { id: 'animation', title: { it: 'Animazione', en: 'Animation', es: 'Animación' }, categoryKeywords: ['animation', 'animazione', 'animación', 'cartoon', 'cartoons', 'animacion'], nameKeywords: ['cartoon', 'animation'] },
  { id: 'anime', title: { it: 'Anime', en: 'Anime', es: 'Anime' }, categoryKeywords: ['anime', 'manga'], nameKeywords: ['anime', 'manga'] },
  { id: 'auto', title: { it: 'Auto & Motori', en: 'Auto & Motors', es: 'Motor' }, categoryKeywords: ['auto', 'automotive', 'motor', 'motori', 'motorsport', 'racing'], nameKeywords: ['motorsport', 'racing', 'motor'] },
  { id: 'kids', title: { it: 'Bambini', en: 'Kids', es: 'Infantil' }, categoryKeywords: ['kids', 'child', 'children', 'bambini', 'infantiles', 'infantil', 'junior', 'ragazzi', 'kinder'], nameKeywords: ['kids', 'junior', 'bambini', 'infantil', 'cartoons'] },
  { id: 'movies', title: { it: 'Cinema & Film', en: 'Movies & Film', es: 'Cine y Películas' }, categoryKeywords: ['movie', 'movies', 'cinema', 'cine', 'películas', 'peliculas', 'film', 'vod'], nameKeywords: ['cinema', 'movie', 'movies', 'film'] },
  { id: 'classic', title: { it: 'Classici & Retrò', en: 'Classic & Retro', es: 'Clásicos' }, categoryKeywords: ['classic', 'classics', 'classici', 'retro', 'clásicos', 'vintage'], nameKeywords: ['classic', 'retro', 'vintage'] },
  { id: 'comedy', title: { it: 'Commedia', en: 'Comedy', es: 'Comedia' }, categoryKeywords: ['comedy', 'commedia', 'comedia', 'humor', 'humour', 'satire'], nameKeywords: ['comedy', 'commedia', 'humor'] },
  { id: 'cooking', title: { it: 'Cucina & Food', en: 'Cooking & Food', es: 'Cocina y Gastronomía' }, categoryKeywords: ['cooking', 'cucina', 'cocina', 'food', 'culinary', 'gastronomia', 'recipes'], nameKeywords: ['cooking', 'food', 'cucina', 'cocina'] },
  { id: 'crime', title: { it: 'Crimine & Poliziesco', en: 'Crime', es: 'Crimen' }, categoryKeywords: ['crime', 'crimine', 'crimen', 'poliziesco', 'noir', 'detective', 'investigation'], nameKeywords: ['crime', 'noir', 'detective'] },
  { id: 'culture', title: { it: 'Cultura & Arte', en: 'Culture & Arts', es: 'Cultura y Arte' }, categoryKeywords: ['culture', 'cultura', 'arts', 'art', 'arte', 'theatre', 'teatro'], nameKeywords: ['culture', 'cultura', 'arts'] },
  { id: 'documentary', title: { it: 'Documentari', en: 'Documentary', es: 'Documentales' }, categoryKeywords: ['documentary', 'documentaries', 'documentari', 'documentales', 'doc', 'docu', 'wildlife'], nameKeywords: ['documentary', 'docu', 'wildlife'] },
  { id: 'drama', title: { it: 'Dramma', en: 'Drama', es: 'Drama' }, categoryKeywords: ['drama', 'dramma', 'drammatico', 'drame', 'melodrama'], nameKeywords: ['drama', 'dramma'] },
  { id: 'business', title: { it: 'Economia & Finanza', en: 'Business & Finance', es: 'Economía y Finanzas' }, categoryKeywords: ['business', 'economia', 'finance', 'finanza', 'financial', 'economy', 'markets'], nameKeywords: ['finance', 'business', 'economia'] },
  { id: 'education', title: { it: 'Educazione', en: 'Education', es: 'Educación' }, categoryKeywords: ['education', 'educazione', 'educación', 'educational', 'learning', 'scuola', 'school'], nameKeywords: ['education', 'learning'] },
  { id: 'family', title: { it: 'Famiglia', en: 'Family', es: 'Familia' }, categoryKeywords: ['family', 'famiglia', 'familia'], nameKeywords: ['family', 'famiglia'] },
  { id: 'fantasy', title: { it: 'Fantasy', en: 'Fantasy', es: 'Fantasía' }, categoryKeywords: ['fantasy', 'fantasia', 'fantasía', 'mythology', 'magic'], nameKeywords: ['fantasy'] },
  { id: 'scifi', title: { it: 'Fantascienza', en: 'Sci-Fi', es: 'Ciencia Ficción' }, categoryKeywords: ['sci-fi', 'scifi', 'fantascienza', 'science-fiction', 'cyberpunk', 'space'], nameKeywords: ['sci-fi', 'scifi', 'fantascienza'] },
  { id: 'general', title: { it: 'Generali', en: 'General', es: 'General' }, categoryKeywords: ['general', 'generali', 'generale', 'undefined', 'eventuales', 'variedades', 'variety'], nameKeywords: [] },
  { id: 'war', title: { it: 'Guerra', en: 'War', es: 'Bélico' }, categoryKeywords: ['war', 'guerra', 'belico', 'bélico', 'military'], nameKeywords: ['war', 'guerra'] },
  { id: 'horror', title: { it: 'Horror', en: 'Horror', es: 'Terror' }, categoryKeywords: ['horror', 'orrore', 'terror', 'thriller', 'paranormal', 'zombie'], nameKeywords: ['horror', 'terror'] },
  { id: 'entertainment', title: { it: 'Intrattenimento', en: 'Entertainment', es: 'Entretenimiento' }, categoryKeywords: ['entertainment', 'intrattenimento', 'entretenimiento', 'show', 'reality', 'variety'], nameKeywords: ['entertainment', 'reality'] },
  { id: 'legislative', title: { it: 'Istituzionale', en: 'Legislative & Civic', es: 'Institucional' }, categoryKeywords: ['legislative', 'parliament', 'government', 'istituzionale', 'civic', 'public', 'senato', 'senate'], nameKeywords: ['parliament', 'civic'] },
  { id: 'lifestyle', title: { it: 'Lifestyle', en: 'Lifestyle', es: 'Estilo de vida' }, categoryKeywords: ['lifestyle', 'home', 'living', 'wellness', 'design'], nameKeywords: ['lifestyle', 'living'] },
  { id: 'local', title: { it: 'Locali & Regionali', en: 'Local & Regional', es: 'Locales y Regionales' }, categoryKeywords: ['local', 'locali', 'regional', 'regionali', 'autonómicas', 'civic'], nameKeywords: [] },
  { id: 'weather', title: { it: 'Meteo', en: 'Weather', es: 'Clima y Tiempo' }, categoryKeywords: ['weather', 'meteo', 'tiempo', 'clima', 'forecast'], nameKeywords: ['weather', 'meteo'] },
  { id: 'mystery', title: { it: 'Mistero', en: 'Mystery', es: 'Misterio' }, categoryKeywords: ['mystery', 'mistero', 'misterio', 'giallo'], nameKeywords: ['mystery', 'mistero'] },
  { id: 'fashion', title: { it: 'Moda', en: 'Fashion', es: 'Moda' }, categoryKeywords: ['fashion', 'moda', 'style'], nameKeywords: ['fashion', 'moda'] },
  { id: 'music', title: { it: 'Musica', en: 'Music', es: 'Música' }, categoryKeywords: ['music', 'musica', 'música', 'musicales', 'musical', 'concert', 'hits', 'opera'], nameKeywords: ['music', 'musica', 'hits', 'concert'] },
  { id: 'outdoor', title: { it: 'Natura & Outdoor', en: 'Nature & Outdoor', es: 'Naturaleza' }, categoryKeywords: ['outdoor', 'nature', 'natura', 'wildlife', 'fishing', 'hunting'], nameKeywords: ['nature', 'wildlife', 'outdoor'] },
  { id: 'news', title: { it: 'News & Informazione', en: 'News & Information', es: 'Noticias e Informativos' }, categoryKeywords: ['news', 'informativos', 'informazione', 'noticias', 'notizie', 'aktuell', 'journal', 'info'], nameKeywords: ['news', 'notizie', 'noticias'] },
  { id: 'religious', title: { it: 'Religione', en: 'Religious', es: 'Religión' }, categoryKeywords: ['religious', 'religione', 'religión', 'religiosos', 'christian', 'islamic', 'catholic', 'gospel', 'faith'], nameKeywords: ['religious', 'gospel', 'faith'] },
  { id: 'romance', title: { it: 'Romantico', en: 'Romance', es: 'Romance' }, categoryKeywords: ['romance', 'romantico', 'romántico', 'love', 'amore', 'sentimental'], nameKeywords: ['romance'] },
  { id: 'science', title: { it: 'Scienza & Tech', en: 'Science & Tech', es: 'Ciencia y Tecnología' }, categoryKeywords: ['science', 'scienza', 'ciencia', 'tech', 'technology', 'space'], nameKeywords: ['science', 'tech'] },
  { id: 'series', title: { it: 'Serie TV', en: 'TV Series', es: 'Series TV' }, categoryKeywords: ['series', 'serie', 'novelas', 'telenovelas', 'doramas', 'sitcom'], nameKeywords: ['series', 'telenovelas'] },
  { id: 'shop', title: { it: 'Shopping', en: 'Shopping', es: 'Compras' }, categoryKeywords: ['shop', 'shopping', 'teleshop', 'commerce', 'deals'], nameKeywords: ['shopping', 'teleshop'] },
  { id: 'sports', title: { it: 'Sport', en: 'Sports', es: 'Deportes' }, categoryKeywords: ['sport', 'sports', 'deporte', 'deportes', 'deportivos', 'football', 'soccer', 'calcio', 'tennis', 'basket', 'boxing'], nameKeywords: ['sport', 'sports', 'football', 'soccer', 'calcio'] },
  { id: 'history', title: { it: 'Storico', en: 'History', es: 'Historia' }, categoryKeywords: ['history', 'storico', 'storia', 'historia', 'historical', 'biography'], nameKeywords: ['history', 'storia'] },
  { id: 'thriller', title: { it: 'Thriller', en: 'Thriller', es: 'Suspense' }, categoryKeywords: ['thriller', 'suspense', 'psychological'], nameKeywords: ['thriller', 'suspense'] },
  { id: 'travel', title: { it: 'Viaggi', en: 'Travel', es: 'Viajes' }, categoryKeywords: ['travel', 'viaggi', 'viajes', 'tourism', 'turismo'], nameKeywords: ['travel', 'tourism'] },
  { id: 'western', title: { it: 'Western', en: 'Western', es: 'Oeste' }, categoryKeywords: ['western', 'oeste', 'cowboy'], nameKeywords: ['western'] },
];

const MIX_LANG_BY_CODE = new Map(MIX_LANGUAGES.map((l) => [l.code, l]));
const MIX_GENRE_BY_ID = new Map(MIX_GENRES.map((g) => [g.id, g]));

function mixFilterLabel(opt) {
  const l = getLang();
  return opt.title[l] || opt.title.it;
}

function sortedMixLanguages() {
  const l = getLang();
  const collator = new Intl.Collator(l === 'en' ? 'en' : l === 'es' ? 'es' : 'it', { sensitivity: 'base' });
  return [...MIX_LANGUAGES].sort((a, b) => collator.compare(mixFilterLabel(a), mixFilterLabel(b)));
}

function sortedMixGenres() {
  const l = getLang();
  const collator = new Intl.Collator(l === 'en' ? 'en' : l === 'es' ? 'es' : 'it', { sensitivity: 'base' });
  return [...MIX_GENRES].sort((a, b) => collator.compare(mixFilterLabel(a), mixFilterLabel(b)));
}

function hasActiveMixFilters() {
  return state.selectedMixLanguages.size > 0 || state.selectedMixGenres.size > 0;
}

function isLangDelimiter(ch) {
  return ch === ' ' || ch === '(' || ch === ')' || ch === '/' || ch === '-' || ch === '_' || ch === ',' || ch === ';';
}

function matchesLangToken(tok, aliases) {
  if (aliases.includes(tok)) return true;
  return aliases.some((al) => {
    if (al.length < 4) return false;
    let from = 0;
    while (true) {
      const idx = tok.indexOf(al, from);
      if (idx < 0) return false;
      const startOk = idx === 0 || isLangDelimiter(tok[idx - 1]);
      const endIdx = idx + al.length;
      const endOk = endIdx === tok.length || isLangDelimiter(tok[endIdx]);
      if (startOk && endOk) return true;
      from = idx + 1;
    }
  });
}

function matchesMixLanguages(ch) {
  if (!state.selectedMixLanguages.size) return true;
  const selected = [...state.selectedMixLanguages].map((c) => MIX_LANG_BY_CODE.get(c)).filter(Boolean);
  if (!selected.length) return true;

  const rawLangs = [
    ...(ch.languages || []),
    ...(ch.languageNames || []),
    ...(ch.language ? [ch.language] : []),
  ];
  const tokens = rawLangs
    .flatMap((v) => String(v || '').toLowerCase().split(/[;,/|]+/))
    .map((s) => s.trim())
    .filter(Boolean);

  if (tokens.length) {
    return selected.some((opt) => tokens.some((tok) => matchesLangToken(tok, opt.aliases)));
  }

  const rawCountry = String(ch.country || '').trim().toUpperCase();
  const normCountry = rawCountry === 'UK' ? 'GB' : rawCountry;
  if (normCountry.length !== 2) return false;
  return selected.some((opt) => opt.defaultCountries.includes(normCountry));
}

function matchesMixGenres(ch) {
  if (!state.selectedMixGenres.size) return true;
  const selected = [...state.selectedMixGenres].map((id) => MIX_GENRE_BY_ID.get(id)).filter(Boolean);
  if (!selected.length) return true;

  const rawCats = [
    ...(ch.categories || []),
    ...(ch.categoryNames || []),
    ...(ch.category ? [ch.category] : []),
  ];
  const catTokens = rawCats
    .flatMap((v) => String(v || '').toLowerCase().split(/[;,/|]+/))
    .map((s) => s.trim())
    .filter(Boolean);
  const nameLower = ` ${String(ch.name || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()} `;

  return selected.some(
    (g) =>
      catTokens.some((tok) => g.categoryKeywords.includes(tok) || g.categoryKeywords.some((kw) => kw.length >= 4 && tok.includes(kw))) ||
      g.nameKeywords.some((kw) => nameLower.includes(` ${kw} `)),
  );
}

/* ---------------- Local Browser M3U Parser (5-Stage Country Inference) ---------------- */
const MEDIA_TAG_RE = /^(SD|HD|FHD|UHD|4K|8K|TV|FM|AM|RADIO|LIVE|1080P|720P|480P)$/;
const COUNTRY_NAMES_TO_ISO = new Map([
  ['ITALY','IT'],['ITALIA','IT'],['ITALIAN','IT'],
  ['SPAIN','ES'],['ESPANA','ES'],['SPANISH','ES'],
  ['FRANCE','FR'],['FRENCH','FR'],['GERMANY','DE'],['GERMAN','DE'],
  ['UK','GB'],['UNITED KINGDOM','GB'],['USA','US'],['UNITED STATES','US'],
  ['CANADA','CA'],['MEXICO','MX'],['BRAZIL','BR'],['ARGENTINA','AR'],
  ['ECUADOR','EC'],['COLOMBIA','CO'],['CHILE','CL'],['PERU','PE'],
  ['PORTUGAL','PT'],['SWITZERLAND','CH'],['JAPAN','JP'],['KOREA','KR'],
  ['CHINA','CN'],['INDIA','IN'],['AUSTRALIA','AU'],['TURKEY','TR'],
]);

function inferCountryFromAttrs(tvgCountry, tvgId, groupTitle, name, streamUrl) {
  const c1 = (tvgCountry || '').trim().split(/[,;]/)[0].trim().toUpperCase();
  if (/^[A-Z]{2}$/.test(c1)) return c1 === 'UK' ? 'GB' : c1;
  if (tvgId) {
    const at = tvgId.indexOf('@');
    const head = at >= 0 ? tvgId.slice(0, at) : tvgId;
    const dot = head.lastIndexOf('.');
    if (dot > 0) {
      const suf = head.slice(dot + 1).toUpperCase();
      if (/^[A-Z]{2}$/.test(suf)) return suf === 'UK' ? 'GB' : suf;
    }
  }
  const checkParen = (txt) => {
    const matches = txt.match(/\(([A-Za-z\s]{2,20})\)/g) || [];
    for (let i = matches.length - 1; i >= 0; i--) {
      const inner = matches[i].slice(1, -1).trim().toUpperCase();
      if (MEDIA_TAG_RE.test(inner)) continue;
      if (/^[A-Z]{2}$/.test(inner)) return inner === 'UK' ? 'GB' : inner;
      if (COUNTRY_NAMES_TO_ISO.has(inner)) return COUNTRY_NAMES_TO_ISO.get(inner);
    }
    return '';
  };
  if (groupTitle) {
    const g = checkParen(groupTitle);
    if (g) return g;
  }
  if (name) {
    const n = checkParen(name);
    if (n) return n;
  }
  try {
    const host = new URL(streamUrl).hostname.toLowerCase();
    const parts = host.split('.');
    const tld = parts[parts.length - 1]?.toUpperCase() || '';
    if (/^[A-Z]{2}$/.test(tld) && !['TV','IO','CO','ME','FM','AM','NU','CC','WS','TO','AI'].includes(tld)) {
      return tld === 'UK' ? 'GB' : tld;
    }
  } catch (_) {}
  return '';
}

function parseM3uText(text) {
  let normalized = String(text || '').replace(/^\uFEFF/, '');
  const head = normalized.slice(0, 16384);
  if (/#EXT-X-TARGETDURATION|#EXT-X-MEDIA-SEQUENCE|#EXT-X-STREAM-INF/i.test(head)) {
    return [];
  }
  const plsHead = normalized.trimStart().slice(0, 1024);
  if (/^\[playlist\]/i.test(plsHead) || (plsHead.includes('NumberOfEntries=') && /File1=/i.test(plsHead))) {
    const files = new Map();
    const titles = new Map();
    for (const rawLine of normalized.split(/\r?\n/)) {
      const l = rawLine.trim();
      const fm = /^File(\d+)\s*=\s*(.+)$/i.exec(l);
      if (fm) { files.set(Number(fm[1]), fm[2].trim()); continue; }
      const tm = /^Title(\d+)\s*=\s*(.+)$/i.exec(l);
      if (tm) titles.set(Number(tm[1]), tm[2].trim());
    }
    if (!files.size) return [];
    const plsLines = ['#EXTM3U'];
    for (const [idx, u] of [...files.entries()].sort((a, b) => a[0] - b[0])) {
      const label = (titles.get(idx) || u.split('/').pop()?.split('?')[0] || `Canale ${idx}`).replace(/,/g, ' ');
      plsLines.push(`#EXTINF:-1,${label}`, u);
    }
    normalized = plsLines.join('\n');
  }

  const lines = normalized.split(/\r?\n/);
  const result = [];
  let pending = null;
  let pendingExtgrp = '';
  let num = 1;
  const extractComma = (s) => {
    let inQuotes = false;
    let commaIdx = -1;
    for (let i = 0; i < s.length; i++) {
      if (s[i] === '"') inQuotes = !inQuotes;
      else if (s[i] === ',' && !inQuotes) commaIdx = i;
    }
    return commaIdx;
  };
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith('#EXTINF:')) {
      const comma = extractComma(line);
      const meta = comma >= 0 ? line.slice(8, comma) : line.slice(8);
      const name = (comma >= 0 ? line.slice(comma + 1) : `Canale ${num}`).trim() || `Canale ${num}`;
      const getAttr = (k) => {
        const m = meta.match(new RegExp(`${k}\\s*=\\s*"([^"]*)"`, 'i'));
        return m ? m[1] : '';
      };
      pending = {
        name,
        logo: getAttr('tvg-logo'),
        tvgCountry: getAttr('tvg-country'),
        tvgId: getAttr('tvg-id'),
        groupTitle: getAttr('group-title'),
        language: getAttr('tvg-language'),
        chno: Number.parseInt(getAttr('tvg-chno'), 10) || num,
      };
    } else if (/^#EXTGRP:/i.test(line)) {
      pendingExtgrp = line.slice(8).trim();
    } else if (!line.startsWith('#') && /^https?:\/\//i.test(line)) {
      const info = pending || { name: `Canale ${num}`, logo: '', chno: num };
      const grp = info.groupTitle || pendingExtgrp || '';
      pending = null;
      pendingExtgrp = '';
      const country = inferCountryFromAttrs(info.tvgCountry, info.tvgId, grp, info.name, line);
      const languages = String(info.language || '')
        .toLowerCase()
        .split(/[;,/|]+/)
        .map((s) => s.trim())
        .filter(Boolean);
      result.push({
        id: `local-${num}-${info.name.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 12)}`,
        number: info.chno || num,
        name: info.name,
        country,
        countryName: country || '',
        flag: country ? countryFlag(country) : '',
        categoryNames: [grp || 'Generali'],
        languages,
        quality: /\b(4k|uhd|2160p?)\b/i.test(info.name)
          ? '4K'
          : /\b(fhd|1080[pi]?)\b/i.test(info.name)
            ? '1080p'
            : /\b(hd|720p?)\b/i.test(info.name)
              ? '720p'
              : 'HD',
        logo: info.logo || null,
        favorite: false,
        status: 'online',
        streamUrl: line,
      });
      num++;
    }
  }
  return result;
}

/* ---------------- Topbar & Bottom Nav Sync ---------------- */
function updateShellChrome() {
  // Localization
  $('#nav-tab-channels').textContent = t('Canali', 'Channels', 'Canales');
  $('#nav-tab-favorites').textContent = t('Preferiti', 'Favorites', 'Favoritos');
  $('#bottom-nav-channels-label').textContent = t('Canali', 'Channels', 'Canales');
  $('#bottom-nav-favorites-label').textContent = t('Preferiti', 'Favorites', 'Favoritos');
  $('#bottom-nav-settings-label').textContent = t('Impostazioni', 'Settings', 'Ajustes');

  const searchInput = $('#global-search-input');
  if (searchInput) {
    searchInput.placeholder = t('Cerca canali…', 'Search channels…', 'Buscar canales…');
  }

  // Nav highlights
  const activeNav = state.searchQuery ? 'channels' : state.page;
  $$('.subtab-btn').forEach((btn) => {
    const isAct = btn.dataset.nav === activeNav;
    btn.classList.toggle('is-active', isAct);
    btn.setAttribute('aria-selected', String(isAct));
  });
  $$('.bottom-nav-item[data-nav]').forEach((btn) => {
    btn.classList.toggle('is-active', btn.dataset.nav === activeNav);
  });
  $('#topbar-settings-btn')?.classList.toggle('is-active', state.page === 'settings');

  // Country button (only when not searching & on channels tab, or visible on mobile/tablet)
  const countryBtn = $('#topbar-country-btn');
  if (countryBtn) {
    countryBtn.hidden = Boolean(state.searchQuery) || state.page !== 'channels';
    const countries = state.config?.facets?.countries || [];
    const matched = countries.find((c) => c.code === state.tvCountry);
    $('#topbar-country-flag').textContent = matched?.flag || countryFlag(state.tvCountry);
    $('#topbar-country-name').textContent = matched?.name || state.tvCountry;
  }

  // Profile button
  const p = state.activeProfile;
  $('#topbar-profile-avatar').textContent = p?.avatar || '👤';

  // Status pill
  const dot = $('#topbar-status-dot');
  const text = $('#topbar-status-text');
  if (dot && text) {
    dot.classList.toggle('on', !state.serverDisconnected);
    dot.classList.toggle('warn', state.serverDisconnected);
    if (state.serverDisconnected) {
      text.textContent = t('Offline', 'Offline', 'Offline');
    } else if (state.searchQuery) {
      text.textContent = state.searchLoading
        ? t('Cerco…', 'Searching…', 'Buscando…')
        : `${state.searchResults.length} ${t('risultati', 'results', 'resultados')}`;
    } else if (state.page === 'favorites') {
      text.textContent = `${state.favorites.length} ${t('Preferiti', 'Favorites', 'Favoritos')}`;
    } else {
      const count = state.localOverrideChannels ? state.localOverrideChannels.length : state.channelsTotal;
      text.textContent = `${fmtNum(count)} ${count === 1 ? t('canale', 'channel', 'canal') : t('canali', 'channels', 'canales')}`;
    }
  }

  // Update badge
  const updBtn = $('#topbar-update-badge');
  if (updBtn) {
    if (state.updateInfo?.updateAvailable) {
      updBtn.hidden = false;
      $('#topbar-update-text').textContent = `⬆ v${state.updateInfo.latest}`;
    } else {
      updBtn.hidden = true;
    }
  }
}

/* ---------------- Data Fetching ---------------- */
async function loadServerConfig() {
  const cfg = await api('/api/config');
  state.config = cfg;
  state.serverDisconnected = false;
  return cfg;
}

async function loadProfiles() {
  const data = await api('/api/profiles');
  state.profiles = sortProfiles(data.profiles || []);
  const savedId = localStorage.getItem('casatv_profile_id');
  const chosen = state.profiles.find((p) => p.id === savedId) || state.profiles[0] || null;
  state.activeProfile = chosen;
  if (chosen) {
    localStorage.setItem('casatv_profile_id', chosen.id);
    if (!localStorage.getItem('casatv_tv_country') && chosen.country) {
      state.tvCountry = chosen.country;
    }
  }
}

function sortProfiles(list) {
  return [...list].sort((a, b) => {
    const aGuest = a.id === 'guest' || a.isGuest;
    const bGuest = b.id === 'guest' || b.isGuest;
    if (aGuest !== bGuest) return aGuest ? 1 : -1;
    return String(a.name || '').localeCompare(String(b.name || ''));
  });
}

function currentCountryTitle() {
  const countries = state.config?.facets?.countries || [];
  const countryObj = countries.find((c) => c.code === state.tvCountry);
  return countryObj?.name || state.tvCountry;
}

function channelsGridListKey() {
  const src = state.localOverrideChannels ? `local:${state.localSourceLabel}` : state.tvCountry;
  const fb = state.channelsFallback ? 'fb' : 'ok';
  return `channels:${src}:${fb}:${state.deviceType}`;
}

function disconnectChannelsObserver() {
  if (state.channelsObserver) {
    state.channelsObserver.disconnect();
    state.channelsObserver = null;
  }
}

function setupChannelsInfiniteScroll() {
  disconnectChannelsObserver();
  if (state.page !== 'channels' || state.searchQuery) return;
  if (state.channels.length >= state.channelsTotal) return;
  const sentinel = $('#channels-load-sentinel');
  if (!sentinel || typeof IntersectionObserver === 'undefined') return;
  const obs = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        obs.disconnect();
        if (state.channelsObserver === obs) state.channelsObserver = null;
        if (!state.channelsLoading && state.channels.length < state.channelsTotal) {
          loadChannels({ reset: false });
        }
      }
    },
    { rootMargin: '500px 0px' },
  );
  obs.observe(sentinel);
  state.channelsObserver = obs;
}

function updateChannelsFooterDom() {
  const root = $('#app');
  const footer = root ? $('.grid-footer', root) : null;
  if (!footer) return false;
  const hasMore = state.channels.length < state.channelsTotal;
  const countryTitle = currentCountryTitle();
  footer.innerHTML = `
    ${hasMore ? `<div class="channels-load-sentinel" id="channels-load-sentinel" aria-hidden="true"></div><button type="button" class="casa-btn ghost" id="btn-load-more-ch">${t('Carica altri canali', 'Load more channels')} (${fmtNum(state.channels.length)} / ${fmtNum(state.channelsTotal)})</button>` : ''}
    <p id="channels-footer-summary">${t('Diretta TV', 'Live TV')} · ${esc(countryTitle)} · ${fmtNum(state.channels.length)} ${t('canali visualizzati', 'channels loaded')}</p>
  `;
  $('#btn-load-more-ch')?.addEventListener('click', () => loadChannels({ reset: false }));
  return true;
}

function appendChannelCardsToDom(newItems, startIdx, { showFlag = false } = {}) {
  if (state.page !== 'channels' || state.searchQuery) return false;
  const root = $('#app');
  if (!root) return false;
  const isMobile = state.deviceType === 'smartphone';
  const gridSel = isMobile ? '.mobile-channels-list' : '.channels-grid';
  const gridEl = $(gridSel, root);
  if (!gridEl || gridEl.dataset.listKey !== channelsGridListKey()) return false;
  if (newItems.length > 0) {
    const html = newItems
      .map((ch, i) => renderChannelItemHtml(ch, startIdx + i, { showFlag, isMobile, eager: false }))
      .join('');
    gridEl.insertAdjacentHTML('beforeend', html);
  }
  gridEl._channelList = state.channels;
  updateChannelsFooterDom();
  setupChannelsInfiniteScroll();
  return true;
}

function syncChannelFavoriteButtons(channelId, isFav) {
  const targetId = String(channelId || '');
  if (!targetId) return;
  for (const card of $$('#app [data-id]')) {
    if (card.dataset.id === targetId) {
      const btn = $('[data-fav-idx]', card);
      if (btn) {
        btn.classList.toggle('is-fav', isFav);
        btn.textContent = isFav ? '★' : '☆';
      }
    }
  }
}

async function loadChannels({ reset = true } = {}) {
  if (!reset && state.channelsLoading) return;

  if (state.localOverrideChannels) {
    const all = state.localOverrideChannels;
    let filtered = state.tvCountry
      ? all.filter((c) => c.country?.toUpperCase() === state.tvCountry.toUpperCase())
      : all;
    let fallback = false;
    if (!filtered.length) {
      filtered = all;
      fallback = all.length > 0;
    }
    const prevLen = reset ? 0 : state.channels.length;
    state.localVisibleLimit = reset ? CHANNEL_PAGE_SIZE : prevLen + CHANNEL_PAGE_SIZE;
    state.channelsFallback = fallback;
    state.channelsTotal = filtered.length;
    const nextVisible = filtered.slice(0, state.localVisibleLimit);
    const appendedItems = reset ? nextVisible : nextVisible.slice(prevLen);
    state.channels = nextVisible;
    updateShellChrome();
    if (state.page === 'channels' && !state.searchQuery) {
      if (reset || !appendChannelCardsToDom(appendedItems, prevLen, { showFlag: true })) {
        renderCurrentScreen();
      }
    }
    return;
  }

  const seq = reset ? ++state.channelsRequestSeq : state.channelsRequestSeq;
  const prevLen = reset ? 0 : state.channels.length;
  state.channelsLoading = true;
  if (reset) {
    state.channels = [];
    state.channelsFallback = false;
  } else {
    const btn = $('#btn-load-more-ch');
    if (btn) {
      btn.disabled = true;
      btn.textContent = t('Caricamento…', 'Loading…');
    }
  }
  updateShellChrome();
  if (state.page === 'channels' && reset && !state.searchQuery) renderCurrentScreen();

  let items = [];
  let loadFailed = false;
  try {
    const offset = reset ? 0 : state.channels.length;
    const qs = new URLSearchParams({
      limit: String(CHANNEL_PAGE_SIZE),
      offset: String(offset),
      sort: 'number',
    });
    if (state.tvCountry && (reset || !state.channelsFallback)) qs.set('country', state.tvCountry);
    let data = await api(`/api/channels?${qs.toString()}`);
    if (seq !== state.channelsRequestSeq) return;
    items = data.items || [];
    let fallback = false;
    if (reset && items.length === 0 && state.tvCountry) {
      data = await api(`/api/channels?limit=${CHANNEL_PAGE_SIZE}&offset=0&sort=number`);
      if (seq !== state.channelsRequestSeq) return;
      items = data.items || [];
      fallback = items.length > 0;
    }
    state.channels = reset ? items : state.channels.concat(items);
    state.channelsTotal = Number(data.total ?? state.channels.length);
    if (reset) state.channelsFallback = fallback;
    state.serverDisconnected = false;
  } catch (err) {
    if (seq !== state.channelsRequestSeq) return;
    loadFailed = true;
    state.serverDisconnected = true;
    toast(`${t('Errore caricamento canali', 'Channel load error')}: ${err.message}`, 'err');
  } finally {
    if (seq === state.channelsRequestSeq) {
      state.channelsLoading = false;
      updateShellChrome();
      if (state.page === 'channels' && !state.searchQuery) {
        if (reset) {
          renderCurrentScreen();
        } else if (loadFailed) {
          const btn = $('#btn-load-more-ch');
          if (btn) {
            btn.disabled = false;
            btn.textContent = `${t('Carica altri canali', 'Load more channels')} (${fmtNum(state.channels.length)} / ${fmtNum(state.channelsTotal)})`;
          }
        } else if (!appendChannelCardsToDom(items, prevLen, { showFlag: state.channelsFallback })) {
          renderCurrentScreen();
        }
      }
    }
  }
}

async function loadFavorites() {
  state.favoritesLoading = true;
  if (state.page === 'favorites' && !state.searchQuery) renderCurrentScreen();
  try {
    if (state.localOverrideChannels) {
      state.favorites = state.localOverrideChannels.filter((c) => c.favorite);
    } else {
      const data = await api('/api/channels?favorite=1&limit=1000&sort=number');
      state.favorites = data.items || [];
    }
  } catch (err) {
    toast(`${t('Errore caricamento preferiti', 'Favorites error')}: ${err.message}`, 'err');
  } finally {
    state.favoritesLoading = false;
    updateShellChrome();
    if (state.page === 'favorites' && !state.searchQuery) renderCurrentScreen();
  }
}

async function toggleFavorite(ch) {
  if (!ch) return;
  const next = !ch.favorite;
  if (state.localOverrideChannels) {
    ch.favorite = next;
    state.favorites = state.localOverrideChannels.filter((c) => c.favorite);
    updateShellChrome();
    updatePlayerFavButton();
    if (state.page === 'favorites') {
      renderCurrentScreen();
    } else if (state.page !== 'settings') {
      syncChannelFavoriteButtons(ch.id, next);
    }
    return;
  }
  try {
    await api(`/api/channels/${encodeURIComponent(ch.id)}`, {
      method: 'PATCH',
      body: { favorite: next },
    });
    ch.favorite = next;
    [state.channels, state.searchResults, state.allWorldPool, state.mixHistory, state.playerQueue].forEach((arr) => {
      arr.forEach((item) => {
        if (item && item.id === ch.id) item.favorite = next;
      });
    });
    if (state.playerChannel?.id === ch.id) state.playerChannel.favorite = next;
    toast(
      next
        ? t('Aggiunto ai preferiti', 'Added to favorites', 'Añadido a favoritos')
        : t('Rimosso dai preferiti', 'Removed from favorites', 'Quitado de favoritos'),
    );
    await loadFavorites();
    updatePlayerFavButton();
    if (state.page !== 'settings' && state.page !== 'favorites') {
      syncChannelFavoriteButtons(ch.id, next);
    }
  } catch (err) {
    toast(err.message, 'err');
  }
}

function performGlobalSearch(query) {
  state.searchQuery = query;
  $('#global-search-clear').hidden = !query;
  clearTimeout(state.searchTimer);
  const needle = query.trim();
  if (!needle) {
    state.searchResults = [];
    state.searchLoading = false;
    $('#search-spinner').hidden = true;
    updateShellChrome();
    renderCurrentScreen();
    return;
  }
  state.searchLoading = true;
  $('#search-spinner').hidden = false;
  updateShellChrome();
  state.searchTimer = setTimeout(async () => {
    try {
      if (state.localOverrideChannels) {
        const lower = needle.toLowerCase();
        state.searchResults = state.localOverrideChannels.filter((c) => c.name.toLowerCase().includes(lower));
      } else {
        const data = await api(`/api/channels?q=${encodeURIComponent(needle)}&limit=160&sort=number`);
        state.searchResults = data.items || [];
      }
    } catch (_) {
      state.searchResults = [];
    } finally {
      state.searchLoading = false;
      $('#search-spinner').hidden = true;
      updateShellChrome();
      renderCurrentScreen();
    }
  }, 240);
}

/* ---------------- Renderers: Channels / Favorites / Search ---------------- */
function stopCardPreview() {
  state.hoveredPreviewCard = null;
  clearTimeout(state.cardPreviewTimer);
  state.cardPreviewTimer = null;
  if (state.cardPreviewHls) {
    state.cardPreviewHls.destroy();
    state.cardPreviewHls = null;
  }
  const video = state.cardPreviewVideo;
  if (video) {
    video.onloadeddata = null;
    video.classList.remove('is-visible');
    try {
      video.pause();
      video.removeAttribute('src');
      video.load();
    } catch (_) {}
    video.remove();
    state.cardPreviewVideo = null;
  }
  if (state.cardPreviewCard) {
    $('.card-live-tag', state.cardPreviewCard)?.classList.remove('is-visible');
    state.cardPreviewCard = null;
  }
}

function attachCardPreview(cardEl, streamUrl) {
  stopCardPreview();
  state.hoveredPreviewCard = cardEl;
  if (state.deviceType === 'smartphone' || !streamUrl || !/^https?:\/\//i.test(streamUrl)) return;
  state.cardPreviewTimer = setTimeout(() => {
    if (!cardEl || !document.body.contains(cardEl) || state.hoveredPreviewCard !== cardEl) return;
    const artwork = $('.card-artwork', cardEl);
    if (!artwork) return;
    const video = document.createElement('video');
    video.className = 'card-preview-video';
    video.muted = true;
    video.playsInline = true;
    video.setAttribute('muted', '');
    video.setAttribute('playsinline', '');
    const overlayAnchor = artwork.querySelector('.card-num-badge, .card-top-right, .card-status-dot, .card-live-tag');
    if (overlayAnchor) artwork.insertBefore(video, overlayAnchor);
    else artwork.appendChild(video);

    state.cardPreviewVideo = video;
    state.cardPreviewCard = cardEl;
    const liveTag = $('.card-live-tag', cardEl);
    const onPlaying = () => {
      if (state.cardPreviewVideo !== video) return;
      video.classList.add('is-visible');
      liveTag?.classList.add('is-visible');
    };
    if (window.Hls && window.Hls.isSupported() && /\.m3u8|m3u8/i.test(streamUrl)) {
      const hls = new window.Hls({ enableWorker: true, lowLatencyMode: true });
      state.cardPreviewHls = hls;
      hls.loadSource(streamUrl);
      hls.attachMedia(video);
      hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
        if (state.cardPreviewVideo !== video) return;
        video.play().then(onPlaying).catch(() => {});
      });
    } else {
      video.src = streamUrl;
      video.onloadeddata = onPlaying;
      video.play().catch(() => {});
    }
  }, 850);
}

function renderLogoOrFallback(ch, mobile = false, eager = false) {
  const loading = eager ? 'eager' : 'lazy';
  const priority = eager ? 'high' : 'low';
  if (mobile) {
    if (ch.logo) {
      return `<img src="${esc(ch.logo)}" alt="${esc(ch.name)}" loading="${loading}" decoding="async" fetchpriority="${priority}" data-fallback="${esc(ch.number || initials(ch.name))}" data-fallback-mobile="1" />`;
    }
    return `<span class="row-num-fallback">${esc(ch.number || initials(ch.name))}</span>`;
  }
  if (ch.logo) {
    return `<img class="card-logo-img" src="${esc(ch.logo)}" alt="${esc(ch.name)}" loading="${loading}" decoding="async" fetchpriority="${priority}" data-fallback="${esc(initials(ch.name))}" data-fallback-mobile="0" />`;
  }
  return `<div class="fallback-monogram">${esc(initials(ch.name))}</div>`;
}

function renderChannelItemHtml(ch, idx, { showFlag = false, isMobile = false, eager = false } = {}) {
  if (isMobile) {
    return `
      <div class="mobile-channel-row" data-idx="${idx}" data-id="${esc(ch.id)}" tabindex="0" role="button">
        <div class="row-logo-box">${renderLogoOrFallback(ch, true, eager)}</div>
        <div class="row-text">
          <strong>${esc(ch.name)}</strong>
          <small>${showFlag && (ch.flag || ch.country) ? `${ch.flag || countryFlag(ch.country)} ` : ''}${esc((ch.categoryNames && ch.categoryNames[0]) || ch.countryName || ch.quality || '')}</small>
        </div>
        <button type="button" class="row-fav-btn ${ch.favorite ? 'is-fav' : ''}" data-fav-idx="${idx}" aria-label="Preferito">
          ${ch.favorite ? '★' : '☆'}
        </button>
      </div>
    `;
  }

  return `
    <article class="grid-channel-card" data-idx="${idx}" data-id="${esc(ch.id)}" tabindex="0" role="button">
      <div class="card-artwork">
        ${renderLogoOrFallback(ch, false, eager)}
        ${ch.number ? `<span class="card-num-badge">${esc(ch.number)}</span>` : ''}
        <div class="card-top-right">
          ${showFlag && (ch.flag || ch.country) ? `<span class="card-flag-badge">${ch.flag || countryFlag(ch.country)}</span>` : ''}
          <button type="button" class="card-fav-btn ${ch.favorite ? 'is-fav' : ''}" data-fav-idx="${idx}" title="${t('Preferito', 'Favorite', 'Favorito')}">${ch.favorite ? '★' : '☆'}</button>
        </div>
        <span class="card-status-dot"><span></span></span>
        <span class="card-live-tag"><i></i>LIVE</span>
      </div>
      <div class="card-meta">
        <div class="card-title-row">
          <h4 class="card-title">${esc(ch.name)}</h4>
        </div>
        <p class="card-sub">${esc((ch.categoryNames && ch.categoryNames[0]) || ch.countryName || ch.quality || '')}</p>
      </div>
    </article>
  `;
}

function renderChannelGridCards(channels, { showFlag = false, listKey = '' } = {}) {
  const isMobile = state.deviceType === 'smartphone';
  const cls = isMobile ? 'mobile-channels-list' : 'channels-grid';
  const keyAttr = listKey ? ` data-list-key="${esc(listKey)}"` : '';
  return `
    <div class="${cls}"${keyAttr}>
      ${channels
        .map((ch, idx) => renderChannelItemHtml(ch, idx, { showFlag, isMobile, eager: idx < 18 }))
        .join('')}
    </div>
  `;
}

function bindChannelListEvents(container, list) {
  const isMobile = state.deviceType === 'smartphone';
  const gridSel = isMobile ? '.mobile-channels-list' : '.channels-grid';
  const gridEl = $(gridSel, container);
  if (!gridEl) return;
  gridEl._channelList = list;
  if (gridEl.dataset.bound === '1') return;
  gridEl.dataset.bound = '1';

  const cardSel = isMobile ? '.mobile-channel-row' : '.grid-channel-card';

  gridEl.addEventListener('click', (e) => {
    const favBtn = e.target.closest('[data-fav-idx]');
    if (favBtn && gridEl.contains(favBtn)) {
      e.stopPropagation();
      const idx = Number(favBtn.dataset.favIdx);
      const curList = gridEl._channelList || [];
      toggleFavorite(curList[idx]);
      return;
    }
    const card = e.target.closest(cardSel);
    if (!card || !gridEl.contains(card)) return;
    const idx = Number(card.dataset.idx);
    const curList = gridEl._channelList || [];
    const ch = curList[idx];
    if (!ch) return;
    stopCardPreview();
    openPlayer(ch, curList, { isMix: false });
  });

  gridEl.addEventListener('keydown', (e) => {
    const card = e.target.closest(cardSel);
    if (!card || !gridEl.contains(card)) return;
    const idx = Number(card.dataset.idx);
    const curList = gridEl._channelList || [];
    const ch = curList[idx];
    if (!ch) return;
    if (e.key === 'Enter' || e.key === ' ') {
      if (e.target.closest('[data-fav-idx]') && e.key === 'Enter') return;
      e.preventDefault();
      stopCardPreview();
      openPlayer(ch, curList, { isMix: false });
    } else if (e.key.toLowerCase() === 'm') {
      e.preventDefault();
      toggleFavorite(ch);
    }
  });

  if (!isMobile) {
    gridEl.addEventListener('mouseover', (e) => {
      const card = e.target.closest(cardSel);
      if (!card || !gridEl.contains(card) || card === state.hoveredPreviewCard) return;
      const idx = Number(card.dataset.idx);
      const ch = (gridEl._channelList || [])[idx];
      if (ch) attachCardPreview(card, ch.streamUrl);
    });
    gridEl.addEventListener('mouseout', (e) => {
      const card = e.target.closest(cardSel);
      if (!card || card !== state.hoveredPreviewCard) return;
      if (e.relatedTarget && card.contains(e.relatedTarget)) return;
      stopCardPreview();
    });
    gridEl.addEventListener('focusin', (e) => {
      const card = e.target.closest(cardSel);
      if (!card || !gridEl.contains(card) || card === state.hoveredPreviewCard) return;
      const idx = Number(card.dataset.idx);
      const ch = (gridEl._channelList || [])[idx];
      if (ch) attachCardPreview(card, ch.streamUrl);
    });
    gridEl.addEventListener('focusout', (e) => {
      const card = e.target.closest(cardSel);
      if (!card || (e.relatedTarget && card.contains(e.relatedTarget))) return;
      if (state.hoveredPreviewCard === card) stopCardPreview();
    });
  }

  gridEl.addEventListener(
    'error',
    (e) => {
      const img = e.target;
      if (!img || img.tagName !== 'IMG' || img.dataset.fallback == null) return;
      const fallbackEl = document.createElement(img.dataset.fallbackMobile === '1' ? 'span' : 'div');
      fallbackEl.className = img.dataset.fallbackMobile === '1' ? 'row-num-fallback' : 'fallback-monogram';
      fallbackEl.textContent = img.dataset.fallback;
      img.replaceWith(fallbackEl);
    },
    true,
  );
}

function renderCurrentScreen() {
  stopCardPreview();
  disconnectChannelsObserver();
  const root = $('#app');
  if (!root) return;

  // Global search view overrides tabs cleanly (matching TvModeScreen)
  if (state.searchQuery.trim()) {
    if (state.searchLoading && !state.searchResults.length) {
      root.innerHTML = `<div class="loading-box"><div class="spinner"></div><p>${t('Cerco tra i canali…', 'Searching channels…', 'Buscando canales…')}</p></div>`;
      return;
    }
    if (!state.searchLoading && !state.searchResults.length) {
      root.innerHTML = `
        <div class="empty-box">
          <h3>${t('Nessun risultato trovato', 'No results found', 'Ningún resultado')}</h3>
          <p>${t(`Nessun canale corrisponde a "${esc(state.searchQuery)}".`, `No channel matches "${esc(state.searchQuery)}".`)}</p>
          <button type="button" class="casa-btn primary" id="btn-clear-search-empty">${t('Cancella ricerca', 'Clear search', 'Borrar búsqueda')}</button>
        </div>
      `;
      $('#btn-clear-search-empty')?.addEventListener('click', () => {
        $('#global-search-input').value = '';
        performGlobalSearch('');
      });
      return;
    }
    root.innerHTML = `
      <div class="search-banner">
        <strong>${t(`Ricerca globale per "${esc(state.searchQuery)}"`, `Worldwide search for "${esc(state.searchQuery)}"`)}</strong>
        <span>${t('Catalogo globale', 'Worldwide catalog', 'Catálogo mundial')}</span>
      </div>
      ${renderChannelGridCards(state.searchResults, { showFlag: true })}
    `;
    bindChannelListEvents(root, state.searchResults);
    return;
  }

  if (state.page === 'settings') {
    renderSettingsScreen(root);
    return;
  }

  if (state.page === 'favorites') {
    if (state.favoritesLoading && !state.favorites.length) {
      root.innerHTML = `<div class="loading-box"><div class="spinner"></div><p>${t('Carico i preferiti…', 'Loading favorites…')}</p></div>`;
      return;
    }
    if (!state.favorites.length) {
      root.innerHTML = `
        <div class="empty-box">
          <h3>${t('Nessun preferito', 'No favorites yet', 'Sin favoritos')}</h3>
          <p>${t(
            'Sfoglia i canali e clicca la stellina ☆ su un canale o premi M sul player per salvarlo qui.',
            'Browse channels and click ☆ on a channel or press M in the player to save it here.',
          )}</p>
          <button type="button" class="casa-btn primary" id="btn-fav-browse">${t('Vai ai canali', 'Go to channels', 'Ir a canales')}</button>
        </div>
      `;
      $('#btn-fav-browse')?.addEventListener('click', () => navigate('channels'));
      return;
    }
    root.innerHTML = `
      <div class="favorites-head">
        <div>
          <h2 style="display:inline">${t('Preferiti', 'Favorites', 'Favoritos')}</h2>
          <span>· ${state.favorites.length} ${state.favorites.length === 1 ? t('canale', 'channel', 'canal') : t('canali', 'channels', 'canales')}</span>
        </div>
        <button type="button" class="casa-btn ghost sm" id="btn-fav-to-channels">${t('Sfoglia i canali', 'Browse channels', 'Explorar canales')}</button>
      </div>
      ${renderChannelGridCards(state.favorites, { showFlag: true })}
    `;
    $('#btn-fav-to-channels')?.addEventListener('click', () => navigate('channels'));
    bindChannelListEvents(root, state.favorites);
    return;
  }

  // Default: Canali view
  if (state.channelsLoading && !state.channels.length) {
    root.innerHTML = `<div class="loading-box"><div class="spinner"></div><p>${t('Carico i canali del paese…', 'Loading country channels…')}</p></div>`;
    return;
  }
  if (!state.channels.length) {
    root.innerHTML = `
      <div class="empty-box">
        <h3>${t('Nessun canale', 'No channels', 'Sin canales')}</h3>
        <p>${t(`Non ci sono canali per ${esc(state.tvCountry)}. Scegli un altro paese.`, `No channels for ${esc(state.tvCountry)}. Choose another country.`)}</p>
        <button type="button" class="casa-btn primary" id="btn-empty-country">${t('Cambia paese', 'Change country', 'Cambiar país')}</button>
      </div>
    `;
    $('#btn-empty-country')?.addEventListener('click', openCountryModal);
    return;
  }

  const countryTitle = currentCountryTitle();
  const fallbackBanner = state.channelsFallback
    ? `<div class="fallback-notice">${t(
        `Nessun canale per ${esc(countryTitle)}: mostro l'intero catalogo.`,
        `No channels for ${esc(countryTitle)}: showing the entire catalog.`,
      )}</div>`
    : '';
  const hasMore = state.channels.length < state.channelsTotal;

  root.innerHTML = `
    ${state.localOverrideChannels ? `<div class="fallback-notice">📁 ${esc(state.localSourceLabel)} · ${fmtNum(state.channelsTotal)} canali · <button type="button" class="casa-btn ghost sm" id="btn-exit-local-m3u">${t('Torna al server', 'Back to server')}</button></div>` : fallbackBanner}
    ${renderChannelGridCards(state.channels, { showFlag: state.channelsFallback || Boolean(state.localOverrideChannels), listKey: channelsGridListKey() })}
    <div class="grid-footer">
      ${hasMore ? `<div class="channels-load-sentinel" id="channels-load-sentinel" aria-hidden="true"></div><button type="button" class="casa-btn ghost" id="btn-load-more-ch">${t('Carica altri canali', 'Load more channels')} (${fmtNum(state.channels.length)} / ${fmtNum(state.channelsTotal)})</button>` : ''}
      <p id="channels-footer-summary">${t('Diretta TV', 'Live TV')} · ${esc(countryTitle)} · ${fmtNum(state.channels.length)} ${t('canali visualizzati', 'channels loaded')}</p>
    </div>
  `;
  $('#btn-exit-local-m3u')?.addEventListener('click', () => {
    state.localOverrideChannels = null;
    state.localSourceLabel = '';
    loadChannels({ reset: true });
    loadFavorites();
  });
  $('#btn-load-more-ch')?.addEventListener('click', () => loadChannels({ reset: false }));
  bindChannelListEvents(root, state.channels);
  setupChannelsInfiniteScroll();
}

/* ---------------- Navigation ---------------- */
function navigate(page) {
  if (state.searchQuery) {
    $('#global-search-input').value = '';
    state.searchQuery = '';
    state.searchResults = [];
    $('#global-search-clear').hidden = true;
  }
  state.page = page;
  if (window.location.hash !== `#/${page}`) {
    history.replaceState(null, '', `#/${page}`);
  }
  if (page === 'favorites') loadFavorites();
  if (page === 'settings') refreshSystemStatus();
  updateShellChrome();
  renderCurrentScreen();
}

/* ---------------- Profile Gate Modal ("Chi sta guardando?") ---------------- */
function openProfileGate(allowCancel = true) {
  const modal = $('#profile-modal');
  if (!modal) return;
  state.profileManaging = false;
  state.profileEditing = null;
  renderProfileGateView(allowCancel);
  modal.hidden = false;
  requestAnimationFrame(() => {
    const preferredId = state.activeProfile?.id || localStorage.getItem('casatv_profile_id');
    const first =
      (preferredId && $(`#modal-profiles-grid .profile-tile[data-prof-id="${CSS.escape(preferredId)}"]`)) ||
      $('#modal-profiles-grid .profile-tile');
    first?.focus();
    setTimeout(() => {
      if (!modal.hidden && !state.profileEditing && document.activeElement !== first) {
        first?.focus();
      }
    }, 90);
  });
}

function closeProfileGate() {
  const modal = $('#profile-modal');
  if (!modal) return;
  modal.hidden = true;
  sessionStorage.setItem('casatv_profile_picked', '1');
}

function renderProfileGateView(allowCancel = true) {
  const gateView = $('#profile-gate-view');
  const editView = $('#profile-editor-view');
  if (state.profileEditing) {
    gateView.hidden = true;
    editView.hidden = false;
    renderProfileEditor();
    return;
  }
  gateView.hidden = false;
  editView.hidden = true;
  $('#profile-gate-title').textContent = t("Chi sta guardando?", "Who's watching?", "¿Quién está viendo?");
  $('#profile-gate-sub').textContent = t('Scegli un profilo per continuare', 'Choose a profile to continue', 'Elige un perfil para continuar');

  const preferredId = state.activeProfile?.id || localStorage.getItem('casatv_profile_id');
  const preferredIdx = preferredId ? state.profiles.findIndex((p) => p.id === preferredId) : -1;
  const focusIdx = preferredIdx >= 0 ? preferredIdx : 0;
  const grid = $('#modal-profiles-grid');
  grid.innerHTML =
    state.profiles
      .map(
        (p, idx) => `
      <button type="button" class="profile-tile ${idx === focusIdx ? 'is-primary-focus' : ''}" data-prof-id="${esc(p.id)}">
        <div class="profile-tile-circle">${esc(p.avatar || '👤')}</div>
        <strong>${esc(p.name)}</strong>
        <small class="${state.profileManaging ? 'is-edit' : ''}">${state.profileManaging ? t('Modifica', 'Edit', 'Editar') : `${countryFlag(p.country)} ${esc((p.language || 'it').toUpperCase())}`}</small>
      </button>
    `,
      )
      .join('') +
    `
      <button type="button" class="profile-tile" id="btn-tile-add-profile">
        <div class="profile-tile-circle" style="color:var(--muted);font-weight:300">+</div>
        <strong>${t('Aggiungi', 'Add', 'Añadir')}</strong>
        <small>${t('Nuovo profilo', 'New profile', 'Nuevo perfil')}</small>
      </button>
    `;

  const backBtn = $('#btn-gate-back');
  backBtn.hidden = !allowCancel || state.profileManaging || !state.activeProfile;
  backBtn.textContent = t('Torna indietro', 'Go back', 'Volver');
  backBtn.onclick = closeProfileGate;

  const manageBtn = $('#btn-gate-manage');
  manageBtn.textContent = state.profileManaging
    ? t('Fine', 'Done', 'Listo')
    : t('Gestisci profili', 'Manage profiles', 'Gestionar perfiles');
  manageBtn.classList.toggle('primary', state.profileManaging);
  manageBtn.onclick = () => {
    state.profileManaging = !state.profileManaging;
    renderProfileGateView(allowCancel);
  };

  $$('[data-prof-id]', grid).forEach((tile) => {
    tile.addEventListener('click', async () => {
      const prof = state.profiles.find((p) => p.id === tile.dataset.profId);
      if (!prof) return;
      if (state.profileManaging) {
        if (prof.id === 'guest') {
          toast(t("Il profilo Ospite è temporaneo e non può essere modificato.", "Guest profile cannot be edited."), 'err');
          return;
        }
        state.profileEditing = prof;
        renderProfileGateView(allowCancel);
      } else {
        await selectActiveProfile(prof);
        closeProfileGate();
      }
    });
  });

  $('#btn-tile-add-profile')?.addEventListener('click', () => {
    state.profileEditing = 'new';
    renderProfileGateView(allowCancel);
  });
}

async function selectActiveProfile(prof) {
  state.activeProfile = prof;
  localStorage.setItem('casatv_profile_id', prof.id);
  if (prof.country) {
    state.tvCountry = prof.country;
    localStorage.setItem('casatv_tv_country', prof.country);
  }
  updateShellChrome();
  await loadServerConfig().catch(() => {});
  await loadChannels({ reset: true });
  await loadFavorites();
  renderCurrentScreen();
}

function renderProfileEditor() {
  const existing = state.profileEditing === 'new' ? null : state.profileEditing;
  $('#profile-editor-title').textContent = existing
    ? t('Modifica profilo', 'Edit profile', 'Editar perfil')
    : t('Nuovo profilo', 'New profile', 'Nuevo perfil');
  const nameInput = $('#editor-prof-name');
  const langSel = $('#editor-prof-lang');
  const countrySel = $('#editor-prof-country');
  const previewEl = $('#editor-avatar-preview');
  const delBtn = $('#btn-editor-delete');

  let selectedAvatar = existing?.avatar || PROFILE_AVATARS[0];
  nameInput.value = existing?.name || '';
  langSel.value = existing?.language || 'it';
  previewEl.textContent = selectedAvatar;

  const countries = state.config?.facets?.countries || [{ code: 'IT', name: 'Italia', flag: '🇮🇹' }];
  countrySel.innerHTML = countries
    .map((c) => `<option value="${esc(c.code)}">${c.flag || countryFlag(c.code)} ${esc(c.name)}</option>`)
    .join('');
  countrySel.value = existing?.country || state.tvCountry || 'IT';

  const avatarGrid = $('#editor-avatar-grid');
  avatarGrid.innerHTML = PROFILE_AVATARS.map(
    (a) => `<button type="button" class="avatar-cell ${a === selectedAvatar ? 'is-selected' : ''}" data-av="${a}">${a}</button>`,
  ).join('');
  $$('.avatar-cell', avatarGrid).forEach((btn) => {
    btn.addEventListener('click', () => {
      selectedAvatar = btn.dataset.av;
      previewEl.textContent = selectedAvatar;
      $$('.avatar-cell', avatarGrid).forEach((b) => b.classList.toggle('is-selected', b === btn));
    });
  });

  delBtn.hidden = !existing || state.profiles.length <= 1;
  delBtn.onclick = async () => {
    if (!existing) return;
    try {
      await api(`/api/profiles/${encodeURIComponent(existing.id)}`, { method: 'DELETE' });
      await loadProfiles();
      state.profileEditing = null;
      renderProfileGateView(true);
      toast(t('Profilo eliminato', 'Profile deleted'));
    } catch (err) {
      toast(err.message, 'err');
    }
  };

  $('#btn-editor-cancel').onclick = () => {
    state.profileEditing = null;
    renderProfileGateView(true);
  };

  $('#btn-editor-save').onclick = async () => {
    const name = nameInput.value.trim();
    if (!name) {
      toast(t('Inserisci un nome per il profilo', 'Enter a profile name'), 'err');
      return;
    }
    try {
      if (existing) {
        const updated = await api(`/api/profiles/${encodeURIComponent(existing.id)}`, {
          method: 'PATCH',
          body: { name, avatar: selectedAvatar, language: langSel.value, country: countrySel.value },
        });
        if (state.activeProfile?.id === updated.id) state.activeProfile = updated;
      } else {
        await api('/api/profiles', {
          method: 'POST',
          body: { name, avatar: selectedAvatar, language: langSel.value, country: countrySel.value },
        });
      }
      await loadProfiles();
      state.profileEditing = null;
      renderProfileGateView(true);
      updateShellChrome();
      renderCurrentScreen();
      toast(t('Profilo salvato', 'Profile saved'));
    } catch (err) {
      toast(err.message, 'err');
    }
  };
}

/* ---------------- Country Modal ---------------- */
function openCountryModal() {
  const modal = $('#country-modal');
  if (!modal) return;
  $('#country-modal-title').textContent = t('Paese della modalità TV', 'TV mode country', 'País del modo TV');
  $('#country-modal-sub').textContent = t('I canali vengono ordinati per il paese scelto.', 'Channels are sorted for the chosen country.');

  const allCountries = state.config?.facets?.countries || [{ code: 'IT', name: 'Italia', flag: '🇮🇹', count: 0 }];
  const listEl = $('#modal-countries-list');
  const input = $('#country-search-input');

  function draw(q = '') {
    const needle = q.trim().toLowerCase();
    const rows = needle
      ? allCountries.filter((c) => c.name.toLowerCase().includes(needle) || c.code.toLowerCase().includes(needle))
      : allCountries;
    listEl.innerHTML = rows
      .map(
        (c) => `
      <button type="button" class="country-tile ${c.code === state.tvCountry ? 'is-selected' : ''}" data-c="${esc(c.code)}">
        <span>${c.flag || countryFlag(c.code)}</span>
        <span class="c-name">${esc(c.name)}</span>
        <span class="c-count">${c.count || ''}</span>
      </button>
    `,
      )
      .join('');
    $$('.country-tile', listEl).forEach((btn) => {
      btn.addEventListener('click', () => {
        state.tvCountry = btn.dataset.c;
        localStorage.setItem('casatv_tv_country', state.tvCountry);
        modal.hidden = true;
        updateShellChrome();
        loadChannels({ reset: true });
      });
    });
  }

  input.value = '';
  input.oninput = () => draw(input.value);
  draw('');
  modal.hidden = false;
  requestAnimationFrame(() => input.focus());
}

/* ---------------- Mappamondo (Interactive World Map) & Mix Zapping ---------------- */
async function ensureMixPoolLoaded() {
  if (state.localOverrideChannels) {
    state.allWorldPool = state.localOverrideChannels;
    state.mixPoolLoaded = true;
    return state.allWorldPool;
  }
  if (state.mixPoolLoaded && state.allWorldPool.length) return state.allWorldPool;
  if (state.mixPoolPromise) return state.mixPoolPromise;
  state.mixPoolPromise = (async () => {
    try {
      const collected = [];
      let offset = 0;
      const limit = 10000;
      while (offset < 40000) {
        const data = await api(`/api/channels?limit=${limit}&offset=${offset}&sort=number`);
        const batch = data.items || [];
        if (!batch.length) break;
        collected.push(...batch);
        offset += batch.length;
        if (!data.hasMore) break;
      }
      state.allWorldPool = collected.length ? collected : state.channels.slice();
      state.mixPoolLoaded = collected.length > 0;
    } catch (_) {
      state.allWorldPool = state.channels.slice();
    } finally {
      state.mixPoolPromise = null;
    }
    if (!$('#mix-modal')?.hidden) updateMappamondoUI();
    if (!$('#mix-filters-overlay')?.hidden) renderMixFiltersUI();
    return state.allWorldPool;
  })();
  return state.mixPoolPromise;
}

function resetMixState(resetRegions = true) {
  if (state.mixVerifyAbort) {
    state.mixVerifyAbort.abort();
    state.mixVerifyAbort = null;
  }
  state.mixHistory = [];
  state.mixIndex = -1;
  state.deadMixIds.clear();
  state.verifiedNextMix = null;
  state.mixErrorRetryCount = 0;
  if (resetRegions) {
    state.selectedMixRegions = new Set(['ALL_WORLD']);
    state.selectedMixLanguages.clear();
    state.selectedMixGenres.clear();
  }
}

function toggleMixRegionSelection(regionId) {
  if (regionId === 'ALL_WORLD') {
    state.selectedMixRegions = new Set(['ALL_WORLD']);
  } else {
    const next = new Set(state.selectedMixRegions);
    next.delete('ALL_WORLD');
    if (next.has(regionId)) next.delete(regionId);
    else next.add(regionId);
    state.selectedMixRegions = next.size ? next : new Set(['ALL_WORLD']);
  }
  updateMappamondoUI();
}

function computeMixRegionCounts() {
  const counts = {};
  if (hasActiveMixFilters() && (state.allWorldPool.length || state.channels.length)) {
    getFilteredMixPool(false).forEach((c) => {
      const reg = resolveMixRegion(c.country);
      if (reg) counts[reg] = (counts[reg] || 0) + 1;
    });
    return counts;
  }
  (state.config?.facets?.countries || []).forEach((c) => {
    const reg = resolveMixRegion(c.code);
    if (reg) counts[reg] = (counts[reg] || 0) + Number(c.count || 0);
  });
  return counts;
}

function openMixRegionPicker({ resetSession = true } = {}) {
  if (resetSession) resetMixState(true);
  const filtersOverlay = $('#mix-filters-overlay');
  if (filtersOverlay) filtersOverlay.hidden = true;
  ensureMixPoolLoaded();
  renderMappamondoSvgOnce();
  updateMappamondoUI();
  $('#mix-modal').hidden = false;
  requestAnimationFrame(() => $('#mix-world-svg .region-group[data-reg="EUROPE"]')?.focus());
}

function renderMappamondoSvgOnce() {
  const globeG = $('#mix-map-globe');
  const graticule = $('#mix-map-graticule');
  const regionsG = $('#mix-map-regions');
  if (!graticule || !regionsG || regionsG.dataset.built) return;
  regionsG.dataset.built = '1';

  const ns = 'http://www.w3.org/2000/svg';
  const defs = document.createElementNS(ns, 'defs');
  defs.innerHTML = `
    <radialGradient id="ocean-sphere-grad" cx="50%" cy="46%" r="58%">
      <stop offset="0%" stop-color="#0A223E"/>
      <stop offset="100%" stop-color="#050E1B"/>
    </radialGradient>
    <linearGradient id="cyan-selected-grad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#00D4FF"/>
      <stop offset="100%" stop-color="#0284C7"/>
    </linearGradient>
    <clipPath id="nat-earth-clip">
      <path d="${WORLD_MAP_GRATICULE.globeD}"/>
    </clipPath>
  `;
  $('#mix-world-svg').prepend(defs);

  // Natural Earth I curved ocean sphere & clipped graticule
  if (globeG) {
    const oceanFill = document.createElementNS(ns, 'path');
    oceanFill.setAttribute('class', 'map-globe-fill');
    oceanFill.setAttribute('d', WORLD_MAP_GRATICULE.globeD);
    const oceanRim = document.createElementNS(ns, 'path');
    oceanRim.setAttribute('class', 'map-globe-rim');
    oceanRim.setAttribute('d', WORLD_MAP_GRATICULE.globeD);
    globeG.append(oceanFill, oceanRim);
  }

  graticule.setAttribute('clip-path', 'url(#nat-earth-clip)');
  WORLD_MAP_GRATICULE.parallelsD.forEach((d) => {
    const p = document.createElementNS(ns, 'path');
    p.setAttribute('d', d);
    graticule.appendChild(p);
  });
  WORLD_MAP_GRATICULE.meridiansD.forEach((d) => {
    const p = document.createElementNS(ns, 'path');
    p.setAttribute('d', d);
    graticule.appendChild(p);
  });
  if (WORLD_MAP_GRATICULE.equatorD) {
    const eq = document.createElementNS(ns, 'path');
    eq.setAttribute('class', 'equator');
    eq.setAttribute('d', WORLD_MAP_GRATICULE.equatorD);
    graticule.appendChild(eq);
  }

  // High-definition Natural Earth 110m vector country outlines grouped by MixRegion
  MIX_REGIONS.filter((r) => r.id !== 'ALL_WORLD').forEach((reg) => {
    const g = document.createElementNS(ns, 'g');
    g.setAttribute('class', 'region-group');
    g.setAttribute('data-reg', reg.id);
    g.setAttribute('tabindex', '0');
    g.setAttribute('role', 'button');
    g.setAttribute('aria-label', regionTitle(reg));

    const polysD = WORLD_MAP_REGIONS[reg.id]?.polygonsD || [];
    polysD.forEach((d) => {
      const path = document.createElementNS(ns, 'path');
      path.setAttribute('class', 'region-shape');
      path.setAttribute('d', d);
      g.appendChild(path);
    });

    const showHover = () => {
      state.hoveredMixRegion = reg.id;
      updateMappamondoCallout(reg);
      updateMappamondoHoverState();
    };
    g.addEventListener('mouseenter', showHover);
    g.addEventListener('focus', showHover);
    g.addEventListener('click', () => {
      state.hoveredMixRegion = reg.id;
      toggleMixRegionSelection(reg.id);
    });
    g.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        toggleMixRegionSelection(reg.id);
      } else if (e.key.startsWith('Arrow')) {
        const dir = e.key.replace('Arrow', '').toLowerCase();
        const nextId = reg.neighbors?.[dir];
        if (nextId) {
          e.preventDefault();
          $(`#mix-world-svg .region-group[data-reg="${nextId}"]`)?.focus();
        }
      }
    });
    regionsG.appendChild(g);
  });

  $('#mix-map-container')?.addEventListener('mouseleave', () => {
    const callout = $('#mix-map-callout');
    if (callout) callout.hidden = true;
    state.hoveredMixRegion = '';
    updateMappamondoHoverState();
  });
}

function updateMappamondoHoverState() {
  $$('#mix-world-svg .region-group').forEach((g) => g.classList.toggle('is-hovered', g.dataset.reg === state.hoveredMixRegion));
  $$('#mix-regions-strip .mix-region-card').forEach((c) => c.classList.toggle('is-hovered', c.dataset.cardReg === state.hoveredMixRegion));
}

function updateMappamondoCallout(reg) {
  const callout = $('#mix-map-callout');
  const anchor = WORLD_MAP_REGIONS[reg?.id]?.anchor || reg?.anchor;
  if (!callout || !anchor) return;
  // Show ONLY compact region name on map floating callout (counts & details stay in bottom strip)
  $('#mix-callout-name').textContent = regionTitle(reg);
  callout.style.left = `${anchor[0] * 100}%`;
  callout.style.top = `${Math.max(11, anchor[1] * 100 - 4)}%`;
  callout.hidden = false;
}

function updateMappamondoUI() {
  const allWorld = state.selectedMixRegions.has('ALL_WORLD');
  const activeFiltersCount = state.selectedMixLanguages.size + state.selectedMixGenres.size;
  const counts = computeMixRegionCounts();
  const poolReady = state.mixPoolLoaded || Boolean(state.localOverrideChannels);
  const matchingCount = poolReady
    ? getFilteredMixPool(true).length
    : state.config?.stats?.channels || state.allWorldPool.length || state.channelsTotal;

  $('#mix-modal-title').textContent = t('MIX · MAPPAMONDO', 'MIX · WORLD MAP', 'MIX · MAPAMUNDI');
  const filterNote = activeFiltersCount > 0 ? ` · ${activeFiltersCount} ${t('filtri', 'filters', 'filtros')}` : '';
  const chWord = t('canali', 'channels', 'canales');
  $('#mix-active-summary').textContent = allWorld
    ? `${regionTitle(MIX_REGIONS[0])} · ${fmtNum(matchingCount)} ${chWord}${filterNote}`
    : `${[...state.selectedMixRegions]
        .map((id) => regionTitle(MIX_REGIONS.find((r) => r.id === id)))
        .join(' · ')} · ${fmtNum(matchingCount)} ${chWord}${filterNote}`;

  const allWorldBtn = $('#mix-btn-all-world');
  allWorldBtn.textContent = `🌍 ${t('Tutto il mondo', 'All World', 'Todo el mundo')}`;
  allWorldBtn.classList.toggle('is-selected', allWorld);

  const filtersBtn = $('#mix-btn-filters');
  if (filtersBtn) {
    const fLabel = t('Filtri', 'Filters', 'Filtros');
    filtersBtn.textContent = activeFiltersCount > 0 ? `⚙ ${fLabel} (${activeFiltersCount})` : `⚙ ${fLabel}`;
    filtersBtn.classList.toggle('is-selected', activeFiltersCount > 0);
  }

  $('#mix-btn-start').textContent = `▶ ${t('Avvia Mix', 'Start Mix', 'Iniciar Mix')}`;

  $$('#mix-world-svg .region-group').forEach((g) => {
    const id = g.dataset.reg;
    g.classList.toggle('is-selected', state.selectedMixRegions.has(id));
    g.classList.toggle('is-world', allWorld);
    g.classList.toggle('is-hovered', state.hoveredMixRegion === id);
  });

  // Render bottom strip with crisp zoomed Natural Earth mini-map SVG, counts & sociological subtitles
  const strip = $('#mix-regions-strip');
  strip.innerHTML = MIX_REGIONS.filter((r) => r.id !== 'ALL_WORLD')
    .map((r) => {
      const selected = state.selectedMixRegions.has(r.id);
      const count = counts[r.id] || 0;
      const geo = WORLD_MAP_REGIONS[r.id];
      const viewBox = geo?.bboxSvg ? geo.bboxSvg.join(' ') : '0 0 100 52';
      const miniPaths = (geo?.polygonsD || [])
        .map((d) => `<path d="${d}" fill="${selected ? '#00D4FF' : '#38BDF8'}" fill-opacity="${selected ? '0.95' : '0.50'}"/>`)
        .join('');
      return `
        <button type="button" class="mix-region-card ${selected ? 'is-selected' : ''}" data-card-reg="${r.id}">
          <svg class="mix-mini-map" viewBox="${viewBox}" preserveAspectRatio="xMidYMid meet">${miniPaths}</svg>
          <div class="mix-region-card-meta">
            <div class="mix-region-card-top">
              <span class="mix-region-card-title">${esc(regionTitle(r))}</span>
              <span class="mix-region-card-count">${fmtNum(count)}</span>
            </div>
            <div class="mix-region-card-sub">${esc(regionSub(r))}</div>
          </div>
        </button>
      `;
    })
    .join('');

  $$('[data-card-reg]', strip).forEach((card) => {
    const id = card.dataset.cardReg;
    card.addEventListener('click', () => {
      state.hoveredMixRegion = id;
      toggleMixRegionSelection(id);
    });
    card.addEventListener('mouseenter', () => {
      state.hoveredMixRegion = id;
      const reg = MIX_REGIONS.find((r) => r.id === id);
      if (reg) updateMappamondoCallout(reg);
      $$('#mix-world-svg .region-group').forEach((g) => g.classList.toggle('is-hovered', g.dataset.reg === id));
    });
  });
}

function openMixFiltersOverlay() {
  const overlay = $('#mix-filters-overlay');
  if (!overlay) return;
  overlay.hidden = false;
  renderMixFiltersUI();
  ensureMixPoolLoaded();
}

function closeMixFiltersOverlay() {
  const overlay = $('#mix-filters-overlay');
  if (overlay) overlay.hidden = true;
  updateMappamondoUI();
}

function toggleMixLanguage(code) {
  if (state.selectedMixLanguages.has(code)) state.selectedMixLanguages.delete(code);
  else state.selectedMixLanguages.add(code);
  renderMixFiltersUI();
  updateMappamondoUI();
}

function toggleMixGenre(id) {
  if (state.selectedMixGenres.has(id)) state.selectedMixGenres.delete(id);
  else state.selectedMixGenres.add(id);
  renderMixFiltersUI();
  updateMappamondoUI();
}

function clearMixFilters() {
  if (!state.selectedMixLanguages.size && !state.selectedMixGenres.size) return;
  state.selectedMixLanguages.clear();
  state.selectedMixGenres.clear();
  renderMixFiltersUI();
  updateMappamondoUI();
}

function renderMixFiltersUI() {
  const overlay = $('#mix-filters-overlay');
  if (!overlay) return;
  const totalActive = state.selectedMixLanguages.size + state.selectedMixGenres.size;
  const poolReady = state.mixPoolLoaded || Boolean(state.localOverrideChannels);
  const matchingCount = poolReady
    ? getFilteredMixPool(true).length
    : state.config?.stats?.channels || state.allWorldPool.length || state.channelsTotal;

  $('#mix-filters-title').textContent = t(
    'FILTRI MIX · LINGUE E GENERI',
    'MIX FILTERS · LANGUAGES & GENRES',
    'FILTROS MIX · IDIOMAS Y GÉNEROS',
  );
  const countTxt = `${fmtNum(matchingCount)} ${t('canali', 'channels', 'canales')}`;
  $('#mix-filters-summary').textContent =
    totalActive === 0
      ? `${t('Nessun filtro attivo', 'No active filters', 'Sin filtros activos')} · ${countTxt}`
      : `${totalActive} ${t('filtri attivi', 'active filters', 'filtros activos')} · ${countTxt}`;

  const clearBtn = $('#mix-filters-clear');
  if (clearBtn) {
    clearBtn.textContent = t('Azzera', 'Clear', 'Borrar');
    clearBtn.disabled = totalActive === 0;
  }
  const applyBtn = $('#mix-filters-apply');
  if (applyBtn) {
    applyBtn.textContent = `✓ ${t('Applica', 'Apply', 'Aplicar')}`;
  }

  const langs = sortedMixLanguages();
  const genres = sortedMixGenres();

  const langTab = $('#mix-tab-langs');
  const genreTab = $('#mix-tab-genres');
  if (langTab) {
    langTab.textContent =
      `${t('Lingue', 'Languages', 'Idiomas')} (${langs.length})` +
      (state.selectedMixLanguages.size ? ` · ${state.selectedMixLanguages.size}` : '');
    const isActive = state.mixFilterTab !== 'genres';
    langTab.classList.toggle('is-active', isActive);
    langTab.setAttribute('aria-selected', String(isActive));
  }
  if (genreTab) {
    genreTab.textContent =
      `${t('Generi', 'Genres', 'Géneros')} (${genres.length})` +
      (state.selectedMixGenres.size ? ` · ${state.selectedMixGenres.size}` : '');
    const isActive = state.mixFilterTab === 'genres';
    genreTab.classList.toggle('is-active', isActive);
    genreTab.setAttribute('aria-selected', String(isActive));
  }

  const grid = $('#mix-filters-grid');
  if (!grid) return;
  if (state.mixFilterTab === 'genres') {
    grid.innerHTML = genres
      .map((g) => {
        const selected = state.selectedMixGenres.has(g.id);
        return `
          <button type="button" class="mix-filter-chip ${selected ? 'is-selected' : ''}" data-filter-genre="${esc(g.id)}">
            <span class="mix-filter-check">${selected ? '✓' : ''}</span>
            <span class="mix-filter-label">${esc(mixFilterLabel(g))}</span>
          </button>
        `;
      })
      .join('');
    $$('[data-filter-genre]', grid).forEach((btn) =>
      btn.addEventListener('click', () => toggleMixGenre(btn.dataset.filterGenre)),
    );
  } else {
    grid.innerHTML = langs
      .map((l) => {
        const selected = state.selectedMixLanguages.has(l.code);
        return `
          <button type="button" class="mix-filter-chip ${selected ? 'is-selected' : ''}" data-filter-lang="${esc(l.code)}">
            <span class="mix-filter-check">${selected ? '✓' : ''}</span>
            <span class="mix-filter-label">${esc(mixFilterLabel(l))}</span>
          </button>
        `;
      })
      .join('');
    $$('[data-filter-lang]', grid).forEach((btn) =>
      btn.addEventListener('click', () => toggleMixLanguage(btn.dataset.filterLang)),
    );
  }
}

function getFilteredMixPool(applyRegions = true) {
  const base = state.allWorldPool.length ? state.allWorldPool : state.channels;
  const filtered = base.filter((c) => {
    if (!/^https?:\/\//i.test(c.streamUrl || '') || state.deadMixIds.has(c.id)) return false;
    if (!state.mixIncludeAudioOnly && (state.runtimeAudioOnlyIds.has(c.id) || isLikelyAudioOnlyChannel(c))) {
      return false;
    }
    if (!matchesMixLanguages(c)) return false;
    if (!matchesMixGenres(c)) return false;
    return true;
  });
  if (!applyRegions || state.selectedMixRegions.has('ALL_WORLD')) return filtered;
  return filtered.filter((c) => {
    const reg = resolveMixRegion(c.country);
    return reg && state.selectedMixRegions.has(reg);
  });
}

function getRegionalMixPool() {
  return getFilteredMixPool(true);
}

function pickRandomMixCandidate(excludeIds = new Set()) {
  const pool = getRegionalMixPool().filter((c) => !excludeIds.has(c.id));
  if (!pool.length) return null;
  return pool[Math.floor(Math.random() * pool.length)];
}

async function probeStreamReachable(url, signal) {
  if (!/^https?:\/\//i.test(url)) return false;
  if (signal?.aborted) return false;
  if (!window.Hls?.isSupported?.() && !isHlsStreamUrl(url) && !isDashStreamUrl(url) && !isMpegTsStreamUrl(url)) {
    return true;
  }
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 3200);
  if (signal) signal.addEventListener('abort', () => ctrl.abort(), { once: true });
  try {
    const res = await fetch(url, { method: 'GET', mode: 'cors', signal: ctrl.signal });
    if (!res.ok) {
      ctrl.abort();
      return false;
    }
    if (isMpegTsStreamUrl(url)) {
      ctrl.abort();
      return true;
    }
    let text = '';
    if (res.body?.getReader) {
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let total = 0;
      while (total < 4096) {
        const { done, value } = await reader.read();
        if (done) break;
        if (value) {
          total += value.byteLength;
          text += decoder.decode(value, { stream: true });
        }
      }
      ctrl.abort();
    } else {
      text = (await res.text()).slice(0, 4096);
    }
    if (isHlsStreamUrl(url)) return text.includes('#EXTM3U');
    if (isDashStreamUrl(url)) return text.includes('<MPD');
    return text.length > 0;
  } catch (err) {
    // Abort means navigation moved on; network error/CORS means unplayable in browser
    if (err?.name === 'AbortError' && signal?.aborted) throw err;
    return false;
  } finally {
    clearTimeout(timer);
  }
}

function preverifyNextMixCandidate() {
  if (state.mixVerifyAbort) state.mixVerifyAbort.abort();
  state.verifiedNextMix = null;
  const abortCtrl = new AbortController();
  state.mixVerifyAbort = abortCtrl;
  const visited = new Set(state.mixHistory.map((c) => c.id));
  (async () => {
    for (let attempt = 0; attempt < 12; attempt++) {
      if (abortCtrl.signal.aborted || !state.playerIsMix) return;
      const cand = pickRandomMixCandidate(visited);
      if (!cand) return;
      visited.add(cand.id);
      const ok = await probeStreamReachable(cand.streamUrl, abortCtrl.signal);
      if (abortCtrl.signal.aborted || !state.playerIsMix) return;
      if (ok) {
        state.verifiedNextMix = cand;
        return;
      }
      state.deadMixIds.add(cand.id);
    }
  })().catch(() => {});
}

async function startMixPlayback() {
  resetMixState(false);
  state.mixAudioSkipCount = 0;
  await ensureMixPoolLoaded();
  const pool = getRegionalMixPool();
  if (!pool.length) {
    toast(
      hasActiveMixFilters()
        ? t(
            'Nessun canale corrisponde ai filtri selezionati',
            'No channels match the selected filters',
            'Ningún canal coincide con los filtros seleccionados',
          )
        : t(
            'Nessun canale disponibile nelle regioni selezionate',
            'No channels in selected regions',
            'Sin canales en las regiones seleccionadas',
          ),
      'err',
    );
    return;
  }
  $('#mix-modal').hidden = true;
  const first = pickRandomMixCandidate();
  if (!first) return;
  state.mixHistory = [first];
  state.mixIndex = 0;
  openPlayer(first, [first], { isMix: true });
  preverifyNextMixCandidate();
}

function stepMixChannel(delta) {
  if (!state.playerIsMix) return;
  if (delta < 0) {
    // Revisit already visited Mix channel without fetching new ones
    if (state.mixIndex > 0) {
      state.mixIndex -= 1;
      const ch = state.mixHistory[state.mixIndex];
      tunePlayerChannel(ch, { isMix: true, skipMarkPlayed: true });
      preverifyNextMixCandidate();
    }
    return;
  }
  // Going forward: replay history if user previously went back
  if (state.mixIndex + 1 < state.mixHistory.length) {
    state.mixIndex += 1;
    const ch = state.mixHistory[state.mixIndex];
    tunePlayerChannel(ch, { isMix: true, skipMarkPlayed: true });
    preverifyNextMixCandidate();
    return;
  }
  // Reached end of visited list: propose verified channel (if slow zap) or random fast candidate
  const visited = new Set(state.mixHistory.map((c) => c.id));
  const next = state.verifiedNextMix || pickRandomMixCandidate(visited) || pickRandomMixCandidate();
  state.verifiedNextMix = null;
  if (!next) {
    toast(t('Nessun altro canale Mix disponibile', 'No more Mix channels available'), 'err');
    return;
  }
  state.mixHistory.push(next);
  state.mixIndex = state.mixHistory.length - 1;
  tunePlayerChannel(next, { isMix: true, skipMarkPlayed: true });
  preverifyNextMixCandidate();
}

/* ---------------- Player, Quick Drawer & Multi-TV Sync ---------------- */
function openPlayer(channel, queue, { isMix = false } = {}) {
  state.playerOpen = true;
  state.playerQueue = queue && queue.length ? queue : [channel];
  state.playerIsMix = isMix;
  state.drawerOpen = false;
  $('#player-quick-drawer').hidden = true;
  $('#player-modal').hidden = false;
  tunePlayerChannel(channel, { isMix, skipMarkPlayed: isMix });
  updatePlayerChromeVisibility();
  wakePlayerControls();
  showTemporaryHintsBanner();
}

function closePlayer() {
  state.playerOpen = false;
  state.tuneSeq += 1;
  stopWebRecoveryLoops();
  stopPlayerStream();
  stopMultiTvSync();
  closeDrawerPreview();
  hideTemporaryHintsBanner();
  renderAudioOnlyStage(null, false);
  state.drawerOpen = false;
  $('#player-quick-drawer').hidden = true;
  $('#player-modal').hidden = true;
  if (document.pictureInPictureElement) {
    document.exitPictureInPicture().catch(() => {});
  }
  if (state.playerIsMix) {
    state.playerIsMix = false;
    resetMixState(true);
  }
  state.playbackState = 'idle';
  state.playbackChannel = '';
}

function stopWebRecoveryLoops() {
  clearTimeout(state.reconnectTimer);
  state.reconnectTimer = null;
  clearTimeout(state.playerTuneFailTimer);
  state.playerTuneFailTimer = null;
  clearTimeout(state.audioCheckTimer);
  state.audioCheckTimer = null;
  clearInterval(state.cushionTimer);
  state.cushionTimer = null;
  state.reconnectActive = false;
  state.cushionBuilding = false;
  const pill = $('#player-reconnect-pill');
  if (pill) pill.hidden = true;
}

function hideTemporaryHintsBanner() {
  clearTimeout(state.hintsBannerTimer);
  state.hintsBannerTimer = null;
  const banner = $('#player-hints-banner');
  if (banner) banner.hidden = true;
}

function showTemporaryHintsBanner() {
  hideTemporaryHintsBanner();
  const banner = $('#player-hints-banner');
  if (
    !banner ||
    !state.showRemoteHintsBanner ||
    !state.playerOpen ||
    state.drawerOpen ||
    state.deviceType === 'smartphone'
  ) {
    return;
  }
  const chips = state.playerIsMix
    ? [
        `<span class="hint-chip"><kbd>◀</kbd> ${t('Prec.', 'Prev', 'Ant.')}</span>`,
        `<span class="hint-chip"><kbd>▲▼▶</kbd> Mix</span>`,
        `<span class="hint-chip"><kbd>M</kbd> ${t('Preferito', 'Favorite', 'Favorito')}</span>`,
        `<span class="hint-chip"><kbd>Esc</kbd> ${t('Esci', 'Exit', 'Salir')}</span>`,
      ]
    : [
        `<span class="hint-chip"><kbd>◀</kbd> ${t('Lista', 'List', 'Lista')}</span>`,
        `<span class="hint-chip"><kbd>▲▼</kbd> ${t('Canali', 'Channels', 'Canales')}</span>`,
        `<span class="hint-chip"><kbd>0-9</kbd> ${t('Numero', 'Number', 'Número')}</span>`,
        `<span class="hint-chip"><kbd>M</kbd> ${t('Preferito', 'Favorite', 'Favorito')}</span>`,
        `<span class="hint-chip"><kbd>Esc</kbd> ${t('Esci', 'Exit', 'Salir')}</span>`,
      ];
  banner.innerHTML = chips.join('');
  banner.hidden = false;
  state.hintsBannerTimer = setTimeout(() => {
    banner.hidden = true;
    state.hintsBannerTimer = null;
  }, 6000);
}

function renderAudioOnlyStage(ch, visible) {
  const stage = $('#player-audio-stage');
  if (!stage) return;
  if (!visible || !ch) {
    stage.hidden = true;
    return;
  }
  const logoEl = $('#player-audio-logo');
  const monoEl = $('#player-audio-monogram');
  const titleEl = $('#player-audio-title');
  const badgeEl = $('#player-audio-badge');
  const subEl = $('#player-audio-sub');

  if (titleEl) titleEl.textContent = ch.name || 'Canale';
  if (badgeEl) badgeEl.textContent = t('SOLO AUDIO', 'AUDIO ONLY', 'SOLO AUDIO');
  if (monoEl) monoEl.textContent = initials(ch.name || 'TV');

  const cName = getLocalizedCountryName(ch.country, ch.countryName);
  const flag = ch.flag || (ch.country ? countryFlag(ch.country) : '');
  const cLabel = [flag, cName].filter(Boolean).join(' ');
  const catLabel = (ch.categoryNames && ch.categoryNames[0]) || (ch.categories && ch.categories[0]) || '';
  const subParts = [cLabel, catLabel && catLabel.toLowerCase() !== cName.toLowerCase() ? catLabel : ''].filter(Boolean);
  if (subEl) subEl.textContent = subParts.join(' · ');

  if (logoEl && monoEl) {
    if (ch.logo) {
      monoEl.hidden = true;
      logoEl.hidden = false;
      logoEl.onload = () => {
        if (state.playerChannel?.id === ch.id) {
          logoEl.hidden = false;
          monoEl.hidden = true;
        }
      };
      logoEl.onerror = () => {
        logoEl.hidden = true;
        monoEl.hidden = false;
      };
      logoEl.src = ch.logo;
    } else {
      logoEl.hidden = true;
      logoEl.removeAttribute('src');
      monoEl.hidden = false;
    }
  }
  stage.hidden = false;
}

function isHlsStreamUrl(url) {
  return /\.m3u8(\?|$|#)|[/?&]m3u8\b|format=m3u8/i.test(String(url || ''));
}

function isDashStreamUrl(url) {
  return /\.mpd(\?|$|#)|format=mpd|application\/dash\+xml/i.test(String(url || ''));
}

function isMpegTsStreamUrl(url) {
  const s = String(url || '');
  return !isHlsStreamUrl(s) && !isDashStreamUrl(s) && (/\.ts(\?|$|#)|[?&](output|type|format)=ts\b/i.test(s));
}

function stopPlayerStream({ keepFrame = false } = {}) {
  const video = $('#preview-video');
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }
  if (state.dashPlayer) {
    try {
      state.dashPlayer.reset();
    } catch (_) {}
    state.dashPlayer = null;
  }
  if (state.mpegtsPlayer) {
    try {
      state.mpegtsPlayer.pause();
      state.mpegtsPlayer.unload();
      state.mpegtsPlayer.detachMediaElement();
      state.mpegtsPlayer.destroy();
    } catch (_) {}
    state.mpegtsPlayer = null;
  }
  if (video) {
    video.onplaying = null;
    video.oncanplay = null;
    video.onloadedmetadata = null;
    video.onloadeddata = null;
    video.onresize = null;
    video.onended = null;
    video.onerror = null;
    if (!keepFrame) {
      video.pause();
      video.removeAttribute('src');
      video.load();
      video.playbackRate = 1.0;
    }
  }
}

function setVideoPreservesPitch(video, enabled) {
  if (!video) return;
  try {
    if ('preservesPitch' in video) video.preservesPitch = enabled;
    if ('mozPreservesPitch' in video) video.mozPreservesPitch = enabled;
    if ('webkitPreservesPitch' in video) video.webkitPreservesPitch = enabled;
  } catch (_) {}
}

function getVideoBufferedAheadSec(video) {
  if (!video || !video.buffered || !video.buffered.length) return 0;
  const tNow = video.currentTime || 0;
  for (let i = 0; i < video.buffered.length; i++) {
    const s = video.buffered.start(i);
    const e = video.buffered.end(i);
    if (tNow >= s - 0.5 && tNow <= e + 0.5) {
      return Math.max(0, e - tNow);
    }
  }
  return Math.max(0, video.buffered.end(video.buffered.length - 1) - tNow);
}

function getVideoLiveWindowSec(video) {
  if (!video || !video.seekable || !video.seekable.length) return 0;
  const s = video.seekable.start(0);
  const e = video.seekable.end(video.seekable.length - 1);
  return Number.isFinite(e - s) && e > s ? e - s : 0;
}

function tunePlayerChannel(ch, { isMix = state.playerIsMix, skipMarkPlayed = isMix, preserveSourceIdx = false } = {}) {
  if (!ch) return;
  const seq = ++state.tuneSeq;
  stopWebRecoveryLoops();
  stopPlayerStream();
  state.reconnectAttempt = 0;
  state.reconnectActive = false;
  state.cushionBuilding = false;
  state.hasPlayedCurrentChannel = false;
  state.currentStreamAudioOnly = false;
  state.bufferingSinceMs = 0;
  state.readySinceMs = 0;
  state.lastStallTime = -1;
  state.lastStallWallMs = 0;
  state.playerChannel = ch;
  if (!preserveSourceIdx) {
    state.playerSources = [];
    state.playerSourceIdx = 0;
  }
  state.playbackState = 'playing';
  state.playbackChannel = ch.name;
  renderAudioOnlyStage(ch, false);

  // Update Bottom OSD Strip (strictly fixed 58px height)
  const mixBadge = $('#strip-mix-badge');
  mixBadge.hidden = !isMix;
  if (isMix) {
    mixBadge.textContent = `MIX · ${state.mixIndex + 1}/${state.mixHistory.length}`;
  }
  $('#strip-num-badge').textContent = ch.number ? String(ch.number) : ch.flag || countryFlag(ch.country);
  $('#player-ch-name').textContent = ch.name || 'Canale';

  const resolvedFlag = ch.flag || (ch.country ? countryFlag(ch.country) : '');
  const resolvedCountryName = getLocalizedCountryName(ch.country, ch.countryName);
  const countryWrap = $('#player-ch-country');
  if (countryWrap) {
    if (resolvedFlag || resolvedCountryName) {
      $('#player-ch-flag').textContent = resolvedFlag;
      $('#player-ch-country-name').textContent = resolvedCountryName;
      countryWrap.hidden = false;
    } else {
      countryWrap.hidden = true;
    }
  }

  const qBadge = $('#player-ch-quality');
  if (ch.quality) {
    qBadge.hidden = false;
    qBadge.textContent = ch.quality;
  } else {
    qBadge.hidden = true;
  }
  $('#player-mix-regions-btn').hidden = !isMix;
  updatePlayerFavButton();
  updateClock();

  const video = $('#preview-video');
  const loading = $('#player-loading');
  const errBox = $('#player-error');
  const reconnectPill = $('#player-reconnect-pill');
  const reconnectLabel = $('#player-reconnect-label');
  loading.hidden = false;
  errBox.hidden = true;
  if (reconnectPill) reconnectPill.hidden = true;
  $('#player-tuning-label').textContent = t('Sintonizzazione…', 'Tuning…', 'Sintonizando…');

  const url = ch.streamUrl;
  const clearAudioOnlyIfVideo = () => {
    if (!state.playerOpen || state.tuneSeq !== seq) return;
    if ((video.videoWidth || 0) > 0 && (video.videoHeight || 0) > 0) {
      clearTimeout(state.audioCheckTimer);
      state.audioCheckTimer = null;
      state.currentStreamAudioOnly = false;
      state.mixAudioSkipCount = 0;
      state.mixErrorRetryCount = 0;
      renderAudioOnlyStage(ch, false);
    }
  };
  const handleConfirmedAudioOnly = () => {
    if (!state.playerOpen || state.tuneSeq !== seq || state.currentStreamAudioOnly) return;
    if ((video.videoWidth || 0) > 0 && (video.videoHeight || 0) > 0) return;
    clearTimeout(state.audioCheckTimer);
    state.audioCheckTimer = null;
    state.currentStreamAudioOnly = true;
    if (isMix && !state.mixIncludeAudioOnly && state.mixAudioSkipCount < 10) {
      state.runtimeAudioOnlyIds.add(ch.id);
      if (state.mixHistory[state.mixHistory.length - 1]?.id === ch.id) {
        state.mixHistory.pop();
        state.mixIndex = Math.max(-1, state.mixHistory.length - 1);
      }
      state.mixAudioSkipCount += 1;
      stepMixChannel(1);
      return;
    }
    state.mixErrorRetryCount = 0;
    renderAudioOnlyStage(ch, true);
  };

  const onReady = () => {
    if (!state.playerOpen || state.tuneSeq !== seq) return;
    loading.hidden = true;
    errBox.hidden = true;
    if (reconnectPill) reconnectPill.hidden = true;
    state.reconnectActive = false;
    state.hasPlayedCurrentChannel = true;
    if (!(isMix && !state.mixIncludeAudioOnly && isLikelyAudioOnlyChannel(ch))) {
      state.mixErrorRetryCount = 0;
    }
    if ((video.videoWidth || 0) > 0 && (video.videoHeight || 0) > 0) {
      clearAudioOnlyIfVideo();
    } else if (isLikelyAudioOnlyChannel(ch)) {
      handleConfirmedAudioOnly();
    } else if (!state.audioCheckTimer) {
      state.audioCheckTimer = setTimeout(() => {
        state.audioCheckTimer = null;
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        if (!video.paused && video.readyState >= 2 && (video.videoWidth || 0) === 0 && (video.videoHeight || 0) === 0) {
          handleConfirmedAudioOnly();
        }
      }, 650);
    }
    if ('mediaSession' in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: ch.name,
        artist: ch.countryName || 'CasaTV Live',
        artwork: ch.logo ? [{ src: ch.logo }] : [],
      });
    }
  };
  const onFail = (msg) => {
    if (!state.playerOpen || state.tuneSeq !== seq) return;
    loading.hidden = true;
    renderAudioOnlyStage(ch, false);
    hideTemporaryHintsBanner();
    if (reconnectPill) reconnectPill.hidden = true;
    state.reconnectActive = false;
    if (isMix && state.mixErrorRetryCount < 5) {
      state.deadMixIds.add(ch.id);
      if (state.mixHistory[state.mixHistory.length - 1]?.id === ch.id) {
        state.mixHistory.pop();
        state.mixIndex = Math.max(-1, state.mixHistory.length - 1);
      }
      state.mixErrorRetryCount += 1;
      stepMixChannel(1);
      return;
    }
    errBox.hidden = false;
    $('#player-error-title').textContent = t('Flusso non disponibile', 'Stream unavailable', 'Transmisión no disponible');
    $('#player-error-msg').textContent = msg || t('Impossibile riprodurre questo canale al momento.', 'Unable to play this stream right now.');
    const nextSrcBtn = $('#player-next-src-btn');
    nextSrcBtn.hidden = state.playerSources.length <= 1;
  };

  let attachStream;
  const scheduleWebReconnect = (reason) => {
    if (!state.playerOpen || state.tuneSeq !== seq) return;
    if (isMix && !state.hasPlayedCurrentChannel && state.mixErrorRetryCount < 5) {
      onFail();
      return;
    }
    const backoffs = [700, 1500, 2500, 4000, 6000, 8000];
    clearTimeout(state.reconnectTimer);
    if (state.reconnectAttempt < backoffs.length) {
      const delayMs = backoffs[state.reconnectAttempt++];
      state.reconnectActive = true;
      loading.hidden = true;
      errBox.hidden = true;
      if (reconnectLabel) {
        reconnectLabel.textContent = t('Riconnessione in corso…', 'Reconnecting…', 'Reconectando…');
      }
      if (reconnectPill) reconnectPill.hidden = false;
      state.reconnectTimer = setTimeout(() => {
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        let targetUrl = state.playerChannel?.streamUrl || url;
        if (state.reconnectAttempt === 3 && state.playerSources.length > 1) {
          state.playerSourceIdx = (state.playerSourceIdx + 1) % state.playerSources.length;
          targetUrl = state.playerSources[state.playerSourceIdx].url;
          state.playerChannel = { ...state.playerChannel, streamUrl: targetUrl };
        }
        attachStream(targetUrl, { keepFrame: true });
      }, delayMs);
    } else {
      state.reconnectActive = false;
      if (reconnectPill) reconnectPill.hidden = true;
      onFail(t(
        'Flusso temporaneamente interrotto. Riconnessione automatica in attesa…',
        'Stream temporarily interrupted. Background reconnect waiting…',
        'Flujo interrumpido temporalmente. Reconexión en segundo plano…',
      ));
      state.reconnectTimer = setTimeout(() => {
        if (!state.playerOpen || state.tuneSeq !== seq || errBox.hidden) return;
        state.reconnectAttempt = 0;
        state.reconnectActive = true;
        errBox.hidden = true;
        if (reconnectLabel) {
          reconnectLabel.textContent = t('Riconnessione in corso…', 'Reconnecting…', 'Reconectando…');
        }
        if (reconnectPill) reconnectPill.hidden = false;
        attachStream(state.playerChannel?.streamUrl || url, { keepFrame: true });
      }, 15000);
    }
  };

  attachStream = (streamUrl, { keepFrame = false } = {}) => {
    if (!state.playerOpen || state.tuneSeq !== seq) return;
    stopPlayerStream({ keepFrame });
    if (!streamUrl) {
      onFail(t('Questo canale non ha un flusso valido o disponibile.', 'This channel has no valid or available stream.'));
      return;
    }
    setVideoPreservesPitch(video, true);
    video.playbackRate = 1.0;
    video.onplaying = onReady;
    video.onloadeddata = clearAudioOnlyIfVideo;
    video.onresize = clearAudioOnlyIfVideo;
    video.onended = () => {
      if (state.playerOpen && state.tuneSeq === seq && (state.hasPlayedCurrentChannel || !Number.isFinite(video.duration))) {
        scheduleWebReconnect('ended');
      }
    };

    if (isDashStreamUrl(streamUrl) && !window.dashjs?.MediaPlayer) {
      ensureVendorScript('/vendor/dash.mediaplayer.min.js')
        .then(() => {
          if (state.playerOpen && state.tuneSeq === seq) attachStream(streamUrl, { keepFrame });
        })
        .catch(() => scheduleWebReconnect('dash_script_failed'));
      return;
    }
    if (isDashStreamUrl(streamUrl) && window.dashjs?.MediaPlayer) {
      const dp = window.dashjs.MediaPlayer().create();
      dp.updateSettings({
        streaming: {
          delay: { liveDelay: state.liveSafetyBuffer ? 8 : 4 },
          buffer: {
            bufferTimeAtTopQuality: state.liveSafetyBuffer ? 30 : 14,
            bufferTimeAtTopQualityLongForm: 40,
            stableBufferTime: state.liveSafetyBuffer ? 16 : 8,
          },
          retryAttempts: {
            MPD: 4,
            MediaSegment: state.liveSafetyBuffer ? 6 : 3,
          },
          retryIntervals: {
            MPD: 700,
            MediaSegment: 700,
          },
        },
      });
      state.dashPlayer = dp;
      const ev = window.dashjs.MediaPlayer.events;
      dp.on(ev.PLAYBACK_PLAYING, () => onReady());
      dp.on(ev.STREAM_INITIALIZED, () => {
        onReady();
        const hasAudio = dp.getTracksFor?.('audio')?.length > 0;
        const hasVideo = dp.getTracksFor?.('video')?.length > 0;
        if (hasAudio && !hasVideo) handleConfirmedAudioOnly();
      });
      dp.on(ev.ERROR, (e) => {
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        try {
          dp.reset();
        } catch (_) {}
        if (state.dashPlayer === dp) state.dashPlayer = null;
        scheduleWebReconnect(e?.error?.code || e?.error?.message || 'dash_error');
      });
      dp.initialize(video, streamUrl, true);
    } else if (window.Hls && window.Hls.isSupported() && isHlsStreamUrl(streamUrl)) {
      const hls = new window.Hls({
        enableWorker: true,
        lowLatencyMode: !state.liveSafetyBuffer,
        backBufferLength: 12,
        maxBufferLength: state.liveSafetyBuffer ? 30 : 14,
        maxMaxBufferLength: 45,
        liveSyncDurationCount: state.liveSafetyBuffer ? 4 : 3,
        manifestLoadingMaxRetry: state.liveSafetyBuffer ? 6 : 3,
        levelLoadingMaxRetry: state.liveSafetyBuffer ? 6 : 3,
        fragLoadingMaxRetry: state.liveSafetyBuffer ? 8 : 3,
        manifestLoadingRetryDelay: 700,
        levelLoadingRetryDelay: 700,
        fragLoadingRetryDelay: 700,
      });
      let fatalCount = 0;
      state.hls = hls;
      hls.loadSource(streamUrl);
      hls.attachMedia(video);
      hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
        video.play().then(onReady).catch(() => {});
      });
      hls.on(window.Hls.Events.BUFFER_CREATED, (_, data) => {
        const trackKeys = Object.keys(data?.tracks || {});
        if (trackKeys.length && trackKeys.includes('audio') && !trackKeys.includes('video') && !trackKeys.includes('audiovideo')) {
          handleConfirmedAudioOnly();
        }
      });
      hls.on(window.Hls.Events.ERROR, (_, data) => {
        if (!state.playerOpen || state.tuneSeq !== seq || !data.fatal) return;
        fatalCount += 1;
        if (fatalCount === 1 && data.type === window.Hls.ErrorTypes.NETWORK_ERROR) {
          hls.startLoad();
        } else if (fatalCount <= 2 && data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
          hls.recoverMediaError();
        } else {
          hls.destroy();
          state.hls = null;
          scheduleWebReconnect(data.details || data.type || 'hls_error');
        }
      });
    } else if (isMpegTsStreamUrl(streamUrl) && !window.mpegts) {
      ensureVendorScript('/vendor/mpegts.min.js')
        .then(() => {
          if (state.playerOpen && state.tuneSeq === seq) attachStream(streamUrl, { keepFrame });
        })
        .catch(() => scheduleWebReconnect('mpegts_script_failed'));
    } else if (isMpegTsStreamUrl(streamUrl) && window.mpegts?.isSupported?.()) {
      const mp = window.mpegts.createPlayer(
        { type: 'mpegts', isLive: true, url: streamUrl },
        {
          enableWorker: true,
          liveBufferLatencyChasing: !state.liveSafetyBuffer,
          liveBufferLatencyMaxLatency: state.liveSafetyBuffer ? 12 : 4,
          liveBufferLatencyMinRemain: state.liveSafetyBuffer ? 4 : 1.5,
          enableStashBuffer: true,
          stashInitialSize: state.liveSafetyBuffer ? 393216 : 131072,
        },
      );
      state.mpegtsPlayer = mp;
      mp.on(window.mpegts.Events.ERROR, (errType, errDetail) => {
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        try {
          mp.pause();
          mp.unload();
          mp.detachMediaElement();
          mp.destroy();
        } catch (_) {}
        if (state.mpegtsPlayer === mp) state.mpegtsPlayer = null;
        scheduleWebReconnect(errDetail || errType || 'mpegts_error');
      });
      mp.attachMediaElement(video);
      mp.load();
      Promise.resolve(mp.play()).then(onReady).catch(() => {});
    } else {
      video.src = streamUrl;
      video.onloadedmetadata = () => {
        video.play().catch(() => {});
        onReady();
      };
      video.onerror = () => {
        if (state.playerOpen && state.tuneSeq === seq) {
          scheduleWebReconnect('media_error');
        }
      };
      video.play().catch(() => {});
    }
  };

  attachStream(url, { keepFrame: false });

  state.cushionTimer = setInterval(() => {
    if (!state.playerOpen || state.tuneSeq !== seq) {
      clearInterval(state.cushionTimer);
      state.cushionTimer = null;
      return;
    }
    const now = Date.now();
    const readyPlaying = !video.paused && !video.ended && video.readyState >= 3;

    if (readyPlaying && errBox.hidden && !state.reconnectActive) {
      if (!state.readySinceMs) state.readySinceMs = now;
      if (state.reconnectAttempt > 0 && now - state.readySinceMs >= 6000) {
        state.reconnectAttempt = 0;
      }
    } else {
      state.readySinceMs = 0;
    }

    if (!video.paused && !video.ended && video.readyState < 3 && errBox.hidden && !state.reconnectActive) {
      if (!state.bufferingSinceMs) state.bufferingSinceMs = now;
      if (now - state.bufferingSinceMs > 12000) {
        state.bufferingSinceMs = now;
        scheduleWebReconnect('buffering_timeout');
        return;
      }
    } else {
      state.bufferingSinceMs = 0;
    }

    if (readyPlaying && errBox.hidden && !state.reconnectActive) {
      const tCur = video.currentTime || 0;
      if (Math.abs(tCur - state.lastStallTime) < 0.01) {
        if (now - state.lastStallWallMs > 6000) {
          state.lastStallWallMs = now;
          scheduleWebReconnect('stall_watchdog');
          return;
        }
      } else {
        state.lastStallTime = tCur;
        state.lastStallWallMs = now;
      }
    } else {
      state.lastStallTime = -1;
      state.lastStallWallMs = now;
    }

    if (readyPlaying) {
      if ((video.videoWidth || 0) > 0 && (video.videoHeight || 0) > 0) {
        clearAudioOnlyIfVideo();
      } else if ((video.currentTime || 0) > 0.45 && errBox.hidden && !state.reconnectActive) {
        handleConfirmedAudioOnly();
      }
      const winSec = getVideoLiveWindowSec(video);
      const isLiveLike = !Number.isFinite(video.duration) || video.duration === Infinity || winSec > 0;
      if (!state.liveSafetyBuffer || !isLiveLike) {
        state.cushionBuilding = false;
        if (Math.abs((video.playbackRate || 1) - 1.0) > 0.002) {
          setVideoPreservesPitch(video, true);
          video.playbackRate = 1.0;
        }
      } else {
        const aheadSec = getVideoBufferedAheadSec(video);
        const effectiveTargetSec = winSec > 0 ? Math.max(4, Math.min(8, winSec * 0.55)) : 8;
        const lowWatermarkSec = Math.max(2, effectiveTargetSec - 2.5);
        if (!state.cushionBuilding && aheadSec < lowWatermarkSec) {
          state.cushionBuilding = true;
        } else if (state.cushionBuilding && aheadSec >= effectiveTargetSec) {
          state.cushionBuilding = false;
        }
        const targetRate = state.cushionBuilding ? 0.97 : 1.0;
        if (Math.abs((video.playbackRate || 1) - targetRate) > 0.003 &&
            (targetRate === 1.0 || now - state.lastCushionRateMs >= 3000)) {
          setVideoPreservesPitch(video, true);
          video.playbackRate = targetRate;
          state.lastCushionRateMs = now;
        }
      }
    }
  }, 1000);

  // Only log watched history when NOT in Mix mode (honoring Mix rule)
  if (!skipMarkPlayed && ch.id && !state.localOverrideChannels) {
    api(`/api/channels/${encodeURIComponent(ch.id)}/played`, { method: 'POST' }).catch(() => {});
  }
  if (ch.id && !state.localOverrideChannels) {
    api(`/api/channels/${encodeURIComponent(ch.id)}/streams`)
      .then((res) => {
        state.playerSources = res.sources || [];
        $('#player-next-src-btn').hidden = state.playerSources.length <= 1;
      })
      .catch(() => {});
  }
  startMultiTvSync(ch);
  if (state.drawerOpen) renderDrawerList();
  wakePlayerControls();
}

function stepNormalChannel(delta) {
  if (state.playerIsMix) {
    stepMixChannel(delta);
    return;
  }
  const q = state.playerQueue;
  if (!q.length) return;
  const idx = q.findIndex((c) => c.id === state.playerChannel?.id);
  const nextIdx = (idx + delta + q.length) % q.length;
  tunePlayerChannel(q[nextIdx], { isMix: false });
}

function updatePlayerFavButton() {
  const btn = $('#player-fav-btn');
  if (!btn || !state.playerChannel) return;
  const fav = Boolean(state.playerChannel.favorite);
  btn.textContent = fav ? `★ ${t('Preferito', 'Favorite')}` : `☆ ${t('Preferito', 'Favorite')}`;
  btn.classList.toggle('is-active', fav);
}

function updatePlayerChromeVisibility() {
  const pipBtn = $('#player-pip-btn');
  if (pipBtn) pipBtn.hidden = !document.pictureInPictureEnabled;
  const drawerBtn = $('#player-drawer-toggle');
  if (drawerBtn) drawerBtn.hidden = state.playerIsMix;
}

function wakePlayerControls() {
  $('#player-top-actions')?.classList.remove('is-idle');
  $('#player-bottom-strip')?.classList.remove('is-idle');
  clearTimeout(state.idleTimer);
  if (!state.drawerOpen && state.deviceType === 'pc') {
    state.idleTimer = setTimeout(() => {
      if (state.playerOpen && !state.drawerOpen) {
        $('#player-top-actions')?.classList.add('is-idle');
        $('#player-bottom-strip')?.classList.add('is-idle');
      }
    }, 4500);
  }
}

function updateClock() {
  const localLabelEl = $('#strip-local-clock-label');
  const localTimeEl = $('#strip-clock');
  const countryBlock = $('#strip-country-clock-block');
  const countryLabelEl = $('#strip-country-clock-label');
  const countryTimeEl = $('#strip-country-clock');
  const divider = $('#strip-clock-divider');

  if (localLabelEl) {
    localLabelEl.textContent = t('ORA LOCALE', 'LOCAL TIME', 'HORA LOCAL');
  }
  if (localTimeEl) {
    localTimeEl.textContent = new Date().toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
  }

  const ch = state.playerChannel;
  const countryTime = ch?.country ? getCountryTimeText(ch.country) : null;
  if (countryBlock && countryTimeEl && countryLabelEl) {
    if (countryTime) {
      const flag = ch.flag || countryFlag(ch.country);
      const name = getLocalizedCountryName(ch.country, ch.countryName) || ch.country;
      countryLabelEl.textContent = [flag, String(name || '').toUpperCase()].filter(Boolean).join(' ');
      countryTimeEl.textContent = countryTime;
      countryBlock.hidden = false;
      if (divider) divider.hidden = false;
    } else {
      countryBlock.hidden = true;
      if (divider) divider.hidden = true;
    }
  }
}
setInterval(updateClock, 15000);

/* Quick Left-Side Channel Drawer inside Player */
function togglePlayerDrawer(forceOpen) {
  if (state.playerIsMix) return;
  const next = typeof forceOpen === 'boolean' ? forceOpen : !state.drawerOpen;
  state.drawerOpen = next;
  const drawer = $('#player-quick-drawer');
  drawer.hidden = !next;
  if (next) {
    hideTemporaryHintsBanner();
    const idx = Math.max(0, state.playerQueue.findIndex((c) => c.id === state.playerChannel?.id));
    state.drawerCursorIdx = idx;
    renderDrawerList();
    wakePlayerControls();
    requestAnimationFrame(() => {
      const target = $(`.drawer-item[data-d-idx="${state.drawerCursorIdx}"]`);
      target?.scrollIntoView({ block: 'center' });
      target?.focus();
    });
  } else {
    closeDrawerPreview();
    wakePlayerControls();
  }
}

function closeDrawerPreview() {
  clearTimeout(state.drawerPreviewTimer);
  state.drawerPreviewTimer = null;
  if (state.drawerPreviewHls) {
    state.drawerPreviewHls.destroy();
    state.drawerPreviewHls = null;
  }
  const video = state.drawerPreviewVideo;
  if (video) {
    video.onloadeddata = null;
    video.classList.remove('is-visible');
    try {
      video.pause();
      video.removeAttribute('src');
      video.load();
    } catch (_) {}
    video.remove();
    state.drawerPreviewVideo = null;
  }
  state.drawerPreviewItem = null;
}

function startDrawerPreview(itemEl, streamUrl) {
  closeDrawerPreview();
  state.drawerPreviewItem = itemEl;
  if (!streamUrl || !/^https?:\/\//i.test(streamUrl)) return;
  state.drawerPreviewTimer = setTimeout(() => {
    if (!itemEl || !state.drawerOpen || state.drawerPreviewItem !== itemEl) return;
    const box = $('.drawer-item-box', itemEl);
    if (!box) return;
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.setAttribute('muted', '');
    video.setAttribute('playsinline', '');
    box.appendChild(video);
    state.drawerPreviewVideo = video;
    const onShow = () => {
      if (state.drawerPreviewVideo === video) video.classList.add('is-visible');
    };
    if (window.Hls && window.Hls.isSupported() && /\.m3u8|m3u8/i.test(streamUrl)) {
      const hls = new window.Hls({ enableWorker: true, lowLatencyMode: true });
      state.drawerPreviewHls = hls;
      hls.loadSource(streamUrl);
      hls.attachMedia(video);
      hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
        if (state.drawerPreviewVideo !== video) return;
        video.play().then(onShow).catch(() => {});
      });
    } else {
      video.src = streamUrl;
      video.onloadeddata = onShow;
      video.play().catch(() => {});
    }
  }, 650);
}

function renderDrawerList() {
  const listEl = $('#drawer-list');
  const queue = state.playerQueue;
  $('#drawer-title').textContent = t('Canali', 'Channels', 'Canales');
  $('#drawer-sub').textContent = t('Sfoglia senza interrompere · Invio o clic per sintonizzare', 'Browse smoothly · Enter or click to tune');
  listEl._drawerQueue = queue;
  listEl.innerHTML = queue
    .map(
      (ch, i) => `
    <button type="button" class="drawer-item ${ch.id === state.playerChannel?.id ? 'is-playing' : ''} ${i === state.drawerCursorIdx ? 'is-cursor' : ''}" data-d-idx="${i}">
      <div class="drawer-item-box">
        ${ch.logo ? `<img src="${esc(ch.logo)}" alt="" loading="lazy" decoding="async" />` : `<span style="font-size:10px;font-weight:800;color:var(--accent)">${esc(initials(ch.name))}</span>`}
      </div>
      <div class="drawer-item-meta">
        <strong>${ch.number ? `${ch.number}. ` : ''}${esc(ch.name)}</strong>
        <small>${esc((ch.categoryNames && ch.categoryNames[0]) || ch.quality || '')}</small>
      </div>
    </button>
  `,
    )
    .join('');

  if (listEl.dataset.bound !== '1') {
    listEl.dataset.bound = '1';
    listEl.addEventListener('click', (e) => {
      const item = e.target.closest('.drawer-item');
      if (!item || !listEl.contains(item)) return;
      const i = Number(item.dataset.dIdx);
      const ch = (listEl._drawerQueue || [])[i];
      if (!ch) return;
      closeDrawerPreview();
      tunePlayerChannel(ch, { isMix: false });
      togglePlayerDrawer(false);
    });
    const onHighlight = (e) => {
      const item = e.target.closest('.drawer-item');
      if (!item || !listEl.contains(item)) return;
      const i = Number(item.dataset.dIdx);
      if (state.drawerPreviewItem === item && state.drawerCursorIdx === i) return;
      state.drawerCursorIdx = i;
      const prevCursor = $('.drawer-item.is-cursor', listEl);
      if (prevCursor && prevCursor !== item) prevCursor.classList.remove('is-cursor');
      item.classList.add('is-cursor');
      const ch = (listEl._drawerQueue || [])[i];
      startDrawerPreview(item, ch?.streamUrl);
    };
    listEl.addEventListener('mouseover', onHighlight);
    listEl.addEventListener('focusin', onHighlight);
    listEl.addEventListener(
      'error',
      (e) => {
        if (e.target?.tagName === 'IMG') e.target.remove();
      },
      true,
    );
  }
}

/* Multi-TV Synchronization (/api/sync) */
function stopMultiTvSync() {
  clearInterval(state.syncTimer);
  state.syncTimer = null;
  state.syncActivePeers = 1;
  $('#player-sync-pill').hidden = true;
  if (!state.localOverrideChannels) {
    api('/api/sync', {
      method: 'POST',
      body: { deviceId: state.deviceId, channelId: '' },
    }).catch(() => {});
  }
}

function startMultiTvSync(ch) {
  stopMultiTvSync();
  if (!state.multiTvSync || !ch?.id || state.localOverrideChannels) return;
  const tick = async () => {
    const v = $('#preview-video');
    if (!state.playerOpen || !v) return;
    try {
      const resp = await api('/api/sync', {
        method: 'POST',
        body: {
          deviceId: state.deviceId,
          deviceName: `${detectBrowserName()} (${deviceLabel()})`,
          channelId: ch.id,
          isLive: true,
          positionMs: Math.round((v.currentTime || 0) * 1000),
          liveOffsetMs: 0,
        },
      });
      const pill = $('#player-sync-pill');
      if (resp.syncActive) {
        pill.hidden = false;
        pill.textContent = `● Sync Multi-TV (${resp.peerCount})`;
      } else {
        pill.hidden = true;
      }
    } catch (_) {}
  };
  tick();
  state.syncTimer = setInterval(tick, 4000);
}

/* Numeric Channel Entry */
function pushNumberDigit(digit) {
  if (state.playerIsMix || state.numBuffer.length >= 4) return;
  state.numBuffer += digit;
  $('#player-num-digits').textContent = state.numBuffer;
  $('#player-num-overlay').hidden = false;
  clearTimeout(state.numTimer);
  state.numTimer = setTimeout(async () => {
    const num = Number.parseInt(state.numBuffer, 10);
    state.numBuffer = '';
    $('#player-num-overlay').hidden = true;
    if (!Number.isFinite(num)) return;
    const localMatch = state.playerQueue.find((c) => Number(c.number) === num);
    if (localMatch) {
      tunePlayerChannel(localMatch, { isMix: false });
      return;
    }
    try {
      const remote = await api(`/api/channels/by-number/${num}`);
      if (remote) tunePlayerChannel(remote, { isMix: false });
    } catch (_) {
      toast(t(`Canale ${num} non trovato`, `Channel ${num} not found`), 'err');
    }
  }, 1400);
}

/* ---------------- 4-Section Settings Screen (Matches TV & Mobile) ---------------- */
async function refreshSystemStatus() {
  try {
    state.system = await api('/api/system');
    if (state.page === 'settings' && state.settingsSection === 'server') {
      renderCurrentScreen();
    }
  } catch (_) {}
}

async function checkGitHubUpdates(manual = false) {
  if (!manual) {
    if (state.updateFrequency === 'NEVER') return;
    const lastCheck = Number(localStorage.getItem('casatv_last_update_check') || 0);
    const intervalMs = state.updateFrequency === 'DAILY' ? 86_400_000 : 7 * 86_400_000;
    if (lastCheck > 0 && Date.now() - lastCheck < intervalMs) return;
  }
  const current = WEB_APP_VERSION;
  try {
    const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`, {
      headers: { Accept: 'application/vnd.github+json' },
    });
    if (res.ok) {
      localStorage.setItem('casatv_last_update_check', String(Date.now()));
      const rel = await res.json();
      const latest = String(rel.tag_name || '').replace(/^[vV]/, '').trim();
      const assets = (rel.assets || []).map((a) => ({ name: a.name, url: a.browser_download_url }));
      const newer = compareVersions(latest, current) > 0;
      state.updateInfo = {
        latest,
        current,
        updateAvailable: newer,
        notes: rel.body || '',
        assets,
        source: 'GitHub Releases',
      };
      updateShellChrome();
      if (manual) {
        toast(
          newer
            ? t(`Nuova versione v${latest} disponibile su GitHub!`, `New version v${latest} available on GitHub!`)
            : t(`CasaTV è aggiornato all'ultima versione (${current}).`, `CasaTV is up to date (${current}).`),
        );
      }
      if (state.page === 'settings') renderCurrentScreen();
      return;
    }
  } catch (_) {}

  try {
    const lan = await api('/api/version');
    state.updateInfo = { ...lan, source: 'Server locale' };
    updateShellChrome();
    if (manual) {
      toast(
        lan.updateAvailable
          ? t(`Disponibile v${lan.latest} sul server`, `v${lan.latest} available on server`)
          : t(`Nessun aggiornamento trovato (${current}).`, `No updates found (${current}).`),
      );
    }
  } catch (_) {
    if (manual) toast(t('Impossibile contattare GitHub Releases', 'Unable to reach GitHub Releases'), 'err');
  }
}

function compareVersions(a, b) {
  const pa = String(a || '').split('.').map((x) => parseInt(x, 10) || 0);
  const pb = String(b || '').split('.').map((x) => parseInt(x, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d !== 0) return d;
  }
  return 0;
}

function renderSettingsScreen(root) {
  const p = state.activeProfile || {};
  const stats = state.config?.stats || {};
  const sec = state.settingsSection;
  const sections = [
    {
      id: 'profile',
      title: t('Profilo & Aspetto', 'Profile & Appearance', 'Perfil y Aspecto'),
      sub: t('Utente, lingua, tema e Multi-TV', 'User, language, theme & Multi-TV'),
      icon: `<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"></circle><path d="M5 20a7 7 0 0 1 14 0"></path></svg>`,
    },
    {
      id: 'sources',
      title: t('Sorgenti & Playlist', 'Sources & Playlists', 'Fuentes y Listas'),
      sub: t('Liste M3U, catalogo e file locali', 'M3U lists, catalog & local files'),
      icon: `<svg viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>`,
    },
    {
      id: 'server',
      title: t('Server & Sistema', 'Server & System', 'Servidor y Sistema'),
      sub: t('Rete, risorse e aggiornamenti', 'Network, resources & updates'),
      icon: `<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="7" rx="2"></rect><rect x="3" y="13" width="18" height="7" rx="2"></rect></svg>`,
    },
    {
      id: 'guide',
      title: t('Guida & Info', 'Guide & Info', 'Guía e Info'),
      sub: t('Comandi e versione app', 'Controls & app version'),
      icon: `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"></circle><path d="M12 16v-4M12 8h.01"></path></svg>`,
    },
  ];

  root.innerHTML = `
    <div class="settings-shell">
      <aside class="settings-sidebar">
        <button type="button" class="casa-btn ghost" id="btn-settings-back" style="width:100%;justify-content:flex-start">
          ← ${t('Torna alla TV', 'Back to TV', 'Volver a TV')}
        </button>
        <div class="settings-eyebrow">${t('IMPOSTAZIONI', 'SETTINGS', 'AJUSTES')}</div>
        <div class="settings-nav-list">
          ${sections
            .map(
              (s) => `
            <button type="button" class="settings-nav-item ${sec === s.id ? 'is-active' : ''}" data-sec="${s.id}">
              <span class="nav-item-icon">${s.icon}</span>
              <span class="nav-item-text">
                <strong>${esc(s.title)}</strong>
                <small>${esc(s.sub)}</small>
              </span>
            </button>
          `,
            )
            .join('')}
        </div>
        <div class="settings-server-footer">
          <span class="status-dot ${state.serverDisconnected ? 'warn' : 'on'}"></span>
          <div class="footer-meta">
            <strong>${esc(state.config?.name || 'CasaTV Server')}</strong>
            <small>${state.serverDisconnected ? t('Offline', 'Offline') : esc(state.serverUrl.replace(/^https?:\/\//, ''))}</small>
          </div>
          <span class="device-mode-chip" title="${t('Interfaccia adattata al dispositivo rilevato', 'UI optimized for detected device')}">${deviceShortBadge()}</span>
        </div>
      </aside>

      <div class="settings-detail" id="settings-detail-pane"></div>
    </div>
  `;

  $('#btn-settings-back')?.addEventListener('click', () => navigate('channels'));
  $$('[data-sec]', root).forEach((btn) => {
    btn.addEventListener('click', () => {
      state.settingsSection = btn.dataset.sec;
      renderSettingsScreen(root);
    });
  });

  const detail = $('#settings-detail-pane');
  if (sec === 'profile') renderSettingsProfile(detail, p);
  else if (sec === 'sources') renderSettingsSources(detail, stats);
  else if (sec === 'server') renderSettingsServer(detail);
  else renderSettingsGuide(detail);
}

function renderSettingsProfile(detail, p) {
  const isLight = state.theme === 'light';
  const lang = p.language || 'it';
  detail.innerHTML = `
    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Profilo e preferenze', 'Profile and preferences', 'Perfil y preferencias')}</h3>
        <p>${t('Lingua, paese e preferiti sono salvati per questo profilo.', 'Language, country and favorites stay saved for this profile.')}</p>
      </div>
      <div class="setting-row">
        <div style="display:flex;align-items:center;gap:12px">
          <div class="editor-avatar-preview" style="width:52px;height:52px;font-size:26px">${esc(p.avatar || '👤')}</div>
          <div>
            <strong style="font-size:17px">${esc(p.name || 'Ospite')}</strong>
            <div style="font-size:12px;color:var(--muted)">${countryFlag(p.country)} ${esc(p.countryName || p.country || 'IT')} · ${esc((lang).toUpperCase())}</div>
          </div>
        </div>
        <button type="button" class="casa-btn ghost" id="btn-set-switch-prof">${t('Cambia profilo', 'Switch profile', 'Cambiar perfil')}</button>
      </div>
      <div class="setting-row">
        <span class="setting-label">${t("LINGUA DELL'APP", 'APP LANGUAGE', 'IDIOMA')}</span>
        <div class="btn-row">
          ${[
            { code: 'it', name: 'Italiano' },
            { code: 'en', name: 'English' },
            { code: 'es', name: 'Español' },
          ]
            .map(
              (l) => `<button type="button" class="casa-btn sm ${lang === l.code ? 'primary is-solid' : 'ghost'}" data-lang="${l.code}">${l.name}</button>`,
            )
            .join('')}
        </div>
      </div>
      <div class="setting-row">
        <div>
          <span class="setting-label" style="display:block">${t('PAESE PREFERITO', 'PREFERRED COUNTRY', 'PAÍS PREFERIDO')}</span>
          <strong style="font-size:14px">${countryFlag(state.tvCountry)} ${esc(state.config?.facets?.countries?.find((c) => c.code === state.tvCountry)?.name || state.tvCountry)}</strong>
        </div>
        <button type="button" class="casa-btn ghost" id="btn-set-country">${t('Cambia paese', 'Change country', 'Cambiar país')}</button>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Tema', 'Theme', 'Tema')}</h3>
      </div>
      <div class="btn-row">
        <button type="button" class="casa-btn ${isLight ? 'primary is-solid' : 'ghost'}" id="btn-theme-light">${t('CHIARO', 'LIGHT', 'CLARO')}</button>
        <button type="button" class="casa-btn ${!isLight ? 'primary is-solid' : 'ghost'}" id="btn-theme-dark">${t('SCURO', 'DARK', 'OSCURO')}</button>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Sincronizzazione Multi-TV', 'Multi-TV Synchronization', 'Sincronización Multi-TV')}</h3>
        <p>${t('Allinea automaticamente la riproduzione quando più dispositivi in casa guardano lo stesso canale.', 'Automatically aligns playback when multiple devices watch the same channel.')}</p>
      </div>
      <div class="setting-row">
        <div>
          <strong style="color:${state.multiTvSync ? 'var(--accent)' : 'var(--muted)'};font-size:14px">
            ${state.multiTvSync ? t('Sincronizzazione automatica attiva', 'Automatic sync enabled') : t('Sincronizzazione disattivata', 'Sync disabled')}
          </strong>
          <div style="font-size:11.5px;color:var(--dim)">${deviceShortBadge()} · CasaTV Sync</div>
        </div>
        <button type="button" class="casa-btn ${!state.multiTvSync ? 'primary' : 'ghost'}" id="btn-toggle-sync">
          ${state.multiTvSync ? t('Disattiva', 'Disable', 'Desactivar') : t('Attiva', 'Enable', 'Activar')}
        </button>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Buffer anti-interruzione', 'Anti-freeze safety buffer', 'Búfer anti-cortes')}</h3>
        <p>${t('Crea gradualmente una riserva invisibile (~8s) e riconnette il canale in background se il flusso cade.', 'Gradually builds an invisible ~8s safety cushion and reconnects silently if the stream drops.', 'Crea gradualmente un colchón invisible (~8s) y reconecta el canal en segundo plano si se corta.')}</p>
      </div>
      <div class="setting-row">
        <div>
          <strong style="color:${state.liveSafetyBuffer ? 'var(--accent)' : 'var(--muted)'};font-size:14px">
            ${state.liveSafetyBuffer ? t('Protezione anti-blocco attiva', 'Anti-freeze protection enabled', 'Protección anti-cortes activa') : t('Riserva disattivata (solo bassa latenza)', 'Safety buffer disabled (low latency)', 'Búfer desactivado')}
          </strong>
          <div style="font-size:11.5px;color:var(--dim)">${t('Rallenta impercettibilmente all\'avvio fino a ~8s per assorbire cali di rete e rigenera il buffer dopo una riconnessione.', 'Imperceptibly paces playback up to ~8s so temporary network drops recover seamlessly.', 'Modula imperceptiblemente la reproducción hasta ~8s para absorber cortes temporales de red.')}</div>
        </div>
        <button type="button" class="casa-btn ${!state.liveSafetyBuffer ? 'primary' : 'ghost'}" id="btn-toggle-safety-buffer">
          ${state.liveSafetyBuffer ? t('Disattiva', 'Disable', 'Desactivar') : t('Attiva', 'Enable', 'Activar')}
        </button>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Canali solo audio in Mix', 'Audio-only channels in Mix', 'Canales solo audio en Mix')}</h3>
        <p>${t('Esclude di default radio e flussi senza video (es. FM) dallo zapping casuale Mix.', 'Excludes radio and video-less streams (e.g. FM) from Mix shuffle by default.', 'Excluye por defecto radios y flujos sin vídeo (ej. FM) del Mix aleatorio.')}</p>
      </div>
      <div class="setting-row">
        <div>
          <strong style="color:${state.mixIncludeAudioOnly ? 'var(--accent)' : 'var(--muted)'};font-size:14px">
            ${state.mixIncludeAudioOnly ? t('Inclusi nello zapping Mix', 'Included in Mix shuffle', 'Incluidos en el Mix') : t('Esclusi di default (solo canali con video)', 'Excluded by default (video channels only)', 'Excluidos por defecto (solo vídeo)')}
          </strong>
          <div style="font-size:11.5px;color:var(--dim)">${t('Attiva se vuoi includere le stazioni radio e i canali solo audio durante lo zapping Mix.', 'Enable to include radio stations and audio-only streams during Mix zapping.', 'Actívalo si deseas incluir emisoras de radio y flujos solo audio en Mix.')}</div>
        </div>
        <button type="button" class="casa-btn ${!state.mixIncludeAudioOnly ? 'primary' : 'ghost'}" id="btn-toggle-mix-audio">
          ${state.mixIncludeAudioOnly ? t('Disattiva', 'Disable', 'Desactivar') : t('Attiva', 'Enable', 'Activar')}
        </button>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Banner comandi telecomando', 'Remote controls hint banner', 'Banner de controles del mando')}</h3>
        <p>${t('Mostra in alto per 6 secondi le indicazioni dei tasti quando cambi canale.', 'Shows key hints at the top for 6 seconds when switching channels.', 'Muestra arriba durante 6 segundos los atajos de teclado al cambiar de canal.')}</p>
      </div>
      <div class="setting-row">
        <div>
          <strong style="color:${state.showRemoteHintsBanner ? 'var(--accent)' : 'var(--muted)'};font-size:14px">
            ${state.showRemoteHintsBanner ? t('Banner temporaneo attivo (6s)', 'Temporary banner enabled (6s)', 'Banner temporal activo (6s)') : t('Banner nascosto', 'Banner disabled', 'Banner desactivado')}
          </strong>
          <div style="font-size:11.5px;color:var(--dim)">${t('Se disattivato, resta visibile solo la barra inferiore essenziale con nome, paese e orari.', 'When disabled, only the essential bottom bar with channel, country and clocks stays visible.', 'Si está desactivado, solo queda visible la barra inferior con canal, país y relojes.')}</div>
        </div>
        <button type="button" class="casa-btn ${!state.showRemoteHintsBanner ? 'primary' : 'ghost'}" id="btn-toggle-hints-banner">
          ${state.showRemoteHintsBanner ? t('Disattiva', 'Disable', 'Desactivar') : t('Attiva', 'Enable', 'Activar')}
        </button>
      </div>
    </section>
  `;

  $('#btn-set-switch-prof')?.addEventListener('click', () => openProfileGate(true));
  $('#btn-set-country')?.addEventListener('click', openCountryModal);
  $('#btn-theme-light')?.addEventListener('click', () => {
    applyTheme('light');
    renderCurrentScreen();
  });
  $('#btn-theme-dark')?.addEventListener('click', () => {
    applyTheme('dark');
    renderCurrentScreen();
  });
  $('#btn-toggle-sync')?.addEventListener('click', () => {
    state.multiTvSync = !state.multiTvSync;
    localStorage.setItem('casatv_multi_tv_sync', String(state.multiTvSync));
    renderCurrentScreen();
  });
  $('#btn-toggle-safety-buffer')?.addEventListener('click', () => {
    state.liveSafetyBuffer = !state.liveSafetyBuffer;
    localStorage.setItem('casatv_live_safety_buffer', String(state.liveSafetyBuffer));
    const v = $('#preview-video');
    if (v && !state.liveSafetyBuffer) {
      v.playbackRate = 1.0;
    }
    renderCurrentScreen();
  });
  $('#btn-toggle-mix-audio')?.addEventListener('click', () => {
    state.mixIncludeAudioOnly = !state.mixIncludeAudioOnly;
    localStorage.setItem('casatv_mix_include_audio_only', String(state.mixIncludeAudioOnly));
    state.verifiedNextMix = null;
    renderCurrentScreen();
  });
  $('#btn-toggle-hints-banner')?.addEventListener('click', () => {
    state.showRemoteHintsBanner = !state.showRemoteHintsBanner;
    localStorage.setItem('casatv_show_remote_hints', String(state.showRemoteHintsBanner));
    if (!state.showRemoteHintsBanner) hideTemporaryHintsBanner();
    renderCurrentScreen();
  });

  $$('[data-lang]', detail).forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!state.activeProfile?.id) return;
      try {
        const updated = await api(`/api/profiles/${encodeURIComponent(state.activeProfile.id)}`, {
          method: 'PATCH',
          body: { language: btn.dataset.lang },
        });
        state.activeProfile = updated;
        await loadServerConfig().catch(() => {});
        updateShellChrome();
        renderCurrentScreen();
      } catch (err) {
        toast(err.message, 'err');
      }
    });
  });
}

function renderSettingsSources(detail, stats) {
  const localCount = state.localOverrideChannels?.length || 0;
  const localHasStatus = Boolean(state.localSourceLabel || state.localSourceError || localCount > 0);
  const localIsError = Boolean(state.localSourceError);

  detail.innerHTML = `
    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Playlist canali M3U', 'M3U Channel Playlists', 'Listas M3U')}</h3>
        <p>${t('Carica file M3U, M3U8 o PLS oppure aggiungi un link remoto sul server.', 'Upload M3U, M3U8 or PLS files or add a remote URL on the server.')}</p>
      </div>
      <div class="setting-row">
        <strong id="playlists-count-label">${t('Caricamento playlist…', 'Loading playlists…')}</strong>
        <div class="btn-row">
          <label class="casa-btn primary sm" style="cursor:pointer" id="lbl-upload-server-m3u">
            ${t('Carica file (.M3U / .M3U8 / .PLS)', 'Upload file (.M3U / .M3U8 / .PLS)')}
            <input type="file" id="server-m3u-file" accept=".m3u,.m3u8,.pls,.txt,audio/x-mpegurl,application/vnd.apple.mpegurl,application/x-mpegurl,text/plain" multiple hidden />
          </label>
          <button type="button" class="casa-btn ghost sm" id="btn-add-playlist">+ ${t('Aggiungi Link', 'Add Link')}</button>
          <a class="casa-btn ghost sm" href="/api/playlist/export.m3u" download="casatv-server.m3u">${t('Esporta .M3U', 'Export .M3U')}</a>
        </div>
      </div>
      <div id="add-playlist-box" hidden style="display:flex;flex-direction:column;gap:8px;padding:12px;border-radius:12px;background:var(--bg);border:1px solid var(--border)">
        <input type="text" class="casa-input" id="new-pl-name" placeholder="${t('Nome playlist (es. Canali Italia)', 'Playlist name')}" />
        <input type="url" class="casa-input" id="new-pl-url" placeholder="https://..." />
        <div class="btn-row">
          <button type="button" class="casa-btn primary sm" id="btn-save-new-pl">${t('Salva e Scarica', 'Save & Download')}</button>
          <button type="button" class="casa-btn ghost sm" id="btn-cancel-new-pl">${t('Annulla', 'Cancel')}</button>
        </div>
      </div>
      <div id="playlists-rows" style="display:flex;flex-direction:column;gap:8px"></div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Panoramica Catalogo', 'Catalog Overview', 'Resumen del Catálogo')}</h3>
        <p>${t('Canali e statistiche attualmente disponibili sul server CasaTV.', 'Channels and stats currently available on the CasaTV server.')}</p>
      </div>
      <div class="stats-grid">
        <div class="stat-cell"><small>${t('Canali', 'Channels')}</small><strong>${fmtNum(stats.channels || 0)}</strong></div>
        <div class="stat-cell"><small>${t('Flussi', 'Streams')}</small><strong>${fmtNum(stats.streams || 0)}</strong></div>
        <div class="stat-cell"><small>Online</small><strong class="accent">${fmtNum(stats.onlineStreams || 0)}</strong></div>
        <div class="stat-cell"><small>${t('Paesi', 'Countries')}</small><strong>${fmtNum(stats.countries || 0)}</strong></div>
        <div class="stat-cell"><small>${t('Preferiti', 'Favorites')}</small><strong>${fmtNum(stats.favorites || 0)}</strong></div>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Lista M3U locale (sul browser)', 'Local M3U Playlist (in browser)')}</h3>
        <p>${t('Carica un file (.m3u, .m3u8, .pls, .txt) o URL solo su questo schermo senza modificare il server.', 'Load a file (.m3u, .m3u8, .pls, .txt) or URL only on this browser without altering the server.')}</p>
      </div>
      ${localHasStatus ? `
      <div class="local-status-box ${localIsError ? 'is-error' : ''}">
        <div class="playlist-row-meta">
          <div class="playlist-title-line">
            <strong>${esc(state.localSourceLabel || t('Lista locale', 'Local list'))}</strong>
            <span class="playlist-status-pill ${localIsError ? 'error' : 'ok'}">
              <span class="playlist-status-dot"></span>
              ${localIsError ? t('Non funziona', 'Not working') : `${fmtNum(localCount)} ${t('canali', 'channels')}`}
            </span>
          </div>
          ${localIsError ? `<div class="playlist-error-msg">${esc(state.localSourceError)}</div>` : ''}
        </div>
        <button type="button" class="casa-btn danger sm" id="btn-clear-local-m3u">${t('Rimuovi', 'Remove')}</button>
      </div>
      ` : ''}
      <div class="field-row">
        <input type="url" class="casa-input" id="local-m3u-url" placeholder="https://..." />
        <button type="button" class="casa-btn primary sm" id="btn-load-local-m3u-url">${t('Carica URL', 'Load URL')}</button>
      </div>
      <div class="btn-row">
        <label class="casa-btn ghost sm" style="cursor:pointer">
          ${t('Scegli file .M3U / .M3U8 / .PLS', 'Pick .M3U / .M3U8 / .PLS file')}
          <input type="file" id="local-m3u-file" accept=".m3u,.m3u8,.pls,.txt,audio/x-mpegurl,application/vnd.apple.mpegurl,application/x-mpegurl,text/plain" hidden />
        </label>
      </div>
    </section>
  `;

  $('#btn-add-playlist')?.addEventListener('click', () => {
    $('#add-playlist-box').hidden = false;
  });
  $('#btn-cancel-new-pl')?.addEventListener('click', () => {
    $('#add-playlist-box').hidden = true;
  });
  $('#btn-save-new-pl')?.addEventListener('click', async () => {
    const name = $('#new-pl-name').value.trim();
    const url = $('#new-pl-url').value.trim();
    if (!url) return toast(t('Inserisci un URL valido', 'Enter a valid URL'), 'err');
    try {
      const resp = await api('/api/playlists', { method: 'POST', body: { name, url } });
      $('#add-playlist-box').hidden = true;
      const pl = resp.playlist;
      if (pl?.status === 'error' || !pl?.channelCount) {
        toast(`${pl?.name || 'Playlist'}: ${pl?.lastError || t('non funziona', 'not working')}`, 'err');
      } else {
        toast(t('Playlist importata!', 'Playlist imported!'));
      }
      await loadServerConfig();
      await loadChannels({ reset: true });
      drawPlaylists();
    } catch (err) {
      toast(err.message, 'err');
    }
  });

  $('#server-m3u-file')?.addEventListener('change', async (e) => {
    const files = [...(e.target.files || [])];
    if (!files.length) return;
    toast(`${t('Caricamento di', 'Uploading')} ${files.length} file…`);
    let hasError = false;
    for (const file of files) {
      try {
        const fd = new FormData();
        fd.append('name', file.name);
        fd.append('file', file, file.name);
        const r = await fetch(`${state.serverUrl}/api/playlists/upload?name=${encodeURIComponent(file.name)}`, {
          method: 'POST',
          body: fd,
        });
        const data = await r.json().catch(() => ({}));
        if (!r.ok) {
          hasError = true;
          toast(`${file.name}: ${data.error || `HTTP ${r.status}`}`, 'err');
        } else if (data.playlist?.status === 'error' || !data.playlist?.channelCount) {
          hasError = true;
          toast(`${file.name}: ${data.playlist?.lastError || t('Non funziona', 'Not working')}`, 'err');
        }
      } catch (err) {
        hasError = true;
        toast(`${file.name}: ${err.message}`, 'err');
      }
    }
    e.target.value = '';
    if (!hasError) toast(t('File playlist caricati!', 'Playlist files uploaded!'));
    await loadServerConfig();
    await loadChannels({ reset: true });
    drawPlaylists();
  });

  $('#btn-clear-local-m3u')?.addEventListener('click', () => {
    state.localOverrideChannels = null;
    state.localSourceLabel = '';
    state.localSourceError = '';
    toast(t('Lista locale rimossa', 'Local playlist removed'));
    loadChannels({ reset: true });
    renderCurrentScreen();
  });

  $('#btn-load-local-m3u-url')?.addEventListener('click', async () => {
    const url = $('#local-m3u-url').value.trim();
    if (!/^https?:\/\//i.test(url)) return toast(t('Inserisci un URL http:// o https://', 'Invalid URL'), 'err');
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      applyLocalM3u(text, url);
    } catch (err) {
      state.localOverrideChannels = null;
      state.localSourceLabel = url;
      state.localSourceError = err.message || t('Errore caricamento URL', 'URL load error');
      toast(`${t('Errore caricamento', 'Load error')}: ${state.localSourceError}`, 'err');
      renderCurrentScreen();
    }
  });

  $('#local-m3u-file')?.addEventListener('change', async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    e.target.value = '';
    applyLocalM3u(text, file.name);
  });

  async function drawPlaylists() {
    try {
      const res = await api('/api/playlists');
      const items = res.playlists || [];
      $('#playlists-count-label').textContent = `${items.length} ${t('playlist configurate', 'configured playlists')}`;
      const rowsEl = $('#playlists-rows');
      rowsEl.innerHTML = items.length
        ? items
            .map((pl) => {
              const isBroken = pl.status === 'error' || (pl.channelCount || 0) === 0;
              const isWarn = !isBroken && pl.status === 'warn';
              const rowClass = isBroken ? 'is-error' : isWarn ? 'is-warn' : '';
              const pillClass = isBroken ? 'error' : isWarn ? 'warn' : 'ok';
              const statusText = isBroken
                ? t('Non funziona', 'Not working', 'No funciona')
                : isWarn
                  ? t('Avviso', 'Warning', 'Aviso')
                  : t('Attiva', 'Active', 'Activa');
              const srcTag = pl.url ? 'LINK' : 'FILE';
              const errMsg = pl.lastError || (isBroken ? t('Playlist non valida o senza canali funzionanti', 'Invalid playlist or no working channels') : '');
              return `
          <div class="playlist-row ${rowClass}">
            <div class="playlist-row-meta">
              <div class="playlist-title-line">
                <strong>${esc(pl.name || pl.url || 'Playlist')}</strong>
                <span class="playlist-status-pill ${pillClass}"><span class="playlist-status-dot"></span>${statusText}</span>
                <span class="playlist-source-badge">${srcTag}</span>
              </div>
              <small>${fmtNum(pl.channelCount || 0)} ${t('canali', 'channels')}${pl.url ? ` · ${esc(pl.url)}` : ` · ${t('File caricato', 'Uploaded file')}`}</small>
              ${errMsg ? `<div class="playlist-error-msg">${esc(errMsg)}</div>` : ''}
            </div>
            <div class="btn-row">
              <button type="button" class="casa-btn ghost sm" data-pl-check="${esc(pl.id)}">${t('Verifica', 'Check')}</button>
              ${pl.url ? `<button type="button" class="casa-btn ghost sm" data-pl-refresh="${esc(pl.id)}">${t('Aggiorna', 'Refresh')}</button>` : ''}
              <button type="button" class="casa-btn danger sm" data-pl-del="${esc(pl.id)}">${t('Elimina', 'Delete')}</button>
            </div>
          </div>
        `;
            })
            .join('')
        : `<p style="margin:0;color:var(--muted);font-size:12.5px">${t('Nessuna playlist salvata.', 'No saved playlists.')}</p>`;

      $$('[data-pl-check]', rowsEl).forEach((b) =>
        b.addEventListener('click', async () => {
          b.disabled = true;
          try {
            const resp = await api(`/api/playlists/${encodeURIComponent(b.dataset.plCheck)}/check`, { method: 'POST' });
            const pl = resp.playlist;
            if (pl?.status === 'error') {
              toast(`${pl.name}: ${pl.lastError || t('non funziona', 'not working')}`, 'err');
            } else if (pl?.status === 'warn') {
              toast(`${pl.name}: ${pl.lastError || t('avviso', 'warning')}`);
            } else {
              toast(`${pl?.name || 'Playlist'}: ${t('attiva', 'working')}!`);
            }
            await loadServerConfig();
            drawPlaylists();
          } catch (err) {
            toast(err.message, 'err');
            b.disabled = false;
          }
        }),
      );
      $$('[data-pl-refresh]', rowsEl).forEach((b) =>
        b.addEventListener('click', async () => {
          b.disabled = true;
          try {
            const resp = await api(`/api/playlists/${encodeURIComponent(b.dataset.plRefresh)}/refresh`, { method: 'POST' });
            const pl = resp.playlist;
            if (pl?.status === 'error') {
              toast(`${pl.name}: ${pl.lastError || t('errore', 'error')}`, 'err');
            } else if (pl?.status === 'warn') {
              toast(`${pl.name}: ${pl.lastError || t('avviso', 'warning')}`);
            } else {
              toast(t('Playlist aggiornata', 'Playlist refreshed'));
            }
            await loadServerConfig();
            await loadChannels({ reset: true });
            drawPlaylists();
          } catch (err) {
            toast(err.message, 'err');
            b.disabled = false;
          }
        }),
      );
      $$('[data-pl-del]', rowsEl).forEach((b) =>
        b.addEventListener('click', async () => {
          try {
            await api(`/api/playlists/${encodeURIComponent(b.dataset.plDel)}`, { method: 'DELETE' });
            toast(t('Playlist eliminata', 'Playlist deleted'));
            await loadServerConfig();
            await loadChannels({ reset: true });
            drawPlaylists();
          } catch (err) {
            toast(err.message, 'err');
          }
        }),
      );
    } catch (_) {
      $('#playlists-count-label').textContent = t('Server non raggiungibile', 'Server unreachable');
    }
  }
  drawPlaylists();
}

function applyLocalM3u(text, label) {
  const parsed = parseM3uText(text);
  if (!parsed.length) {
    state.localOverrideChannels = null;
    state.localSourceLabel = label;
    state.localSourceError = t(
      'File non valido o nessun canale IPTV (#EXTINF / PLS) trovato',
      'Invalid file or no valid IPTV channels (#EXTINF / PLS) found',
    );
    toast(`${label}: ${state.localSourceError}`, 'err');
    renderCurrentScreen();
    return;
  }
  state.localOverrideChannels = parsed;
  state.localSourceLabel = label;
  state.localSourceError = '';
  toast(`${t('Caricati', 'Loaded')} ${parsed.length} ${t('canali in locale', 'local channels')}`);
  loadChannels({ reset: true });
  navigate('channels');
}

function renderSettingsServer(detail) {
  const sys = state.system;
  const res = sys?.resources || {};
  const cpu = Math.round(res.cpu?.percent || 0);
  const ram = Math.round(res.memory?.percent || 0);
  const disk = Math.round(res.disk?.percent || 0);
  const devices = sys?.devices || [];
  const urls = state.config?.urls || [];

  detail.innerHTML = `
    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Server collegato', 'Connected server', 'Servidor conectado')}</h3>
        <p>${t('CasaTV è raggiungibile sulla rete locale.', 'CasaTV is reachable on the local network.')}</p>
      </div>
      <div class="setting-row">
        <div>
          <strong style="font-size:17px">${esc(state.config?.name || 'CasaTV')}</strong>
          <div style="font-family:var(--mono);font-size:12px;color:var(--muted)">${esc(state.serverUrl)}</div>
        </div>
        <div style="text-align:right">
          <span class="setting-label" style="display:block">${t('VERSIONE SERVER', 'SERVER VERSION')}</span>
          <strong>${esc(state.config?.version || '—')}</strong>
        </div>
      </div>
      ${
        urls.length
          ? `<div style="display:flex;flex-direction:column;gap:4px">${urls
              .map((u) => `<div style="font-size:11.5px;color:var(--muted);font-family:var(--mono)"><span style="color:var(--dim)">${esc(u.name || 'net')}:</span> ${esc(u.url)}</div>`)
              .join('')}</div>`
          : ''
      }
      <div class="btn-row">
        <button type="button" class="casa-btn primary sm" id="btn-open-server-picker">${t('Cambia server', 'Change server')}</button>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Aggiornamenti & Azioni Server', 'Updates & Server Actions', 'Actualizaciones y Acciones')}</h3>
        <p>${t(`Cerca nuove versioni su GitHub Releases (${GITHUB_REPO}) o sincronizza il server.`, `Check for new releases on GitHub (${GITHUB_REPO}) or sync the server.`)}</p>
      </div>
      <div class="btn-row">
        <button type="button" class="casa-btn ghost sm" id="btn-srv-reload">${t('Ricarica dati', 'Reload data')}</button>
        <button type="button" class="casa-btn ghost sm" id="btn-srv-refresh">${t('Aggiorna catalogo', 'Update catalog')}</button>
        <button type="button" class="casa-btn primary is-solid sm" id="btn-srv-updates">${t('Verifica aggiornamenti GitHub', 'Check GitHub updates')}</button>
      </div>
      ${
        state.updateInfo?.assets?.length
          ? `<div style="display:flex;flex-wrap:wrap;gap:8px;padding:10px 12px;border-radius:12px;background:var(--bg);border:1px solid var(--border)">
              <strong style="width:100%;font-size:12px;color:var(--accent)">GitHub Release v${esc(state.updateInfo.latest)}:</strong>
              ${state.updateInfo.assets
                .map((a) => `<a class="casa-btn ghost sm" href="${esc(a.url)}" target="_blank" rel="noopener">⬇ ${esc(a.name)}</a>`)
                .join('')}
            </div>`
          : ''
      }
      <div>
        <span class="setting-label" style="display:block;margin-bottom:8px">${t('FREQUENZA CONTROLLO AGGIORNAMENTI', 'UPDATE CHECK FREQUENCY')}</span>
        <div class="btn-row">
          ${[
            { id: 'DAILY', label: t('Ogni giorno', 'Daily') },
            { id: 'WEEKLY', label: t('Ogni settimana', 'Weekly') },
            { id: 'MONTHLY', label: t('Ogni mese', 'Monthly') },
          ]
            .map(
              (f) => `<button type="button" class="casa-btn sm ${state.updateFrequency === f.id ? 'primary is-solid' : 'ghost'}" data-freq="${f.id}">${f.label}</button>`,
            )
            .join('')}
        </div>
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Stato del sistema', 'System status', 'Estado del sistema')}</h3>
        <p>${t('Connessione, risorse e dispositivi collegati in tempo reale.', 'Live connection, resources, and connected devices.')}</p>
      </div>
      <div class="resource-row"><span class="res-name">CPU</span><div class="resource-track"><div class="resource-fill ${cpu >= 90 ? 'crit' : cpu >= 75 ? 'warn' : ''}" style="width:${cpu}%"></div></div><span class="resource-pct">${cpu}%</span></div>
      <div class="resource-row"><span class="res-name">RAM</span><div class="resource-track"><div class="resource-fill ${ram >= 90 ? 'crit' : ram >= 75 ? 'warn' : ''}" style="width:${ram}%"></div></div><span class="resource-pct">${ram}%</span></div>
      <div class="resource-row"><span class="res-name">${t('DISCO', 'DISK')}</span><div class="resource-track"><div class="resource-fill ${disk >= 90 ? 'crit' : disk >= 75 ? 'warn' : ''}" style="width:${disk}%"></div></div><span class="resource-pct">${disk}%</span></div>
      <span class="setting-label">${t('DISPOSITIVI CONNESSI', 'CONNECTED DEVICES')} (${devices.length})</span>
      <div style="display:flex;flex-direction:column;gap:6px">
        ${
          devices.length
            ? devices
                .slice(0, 6)
                .map(
                  (d) => `
              <div class="device-row">
                <div class="device-row-main">
                  <span class="status-dot ${d.state === 'playing' ? 'on' : ''}"></span>
                  <div>
                    <strong>${esc(d.name || d.platform)}</strong>
                    <small>${esc(d.platform || '')}${d.ip ? ` · ${esc(d.ip)}` : ''}</small>
                  </div>
                </div>
                <span style="font-size:11.5px;color:${d.state === 'playing' ? 'var(--accent)' : 'var(--dim)'}">${d.state === 'playing' && d.channel ? `▶ ${esc(d.channel)}` : t('inattivo', 'idle')}</span>
              </div>
            `,
                )
                .join('')
            : `<p style="margin:0;color:var(--dim);font-size:12px">${t('Nessun dispositivo rilevato.', 'No devices detected.')}</p>`
        }
      </div>
    </section>
  `;

  $('#btn-open-server-picker')?.addEventListener('click', () => {
    $('#manual-server-input').value = state.serverUrl;
    $('#server-modal').hidden = false;
  });
  $('#btn-srv-reload')?.addEventListener('click', async () => {
    await loadServerConfig();
    await loadChannels({ reset: true });
    await refreshSystemStatus();
    toast(t('Dati del server aggiornati', 'Server data refreshed'));
  });
  $('#btn-srv-refresh')?.addEventListener('click', async () => {
    await api('/api/refresh', { method: 'POST' });
    toast(t('Aggiornamento del catalogo avviato', 'Catalog update started'));
  });
  $('#btn-srv-updates')?.addEventListener('click', () => checkGitHubUpdates(true));
  $$('[data-freq]', detail).forEach((b) =>
    b.addEventListener('click', () => {
      state.updateFrequency = b.dataset.freq;
      localStorage.setItem('casatv_update_freq', state.updateFrequency);
      renderCurrentScreen();
    }),
  );
}

function renderSettingsGuide(detail) {
  const isTouch = state.deviceType !== 'pc';
  const rows = isTouch
    ? [
        [t('Tap Canale', 'Tap Channel'), t('Avvia subito la riproduzione a schermo intero o in finestra', 'Starts playback immediately')],
        ['☰ ' + t('Lista', 'Drawer'), t('Apre il menu laterale dei canali senza interrompere la visione', 'Opens the quick channel drawer')],
        ['PiP', t('Picture-in-Picture per continuare a guardare in finestra mobile', 'Floating Picture-in-Picture window')],
        ['Mix', t('Mappamondo interattivo multi-regione con zapping casuale verificato', 'Interactive World Map with verified Mix zapping')],
      ]
    : [
        ['◀', t('Apre il menù rapido canali laterale con anteprima live', 'Opens quick left channel drawer with live preview')],
        ['▲ ▼', t('Cambia canale (o scorre il menù laterale)', 'Changes channel (or browses left drawer)')],
        ['0 – 9', t('Digita direttamente il numero del canale', 'Direct channel number tuning')],
        ['M / F', t('Aggiunge o rimuove il canale dai Preferiti', 'Toggles channel Favorite')],
        ['P', t('Attiva o disattiva Picture-in-Picture', 'Toggles Picture-in-Picture')],
        ['Esc', t('Chiude il menù aperto o esce dal player', 'Closes drawer or exits player')],
      ];

  detail.innerHTML = `
    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Guida ai Comandi', 'Controls Guide', 'Guía de Controles')}</h3>
        <p>${deviceShortBadge()} · ${t('Interfaccia adattata automaticamente a questo schermo.', 'Interface automatically optimized for this screen.')}</p>
      </div>
      ${rows
        .map(
          ([k, act]) => `<div class="remote-guide-row"><span class="remote-key">${esc(k)}</span><span class="remote-action">${esc(act)}</span></div>`,
        )
        .join('')}
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Informazioni su CasaTV', 'About CasaTV', 'Acerca de CasaTV')}</h3>
      </div>
      <div class="setting-row">
        <div>
          <strong>CasaTV Web App (${deviceShortBadge()})</strong>
          <div style="font-size:12px;color:var(--muted)">${t('Versione Web', 'Web version')} ${WEB_APP_VERSION} · Server ${esc(state.config?.version || '—')}</div>
          ${
            state.updateInfo?.updateAvailable
              ? `<div style="margin-top:4px;font-size:12px;font-weight:700;color:var(--amber)">${t(`Aggiornamento disponibile su GitHub: v${esc(state.updateInfo.latest)}`, `GitHub update ready: v${esc(state.updateInfo.latest)}`)}</div>`
              : ''
          }
        </div>
        <div style="text-align:right;font-size:11px;color:var(--dim)">
          <div>${t('Playlist locali · Streaming LAN', 'Local playlists · LAN streaming', 'Listas locales · Streaming LAN')}</div>
          <div>GitHub Releases · ${GITHUB_REPO}</div>
        </div>
      </div>
    </section>
  `;
}

/* ---------------- Global Event Bindings ---------------- */
function bindGlobalEvents() {
  $$('.subtab-btn[data-nav], .bottom-nav-item[data-nav], #topbar-settings-btn').forEach((btn) => {
    btn.addEventListener('click', () => navigate(btn.dataset.nav));
  });

  $('#btn-open-mix')?.addEventListener('click', () => openMixRegionPicker({ resetSession: true }));
  $('#bottom-nav-mix')?.addEventListener('click', () => openMixRegionPicker({ resetSession: true }));
  $('#topbar-country-btn')?.addEventListener('click', openCountryModal);
  $('#topbar-profile-btn')?.addEventListener('click', () => openProfileGate(true));
  $('#topbar-update-badge')?.addEventListener('click', () => {
    state.settingsSection = 'server';
    navigate('settings');
  });

  const sInput = $('#global-search-input');
  sInput?.addEventListener('input', (e) => performGlobalSearch(e.target.value));
  $('#global-search-clear')?.addEventListener('click', () => {
    if (sInput) sInput.value = '';
    performGlobalSearch('');
  });

  // Mix Mappamondo buttons, filter overlay & close handlers
  $('#mix-btn-all-world')?.addEventListener('click', () => toggleMixRegionSelection('ALL_WORLD'));
  $('#mix-btn-filters')?.addEventListener('click', openMixFiltersOverlay);
  $('#mix-filters-clear')?.addEventListener('click', clearMixFilters);
  $('#mix-filters-apply')?.addEventListener('click', closeMixFiltersOverlay);
  $('#mix-tab-langs')?.addEventListener('click', () => {
    state.mixFilterTab = 'languages';
    renderMixFiltersUI();
  });
  $('#mix-tab-genres')?.addEventListener('click', () => {
    state.mixFilterTab = 'genres';
    renderMixFiltersUI();
  });
  $('#mix-filters-overlay')?.addEventListener('click', (e) => {
    if (e.target === $('#mix-filters-overlay')) closeMixFiltersOverlay();
  });
  $('#mix-btn-start')?.addEventListener('click', startMixPlayback);
  $$('[data-close-mix-modal]').forEach((el) =>
    el.addEventListener('click', () => {
      $('#mix-modal').hidden = true;
    }),
  );

  // Country & Server modal close
  $$('[data-close-country-modal]').forEach((el) =>
    el.addEventListener('click', () => {
      $('#country-modal').hidden = true;
    }),
  );
  $$('[data-close-profile-modal]').forEach((el) => el.addEventListener('click', closeProfileGate));
  $$('[data-close-server-modal]').forEach((el) =>
    el.addEventListener('click', () => {
      $('#server-modal').hidden = true;
    }),
  );
  $('#btn-connect-manual-server')?.addEventListener('click', async () => {
    const raw = $('#manual-server-input').value.trim().replace(/\/+$/, '');
    if (!/^https?:\/\//i.test(raw)) return toast(t('Inserisci un URL valido', 'Enter a valid URL'), 'err');
    state.serverUrl = raw;
    $('#server-modal').hidden = true;
    await bootData();
  });

  // Player controls
  $('#player-close-btn')?.addEventListener('click', closePlayer);
  $('#player-drawer-toggle')?.addEventListener('click', () => togglePlayerDrawer());
  $('#drawer-close-btn')?.addEventListener('click', () => togglePlayerDrawer(false));
  $('#player-mix-regions-btn')?.addEventListener('click', () => {
    const keepRegions = new Set(state.selectedMixRegions);
    const keepLanguages = new Set(state.selectedMixLanguages);
    const keepGenres = new Set(state.selectedMixGenres);
    closePlayer();
    state.selectedMixRegions = keepRegions;
    state.selectedMixLanguages = keepLanguages;
    state.selectedMixGenres = keepGenres;
    openMixRegionPicker({ resetSession: false });
  });
  $('#player-prev-ch')?.addEventListener('click', () => stepNormalChannel(-1));
  $('#player-next-ch')?.addEventListener('click', () => stepNormalChannel(1));
  $('#player-next-ch-btn')?.addEventListener('click', () => stepNormalChannel(1));
  $('#player-fav-btn')?.addEventListener('click', () => toggleFavorite(state.playerChannel));
  $('#player-retry-btn')?.addEventListener('click', () => tunePlayerChannel(state.playerChannel));
  $('#player-next-src-btn')?.addEventListener('click', () => {
    if (state.playerSources.length > 1) {
      state.playerSourceIdx = (state.playerSourceIdx + 1) % state.playerSources.length;
      const src = state.playerSources[state.playerSourceIdx];
      tunePlayerChannel({ ...state.playerChannel, streamUrl: src.url }, { preserveSourceIdx: true });
    }
  });
  $('#player-mute-btn')?.addEventListener('click', () => {
    const v = $('#preview-video');
    v.muted = !v.muted;
    $('#player-mute-btn').textContent = v.muted ? '🔇' : '🔊';
  });
  $('#player-fullscreen-btn')?.addEventListener('click', () => {
    const stage = $('#player-stage');
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    else stage.requestFullscreen?.().catch(() => $('#preview-video')?.webkitEnterFullscreen?.());
  });
  $('#player-pip-btn')?.addEventListener('click', async () => {
    const v = $('#preview-video');
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await v.requestPictureInPicture();
    } catch (err) {
      toast(err.message, 'err');
    }
  });
  $('#player-stage')?.addEventListener('mousemove', wakePlayerControls);
  $('#player-stage')?.addEventListener('touchstart', wakePlayerControls, { passive: true });

  // Keyboard shortcuts across Player & Modals
  window.addEventListener('keydown', (e) => {
    if (state.playerOpen) {
      wakePlayerControls();
      if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        if (state.drawerOpen) togglePlayerDrawer(false);
        else closePlayer();
        return;
      }
      if (state.drawerOpen) {
        if (e.key === 'ArrowRight') {
          e.preventDefault();
          togglePlayerDrawer(false);
        } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault();
          const maxIdx = Math.max(0, state.playerQueue.length - 1);
          const next = Math.max(0, Math.min(maxIdx, state.drawerCursorIdx + (e.key === 'ArrowDown' ? 1 : -1)));
          $(`.drawer-item[data-d-idx="${next}"]`)?.focus();
          $(`.drawer-item[data-d-idx="${next}"]`)?.scrollIntoView({ block: 'nearest' });
        }
        return;
      }
      if (e.key === 'ArrowLeft') {
        e.preventDefault();
        if (!state.playerIsMix) togglePlayerDrawer(true);
        else stepNormalChannel(-1);
      } else if (e.key === 'ArrowRight' && state.playerIsMix) {
        e.preventDefault();
        stepNormalChannel(1);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        stepNormalChannel(1);
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        stepNormalChannel(-1);
      } else if (/^[0-9]$/.test(e.key)) {
        pushNumberDigit(e.key);
      } else if (e.key.toLowerCase() === 'm' || e.key.toLowerCase() === 'f') {
        toggleFavorite(state.playerChannel);
      } else if (e.key.toLowerCase() === 'p') {
        $('#player-pip-btn')?.click();
      }
      return;
    }

    if (e.key === 'Escape') {
      if (!$('#mix-filters-overlay')?.hidden) closeMixFiltersOverlay();
      else if (!$('#mix-modal').hidden) $('#mix-modal').hidden = true;
      else if (!$('#country-modal').hidden) $('#country-modal').hidden = true;
      else if (!$('#server-modal').hidden) $('#server-modal').hidden = true;
      else if (!$('#profile-modal').hidden && state.activeProfile) closeProfileGate();
    }
  });
}

/* ---------------- App Boot ---------------- */
async function bootData() {
  try {
    await loadServerConfig();
    await loadProfiles();
    updateShellChrome();
    await Promise.all([loadChannels({ reset: true }), loadFavorites()]);
    await refreshSystemStatus();
    if (state.systemPollTimer) clearInterval(state.systemPollTimer);
    state.systemPollTimer = setInterval(() => {
      if (state.page === 'settings') refreshSystemStatus();
    }, 15000);
    checkGitHubUpdates(false);
    if (!sessionStorage.getItem('casatv_profile_picked') && state.profiles.length > 0) {
      openProfileGate(false);
    }
  } catch (err) {
    state.serverDisconnected = true;
    updateShellChrome();
    $('#app').innerHTML = `
      <div class="empty-box">
        <h3>${t('Impossibile connettersi al server CasaTV', 'Unable to connect to CasaTV server')}</h3>
        <p>${esc(err.message)}</p>
        <button type="button" class="casa-btn primary" onclick="location.reload()">${t('Riprova', 'Retry')}</button>
      </div>
    `;
  }
}

bindGlobalEvents();
const initialHash = window.location.hash.replace(/^#\/?/, '');
if (initialHash === 'favorites' || initialHash === 'settings') state.page = initialHash;
updateShellChrome();
bootData();
