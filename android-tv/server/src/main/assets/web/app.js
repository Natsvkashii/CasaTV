/* CasaTV Web App — Unified PC, Tablet & Smartphone Client matching Android TV & Mobile */

import { WORLD_MAP_GRATICULE, WORLD_MAP_REGIONS } from './world-map-data.js?v=26.11.025';

const GITHUB_REPO = 'Natsvkashii/CasaTV';
const WEB_APP_VERSION = '26.11.061';
const LOW_RAM_WEB =
  typeof navigator !== 'undefined' &&
  Number(navigator.deviceMemory || 0) > 0 &&
  Number(navigator.deviceMemory) <= 4;
const CHANNEL_PAGE_SIZE = LOW_RAM_WEB ? 36 : 48;
const PROFILE_AVATARS = [
  'blue', 'emerald', 'violet', 'amber', 'rose', 'cyan',
  'indigo', 'teal', 'orange', 'pink', 'slate', 'lime',
];
const AVATAR_COLORS = {
  blue: '#2563EB', emerald: '#059669', violet: '#7C3AED', amber: '#D97706',
  rose: '#E11D48', cyan: '#0891B2', indigo: '#4F46E5', teal: '#0D9488',
  orange: '#EA580C', pink: '#DB2777', slate: '#475569', lime: '#65A30D',
};

const SVG_ICONS = {
  play: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><polygon points="6,4 20,12 6,20" fill="currentColor" stroke="none"/></svg>`,
  pause: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="5" width="4" height="14" fill="currentColor" stroke="none"/><rect x="14" y="5" width="4" height="14" fill="currentColor" stroke="none"/></svg>`,
  star: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>`,
  starFilled: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" fill="currentColor" stroke="none"/></svg>`,
  close: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>`,
  globe: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/></svg>`,
  settings: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.93 4.93l2.12 2.12M16.95 16.95l2.12 2.12M2 12h3M19 12h3M4.93 19.07l2.12-2.12M16.95 7.05l2.12-2.12"/></svg>`,
  check: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>`,
  chevronDown: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>`,
  arrowUp: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7"/></svg>`,
  arrowDown: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12l7 7 7-7"/></svg>`,
  tv: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="12" rx="2"/><path d="M8 21h8M12 17v4"/></svg>`,
  layers: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><polygon points="12 3 2 8 12 13 22 8 12 3"/><path d="M2 12.5 12 17.5 22 12.5M2 17 12 22 22 17"/></svg>`,
  folder: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>`,
  phone: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><rect x="7" y="2" width="10" height="20" rx="2"/><path d="M12 18h.01"/></svg>`,
  tablet: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/></svg>`,
  desktop: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><rect x="2" y="4" width="20" height="13" rx="2"/><path d="M8 21h8M12 17v4"/></svg>`,
  lock: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>`,
  key: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><circle cx="7.5" cy="15.5" r="4.5"/><path d="m21 2-9.6 9.6M15.5 7.5l3 3"/></svg>`,
  info: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/></svg>`,
  list: `<svg class="btn-svg sm" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg>`,
  volumeOn: `<svg class="hud-svg" viewBox="0 0 24 24" aria-hidden="true"><polygon points="11,5 6,9 2,9 2,15 6,15 11,19" fill="currentColor" stroke="none"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"/></svg>`,
  volumeOff: `<svg class="hud-svg" viewBox="0 0 24 24" aria-hidden="true"><polygon points="11,5 6,9 2,9 2,15 6,15 11,19" fill="currentColor" stroke="none"/><path d="m22 9-6 6M16 9l6 6"/></svg>`,
};

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

function normalizeFlagCode(code) {
  const clean = String(code || '').trim().toLowerCase();
  const norm = clean === 'uk' ? 'gb' : clean;
  return /^[a-z]{2}$/.test(norm) ? norm : '';
}

function flagHtml(code, sizeClass = '') {
  const norm = normalizeFlagCode(code);
  if (!norm) return SVG_ICONS.globe;
  return `<img class="flag-svg ${sizeClass}" src="/flags/${norm}.svg" alt="${norm.toUpperCase()}" loading="lazy" />`;
}

function countryFlag() {
  return '';
}

function renderProfileAvatar(avatar, name, sizePx = null) {
  const key = String(avatar || '').trim().toLowerCase();
  const bg = AVATAR_COLORS[key] || AVATAR_COLORS.blue;
  const ini = initials(name || 'TV');
  const style = sizePx ? `background:${bg};font-size:${Math.round(sizePx * 0.42)}px;` : `background:${bg};`;
  return `<span class="profile-avatar-badge" style="${style}">${esc(ini)}</span>`;
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
function isIOSDevice() {
  const ua = navigator.userAgent || '';
  return /iPhone|iPad|iPod/i.test(ua) || (/Macintosh/i.test(ua) && (navigator.maxTouchPoints || 0) > 1);
}

function isSafariBrowser() {
  const ua = navigator.userAgent || '';
  return /Safari/i.test(ua) && !/Chrome|Chromium|Edg|OPR|Brave|Firefox|FxiOS|CriOS/i.test(ua);
}

function needsVodHlsRemux(ch, video) {
  if (!ch?.id) return false;
  const raw = String(ch.streamUrl || '').split(/[?#]/)[0].toLowerCase();
  const isUnsupportedExt = /\.(mkv|avi|mov|flv|wmv|ts)$/.test(raw);
  if (!isUnsupportedExt) return false;
  if (isSafariBrowser() || isIOSDevice()) return true;
  if (raw.endsWith('.mkv') && video && typeof video.canPlayType === 'function') {
    return video.canPlayType('video/x-matroska') === '';
  }
  return !raw.endsWith('.mkv');
}

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
  channelsScrollHandler: null,
  channelsLoadError: false,
  vodObserver: null,
  vodScrollHandler: null,
  vodLoadError: false,
  serverPlaylists: [],
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
  verifiedMixQueue: [],
  mixVerifyAbort: null,
  hoveredMixRegion: 'EUROPE',

  // Player State
  playerOpen: false,
  playerChannel: null,
  playerQueue: [],
  playerIsMix: false,
  playerIsVod: false,
  playerCarouselOpen: true,
  playerCarouselItems: [],
  playerCarouselMode: 'channels', // 'channels' | 'episodes' | 'similar'
  playerProgressTimer: null,
  playerLastSavedPosMs: 0,
  playerPlaybackRate: 1,
  playerSources: [],
  vodType: 'vod', // 'vod' | 'movie' | 'series'
  vodCategory: '',
  vodGenre: '',
  vodPlatform: '',
  vodLanguage: '',
  vodPlatformMenuOpen: false,
  vodLanguageMenuOpen: false,
  vodItems: [],
  vodTotal: 0,
  vodLoading: false,
  vodRequestSeq: 0,
  vodContinue: [],
  vodGenres: [],
  vodPlatforms: [],
  vodLanguages: [],
  vodCategories: [],
  vodCategoriesKey: '',
  rightDrawerOpen: false,
  rightDrawerCursorIdx: 0,
  seriesDetail: null,
  seriesSeason: 1,
  nextEpisodeCandidate: null,
  nextEpisodeTimer: null,
  nextEpisodeCountdown: 0,
  nextEpisodeDismissedId: null,
  activeRecording: null,
  recHoldTimer: null,
  recHoldStartMs: 0,
  recBusy: false,
  recordings: [],
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
  relayTunedChannelId: null,
  playerActiveUrl: '',
  relayPlaylistUrl: '',
  cushionTimer: null,
  cushionBuilding: false,
  lastCushionRateMs: 0,
  lastStallTime: -1,
  lastStallWallMs: 0,
  bufferingSinceMs: 0,
  readySinceMs: 0,
  playerTuneFailTimer: null,

  // GitHub Updates & Admin Lock
  updateInfo: null,
  profileManaging: false,
  profileEditing: null, // null | 'new' | profileObj
  adminPin: sessionStorage.getItem('casatv_admin_pin') || '',
  adminUnlocked: false,
  adminIsDefaultPin: false,
  adminMustChangePin: false,
  adminAuthChecked: false,
  adminVerifiedOldPin: '',
  adminGateError: '',
  adminGateBusy: false,
  importPollTimer: null,
  importStatus: null,
  chunkUploadProgress: null,
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
  if (type === 'smartphone') return `${SVG_ICONS.phone} ${t('Smartphone', 'Smartphone', 'Móvil')}`;
  if (type === 'tablet') return `${SVG_ICONS.tablet} ${t('Tablet', 'Tablet', 'Tablet')}`;
  return `${SVG_ICONS.desktop} ${t('PC / Desktop', 'PC / Desktop', 'PC / Escritorio')}`;
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
  document.documentElement.classList.toggle('is-ios', isIOSDevice());
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
function asciiHeader(val) {
  return String(val || '')
    .normalize('NFKD')
    .replace(/[^\x20-\x7E]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120);
}

async function api(path, opts = {}) {
  const headers = {
    Accept: 'application/json',
    'X-Device-Id': state.deviceId,
    'X-Device-Name': asciiHeader(`${detectBrowserName()} (${deviceLabel()})`),
    'X-Device-Platform': asciiHeader(deviceLabel()),
    'X-App-Version': WEB_APP_VERSION,
    'X-Playback-State': state.playbackState,
    'X-Playback-Channel': asciiHeader(state.playbackChannel),
    ...(opts.headers || {}),
  };
  if (state.activeProfile?.id) {
    headers['X-Profile-Id'] = state.activeProfile.id;
  }
  if (state.adminPin) {
    headers['X-Admin-Pin'] = state.adminPin;
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
      if (res.status === 401 && json.requiresAdminAuth) {
        state.adminUnlocked = false;
        state.adminPin = '';
        sessionStorage.removeItem('casatv_admin_pin');
      }
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
  const vodNavTab = $('#nav-tab-vod');
  if (vodNavTab) vodNavTab.textContent = t('Film & Serie', 'Movies & Series', 'Cine y Series');
  $('#nav-tab-favorites').textContent = t('Preferiti', 'Favorites', 'Favoritos');
  $('#bottom-nav-channels-label').textContent = t('Canali', 'Channels', 'Canales');
  const vodBotLbl = $('#bottom-nav-vod-label');
  if (vodBotLbl) vodBotLbl.textContent = t('Film', 'Movies', 'Cine');
  $('#bottom-nav-favorites-label').textContent = t('Preferiti', 'Favorites', 'Favoritos');
  $('#bottom-nav-settings-label').textContent = t('Impostazioni', 'Settings', 'Ajustes');

  const searchInput = $('#global-search-input');
  if (searchInput) {
    searchInput.placeholder = state.page === 'vod'
      ? t('Cerca film, serie o canali…', 'Search movies, series or channels…', 'Buscar películas, series o canales…')
      : t('Cerca canali…', 'Search channels…', 'Buscar canales…');
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

  // Country button (only shown on Live Channels tab, NOT on global Film & Serie)
  const countryBtn = $('#topbar-country-btn');
  if (countryBtn) {
    countryBtn.hidden = Boolean(state.searchQuery) || state.page !== 'channels';
    const countries = state.config?.facets?.countries || [];
    const matched = countries.find((c) => c.code === state.tvCountry);
    $('#topbar-country-flag').innerHTML = flagHtml(matched?.code || state.tvCountry);
    $('#topbar-country-name').textContent = matched?.name || state.tvCountry;
  }

  // Profile button
  const p = state.activeProfile;
  $('#topbar-profile-avatar').innerHTML = renderProfileAvatar(p?.avatar, p?.name || 'Guest', 34);

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
    } else if (state.page === 'vod') {
      text.textContent = `${fmtNum(state.vodTotal)} ${t('titoli', 'titles', 'títulos')}`;
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
      $('#topbar-update-text').textContent = `v${state.updateInfo.latest}`;
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
  if (state.channelsScrollHandler) {
    window.removeEventListener('scroll', state.channelsScrollHandler);
    state.channelsScrollHandler = null;
  }
}

function disconnectVodObserver() {
  if (state.vodObserver) {
    state.vodObserver.disconnect();
    state.vodObserver = null;
  }
  if (state.vodScrollHandler) {
    window.removeEventListener('scroll', state.vodScrollHandler);
    state.vodScrollHandler = null;
  }
}

function vodGridListKey() {
  return `vod:${state.vodType}:${state.vodLanguage}:${state.vodGenre}:${state.vodPlatform}:${state.vodCategory}:${state.deviceType}`;
}

function setupChannelsInfiniteScroll() {
  disconnectChannelsObserver();
  if (state.page !== 'channels' || state.searchQuery) return;
  if (state.channels.length >= state.channelsTotal) return;
  const sentinel = $('#channels-load-sentinel');
  if (!sentinel) return;

  const triggerMore = () => {
    if (
      state.page === 'channels' &&
      !state.searchQuery &&
      !state.channelsLoading &&
      !state.channelsLoadError &&
      state.channels.length < state.channelsTotal
    ) {
      loadChannels({ reset: false });
    }
  };

  if (typeof IntersectionObserver !== 'undefined') {
    const obs = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          obs.disconnect();
          if (state.channelsObserver === obs) state.channelsObserver = null;
          triggerMore();
        }
      },
      { rootMargin: '650px 0px' },
    );
    obs.observe(sentinel);
    state.channelsObserver = obs;
  }

  let rafPending = false;
  const onScroll = () => {
    if (rafPending) return;
    rafPending = true;
    requestAnimationFrame(() => {
      rafPending = false;
      const el = $('#channels-load-sentinel');
      if (!el) return;
      const rect = el.getBoundingClientRect();
      if (rect.top <= window.innerHeight + 550) {
        triggerMore();
      }
    });
  };
  state.channelsScrollHandler = onScroll;
  window.addEventListener('scroll', onScroll, { passive: true });

  requestAnimationFrame(() => {
    const el = $('#channels-load-sentinel');
    if (el && el.getBoundingClientRect().top <= window.innerHeight + 450) {
      triggerMore();
    }
  });
}

function updateChannelsFooterDom() {
  const root = $('#app');
  const footer = root ? $('.grid-footer', root) : null;
  if (!footer) return false;
  const hasMore = state.channels.length < state.channelsTotal;
  const countryTitle = currentCountryTitle();
  const countSummary = hasMore
    ? `${fmtNum(state.channels.length)} / ${fmtNum(state.channelsTotal)} ${t('canali', 'channels', 'canales')}`
    : `${fmtNum(state.channels.length)} ${t('canali visualizzati', 'channels loaded', 'canales cargados')}`;
  const statusHtml = hasMore
    ? `<div class="channels-load-sentinel" id="channels-load-sentinel" aria-hidden="true"></div>${
        state.channelsLoadError
          ? `<button type="button" class="casa-btn ghost sm" id="btn-retry-more-ch">${t('Riprova caricamento', 'Retry loading', 'Reintentar carga')}</button>`
          : state.channelsLoading
            ? `<div class="infinite-loading-pill"><span class="mini-spinner"></span><span>${t('Caricamento altri canali…', 'Loading more channels…', 'Cargando más canales…')}</span></div>`
            : ''
      }`
    : '';
  footer.innerHTML = `
    ${statusHtml}
    <p id="channels-footer-summary">${t('Diretta TV', 'Live TV')} · ${esc(countryTitle)} · ${countSummary}</p>
  `;
  $('#btn-retry-more-ch')?.addEventListener('click', () => {
    state.channelsLoadError = false;
    loadChannels({ reset: false });
  });
  return true;
}

function appendChannelCardsToDom(newItems, startIdx, { showFlag = false } = {}) {
  if (state.page !== 'channels' || state.searchQuery) return false;
  const root = $('#app');
  if (!root) return false;
  const gridEl = $('.channels-grid', root);
  if (!gridEl || gridEl.dataset.listKey !== channelsGridListKey()) return false;
  if (newItems.length > 0) {
    const html = newItems
      .map((ch, i) => renderChannelItemHtml(ch, startIdx + i, { showFlag, eager: false }))
      .join('');
    gridEl.insertAdjacentHTML('beforeend', html);
    scanGridImages(gridEl);
  }
  gridEl._channelList = state.channels;
  updateChannelsFooterDom();
  setupChannelsInfiniteScroll();
  return true;
}

function setupVodInfiniteScroll() {
  disconnectVodObserver();
  if (state.page !== 'vod' || state.searchQuery) return;
  if (state.vodItems.length >= state.vodTotal) return;
  const sentinel = $('#vod-load-sentinel');
  if (!sentinel) return;

  const triggerMore = () => {
    if (
      state.page === 'vod' &&
      !state.searchQuery &&
      !state.vodLoading &&
      !state.vodLoadError &&
      state.vodItems.length < state.vodTotal
    ) {
      loadVodCatalog({ reset: false });
    }
  };

  if (typeof IntersectionObserver !== 'undefined') {
    const obs = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          obs.disconnect();
          if (state.vodObserver === obs) state.vodObserver = null;
          triggerMore();
        }
      },
      { rootMargin: '650px 0px' },
    );
    obs.observe(sentinel);
    state.vodObserver = obs;
  }

  let rafPending = false;
  const onScroll = () => {
    if (rafPending) return;
    rafPending = true;
    requestAnimationFrame(() => {
      rafPending = false;
      const el = $('#vod-load-sentinel');
      if (!el) return;
      const rect = el.getBoundingClientRect();
      if (rect.top <= window.innerHeight + 550) {
        triggerMore();
      }
    });
  };
  state.vodScrollHandler = onScroll;
  window.addEventListener('scroll', onScroll, { passive: true });

  requestAnimationFrame(() => {
    const el = $('#vod-load-sentinel');
    if (el && el.getBoundingClientRect().top <= window.innerHeight + 450) {
      triggerMore();
    }
  });
}

function updateVodFooterDom() {
  const root = $('#app');
  const footer = root ? $('.grid-footer', root) : null;
  if (!footer) return false;
  const hasMore = state.vodItems.length < state.vodTotal;
  const statusHtml = hasMore
    ? `<div class="channels-load-sentinel" id="vod-load-sentinel" aria-hidden="true"></div>${
        state.vodLoadError
          ? `<button type="button" class="casa-btn ghost sm" id="btn-retry-more-vod">${t('Riprova caricamento', 'Retry loading', 'Reintentar carga')}</button>`
          : state.vodLoading
            ? `<div class="infinite-loading-pill"><span class="mini-spinner"></span><span>${t('Caricamento altri titoli…', 'Loading more titles…', 'Cargando más títulos…')}</span></div>`
            : ''
      }`
    : '';
  footer.innerHTML = `
    ${statusHtml}
    <p id="vod-footer-summary">${t('Catalogo Cinema & Serie TV', 'Movies & Series Catalog')} · ${fmtNum(state.vodItems.length)} / ${fmtNum(state.vodTotal)}</p>
  `;
  $('#btn-retry-more-vod')?.addEventListener('click', () => {
    state.vodLoadError = false;
    loadVodCatalog({ reset: false });
  });
  return true;
}

function appendVodCardsToDom(newItems, startIdx) {
  if (state.page !== 'vod' || state.searchQuery) return false;
  const wrap = $('#vod-main-grid-wrap');
  if (!wrap) return false;
  const gridEl = $('.channels-grid', wrap);
  if (!gridEl || gridEl.dataset.listKey !== vodGridListKey()) return false;
  if (newItems.length > 0) {
    const html = newItems
      .map((ch, i) => renderChannelItemHtml(ch, startIdx + i, { showFlag: true, eager: false, groupSeriesTitle: true }))
      .join('');
    gridEl.insertAdjacentHTML('beforeend', html);
    scanGridImages(gridEl);
  }
  gridEl._channelList = state.vodItems;
  updateVodFooterDom();
  setupVodInfiniteScroll();
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
        btn.innerHTML = isFav ? SVG_ICONS.starFilled : SVG_ICONS.star;
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
  state.channelsLoadError = false;
  if (reset) {
    state.channels = [];
    state.channelsFallback = false;
  } else {
    updateChannelsFooterDom();
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
      type: 'live',
    });
    if (state.tvCountry && (reset || !state.channelsFallback)) qs.set('country', state.tvCountry);
    let data = await api(`/api/channels?${qs.toString()}`);
    if (seq !== state.channelsRequestSeq) return;
    items = data.items || [];
    let fallback = false;
    if (reset && items.length === 0 && state.tvCountry) {
      data = await api(`/api/channels?type=live&limit=${CHANNEL_PAGE_SIZE}&offset=0&sort=number`);
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
    state.channelsLoadError = true;
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
          updateChannelsFooterDom();
        } else if (!appendChannelCardsToDom(items, prevLen, { showFlag: state.channelsFallback })) {
          renderCurrentScreen();
        }
      }
    }
  }
}

async function loadVodContinue() {
  try {
    const data = await api('/api/vod/continue?limit=24');
    state.vodContinue = data.items || [];
  } catch (_) {
    state.vodContinue = [];
  }
}

async function removeFromVodContinue(ch) {
  if (!ch?.id) return;
  try {
    await api(`/api/vod/continue/${encodeURIComponent(ch.id)}`, { method: 'DELETE' });
    state.vodContinue = (state.vodContinue || []).filter((item) => item.id !== ch.id);
    [state.vodItems].forEach((arr) => {
      arr.forEach((item) => {
        if (item && item.id === ch.id) item.positionMs = 0;
      });
    });
    if (state.page === 'vod') renderCurrentScreen();
    toast(t('Rimosso da Continua a guardare', 'Removed from Continue Watching', 'Quitado de Seguir viendo'));
  } catch (err) {
    toast(err.message, 'err');
  }
}

async function loadVodCategories() {
  const key = `${state.vodType}|${state.vodLanguage || ''}`;
  if (state.vodCategoriesKey === key && (state.vodGenres.length || state.vodPlatforms.length || state.vodLanguages.length)) return;
  try {
    const qs = new URLSearchParams({ type: state.vodType || 'vod', limit: '240' });
    if (state.vodLanguage) qs.set('lang', state.vodLanguage);
    const data = await api(`/api/vod/categories?${qs.toString()}`);
    const genres = data.genres || data.categories || [];
    const platforms = data.platforms || [];
    const languages = data.languages || [];
    state.vodGenres = genres;
    state.vodPlatforms = platforms;
    if (languages.length || !state.vodLanguages.length) {
      state.vodLanguages = languages;
    }
    state.vodCategories = genres;
    state.vodCategoriesKey = key;
  } catch (_) {
    state.vodCategoriesKey = '';
  }
}

async function loadVodCatalog({ reset = true } = {}) {
  if (!reset && state.vodLoading) return;
  const seq = reset ? ++state.vodRequestSeq : state.vodRequestSeq;
  const prevLen = reset ? 0 : state.vodItems.length;
  state.vodLoading = true;
  state.vodLoadError = false;
  if (reset) {
    state.vodItems = [];
  } else if (state.page === 'vod' && !state.searchQuery) {
    updateVodFooterDom();
  }
  updateShellChrome();
  if (state.page === 'vod' && reset && !state.searchQuery) renderCurrentScreen();

  let addedItems = [];
  let loadFailed = false;
  try {
    const offset = reset ? 0 : state.vodItems.length;
    const qs = new URLSearchParams({
      type: state.vodType || 'vod',
      limit: String(CHANNEL_PAGE_SIZE),
      offset: String(offset),
      sort: 'name',
      paginated: '1',
    });
    if (state.vodLanguage) qs.set('lang', state.vodLanguage);
    if (state.vodGenre) qs.set('genre', state.vodGenre);
    if (state.vodPlatform) qs.set('platform', state.vodPlatform);
    if (state.vodCategory) qs.set('category', state.vodCategory);
    const data = await api(`/api/channels?${qs.toString()}`);
    if (seq !== state.vodRequestSeq) return;
    const items = data.items || [];
    if (reset) {
      state.vodItems = items;
      addedItems = items;
    } else {
      const seen = new Set(state.vodItems.map((x) => x.id));
      addedItems = items.filter((x) => !seen.has(x.id));
      state.vodItems = state.vodItems.concat(addedItems);
    }
    state.vodTotal = Number(data.total ?? state.vodItems.length);
  } catch (err) {
    if (seq === state.vodRequestSeq) {
      loadFailed = true;
      state.vodLoadError = true;
      toast(`${t('Errore caricamento Film & Serie', 'VOD load error')}: ${err.message}`, 'err');
    }
  } finally {
    if (seq === state.vodRequestSeq) {
      state.vodLoading = false;
      updateShellChrome();
      if (state.page === 'vod' && !state.searchQuery) {
        if (reset) {
          renderCurrentScreen();
        } else if (loadFailed) {
          updateVodFooterDom();
        } else if (!appendVodCardsToDom(addedItems, prevLen)) {
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
      hls.on(window.Hls.Events.ERROR, (_, data) => {
        if (data?.fatal && state.cardPreviewHls === hls) {
          stopCardPreview();
        }
      });
    } else {
      video.src = streamUrl;
      video.onloadeddata = onPlaying;
      video.play().catch(() => {});
    }
  }, 850);
}

const webLogoContrastCache = new Map();
let webContrastCanvas = null;

function analyzeWebLogoContrast(img) {
  if (!img || !img.complete || !img.naturalWidth || img.dataset.vod === '1') return;
  const artwork = img.closest('.card-artwork');
  if (!artwork) return;
  const src = img.currentSrc || img.src || '';
  if (!src) return;
  const cached = webLogoContrastCache.get(src);
  if (cached) {
    artwork.classList.toggle('is-light-stage', state.theme === 'dark' && cached.isDark);
    artwork.classList.toggle('is-dark-stage', state.theme === 'light' && cached.isVeryLight);
    return;
  }
  try {
    if (!webContrastCanvas) {
      webContrastCanvas = document.createElement('canvas');
      webContrastCanvas.width = 24;
      webContrastCanvas.height = 24;
    }
    const ctx = webContrastCanvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) return;
    ctx.clearRect(0, 0, 24, 24);
    ctx.drawImage(img, 0, 0, 24, 24);
    const data = ctx.getImageData(0, 0, 24, 24).data;
    let n = 0;
    let lumSum = 0;
    let dark = 0;
    let bright = 0;
    for (let i = 0; i < data.length; i += 4) {
      const a = data[i + 3];
      if (a >= 64) {
        const lum = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
        n += 1;
        lumSum += lum;
        if (lum < 85) dark += 1;
        if (lum > 205) bright += 1;
      }
    }
    const res = n < 10
      ? { isDark: false, isVeryLight: false }
      : {
          isDark: lumSum / n < 102 || (dark / n >= 0.62 && lumSum / n < 125),
          isVeryLight: lumSum / n > 205 || (bright / n >= 0.72 && lumSum / n > 185),
        };
    if (webLogoContrastCache.size > 1500) webLogoContrastCache.clear();
    webLogoContrastCache.set(src, res);
    artwork.classList.toggle('is-light-stage', state.theme === 'dark' && res.isDark);
    artwork.classList.toggle('is-dark-stage', state.theme === 'light' && res.isVeryLight);
  } catch (_) {}
}

function handleGridImageLoaded(img) {
  if (!img || img.tagName !== 'IMG' || !img.classList.contains('card-logo-img')) return;
  img.classList.remove('is-loading');
  img.classList.add('is-loaded');
  const artwork = img.closest('.card-artwork');
  if (artwork) artwork.classList.add('is-loaded');
  if (img.dataset.vod === '1') {
    if (img.naturalWidth > 0 && img.naturalHeight > 0 && img.naturalWidth > img.naturalHeight * 1.08) {
      if (artwork && !artwork.querySelector('.card-still-scrim')) {
        artwork.classList.add('is-still-frame');
        if (artwork.querySelector('.card-tv-tag')) artwork.classList.add('is-still-series');
        const scrim = document.createElement('div');
        scrim.className = 'card-still-scrim';
        const titleEl = document.createElement('span');
        titleEl.className = 'card-still-title';
        titleEl.textContent = img.dataset.fallback || img.alt || '';
        scrim.appendChild(titleEl);
        artwork.appendChild(scrim);
      }
    }
  } else {
    analyzeWebLogoContrast(img);
  }
}

function handleGridImageError(img) {
  if (!img || img.tagName !== 'IMG' || img.dataset.fallback == null) return;
  if (img.dataset.fallbackMobile === '1') {
    const span = document.createElement('span');
    span.className = 'row-num-fallback';
    span.textContent = img.dataset.fallback;
    img.replaceWith(span);
    return;
  }
  const artwork = img.closest('.card-artwork');
  if (artwork && artwork.querySelector('.card-loading-title')) {
    artwork.classList.remove('is-loaded');
    img.remove();
    return;
  }
  const wrap = document.createElement('div');
  wrap.className = 'fallback-title-wrap';
  const txt = document.createElement('span');
  txt.className = 'fallback-title-text';
  txt.textContent = img.dataset.fallback;
  wrap.appendChild(txt);
  img.replaceWith(wrap);
}

function scanGridImages(gridEl) {
  if (!gridEl) return;
  for (const img of gridEl.querySelectorAll('img.card-logo-img')) {
    if (img.complete) {
      if (img.naturalWidth > 0) handleGridImageLoaded(img);
      else handleGridImageError(img);
    }
  }
}

function cleanTileTitle(name) {
  return String(name || '').replace(/^\s*(?:\([^)]{1,10}\)|\[[^\]]{1,10}\])\s*/, '').trim() || String(name || '');
}

function renderLogoOrFallback(ch, mobile = false, eager = false, displayTitle = null) {
  const loading = eager ? 'eager' : 'lazy';
  const priority = eager ? 'high' : 'low';
  const cleanTitle = cleanTileTitle(displayTitle || ch.name);
  const isVod = Boolean(ch.isVod || ch.contentType === 'movie' || ch.contentType === 'series');
  if (mobile) {
    if (ch.logo) {
      return `<img src="${esc(ch.logo)}" alt="${esc(cleanTitle)}" loading="${loading}" decoding="async" fetchpriority="${priority}" data-fallback="${esc(ch.number || cleanTitle)}" data-fallback-mobile="1" />`;
    }
    return `<span class="row-num-fallback">${esc(ch.number || initials(cleanTitle))}</span>`;
  }
  if (ch.logo) {
    return `<div class="card-loading-title"><span class="fallback-title-text">${esc(cleanTitle)}</span></div><img class="card-logo-img is-loading" crossorigin="anonymous" data-vod="${isVod ? '1' : '0'}" src="${esc(ch.logo)}" alt="${esc(cleanTitle)}" loading="${loading}" decoding="async" fetchpriority="${priority}" data-fallback="${esc(cleanTitle)}" data-fallback-mobile="0" />`;
  }
  return `<div class="fallback-title-wrap"><span class="fallback-title-text">${esc(cleanTitle)}</span></div>`;
}

function renderChannelItemHtml(ch, idx, { showFlag = false, eager = false, groupSeriesTitle = false } = {}) {
  const isVod = Boolean(ch.isVod || ch.contentType === 'movie' || ch.contentType === 'series');
  const posMs = Number(ch.positionMs || 0);
  const durMs = Number(ch.durationMs || 0);
  const resumePct = isVod && durMs > 0 && posMs > 15000 ? Math.min(98, Math.max(3, Math.round((posMs / durMs) * 100))) : 0;
  const displayName = (groupSeriesTitle || ch.contentType === 'series') && ch.seriesTitle ? ch.seriesTitle : ch.name;
  const upNextBadgeText = ch.upNext && ch.seasonNumber && ch.episodeNumber
    ? `S${String(ch.seasonNumber).padStart(2, '0')}E${String(ch.episodeNumber).padStart(2, '0')}`
    : (ch.upNext ? 'PROSSIMO' : '');
  let langFlags = isVod
    ? (Array.isArray(ch.languageFlags) ? ch.languageFlags : []).map(normalizeFlagCode).filter(Boolean)
    : [];
  if (isVod && !langFlags.length) {
    const cc = normalizeFlagCode(ch.country);
    if (cc) langFlags = [cc];
  }
  langFlags = [...new Set(langFlags)].slice(0, 5);
  const hasVodCorner = isVod && (langFlags.length > 0 || ch.contentType === 'series');

  return `
    <article class="grid-channel-card ${isVod ? 'is-vod' : ''}" data-idx="${idx}" data-id="${esc(ch.id)}" tabindex="0" role="button" title="${esc(displayName)}" aria-label="${esc(displayName)}">
      <div class="card-artwork">
        ${renderLogoOrFallback(ch, false, eager, displayName)}
        ${ch.favorite ? `<div class="card-top-right"><button type="button" class="card-fav-btn is-fav" data-fav-idx="${idx}" title="${t('Preferito', 'Favorite', 'Favorito')}">${SVG_ICONS.starFilled}</button></div>` : ''}
        ${!isVod ? '<span class="card-live-tag"><i></i>LIVE</span>' : ''}
        ${hasVodCorner ? `
          <div class="card-vod-corner">
            ${langFlags.length ? `<div class="card-lang-flags">${langFlags.map((cc) => `<img class="vod-lang-flag" src="/flags/${cc}.svg" alt="${cc.toUpperCase()}" loading="lazy" decoding="async" />`).join('')}</div>` : ''}
            ${ch.contentType === 'series' ? '<span class="card-tv-tag">TV</span>' : ''}
          </div>` : ''}
        ${upNextBadgeText ? `<span class="upnext-pill">${esc(upNextBadgeText)}</span>` : ''}
        ${resumePct > 0 ? `<div class="card-resume-track"><div class="card-resume-fill" style="width:${resumePct}%"></div></div>` : ''}
      </div>
    </article>
  `;
}

function renderChannelGridCards(channels, { showFlag = false, listKey = '', groupSeriesTitle = false } = {}) {
  const keyAttr = listKey ? ` data-list-key="${esc(listKey)}"` : '';
  return `
    <div class="channels-grid"${keyAttr}>
      ${channels
        .map((ch, idx) => renderChannelItemHtml(ch, idx, { showFlag, eager: idx < 18, groupSeriesTitle }))
        .join('')}
    </div>
  `;
}

function closeCardContextMenu() {
  const old = $('#card-context-menu');
  if (old) old.remove();
}

function openCardContextMenu(ch, curList, clientX = 120, clientY = 120) {
  if (!ch) return;
  closeCardContextMenu();
  const menu = document.createElement('div');
  menu.id = 'card-context-menu';
  menu.className = 'card-context-menu';
  const isVod = Boolean(ch.isVod || ch.contentType === 'movie' || ch.contentType === 'series');
  const isContinue = (state.vodContinue || []).some((x) => x.id === ch.id);
  const title = ch.seriesTitle && ch.contentType === 'series' ? ch.seriesTitle : ch.name;
  const sub = [
    normalizeFlagCode(ch.country) ? flagHtml(ch.country, 'sm') : '',
    esc(ch.countryName || ch.country || ''),
    esc((ch.categoryNames && ch.categoryNames[0]) || ch.quality || ''),
  ]
    .filter(Boolean)
    .join(' ');

  menu.innerHTML = `
    <div class="ctx-head">
      <strong>${esc(title)}</strong>
      <small>${sub}</small>
    </div>
    <div id="ctx-main-view">
      <button type="button" class="ctx-item" data-ctx="play">${SVG_ICONS.play} <span>${t('Riproduci', 'Play', 'Reproducir')}</span></button>
      <button type="button" class="ctx-item" data-ctx="fav">${ch.favorite ? SVG_ICONS.starFilled : SVG_ICONS.star} <span>${ch.favorite ? t('Rimuovi da Preferiti', 'Remove from Favorites', 'Quitar de Favoritos') : t('Aggiungi a Preferiti', 'Add to Favorites', 'Añadir a Favoritos')}</span></button>
      ${isContinue ? `<button type="button" class="ctx-item" data-ctx="remove-continue">${SVG_ICONS.close} <span>${t('Rimuovi da Continua a guardare', 'Remove from Continue Watching', 'Quitar de Seguir viendo')}</span></button>` : ''}
      ${!isVod && ch.id && !state.localOverrideChannels ? `<button type="button" class="ctx-item" data-ctx="sources">${SVG_ICONS.layers} <span id="ctx-sources-label">${t('Scegli sorgente', 'Choose source', 'Elegir fuente')}</span></button>` : ''}
      <button type="button" class="ctx-item" data-ctx="details">${SVG_ICONS.info} <span>${t('Dettagli', 'Details', 'Detalles')}</span></button>
    </div>
    <div id="ctx-sources-view" hidden></div>
    <div id="ctx-details-view" hidden></div>
  `;
  document.body.appendChild(menu);

  const rect = menu.getBoundingClientRect();
  const left = Math.max(12, Math.min(window.innerWidth - rect.width - 12, clientX));
  const top = Math.max(12, Math.min(window.innerHeight - rect.height - 12, clientY));
  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;

  let loadedSources = [];
  if (!isVod && ch.id && !state.localOverrideChannels) {
    api(`/api/channels/${encodeURIComponent(ch.id)}/streams`)
      .then((res) => {
        if (!menu.isConnected) return;
        loadedSources = res?.sources || [];
        const lbl = $('#ctx-sources-label', menu);
        if (lbl && loadedSources.length > 1) {
          lbl.textContent = `${t('Scegli sorgente', 'Choose source', 'Elegir fuente')} (${loadedSources.length})`;
        }
      })
      .catch(() => {});
  }

  menu.addEventListener('click', (e) => {
    e.stopPropagation();
    const act = e.target.closest('[data-ctx]')?.dataset.ctx;
    if (act === 'play') {
      closeCardContextMenu();
      if (ch.contentType === 'series' && !state.localOverrideChannels) openSeriesModal(ch);
      else openPlayer(ch, curList, { isMix: false });
    } else if (act === 'fav') {
      toggleFavorite(ch);
      closeCardContextMenu();
    } else if (act === 'remove-continue') {
      closeCardContextMenu();
      removeFromVodContinue(ch);
    } else if (act === 'sources') {
      const mainView = $('#ctx-main-view', menu);
      const srcView = $('#ctx-sources-view', menu);
      mainView.hidden = true;
      srcView.hidden = false;
      const items = loadedSources.length ? loadedSources : [{ url: ch.streamUrl, quality: ch.quality || 'HD', label: ch.name }];
      srcView.innerHTML = `
        <div class="ctx-sources-list">
          ${items
            .map((s, idx) => {
              const label = s.quality || s.label || `Sorgente ${idx + 1}`;
              const meta = [s.protocol, s.host, s.latencyMs != null ? `${s.latencyMs} ms` : ''].filter(Boolean).join(' · ');
              return `
                <button type="button" class="src-opt-row" data-ctx-pick-src="${idx}">
                  <div>
                    <div>${esc(label)}</div>
                    ${meta ? `<div class="src-opt-meta">${esc(meta)}</div>` : ''}
                  </div>
                  ${s.recommended ? `<span class="src-rec-pill">${t('Migliore', 'Best', 'Mejor')}</span>` : ''}
                </button>
              `;
            })
            .join('')}
        </div>
        <button type="button" class="ctx-item" data-ctx-back="1" style="margin-top:6px">${t('Indietro', 'Back', 'Atrás')}</button>
      `;
    } else if (act === 'details') {
      const mainView = $('#ctx-main-view', menu);
      const detView = $('#ctx-details-view', menu);
      mainView.hidden = true;
      detView.hidden = false;
      detView.innerHTML = `
        <div style="padding:6px 10px;font-size:11.5px;color:var(--muted);word-break:break-all;display:flex;flex-direction:column;gap:4px">
          <div><strong>${t('Nome', 'Name')}:</strong> ${esc(ch.name)}</div>
          ${ch.quality ? `<div><strong>${t('Qualità', 'Quality')}:</strong> ${esc(ch.quality)}</div>` : ''}
          ${ch.streamUrl ? `<div><strong>URL:</strong> ${esc(ch.streamUrl)}</div>` : ''}
        </div>
        <button type="button" class="ctx-item" data-ctx-back="1">${t('Indietro', 'Back', 'Atrás')}</button>
      `;
    } else if (e.target.closest('[data-ctx-back]')) {
      $('#ctx-main-view', menu).hidden = false;
      $('#ctx-sources-view', menu).hidden = true;
      $('#ctx-details-view', menu).hidden = true;
    } else if (e.target.closest('[data-ctx-pick-src]')) {
      const idx = Number(e.target.closest('[data-ctx-pick-src]').dataset.ctxPickSrc);
      const srcs = loadedSources.length ? loadedSources : [{ url: ch.streamUrl, quality: ch.quality || 'HD' }];
      closeCardContextMenu();
      openPlayer(ch, curList, { isMix: false, sources: srcs, sourceIdx: idx, pinSource: true });
    }
  });
}

function bindChannelListEvents(container, list, { openSeriesOnTap = true } = {}) {
  const gridEl = $('.channels-grid', container);
  if (!gridEl) return;
  gridEl._channelList = list;
  if (gridEl.dataset.bound === '1') {
    scanGridImages(gridEl);
    return;
  }
  gridEl.dataset.bound = '1';

  const cardSel = '.grid-channel-card';

  const launchChannelOrSeries = (ch, curList) => {
    if (openSeriesOnTap && ch.contentType === 'series' && !state.localOverrideChannels) {
      openSeriesModal(ch);
    } else {
      openPlayer(ch, curList, { isMix: false });
    }
  };

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
    launchChannelOrSeries(ch, curList);
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
      launchChannelOrSeries(ch, curList);
    } else if (e.key.toLowerCase() === 'm') {
      e.preventDefault();
      toggleFavorite(ch);
    }
  });

  gridEl.addEventListener('contextmenu', (e) => {
    const card = e.target.closest(cardSel);
    if (!card || !gridEl.contains(card)) return;
    e.preventDefault();
    const idx = Number(card.dataset.idx);
    const curList = gridEl._channelList || [];
    const ch = curList[idx];
    if (ch) openCardContextMenu(ch, curList, e.clientX, e.clientY);
  });

  let lpTimer = null;
  let lpStartX = 0;
  let lpStartY = 0;
  gridEl.addEventListener(
    'touchstart',
    (e) => {
      const card = e.target.closest(cardSel);
      if (!card || !gridEl.contains(card) || e.target.closest('[data-fav-idx], [data-remove-continue-idx]')) return;
      const tTouch = e.touches[0];
      if (!tTouch) return;
      lpStartX = tTouch.clientX;
      lpStartY = tTouch.clientY;
      clearTimeout(lpTimer);
      lpTimer = setTimeout(() => {
        lpTimer = null;
        const idx = Number(card.dataset.idx);
        const curList = gridEl._channelList || [];
        const ch = curList[idx];
        if (ch) openCardContextMenu(ch, curList, lpStartX, lpStartY);
      }, 520);
    },
    { passive: true },
  );
  gridEl.addEventListener(
    'touchmove',
    (e) => {
      if (!lpTimer) return;
      const tTouch = e.touches[0];
      if (!tTouch || Math.hypot(tTouch.clientX - lpStartX, tTouch.clientY - lpStartY) > 12) {
        clearTimeout(lpTimer);
        lpTimer = null;
      }
    },
    { passive: true },
  );
  gridEl.addEventListener('touchend', () => {
    if (lpTimer) {
      clearTimeout(lpTimer);
      lpTimer = null;
    }
  });
  gridEl.addEventListener('touchcancel', () => {
    if (lpTimer) {
      clearTimeout(lpTimer);
      lpTimer = null;
    }
  });

  if (state.deviceType === 'pc') {
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
    'load',
    (e) => {
      handleGridImageLoaded(e.target);
    },
    true,
  );

  gridEl.addEventListener(
    'error',
    (e) => {
      handleGridImageError(e.target);
    },
    true,
  );

  scanGridImages(gridEl);
}

function renderCurrentScreen() {
  stopCardPreview();
  disconnectChannelsObserver();
  disconnectVodObserver();
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
    const liveResults = state.searchResults.filter(
      (ch) => !ch.isVod && ch.contentType !== 'movie' && ch.contentType !== 'series',
    );
    const movieResults = state.searchResults.filter(
      (ch) => ch.contentType === 'movie' || (ch.isVod && ch.contentType !== 'series'),
    );
    const seriesResults = state.searchResults.filter((ch) => ch.contentType === 'series');

    const sections = [];
    if (liveResults.length) {
      sections.push({
        id: 'search-sec-live',
        title: `${t('Canali TV', 'TV Channels', 'Canales TV')} (${fmtNum(liveResults.length)})`,
        items: liveResults,
        groupSeriesTitle: false,
      });
    }
    if (movieResults.length) {
      sections.push({
        id: 'search-sec-movies',
        title: `${t('Film', 'Movies', 'Películas')} (${fmtNum(movieResults.length)})`,
        items: movieResults,
        groupSeriesTitle: false,
      });
    }
    if (seriesResults.length) {
      sections.push({
        id: 'search-sec-series',
        title: `${t('Serie TV', 'TV Series', 'Series TV')} (${fmtNum(seriesResults.length)})`,
        items: seriesResults,
        groupSeriesTitle: true,
      });
    }

    root.innerHTML = `
      <div class="search-banner">
        <strong>${t(`Ricerca globale per "${esc(state.searchQuery)}"`, `Worldwide search for "${esc(state.searchQuery)}"`)}</strong>
        <span>${t('Catalogo globale', 'Worldwide catalog', 'Catálogo mundial')}</span>
      </div>
      ${sections
        .map(
          (sec) => `
        <section class="search-section" id="${sec.id}">
          <h3 class="search-section-title">${esc(sec.title)}</h3>
          ${renderChannelGridCards(sec.items, { showFlag: true, groupSeriesTitle: sec.groupSeriesTitle })}
        </section>`,
        )
        .join('')}
    `;
    for (const sec of sections) {
      const secEl = $(`#${sec.id}`, root);
      if (secEl) bindChannelListEvents(secEl, sec.items);
    }
    return;
  }

  if (state.page === 'vod') {
    renderVodScreen(root);
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
            'Sfoglia i canali e clicca la stellina su un canale o premi M sul player per salvarlo qui.',
            'Browse channels and click the star on a channel or press M in the player to save it here.',
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
    ${state.localOverrideChannels ? `<div class="fallback-notice">${SVG_ICONS.folder} ${esc(state.localSourceLabel)} · ${fmtNum(state.channelsTotal)} canali · <button type="button" class="casa-btn ghost sm" id="btn-exit-local-m3u">${t('Torna al server', 'Back to server')}</button></div>` : fallbackBanner}
    ${renderChannelGridCards(state.channels, { showFlag: state.channelsFallback || Boolean(state.localOverrideChannels), listKey: channelsGridListKey() })}
    <div class="grid-footer"></div>
  `;
  $('#btn-exit-local-m3u')?.addEventListener('click', () => {
    state.localOverrideChannels = null;
    state.localSourceLabel = '';
    loadChannels({ reset: true });
    loadFavorites();
  });
  updateChannelsFooterDom();
  bindChannelListEvents(root, state.channels);
  setupChannelsInfiniteScroll();
}

function renderVodScreen(root) {
  const continueItems = state.vodContinue || [];
  const genres = state.vodGenres || [];
  const platforms = state.vodPlatforms || [];
  const languages = state.vodLanguages || [];
  const activePlatformObj = platforms.find((p) => p.id === state.vodPlatform);
  const activeLangObj = languages.find((l) => l.code === state.vodLanguage);
  const hasMore = state.vodItems.length < state.vodTotal;

  const languageMenuHtml = languages.length
    ? `
      <div class="vod-platform-wrap" id="vod-language-wrap">
        <button type="button" class="vod-platform-btn ${state.vodLanguage ? 'is-active' : ''}" id="vod-language-btn" aria-haspopup="true" aria-expanded="${state.vodLanguageMenuOpen ? 'true' : 'false'}">
          <span>${activeLangObj ? `${flagHtml(activeLangObj.flag, 'sm')} ${esc(activeLangObj.name)}` : `${SVG_ICONS.globe} ${t('Tutte le lingue', 'All languages', 'Todos los idiomas')}`}</span>
          <span aria-hidden="true">${SVG_ICONS.chevronDown}</span>
        </button>
        <div class="vod-platform-menu" id="vod-language-menu" ${state.vodLanguageMenuOpen ? '' : 'hidden'} role="menu">
          <button type="button" class="vod-platform-option ${!state.vodLanguage ? 'is-active' : ''}" data-vod-language="" role="menuitem">
            <span>${SVG_ICONS.globe} ${t('Tutte le lingue', 'All languages', 'Todos los idiomas')}</span>
          </button>
          ${languages
            .map(
              (l) => `
            <button type="button" class="vod-platform-option ${state.vodLanguage === l.code ? 'is-active' : ''}" data-vod-language="${esc(l.code)}" role="menuitem">
              <span>${flagHtml(l.flag, 'sm')} ${esc(l.name)}</span>
              <small>${fmtNum(l.count)}</small>
            </button>`,
            )
            .join('')}
        </div>
      </div>
    `
    : '';

  const toolbarHtml = `
    <section class="vod-toolbar">
      <div class="vod-type-row">
        <div class="vod-type-pills">
          <button type="button" class="vod-type-btn ${state.vodType === 'vod' ? 'is-active' : ''}" data-vod-type="vod">${t('Tutti', 'All', 'Todos')}</button>
          <button type="button" class="vod-type-btn ${state.vodType === 'movie' ? 'is-active' : ''}" data-vod-type="movie">${t('Film', 'Movies', 'Películas')}</button>
          <button type="button" class="vod-type-btn ${state.vodType === 'series' ? 'is-active' : ''}" data-vod-type="series">${t('Serie TV', 'Series', 'Series')}</button>
        </div>
        <div class="vod-right-controls">
          ${languageMenuHtml}
          <span class="muted">${fmtNum(state.vodTotal)} ${t('titoli on-demand', 'on-demand titles', 'títulos on-demand')}</span>
        </div>
      </div>
      ${
        genres.length
          ? `<div class="vod-cat-chips">
              <button type="button" class="vod-cat-chip ${!state.vodGenre ? 'is-active' : ''}" data-vod-genre="">${t('Tutti i generi', 'All genres', 'Todos los géneros')}</button>
              ${genres
                .map(
                  (g) =>
                    `<button type="button" class="vod-cat-chip ${state.vodGenre === g.id ? 'is-active' : ''}" data-vod-genre="${esc(g.id)}">${esc(g.name)} (${fmtNum(g.count)})</button>`,
                )
                .join('')}
            </div>`
          : ''
      }
    </section>
  `;

  const continueHtml = continueItems.length
    ? `
      <div class="vod-shelf-wrap">
        <h3 class="vod-shelf-title">${SVG_ICONS.play} ${t('Continua a guardare', 'Continue Watching', 'Seguir viendo')}</h3>
        <div class="vod-continue-row channels-grid" id="vod-continue-grid">
          ${continueItems.map((ch, idx) => renderChannelItemHtml(ch, idx, { showFlag: true, eager: idx < 8, groupSeriesTitle: false })).join('')}
        </div>
      </div>
    `
    : '';

  if (state.vodLoading && !state.vodItems.length) {
    root.innerHTML = `${toolbarHtml}${continueHtml}<div class="loading-box"><div class="spinner"></div><p>${t('Carico Film e Serie…', 'Loading Movies & Series…')}</p></div>`;
  } else if (!state.vodItems.length) {
    root.innerHTML = `${toolbarHtml}${continueHtml}
      <div class="empty-box">
        <h3>${t('Nessun film o serie trovato', 'No movies or series found')}</h3>
        <p>${t('Prova a cambiare lingua o genere.', 'Try changing language or genre.')}</p>
      </div>`;
  } else {
    root.innerHTML = `
      ${toolbarHtml}
      ${continueHtml}
      <div id="vod-main-grid-wrap">
        ${renderChannelGridCards(state.vodItems, { showFlag: true, groupSeriesTitle: true, listKey: vodGridListKey() })}
      </div>
      <div class="grid-footer"></div>
    `;
  }

  $$('[data-vod-type]', root).forEach((btn) =>
    btn.addEventListener('click', () => {
      const next = btn.dataset.vodType || 'vod';
      if (state.vodType === next) return;
      state.vodType = next;
      state.vodGenre = '';
      state.vodPlatform = '';
      state.vodCategory = '';
      state.vodPlatformMenuOpen = false;
      state.vodLanguageMenuOpen = false;
      state.vodCategoriesKey = '';
      loadVodCategories().then(() => { if (state.page === 'vod') renderCurrentScreen(); });
      loadVodCatalog({ reset: true });
    }),
  );
  $('#vod-language-btn', root)?.addEventListener('click', (e) => {
    e.stopPropagation();
    state.vodPlatformMenuOpen = false;
    state.vodLanguageMenuOpen = !state.vodLanguageMenuOpen;
    const menu = $('#vod-language-menu', root);
    const btn = $('#vod-language-btn', root);
    if (menu) menu.hidden = !state.vodLanguageMenuOpen;
    if (btn) btn.setAttribute('aria-expanded', state.vodLanguageMenuOpen ? 'true' : 'false');
  });
  $$('[data-vod-language]', root).forEach((btn) =>
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      state.vodLanguage = btn.dataset.vodLanguage || '';
      state.vodGenre = '';
      state.vodPlatform = '';
      state.vodCategory = '';
      state.vodLanguageMenuOpen = false;
      state.vodCategoriesKey = '';
      loadVodCategories().then(() => { if (state.page === 'vod') renderCurrentScreen(); });
      loadVodCatalog({ reset: true });
    }),
  );
  $$('[data-vod-genre]', root).forEach((btn) =>
    btn.addEventListener('click', () => {
      const nextGenre = btn.dataset.vodGenre || '';
      state.vodGenre = nextGenre;
      state.vodPlatform = '';
      state.vodCategory = '';
      state.vodPlatformMenuOpen = false;
      state.vodLanguageMenuOpen = false;
      loadVodCatalog({ reset: true });
    }),
  );

  const contEl = $('#vod-continue-grid', root);
  if (contEl) {
    contEl._channelList = continueItems;
    contEl.addEventListener('click', (e) => {
      const favBtn = e.target.closest('[data-fav-idx]');
      if (favBtn) {
        e.stopPropagation();
        toggleFavorite(continueItems[Number(favBtn.dataset.favIdx)]);
        return;
      }
      const card = e.target.closest('.grid-channel-card');
      if (!card) return;
      const ch = continueItems[Number(card.dataset.idx)];
      if (ch) openPlayer(ch, continueItems, { isMix: false });
    });
    contEl.addEventListener('contextmenu', (e) => {
      const card = e.target.closest('.grid-channel-card');
      if (!card) return;
      e.preventDefault();
      const ch = continueItems[Number(card.dataset.idx)];
      if (ch) openCardContextMenu(ch, continueItems, e.clientX, e.clientY);
    });
    contEl.addEventListener('load', (e) => handleGridImageLoaded(e.target), true);
    contEl.addEventListener('error', (e) => handleGridImageError(e.target), true);
    scanGridImages(contEl);
  }
  const mainWrap = $('#vod-main-grid-wrap', root);
  if (mainWrap && state.vodItems.length) {
    updateVodFooterDom();
    bindChannelListEvents(mainWrap, state.vodItems);
    setupVodInfiniteScroll();
  }
}

/* ---------------- Navigation ---------------- */
function navigate(page) {
  if (page !== 'settings' && state.importPollTimer) {
    clearInterval(state.importPollTimer);
    state.importPollTimer = null;
  }
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
  if (page === 'vod') {
    loadVodContinue().then(() => { if (state.page === 'vod') renderCurrentScreen(); });
    loadVodCategories().then(() => { if (state.page === 'vod') renderCurrentScreen(); });
    if (!state.vodItems.length) loadVodCatalog({ reset: true });
  }
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
        <div class="profile-tile-circle">${renderProfileAvatar(p.avatar, p.name, 72)}</div>
        <strong>${esc(p.name)}</strong>
        <small class="${state.profileManaging ? 'is-edit' : ''}">${state.profileManaging ? t('Modifica', 'Edit', 'Editar') : `${flagHtml(p.country, 'sm')} ${esc((p.language || 'it').toUpperCase())}`}</small>
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
  const refreshAvatarPreview = () => {
    previewEl.innerHTML = renderProfileAvatar(selectedAvatar, nameInput.value.trim() || existing?.name || 'TV', 78);
  };
  refreshAvatarPreview();
  nameInput.oninput = refreshAvatarPreview;

  const countries = state.config?.facets?.countries || [{ code: 'IT', name: 'Italia', flag: '' }];
  countrySel.innerHTML = countries
    .map((c) => `<option value="${esc(c.code)}">${esc(c.code)} · ${esc(c.name)}</option>`)
    .join('');
  countrySel.value = existing?.country || state.tvCountry || 'IT';

  const avatarGrid = $('#editor-avatar-grid');
  avatarGrid.innerHTML = PROFILE_AVATARS.map(
    (a) => `<button type="button" class="avatar-cell ${a === selectedAvatar ? 'is-selected' : ''}" data-av="${a}">${renderProfileAvatar(a, nameInput.value.trim() || existing?.name || 'TV', 36)}</button>`,
  ).join('');
  $$('.avatar-cell', avatarGrid).forEach((btn) => {
    btn.addEventListener('click', () => {
      selectedAvatar = btn.dataset.av;
      refreshAvatarPreview();
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

  const allCountries = state.config?.facets?.countries || [{ code: 'IT', name: 'Italia', flag: '', count: 0 }];
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
        ${flagHtml(c.code, 'lg')}
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
        if (state.page === 'vod') {
          state.vodCategory = '';
          state.vodGenre = '';
          state.vodPlatform = '';
          state.vodPlatformMenuOpen = false;
          state.vodCategoriesKey = '';
          loadVodCategories().then(() => { if (state.page === 'vod') renderCurrentScreen(); });
          loadVodCatalog({ reset: true });
        }
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
      const limit = 2500;
      while (offset < 7500) {
        const data = await api(`/api/channels?limit=${limit}&offset=${offset}&sort=number`);
        const batch = (data.items || []).filter((c) => isPlayableMixLiveUrl(c.streamUrl));
        if (!(data.items || []).length) break;
        collected.push(...batch);
        offset += data.items.length;
        if (!data.hasMore) break;
      }
      state.allWorldPool = collected.length ? collected : state.channels.filter((c) => isPlayableMixLiveUrl(c.streamUrl));
      state.mixPoolLoaded = collected.length > 0;
    } catch (_) {
      state.allWorldPool = state.channels.filter((c) => isPlayableMixLiveUrl(c.streamUrl));
    } finally {
      state.mixPoolPromise = null;
    }
    if (!$('#mix-modal')?.hidden) {
      updateMappamondoUI();
      preverifyNextMixCandidate();
    }
    if (!$('#mix-filters-overlay')?.hidden) renderMixFiltersUI();
    return state.allWorldPool;
  })();
  return state.mixPoolPromise;
}

function isPlayableMixLiveUrl(raw) {
  const s = String(raw || '').trim();
  if (!/^https?:\/\//i.test(s)) return false;
  const low = s.toLowerCase();
  const path = low.split('?')[0];
  if (
    path.endsWith('.mp4') ||
    path.endsWith('.mkv') ||
    path.endsWith('.avi') ||
    path.endsWith('.mov') ||
    path.endsWith('.m4v') ||
    path.endsWith('.jpg') ||
    path.endsWith('.jpeg') ||
    path.endsWith('.png') ||
    path.endsWith('.webp') ||
    low.includes('/movie/') ||
    low.includes('/series/')
  ) {
    return false;
  }
  return true;
}

function resetMixState(resetRegions = true) {
  if (state.mixVerifyAbort) {
    state.mixVerifyAbort.abort();
    state.mixVerifyAbort = null;
  }
  state.mixHistory = [];
  state.mixIndex = -1;
  state.deadMixIds.clear();
  state.verifiedMixQueue = [];
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
  preverifyNextMixCandidate();
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
      <stop offset="0%" stop-color="#141414"/>
      <stop offset="100%" stop-color="#050505"/>
    </radialGradient>
    <linearGradient id="cyan-selected-grad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF"/>
      <stop offset="100%" stop-color="#D4D4D4"/>
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
  allWorldBtn.innerHTML = `${SVG_ICONS.globe} ${t('Tutto il mondo', 'All World', 'Todo el mundo')}`;
  allWorldBtn.classList.toggle('is-selected', allWorld);

  const filtersBtn = $('#mix-btn-filters');
  if (filtersBtn) {
    const fLabel = t('Filtri', 'Filters', 'Filtros');
    filtersBtn.innerHTML = activeFiltersCount > 0 ? `${SVG_ICONS.settings} ${fLabel} (${activeFiltersCount})` : `${SVG_ICONS.settings} ${fLabel}`;
    filtersBtn.classList.toggle('is-selected', activeFiltersCount > 0);
  }

  $('#mix-btn-start').innerHTML = `${SVG_ICONS.play} ${t('Avvia Mix', 'Start Mix', 'Iniciar Mix')}`;

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
        .map((d) => `<path d="${d}" fill="${selected ? '#FFFFFF' : '#737373'}" fill-opacity="${selected ? '0.95' : '0.48'}"/>`)
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
    applyBtn.innerHTML = `${SVG_ICONS.check} ${t('Applica', 'Apply', 'Aplicar')}`;
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
            <span class="mix-filter-check">${selected ? SVG_ICONS.check : ''}</span>
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
            <span class="mix-filter-check">${selected ? SVG_ICONS.check : ''}</span>
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
    if (!isPlayableMixLiveUrl(c.streamUrl) || state.deadMixIds.has(c.id)) return false;
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

function prewarmWebMixChannel(ch) {
  if (!isIOSDevice()) return;
  if (!ch || !ch.id || !ch.streamUrl) return;
  api('/api/relay/prewarm', {
    method: 'POST',
    body: {
      deviceId: state.deviceId,
      channelId: ch.id,
      channelName: ch.name || '',
      streamUrl: ch.streamUrl,
    },
  }).catch(() => {});
}

async function fetchServerMixReady(limit = 6, prewarm = isIOSDevice(), signal = undefined) {
  if (state.localOverrideChannels) return [];
  const qs = new URLSearchParams({
    limit: String(limit),
    prewarm: (prewarm && isIOSDevice()) ? '1' : '0',
    includeAudioOnly: state.mixIncludeAudioOnly ? '1' : '0',
  });
  const regs = [...state.selectedMixRegions].filter((r) => r && r !== 'ALL_WORLD');
  if (regs.length) qs.set('regions', regs.join(','));
  if (state.selectedMixLanguages.size) qs.set('languages', [...state.selectedMixLanguages].join(','));
  if (state.selectedMixGenres.size) qs.set('genres', [...state.selectedMixGenres].join(','));
  const exclude = [
    ...state.mixHistory.slice(-40).map((c) => c.id),
    ...state.verifiedMixQueue.map((c) => c.id),
    ...[...state.deadMixIds].slice(-80),
  ];
  if (exclude.length) qs.set('exclude', exclude.join(','));
  try {
    const res = await api(`/api/mix/ready?${qs.toString()}`, { signal });
    return Array.isArray(res?.items) ? res.items : [];
  } catch (_) {
    return [];
  }
}

function preverifyNextMixCandidate() {
  if (state.verifiedMixQueue.length >= 6) return;
  if (state.mixVerifyAbort) state.mixVerifyAbort.abort();
  const abortCtrl = new AbortController();
  state.mixVerifyAbort = abortCtrl;
  const active = Boolean(state.playerIsMix || !$('#mix-modal')?.hidden);
  if (!active) return;

  (async () => {
    if (!state.localOverrideChannels) {
      const needed = 6 - state.verifiedMixQueue.length;
      const wasEmpty = state.verifiedMixQueue.length === 0;
      const serverItems = await fetchServerMixReady(needed, wasEmpty, abortCtrl.signal);
      if (abortCtrl.signal.aborted || (!state.playerIsMix && $('#mix-modal')?.hidden)) return;
      if (serverItems.length) {
        serverItems.forEach((ch) => {
          if (ch && !state.deadMixIds.has(ch.id) && !state.verifiedMixQueue.some((q) => q.id === ch.id)) {
            state.verifiedMixQueue.push(ch);
          }
        });
        if (state.verifiedMixQueue.length >= 4) return;
      }
    }

    for (let round = 0; round < 3; round++) {
      if (abortCtrl.signal.aborted || (!state.playerIsMix && $('#mix-modal')?.hidden)) return;
      const needed = 6 - state.verifiedMixQueue.length;
      if (needed <= 0) return;
      const exclude = new Set([
        ...state.mixHistory.map((c) => c.id),
        ...state.verifiedMixQueue.map((c) => c.id),
      ]);
      const batch = [];
      const pool = getRegionalMixPool().filter((c) => !exclude.has(c.id));
      if (!pool.length) return;
      const seenBatch = new Set();
      const targetBatch = Math.min(8, pool.length);
      for (let i = 0; i < targetBatch * 3 && batch.length < targetBatch; i++) {
        const pick = pool[Math.floor(Math.random() * pool.length)];
        if (pick && !seenBatch.has(pick.id)) {
          seenBatch.add(pick.id);
          batch.push(pick);
        }
      }
      if (!batch.length) return;

      const byId = new Map(batch.map((c) => [c.id, c]));
      const wasEmpty = state.verifiedMixQueue.length === 0;
      const res = await api('/api/mix/probe', {
        method: 'POST',
        body: {
          deviceId: state.deviceId,
          maxWinners: Math.min(needed, 5),
          prewarm: wasEmpty,
          candidates: batch.map((c) => ({ id: c.id, name: c.name || '', streamUrl: c.streamUrl })),
        },
        signal: abortCtrl.signal,
      }).catch(() => null);

      if (abortCtrl.signal.aborted || (!state.playerIsMix && $('#mix-modal')?.hidden)) return;
      if (res && Array.isArray(res.dead)) {
        res.dead.forEach((id) => state.deadMixIds.add(id));
      }
      if (res && Array.isArray(res.winners)) {
        res.winners.forEach((id) => {
          const ch = byId.get(id);
          if (ch && !state.verifiedMixQueue.some((q) => q.id === ch.id)) {
            state.verifiedMixQueue.push(ch);
          }
        });
      }
      if (wasEmpty && state.verifiedMixQueue.length && !res?.prewarmedId) {
        prewarmWebMixChannel(state.verifiedMixQueue[0]);
      }
      if (state.verifiedMixQueue.length >= 6) return;
    }
  })().catch(() => {});
}

async function startMixPlayback() {
  let prebakedFirst = state.verifiedMixQueue.shift() || null;
  resetMixState(false);
  state.mixAudioSkipCount = 0;
  if (!prebakedFirst && !state.localOverrideChannels) {
    const quickReady = await fetchServerMixReady(6, true);
    if (quickReady.length) {
      prebakedFirst = quickReady[0];
      quickReady.slice(1).forEach((c) => state.verifiedMixQueue.push(c));
    }
  }
  await ensureMixPoolLoaded();
  const pool = getRegionalMixPool();
  if (!prebakedFirst && !pool.length) {
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
  const first = prebakedFirst || pickRandomMixCandidate();
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
  // Reached end of visited list: pop verified prewarmed channel or random fast candidate
  const visited = new Set(state.mixHistory.map((c) => c.id));
  let next = null;
  while (state.verifiedMixQueue.length && !next) {
    const cand = state.verifiedMixQueue.shift();
    if (cand && !visited.has(cand.id) && !state.deadMixIds.has(cand.id)) {
      next = cand;
    }
  }
  if (!next) {
    next = pickRandomMixCandidate(visited) || pickRandomMixCandidate();
  }
  if (!next) {
    toast(t('Nessun altro canale Mix disponibile', 'No more Mix channels available'), 'err');
    return;
  }
  if (state.verifiedMixQueue.length) {
    prewarmWebMixChannel(state.verifiedMixQueue[0]);
  }
  state.mixHistory.push(next);
  state.mixIndex = state.mixHistory.length - 1;
  tunePlayerChannel(next, { isMix: true, skipMarkPlayed: true });
  preverifyNextMixCandidate();
}

function isVodChannel(ch) {
  if (!ch) return false;
  if (ch.isVod || ch.contentType === 'movie' || ch.contentType === 'series') return true;
  const u = String(ch.streamUrl || '').toLowerCase();
  return u.includes('/movie/') || u.includes('/series/') || /\.(mkv|mp4|avi|m4v|mov|webm)(\?|#|$)/i.test(u);
}

function formatDurationSec(sec) {
  const s = Math.max(0, Math.floor(sec || 0));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const rem = s % 60;
  const pad = (n) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(rem)}` : `${pad(m)}:${pad(rem)}`;
}

function flushCurrentVodProgress(force = false) {
  const ch = state.playerChannel;
  const video = $('#preview-video');
  if (!ch?.id || !state.playerIsVod || !video || state.localOverrideChannels) return;
  const posMs = Math.round((video.currentTime || 0) * 1000);
  const durMs = Number.isFinite(video.duration) && video.duration > 0 ? Math.round(video.duration * 1000) : 0;
  if (!force && Math.abs(posMs - state.playerLastSavedPosMs) < 4000) return;
  if (posMs < 3000 && !force) return;
  state.playerLastSavedPosMs = posMs;
  ch.positionMs = posMs;
  if (durMs > 0) ch.durationMs = durMs;
  api(`/api/channels/${encodeURIComponent(ch.id)}/progress`, {
    method: 'POST',
    body: { positionMs: posMs, durationMs: durMs },
  }).catch(() => {});
}

function seekVodBySeconds(deltaSec) {
  const video = $('#preview-video');
  if (!video || !state.playerIsVod) return;
  const cur = video.currentTime || 0;
  const max = Number.isFinite(video.duration) && video.duration > 0 ? video.duration : cur + Math.max(0, deltaSec);
  video.currentTime = Math.max(0, Math.min(max, cur + deltaSec));
  updatePlayerTimelineUI();
  wakePlayerControls();
}

function updatePlayerTimelineUI() {
  const video = $('#preview-video');
  const scrubber = $('#player-scrubber');
  const fillEl = $('#player-scrubber-fill');
  const bufEl = $('#player-scrubber-buf');
  const thumbEl = $('#player-scrubber-thumb');
  const curEl = $('#player-time-current');
  const totEl = $('#player-time-total');
  const playBtn = state.playerIsVod ? $('#player-playpause-btn') : $('#player-live-playpause-btn');
  if (playBtn && video) {
    playBtn.innerHTML = video.paused ? SVG_ICONS.play : SVG_ICONS.pause;
  }
  if (!scrubber || !fillEl || !thumbEl || !curEl || !totEl) return;
  if (!state.playerIsVod || !video || !Number.isFinite(video.duration) || video.duration <= 0) {
    scrubber.classList.toggle('is-live', !state.playerIsVod);
    fillEl.style.width = state.playerIsVod ? '0%' : '100%';
    if (bufEl) bufEl.style.width = '0%';
    thumbEl.style.left = state.playerIsVod ? '0%' : '100%';
    curEl.textContent = state.playerIsVod ? formatDurationSec(video?.currentTime || 0) : 'LIVE';
    totEl.textContent = state.playerIsVod ? '--:--' : t('IN DIRETTA', 'LIVE TV', 'EN VIVO');
    return;
  }
  scrubber.classList.remove('is-live');
  const cur = Math.max(0, video.currentTime || 0);
  const dur = video.duration;
  const pct = Math.min(100, Math.max(0, (cur / dur) * 100));
  fillEl.style.width = `${pct.toFixed(2)}%`;
  thumbEl.style.left = `${pct.toFixed(2)}%`;
  if (bufEl) {
    const ahead = cur + getVideoBufferedAheadSec(video);
    const bufPct = Math.min(100, Math.max(pct, (ahead / dur) * 100));
    bufEl.style.width = `${bufPct.toFixed(2)}%`;
  }
  curEl.textContent = formatDurationSec(cur);
  totEl.textContent = formatDurationSec(dur);
}

function renderPlayerCarousel() {
  const listEl = $('#player-right-list');
  const titleEl = $('#right-drawer-title');
  const subEl = $('#right-drawer-sub');
  const relBtn = $('#player-related-btn');
  const isSources = state.playerCarouselMode === 'sources';
  const items = state.playerCarouselItems || [];
  if (relBtn) relBtn.hidden = items.length === 0;
  if (!listEl) return;
  if (titleEl) {
    titleEl.textContent =
      isSources
        ? t('Sorgenti canale', 'Channel sources', 'Fuentes del canal')
        : state.playerCarouselMode === 'episodes'
          ? t('Episodi', 'Episodes', 'Episodios')
          : state.playerCarouselMode === 'similar'
            ? t('Film simili', 'Similar movies', 'Películas similares')
            : t('Altri canali', 'Other channels', 'Otros canales');
  }
  if (subEl) {
    subEl.textContent = `${items.length} ${isSources ? t('sorgenti', 'sources', 'fuentes') : t('elementi', 'items', 'elementos')} · ${t('SX o ESC per chiudere', 'LEFT or ESC to close', 'IZQ o ESC para cerrar')}`;
  }
  listEl._drawerItems = items;
  if (isSources) {
    const curUrl = state.playerActiveUrl || state.playerChannel?.streamUrl || '';
    listEl.innerHTML = items
      .map((src, idx) => {
        const isAct = idx === state.playerSourceIdx || (curUrl && src.url === curUrl);
        const cur = idx === state.rightDrawerCursorIdx ? 'is-cursor' : '';
        const title = src.quality || src.label || `${t('Sorgente', 'Source', 'Fuente')} ${idx + 1}`;
        const meta = [src.protocol, src.host, src.latencyMs != null ? `${src.latencyMs} ms` : ''].filter(Boolean).join(' · ');
        return `
          <button type="button" class="drawer-item ${isAct ? 'is-playing' : ''} ${cur}" data-rd-idx="${idx}">
            <div class="drawer-item-meta">
              <strong>${esc(title)}</strong>
              <small>${esc(meta)}</small>
            </div>
            ${src.recommended ? `<span class="src-rec-pill">${t('Migliore', 'Best', 'Mejor')}</span>` : ''}
          </button>
        `;
      })
      .join('');
  } else {
    listEl.innerHTML = items
      .map((item, idx) => {
        const act = item.id === state.playerChannel?.id ? 'is-playing' : '';
        const cur = idx === state.rightDrawerCursorIdx ? 'is-cursor' : '';
        const badge = item.number ? `${item.number}. ` : '';
        return `
          <button type="button" class="drawer-item ${act} ${cur}" data-rd-idx="${idx}">
            <div class="drawer-item-box">
              ${item.logo ? `<img src="${esc(item.logo)}" alt="" loading="lazy" decoding="async" />` : `<span style="font-size:10px;font-weight:800;color:var(--accent)">${esc(initials(item.name))}</span>`}
            </div>
            <div class="drawer-item-meta">
              <strong>${esc(badge)}${esc(item.name)}</strong>
              <small>${esc((item.categoryNames && item.categoryNames[0]) || item.quality || '')}</small>
            </div>
          </button>
        `;
      })
      .join('');
  }

  if (listEl.dataset.bound !== '1') {
    listEl.dataset.bound = '1';
    listEl.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-rd-idx]');
      if (!btn) return;
      const idx = Number(btn.dataset.rdIdx);
      const item = (listEl._drawerItems || [])[idx];
      if (!item) return;
      if (state.playerCarouselMode === 'sources') {
        state.playerSourceIdx = idx;
        toggleRightDrawer(false);
        reportWebChannelSource(state.playerChannel?.id, item.url, 'manual_select');
        tunePlayerChannel({ ...state.playerChannel, streamUrl: item.url }, { preserveSourceIdx: true, pinSource: true });
      } else {
        toggleRightDrawer(false);
        tunePlayerChannel(item, { isMix: false });
      }
    });
    listEl.addEventListener(
      'error',
      (e) => {
        if (e.target?.tagName === 'IMG') e.target.remove();
      },
      true,
    );
  }
}

function toggleRightDrawer(forceOpen) {
  const items = state.playerCarouselItems || [];
  if (!items.length && forceOpen !== false) return;
  const next = typeof forceOpen === 'boolean' ? forceOpen : !state.rightDrawerOpen;
  if (next && state.drawerOpen) togglePlayerDrawer(false);
  state.rightDrawerOpen = next;
  const panel = $('#player-right-drawer');
  if (panel) panel.hidden = !next;
  if (next) {
    hideTemporaryHintsBanner();
    const curIdx = state.playerCarouselMode === 'sources'
      ? Math.max(0, state.playerSourceIdx || 0)
      : Math.max(0, items.findIndex((c) => c.id === state.playerChannel?.id));
    state.rightDrawerCursorIdx = curIdx;
    renderPlayerCarousel();
    wakePlayerControls();
    requestAnimationFrame(() => {
      const target = $(`#player-right-list .drawer-item[data-rd-idx="${state.rightDrawerCursorIdx}"]`);
      target?.scrollIntoView({ block: 'center' });
      target?.focus();
    });
  } else {
    wakePlayerControls();
  }
}

async function openSeriesModal(channel) {
  const modal = $('#series-modal');
  if (!modal) {
    openPlayer(channel, [channel], { isMix: false });
    return;
  }
  state.seriesDetail = null;
  $('#series-modal-title').textContent = channel.seriesTitle || channel.name;
  $('#series-modal-sub').textContent = t('Caricamento episodi…', 'Loading episodes…', 'Cargando episodios…');
  $('#series-episodes-list').innerHTML = `<div class="loading-box"><div class="spinner"></div></div>`;
  $('#series-season-select').innerHTML = '';
  $('#series-hero-poster').innerHTML = channel.logo
    ? `<img src="${esc(channel.logo)}" alt="" />`
    : `<span style="font-size:22px;font-weight:800;color:var(--accent)">${esc(initials(channel.seriesTitle || channel.name))}</span>`;
  modal.hidden = false;
  try {
    const detail = await api(`/api/vod/series/${encodeURIComponent(channel.id)}`);
    state.seriesDetail = detail;
    const seasons = detail.seasons || [];
    const resumeId = detail.resumeEpisodeId || null;
    const resumeSeason = seasons.find((s) => (s.episodes || []).some((e) => e.id === resumeId));
    state.seriesSeason = resumeSeason ? resumeSeason.season : (seasons[0]?.season || 1);
    renderSeriesModal();
  } catch (_) {
    modal.hidden = true;
    openPlayer(channel, [channel], { isMix: false });
  }
}

function closeSeriesModal() {
  const modal = $('#series-modal');
  if (modal) modal.hidden = true;
}

function renderSeriesModal() {
  const detail = state.seriesDetail;
  if (!detail) return;
  const seasons = detail.seasons || [];
  $('#series-modal-title').textContent = detail.showTitle || 'Serie TV';
  const totalEps = detail.totalEpisodes || seasons.reduce((acc, s) => acc + (s.episodes?.length || 0), 0);
  const subParts = [
    detail.flag ? `${detail.flag} ${detail.countryName || detail.country || ''}`.trim() : '',
    detail.category || '',
    `${seasons.length} ${seasons.length === 1 ? t('Stagione', 'Season', 'Temporada') : t('Stagioni', 'Seasons', 'Temporadas')}`,
    `${totalEps} ${t('episodi', 'episodes', 'episodios')}`,
  ].filter(Boolean);
  $('#series-modal-sub').textContent = subParts.join(' · ');

  const selectEl = $('#series-season-select');
  selectEl.hidden = seasons.length <= 1;
  selectEl.innerHTML = seasons
    .map((s) => `<option value="${s.season}" ${Number(s.season) === Number(state.seriesSeason) ? 'selected' : ''}>${t('Stagione', 'Season', 'Temporada')} ${s.season}</option>`)
    .join('');

  const allEpisodes = seasons.flatMap((s) => s.episodes || []);
  const activeSeasonObj = seasons.find((s) => Number(s.season) === Number(state.seriesSeason)) || seasons[0];
  const eps = activeSeasonObj?.episodes || [];
  const resumeEp = allEpisodes.find((e) => e.id === detail.resumeEpisodeId) || eps[0] || allEpisodes[0];
  const playBtn = $('#series-play-btn');
  if (playBtn && resumeEp) {
    const label = resumeEp.positionMs > 15000
      ? `${t('Riprendi', 'Resume', 'Reanudar')} S${String(resumeEp.seasonNumber || 1).padStart(2, '0')}E${String(resumeEp.episodeNumber || 1).padStart(2, '0')}`
      : (resumeEp.upNext
          ? `${t('Prossimo', 'Up Next', 'Siguiente')} S${String(resumeEp.seasonNumber || 1).padStart(2, '0')}E${String(resumeEp.episodeNumber || 1).padStart(2, '0')}`
          : `${t('Riproduci', 'Play', 'Reproducir')} S${String(resumeEp.seasonNumber || 1).padStart(2, '0')}E${String(resumeEp.episodeNumber || 1).padStart(2, '0')}`);
    playBtn.innerHTML = `${SVG_ICONS.play} ${esc(label)}`;
    playBtn.onclick = () => {
      closeSeriesModal();
      openPlayer(resumeEp, allEpisodes, { isMix: false });
    };
  }

  const listEl = $('#series-episodes-list');
  listEl._episodes = eps;
  listEl._allEpisodes = allEpisodes;
  listEl.innerHTML = eps
    .map((ep, idx) => {
      const epNum = ep.episodeNumber || idx + 1;
      const pct = ep.durationMs > 0 && ep.positionMs > 15000
        ? Math.min(98, Math.max(4, Math.round((ep.positionMs / ep.durationMs) * 100)))
        : 0;
      const isRes = ep.id === detail.resumeEpisodeId;
      const epDisplayTitle = ep.episodeTitle
        ? `${ep.episodeTitle}`
        : `${t('Episodio', 'Episode', 'Episodio')} ${epNum}`;
      const statusText = ep.upNext
        ? t('Prossimo episodio', 'Up next', 'Siguiente episodio')
        : (pct > 0 ? `${t('Riprendi da', 'Resume at')} ${formatDurationSec(Math.round(ep.positionMs / 1000))}` : ep.name);
      return `
        <button type="button" class="series-ep-row ${isRes ? 'is-resume' : ''}" data-ep-idx="${idx}">
          <span class="series-ep-num">${epNum}</span>
          <div class="series-ep-thumb">
            ${ep.logo ? `<img src="${esc(ep.logo)}" alt="" loading="lazy" decoding="async" />` : `<span style="font-size:12px;font-weight:800;color:var(--text)">E${epNum}</span>`}
            ${pct > 0 ? `<div class="series-ep-progress"><i style="width:${pct}%"></i></div>` : ''}
          </div>
          <div class="series-ep-info">
            <strong>${esc(epDisplayTitle)}</strong>
            <small>${esc(statusText)}</small>
          </div>
        </button>
      `;
    })
    .join('');

  if (listEl.dataset.bound !== '1') {
    listEl.dataset.bound = '1';
    listEl.addEventListener('click', (e) => {
      const row = e.target.closest('[data-ep-idx]');
      if (!row) return;
      const ep = (listEl._episodes || [])[Number(row.dataset.epIdx)];
      if (!ep) return;
      closeSeriesModal();
      openPlayer(ep, listEl._allEpisodes || listEl._episodes || [ep], { isMix: false });
    });
  }
}

function hideNextEpisodePrompt() {
  clearInterval(state.nextEpisodeTimer);
  state.nextEpisodeTimer = null;
  state.nextEpisodeCountdown = 0;
  const card = $('#player-next-ep-card');
  if (card) card.hidden = true;
}

function updateNextEpisodeCountdown(remainingMs, nextEp) {
  const card = $('#player-next-ep-card');
  if (!card || !nextEp || !state.playerOpen) return;
  const clampedMs = Math.max(0, Math.min(60000, remainingMs));
  const secInt = Math.max(0, Math.min(60, Math.ceil(clampedMs / 1000)));
  const frac = clampedMs / 60000;
  const circumference = 2 * Math.PI * 34;
  const secEl = $('#next-ep-sec');
  if (secEl) secEl.textContent = String(secInt);
  const fgEl = $('#next-ep-ring-fg');
  if (fgEl) {
    fgEl.setAttribute('transform', 'rotate(-90 40 40) scale(1, -1) translate(0 -80)');
    fgEl.style.strokeDasharray = `${circumference.toFixed(2)}`;
    fgEl.style.strokeDashoffset = `${(circumference * (1 - frac)).toFixed(2)}`;
    fgEl.style.opacity = secInt > 0 ? '1' : '0';
  }
  const capEl = $('#next-ep-caption');
  if (capEl) {
    capEl.textContent = state.deviceType === 'smartphone'
      ? t('Tocca per il prossimo episodio', 'Tap for next episode', 'Toca para el siguiente episodio')
      : t('Premi OK per il prossimo episodio', 'Press OK for next episode', 'Pulsa OK para el siguiente episodio');
  }
  card.hidden = false;
}

function showNextEpisodePrompt(nextEp) {
  if (!nextEp) return;
  updateNextEpisodeCountdown(60000, nextEp);
}

function cancelLiveRecHold() {
  clearInterval(state.recHoldTimer);
  state.recHoldTimer = null;
  state.recHoldStartMs = 0;
  const overlay = $('#player-rec-hold-overlay');
  if (overlay) overlay.hidden = true;
}

function syncRecordingHudUI() {
  const rec = state.activeRecording;
  const pill = $('#player-rec-pill');
  if (pill) pill.hidden = !rec || !state.playerOpen || state.playerIsVod;
  const recBtn = $('#player-rec-btn');
  if (recBtn) {
    recBtn.hidden = !state.playerOpen || state.playerIsVod || Boolean(state.localOverrideChannels);
    recBtn.textContent = rec
      ? t('Stop REC', 'Stop REC', 'Detener REC')
      : 'REC (3s)';
    recBtn.classList.toggle('is-active', Boolean(rec));
  }
}

function beginLiveRecHold() {
  if (!state.playerOpen || state.playerIsVod || state.recBusy || state.activeRecording || state.recHoldTimer || state.localOverrideChannels) return;
  state.recHoldStartMs = Date.now();
  const overlay = $('#player-rec-hold-overlay');
  const secEl = $('#player-rec-hold-sec');
  const msgEl = $('#player-rec-hold-msg');
  if (msgEl) {
    msgEl.textContent = t(
      'Continua a tenere premuto per registrare (salvata per 7 giorni)',
      'Keep holding to record (saved for 7 days)',
      'Mantén pulsado para grabar (guardada 7 días)',
    );
  }
  state.recHoldTimer = setInterval(async () => {
    const elapsed = Date.now() - state.recHoldStartMs;
    if (elapsed >= 1000 && elapsed < 3000) {
      const left = Math.max(1, Math.min(2, Math.ceil((3000 - elapsed) / 1000)));
      if (secEl) secEl.textContent = String(left);
      if (overlay) overlay.hidden = false;
    } else if (elapsed >= 3000) {
      const ch = state.playerChannel;
      cancelLiveRecHold();
      if (!ch || state.recBusy || state.activeRecording) return;
      state.recBusy = true;
      try {
        const res = await api('/api/recordings/start', {
          method: 'POST',
          body: {
            channelId: ch.id,
            name: ch.name,
            streamUrl: ch.streamUrl,
            logo: ch.logo || '',
          },
        });
        state.activeRecording = res?.session || res?.recording || res || null;
        syncRecordingHudUI();
        toast(t('Registrazione avviata (salvata per 7 giorni)', 'Recording started (saved for 7 days)'));
      } catch (err) {
        toast(err.message || t('Errore avvio registrazione', 'Recording error'), 'err');
      } finally {
        state.recBusy = false;
      }
    }
  }, 50);
}

async function stopActiveLiveRecording() {
  const rec = state.activeRecording;
  if (!rec?.id || state.recBusy) return;
  state.recBusy = true;
  try {
    await api(`/api/recordings/${encodeURIComponent(rec.id)}/stop`, { method: 'POST', body: {} });
    state.activeRecording = null;
    syncRecordingHudUI();
    toast(t('Registrazione salvata per 7 giorni', 'Recording saved for 7 days'));
  } catch (err) {
    toast(err.message, 'err');
  } finally {
    state.recBusy = false;
  }
}

async function openRecordingsModal() {
  const modal = $('#recordings-modal');
  const listEl = $('#recordings-list');
  if (!modal || !listEl) return;
  modal.hidden = false;
  listEl.innerHTML = `<div class="loading-box"><div class="spinner"></div></div>`;
  try {
    const data = await api('/api/recordings');
    state.recordings = data?.items || [];
    renderRecordingsList();
  } catch (err) {
    listEl.innerHTML = `<div class="empty-box"><p>${esc(err.message)}</p></div>`;
  }
}

function renderRecordingsList() {
  const listEl = $('#recordings-list');
  if (!listEl) return;
  const recs = state.recordings || [];
  if (!recs.length) {
    listEl.innerHTML = `<div class="empty-box"><p>${t('Nessuna registrazione salvata.', 'No saved recordings.')}</p></div>`;
    return;
  }
  listEl.innerHTML = recs
    .map((rec, idx) => {
      const isAct = rec.status === 'recording' || rec.status === 'starting';
      const daysLeft = Math.max(0, Math.floor((Number(rec.expiresAtMs || 0) - Date.now()) / 86400000));
      const durMin = Math.round((Number(rec.recordedMs || 0)) / 60000);
      const dlUrl = `${state.serverUrl}/api/recordings/${encodeURIComponent(rec.id)}/download`;
      return `
        <div class="recording-row" data-rec-idx="${idx}">
          <div class="recording-row-meta">
            <strong>${isAct ? '● REC · ' : ''}${esc(rec.channelName || 'Registrazione')}</strong>
            <small>${durMin}m · ${fmtBytes(Number(rec.bytes || 0))} · ${isAct ? t('In registrazione', 'Recording live') : `${daysLeft} ${t('giorni rimasti', 'days left')}`}</small>
          </div>
          <div class="recording-row-actions">
            <button type="button" class="casa-btn primary sm" data-rec-play="${idx}">${t('Guarda', 'Watch')}</button>
            <a class="casa-btn ghost sm" href="${esc(dlUrl)}" download style="text-decoration:none">${t('Scarica', 'Download')}</a>
            ${isAct ? `<button type="button" class="casa-btn ghost sm" data-rec-stop="${idx}">${t('Stop', 'Stop')}</button>` : ''}
            <button type="button" class="casa-btn danger sm" data-rec-del="${idx}">${t('Elimina', 'Delete')}</button>
          </div>
        </div>
      `;
    })
    .join('');
}

/* ---------------- Player, Quick Drawer & Multi-TV Sync ---------------- */
const webBufDiagThrottle = new Map();
function reportWebBufferingDiag(code, message, extra = {}) {
  try {
    const ch = state.playerChannel;
    if (!ch || state.localOverrideChannels) return;
    const now = Date.now();
    const tKey = `${ch.id}:${code}`;
    if (now - (webBufDiagThrottle.get(tKey) || 0) < 8000) return;
    webBufDiagThrottle.set(tKey, now);
    if (webBufDiagThrottle.size > 200) webBufDiagThrottle.clear();

    const video = $('#player-video');
    let bufferAheadMs = -1;
    if (video && video.buffered && video.buffered.length) {
      try {
        bufferAheadMs = Math.max(0, Math.round((video.buffered.end(video.buffered.length - 1) - (video.currentTime || 0)) * 1000));
      } catch (_) {}
    }
    const details = {
      bufferAheadMs,
      readyState: video?.readyState ?? -1,
      networkState: video?.networkState ?? -1,
      ...extra,
    };
    const payload = {
      code,
      severity: extra?.fatal ? 'error' : 'warn',
      source: 'web',
      deviceId: state.deviceId,
      deviceName: `Web (${state.deviceType})`,
      appVersion: WEB_APP_VERSION,
      channelId: ch.id || '',
      channelName: ch.name || '',
      playbackMode: state.relayPlaylistUrl ? 'relay' : (ch.isVod ? 'vod' : 'direct'),
      streamUrl: state.playerActiveUrl || ch.streamUrl || '',
      message: String(message || '').slice(0, 400),
      details,
    };
    const base = state.serverUrl && state.serverUrl !== window.location.origin ? state.serverUrl : '';
    fetch(`${base}/api/diagnostics/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }).catch(() => {});
  } catch (_) {}
}

function openPlayer(channel, queue, { isMix = false, sources = null, sourceIdx = -1, pinSource = false } = {}) {
  stopCardPreview();
  closeCardContextMenu();
  state.playerOpen = true;
  state.playerQueue = queue && queue.length ? queue : [channel];
  state.playerIsMix = isMix;
  state.drawerOpen = false;
  state.rightDrawerOpen = false;
  $('#player-quick-drawer').hidden = true;
  if ($('#player-right-drawer')) $('#player-right-drawer').hidden = true;
  $('#player-modal').hidden = false;
  const hasPreloaded = Array.isArray(sources) && sources.length > 0 && sourceIdx >= 0 && sourceIdx < sources.length;
  if (hasPreloaded) {
    state.playerSources = sources;
    state.playerSourceIdx = sourceIdx;
  }
  const targetCh = hasPreloaded ? { ...channel, streamUrl: sources[sourceIdx].url } : channel;
  tunePlayerChannel(targetCh, {
    isMix,
    skipMarkPlayed: isMix,
    preserveSourceIdx: hasPreloaded,
    pinSource: hasPreloaded && pinSource,
  });
  updatePlayerChromeVisibility();
  wakePlayerControls();
  showTemporaryHintsBanner();
}

function closePlayer() {
  flushCurrentVodProgress(true);
  state.playerOpen = false;
  state.tuneSeq += 1;
  if (state.relayTunedChannelId) {
    const relId = state.relayTunedChannelId;
    state.relayTunedChannelId = null;
    api('/api/relay/release', {
      method: 'POST',
      body: { deviceId: state.deviceId, channelId: relId },
    }).catch(() => {});
  }
  const v = $('#preview-video');
  if (v) v.controls = false;
  stopWebRecoveryLoops();
  stopPlayerStream();
  stopMultiTvSync();
  closeDrawerPreview();
  hideTemporaryHintsBanner();
  hideNextEpisodePrompt();
  cancelLiveRecHold();
  state.activeRecording = null;
  syncRecordingHudUI();
  state.nextEpisodeCandidate = null;
  state.nextEpisodeDismissedId = null;
  renderAudioOnlyStage(null, false);
  state.drawerOpen = false;
  state.rightDrawerOpen = false;
  $('#player-quick-drawer').hidden = true;
  if ($('#player-right-drawer')) $('#player-right-drawer').hidden = true;
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
  clearInterval(state.playerProgressTimer);
  state.playerProgressTimer = null;
  state.reconnectActive = false;
  state.cushionBuilding = false;
  const pill = $('#player-reconnect-pill');
  if (pill) pill.hidden = true;
}

function reportWebChannelSource(channelId, streamUrl, eventName, latencyMs = -1) {
  if (!channelId || !streamUrl || state.localOverrideChannels) return;
  api(`/api/channels/${encodeURIComponent(channelId)}/source-report`, {
    method: 'POST',
    body: {
      url: streamUrl,
      event: eventName,
      latencyMs,
    },
  }).catch(() => {});
}

function hideTemporaryHintsBanner() {
  clearTimeout(state.hintsBannerTimer);
  state.hintsBannerTimer = null;
  const banner = $('#player-hints-banner');
  if (banner) banner.hidden = true;
}

function showTemporaryHintsBanner() {
  // Intentionally disabled: channel opens cleanly without overlay button hints
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
  const catLabel = (ch.categoryNames && ch.categoryNames[0]) || (ch.categories && ch.categories[0]) || '';
  const subParts = [cName, catLabel && catLabel.toLowerCase() !== cName.toLowerCase() ? catLabel : ''].filter(Boolean);
  if (subEl) subEl.innerHTML = `${normalizeFlagCode(ch.country) ? `${flagHtml(ch.country, 'sm')} ` : ''}${esc(subParts.join(' · '))}`;

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
    video.ontimeupdate = null;
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

function tunePlayerChannel(ch, { isMix = state.playerIsMix, skipMarkPlayed = isMix, preserveSourceIdx = false, pinSource = false } = {}) {
  if (!ch) return;
  flushCurrentVodProgress(true);
  const seq = ++state.tuneSeq;
  stopWebRecoveryLoops();
  stopPlayerStream();
  const vodMode = !isMix && isVodChannel(ch);
  state.playerIsVod = vodMode;
  state.playerPlaybackRate = 1;
  state.playerLastSavedPosMs = 0;
  state.reconnectAttempt = 0;
  state.reconnectActive = false;
  state.cushionBuilding = false;
  state.hasPlayedCurrentChannel = false;
  state.currentStreamAudioOnly = false;
  state.bufferingSinceMs = 0;
  state.readySinceMs = 0;
  state.lastStallTime = -1;
  state.lastStallWallMs = 0;
  state.playerActiveUrl = '';
  state.relayPlaylistUrl = '';
  state.playerChannel = ch;
  if (!preserveSourceIdx) {
    state.playerSources = [];
    state.playerSourceIdx = 0;
  }
  state.playbackState = 'playing';
  state.playbackChannel = ch.name;
  renderAudioOnlyStage(ch, false);

  // Update Liquid Glass + LG Channels HUD
  const mixBadge = $('#strip-mix-badge');
  mixBadge.hidden = !isMix;
  if (isMix) {
    mixBadge.textContent = `MIX · ${state.mixIndex + 1}/${state.mixHistory.length}`;
  }
  $('#strip-num-badge').textContent = vodMode
    ? (ch.contentType === 'series' ? 'SERIE' : 'FILM')
    : (ch.number ? String(ch.number) : (normalizeFlagCode(ch.country).toUpperCase() || 'TV'));
  $('#player-ch-name').textContent = (vodMode && ch.contentType === 'series' && ch.seriesTitle) ? ch.seriesTitle : (ch.name || 'Canale');
  const subEl = $('#player-ch-sub');
  const bottomLbl = $('#player-bottom-label');
  if (subEl) {
    if (vodMode && ch.contentType === 'series' && ch.seasonNumber && ch.episodeNumber) {
      const epT = ch.episodeTitle ? ` "${ch.episodeTitle}"` : '';
      subEl.textContent = `S${ch.seasonNumber}:E${ch.episodeNumber}${epT}`;
      subEl.hidden = false;
    } else {
      subEl.textContent = '';
      subEl.hidden = true;
    }
  }
  if (bottomLbl) {
    bottomLbl.textContent = (vodMode && ch.contentType === 'series' && ch.seasonNumber && ch.episodeNumber)
      ? `${ch.seriesTitle || ch.name}  S${ch.seasonNumber}:E${ch.episodeNumber}${ch.episodeTitle ? ` ${ch.episodeTitle}` : ''}`
      : (ch.name || '');
  }
  const tracksPop = $('#player-tracks-popover');
  if (tracksPop) tracksPop.hidden = true;
  const srcPop = $('#player-source-popover');
  if (srcPop) srcPop.hidden = true;
  const catLabel = $('#player-ch-cat');
  const catText = (ch.categoryNames && ch.categoryNames[0]) || '';
  if (catLabel) {
    catLabel.hidden = !catText;
    catLabel.textContent = catText;
  }
  const modePill = $('#player-mode-pill');
  if (modePill) {
    modePill.classList.toggle('is-vod', vodMode);
    modePill.innerHTML = vodMode
      ? (ch.contentType === 'series' ? 'SERIE TV' : 'FILM')
      : `<span class="live-dot-inline"></span>LIVE`;
  }
  const liveCtrl = $('#player-live-controls');
  const vodCtrl = $('#player-vod-controls');
  if (liveCtrl) liveCtrl.hidden = vodMode;
  if (vodCtrl) vodCtrl.hidden = !vodMode;
  const speedBtn = $('#player-speed-btn');
  if (speedBtn) {
    speedBtn.hidden = !vodMode;
    speedBtn.textContent = '1x';
  }
  const nextEpBtn = $('#player-next-ep-btn');
  hideNextEpisodePrompt();
  cancelLiveRecHold();
  syncRecordingHudUI();
  state.nextEpisodeCandidate = null;
  if (nextEpBtn) nextEpBtn.hidden = true;
  updatePlayerTimelineUI();

  // Populate Right Drawer items (Live TV Sources on Live, or Episodes / Similar on VOD)
  if (!vodMode) {
    state.playerCarouselItems = state.playerSources || [];
    state.playerCarouselMode = 'sources';
    renderPlayerCarousel();
  } else {
    state.playerCarouselItems = [];
    state.playerCarouselMode = 'similar';
    renderPlayerCarousel();
    if (ch.id && !state.localOverrideChannels) {
      api(`/api/vod/related/${encodeURIComponent(ch.id)}?limit=60`)
        .then((res) => {
          if (!state.playerOpen || state.tuneSeq !== seq) return;
          const rel = res?.items || [];
          state.playerCarouselMode = res?.mode || 'similar';
          state.playerCarouselItems = rel;
          renderPlayerCarousel();
          if (res?.mode === 'episodes' && rel.length > 1) {
            const idx = rel.findIndex((it) => it.id === ch.id);
            const nextEp = idx >= 0 && idx + 1 < rel.length ? rel[idx + 1] : null;
            state.nextEpisodeCandidate = nextEp;
            if (nextEpBtn) {
              nextEpBtn.hidden = !nextEp;
              nextEpBtn.onclick = () => {
                if (nextEp) tunePlayerChannel(nextEp, { isMix: false });
              };
            }
          }
        })
        .catch(() => {});
    }
  }

  const hasCountryCode = Boolean(normalizeFlagCode(ch.country));
  const resolvedCountryName = getLocalizedCountryName(ch.country, ch.countryName);
  const countryWrap = $('#player-ch-country');
  if (countryWrap) {
    if (hasCountryCode || resolvedCountryName) {
      $('#player-ch-flag').innerHTML = hasCountryCode ? flagHtml(ch.country, 'sm') : '';
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
  const iosNative = isIOSDevice();
  if (video) {
    video.controls = iosNative;
    video.playsInline = true;
    if (iosNative) {
      video.setAttribute('webkit-playsinline', '');
      video.setAttribute('x-webkit-airplay', 'allow');
    }
  }
  const loading = $('#player-loading');
  const errBox = $('#player-error');
  const reconnectPill = $('#player-reconnect-pill');
  const reconnectLabel = $('#player-reconnect-label');
  loading.hidden = false;
  errBox.hidden = true;
  if (reconnectPill) reconnectPill.hidden = true;
  $('#player-tuning-label').textContent = t('Sintonizzazione…', 'Tuning…', 'Sintonizando…');

  let url = (vodMode && ch.vodStreamUrl) ? ch.vodStreamUrl : ch.streamUrl;
  if (vodMode && needsVodHlsRemux(ch, video)) {
    url = `/api/vod/hls/${encodeURIComponent(ch.id)}/playlist.m3u8`;
  }
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
    if (state._webBufStartMs > 0 && state._webBufSeq === seq) {
      const durMs = Date.now() - state._webBufStartMs;
      state._webBufStartMs = 0;
      if (durMs >= 1500 && !state.reconnectActive) {
        reportWebBufferingDiag(
          'WEB_BUF_SHORT',
          `Pausa buffering web (${durMs}ms${state._lastHlsErr ? ` · ${state._lastHlsErr}` : ''})`,
          {
            bufferingDurationMs: durMs,
            reason: state._lastHlsErr || 'waiting_underrun',
            hlsDetails: state._lastHlsErr || '',
            httpStatus: state._lastHlsHttpStatus || 0,
            isVod: Boolean(vodMode),
          },
        );
        state._lastHlsErr = '';
        state._lastHlsHttpStatus = 0;
      }
    }
    clearTimeout(state.playerTuneFailTimer);
    state.playerTuneFailTimer = null;
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
    clearTimeout(state.playerTuneFailTimer);
    state.playerTuneFailTimer = null;
    loading.hidden = true;
    renderAudioOnlyStage(ch, false);
    hideTemporaryHintsBanner();
    if (reconnectPill) reconnectPill.hidden = true;
    state.reconnectActive = false;
    if (isMix && state.mixErrorRetryCount < 5) {
      state.deadMixIds.add(ch.id);
      if (!state.localOverrideChannels) {
        api('/api/mix/report-dead', { method: 'POST', body: { id: ch.id } }).catch(() => {});
      }
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
        let targetUrl = state.playerActiveUrl || state.playerChannel?.streamUrl || url;
        if (state.reconnectAttempt === 3 && state.playerSources.length > 1) {
          state.playerSourceIdx = (state.playerSourceIdx + 1) % state.playerSources.length;
          targetUrl = state.playerSources[state.playerSourceIdx].url;
          state.playerChannel = { ...state.playerChannel, streamUrl: targetUrl };
          state.playerActiveUrl = targetUrl;
          state.relayPlaylistUrl = '';
        }
        if (iosNative && !vodMode && (targetUrl.includes('/api/relay/') || reason === 'media_error' || reason === 'stall_watchdog')) {
          tunePlayerChannel(state.playerChannel, { isMix, skipMarkPlayed: true, preserveSourceIdx: true });
          return;
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
        if (iosNative && !vodMode && state.playerChannel) {
          tunePlayerChannel(state.playerChannel, { isMix, skipMarkPlayed: true, preserveSourceIdx: true });
          return;
        }
        attachStream(state.playerActiveUrl || state.playerChannel?.streamUrl || url, { keepFrame: true });
      }, 15000);
    }
  };

  const safePlayVideo = () => {
    const p = video.play();
    if (!p || typeof p.then !== 'function') return Promise.resolve();
    return p.catch((err) => {
      if (!state.playerOpen || state.tuneSeq !== seq) return;
      if (err && (err.name === 'NotAllowedError' || /user gesture|not allowed/i.test(err.message || ''))) {
        video.muted = true;
        const muteBtn = $('#player-mute-btn');
        if (muteBtn) muteBtn.innerHTML = SVG_ICONS.volumeOff;
        return video.play().catch(() => {
          onFail(t('Tocca OK o Play per avviare la riproduzione.', 'Tap Play to start playback.', 'Toca Reproducir para iniciar.'));
        });
      }
    });
  };

  const bindVodHandlers = () => {
    if (!vodMode) return;
    let resumeApplied = false;
    const applyVodMeta = () => {
      if (!state.playerOpen || state.tuneSeq !== seq) return;
      if (!resumeApplied) {
        resumeApplied = true;
        const resumeSec = Number(ch.positionMs || 0) / 1000;
        if (resumeSec > 15 && (!Number.isFinite(video.duration) || resumeSec < video.duration - 15)) {
          try {
            video.currentTime = resumeSec;
          } catch (_) {}
        }
      }
      if (state.playerPlaybackRate && state.playerPlaybackRate !== 1) {
        video.playbackRate = state.playerPlaybackRate;
      }
      updatePlayerTimelineUI();
    };
    video.onloadedmetadata = () => {
      applyVodMeta();
      safePlayVideo();
      onReady();
      if (iosNative && state.playerOpen && state.tuneSeq === seq) {
        try {
          if (video.webkitSupportsFullscreen && !video.webkitDisplayingFullscreen) {
            video.webkitEnterFullscreen();
          }
        } catch (_) {}
      }
    };
    video.ontimeupdate = () => {
      if (!state.playerOpen || state.tuneSeq !== seq) return;
      updatePlayerTimelineUI();
      if (
        state.nextEpisodeCandidate &&
        state.nextEpisodeDismissedId !== ch.id &&
        Number.isFinite(video.duration) &&
        video.duration > 65
      ) {
        const remSec = Math.max(0, video.duration - video.currentTime);
        if (remSec <= 60) {
          updateNextEpisodeCountdown(remSec * 1000, state.nextEpisodeCandidate);
          if (remSec <= 0.25) {
            const nextEp = state.nextEpisodeCandidate;
            hideNextEpisodePrompt();
            tunePlayerChannel(nextEp, { isMix: false });
          }
        } else if (!$('#player-next-ep-card')?.hidden) {
          hideNextEpisodePrompt();
        }
      }
    };
    video.onended = () => {
      if (!state.playerOpen || state.tuneSeq !== seq) return;
      flushCurrentVodProgress(true);
      loadVodContinue().then(() => { if (state.page === 'vod') renderCurrentScreen(); });
      if (state.nextEpisodeCandidate) {
        const nextEp = state.nextEpisodeCandidate;
        hideNextEpisodePrompt();
        tunePlayerChannel(nextEp, { isMix: false });
      }
    };
  };

  attachStream = (streamUrl, { keepFrame = false } = {}) => {
    if (!state.playerOpen || state.tuneSeq !== seq) return;
    stopPlayerStream({ keepFrame });
    if (!streamUrl) {
      onFail(t('Questo canale non ha un flusso valido o disponibile.', 'This channel has no valid or available stream.'));
      return;
    }
    state.playerActiveUrl = streamUrl;
    clearTimeout(state.playerTuneFailTimer);
    state.playerTuneFailTimer = setTimeout(() => {
      if (!state.playerOpen || state.tuneSeq !== seq) return;
      if (!state.hasPlayedCurrentChannel && errBox.hidden && !state.reconnectActive) {
        scheduleWebReconnect('tune_timeout');
      }
    }, 15000);
    setVideoPreservesPitch(video, true);
    video.playbackRate = vodMode && state.playerPlaybackRate ? state.playerPlaybackRate : 1.0;
    state._webBufStartMs = 0;
    state._webBufSeq = seq;
    state._lastHlsErr = '';
    state._lastHlsHttpStatus = 0;
    video.onwaiting = () => {
      if (!state.playerOpen || state.tuneSeq !== seq) return;
      if (state.hasPlayedCurrentChannel && !state.reconnectActive && !state._webBufStartMs) {
        state._webBufStartMs = Date.now();
      }
    };
    video.onplaying = onReady;
    video.onloadeddata = clearAudioOnlyIfVideo;
    video.onresize = clearAudioOnlyIfVideo;
    if (!vodMode) {
      video.onended = () => {
        if (state.playerOpen && state.tuneSeq === seq && (state.hasPlayedCurrentChannel || !Number.isFinite(video.duration))) {
          scheduleWebReconnect('ended');
        }
      };
    } else {
      bindVodHandlers();
    }

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
    } else if (!(isSafariBrowser() && streamUrl.includes('/api/vod/hls/')) && !isIOSDevice() && window.Hls && window.Hls.isSupported() && isHlsStreamUrl(streamUrl)) {
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
        safePlayVideo().then(onReady);
      });
      hls.on(window.Hls.Events.FRAG_BUFFERED, () => {
        fatalCount = 0;
      });
      hls.on(window.Hls.Events.BUFFER_CREATED, (_, data) => {
        const trackKeys = Object.keys(data?.tracks || {});
        if (trackKeys.length && trackKeys.includes('audio') && !trackKeys.includes('video') && !trackKeys.includes('audiovideo')) {
          handleConfirmedAudioOnly();
        }
      });
      hls.on(window.Hls.Events.ERROR, (_, data) => {
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        const det = String(data?.details || data?.type || '');
        const httpSt = Number(data?.response?.code || 0);
        if (det) state._lastHlsErr = det;
        if (httpSt > 0) state._lastHlsHttpStatus = httpSt;
        if (
          data?.fatal ||
          det === 'bufferStalledError' ||
          det === 'fragLoadError' ||
          det === 'fragLoadTimeOut' ||
          det === 'manifestLoadError' ||
          det === 'levelLoadError'
        ) {
          reportWebBufferingDiag(
            det === 'bufferStalledError' ? 'WEB_BUF_STALL' : 'WEB_HLS_ERR',
            `HLS ${det || 'error'}${httpSt ? ` (HTTP ${httpSt})` : ''}`,
            {
              hlsType: String(data?.type || ''),
              hlsDetails: det,
              httpStatus: httpSt,
              fatal: Boolean(data?.fatal),
              isVod: Boolean(vodMode),
            },
          );
        }
        if (!data.fatal) return;
        fatalCount += 1;
        if (fatalCount === 1 && data.type === window.Hls.ErrorTypes.NETWORK_ERROR) {
          hls.startLoad();
        } else if (fatalCount === 1 && data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
          hls.recoverMediaError();
        } else if (fatalCount === 2 && data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
          hls.swapAudioCodec();
          hls.recoverMediaError();
        } else {
          hls.destroy();
          state.hls = null;
          scheduleWebReconnect(data.details || data.type || 'hls_error');
        }
      });
    } else if (!isIOSDevice() && isMpegTsStreamUrl(streamUrl) && !window.mpegts) {
      ensureVendorScript('/vendor/mpegts.min.js')
        .then(() => {
          if (state.playerOpen && state.tuneSeq === seq) attachStream(streamUrl, { keepFrame });
        })
        .catch(() => scheduleWebReconnect('mpegts_script_failed'));
    } else if (!isIOSDevice() && isMpegTsStreamUrl(streamUrl) && window.mpegts?.isSupported?.()) {
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
      const useIosNative = isIOSDevice();
      video.controls = useIosNative;
      video.src = streamUrl;
      if (!vodMode) {
        video.onloadedmetadata = () => {
          safePlayVideo();
          onReady();
          if (useIosNative && state.playerOpen && state.tuneSeq === seq) {
            try {
              if (video.webkitSupportsFullscreen && !video.webkitDisplayingFullscreen) {
                video.webkitEnterFullscreen();
              }
            } catch (_) {}
          }
        };
      }
      video.onerror = () => {
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        const code = video.error?.code;
        if (vodMode && code === 4) {
          if (ch.id && !String(streamUrl || '').includes('/api/vod/hls/')) {
            const hlsFallbackUrl = `/api/vod/hls/${encodeURIComponent(ch.id)}/playlist.m3u8`;
            attachStream(hlsFallbackUrl, { keepFrame });
            return;
          }
          loading.hidden = true;
          onFail(
            t(
              'Formato video (es. MKV) non supportato direttamente da questo browser. Prova Chrome/Brave desktop o guarda questo titolo su CasaTV Android TV / Mobile.',
              'This video container (e.g. MKV) is not natively supported by this browser. Try desktop Chrome/Brave or watch on CasaTV Android TV / Mobile.',
            ),
          );
          return;
        }
        scheduleWebReconnect('media_error');
      };
      safePlayVideo();
    }
  };

  if (iosNative && ch.id && !state.localOverrideChannels && !vodMode) {
    if (state.relayTunedChannelId && state.relayTunedChannelId !== ch.id) {
      api('/api/relay/release', {
        method: 'POST',
        body: { deviceId: state.deviceId, channelId: state.relayTunedChannelId },
      }).catch(() => {});
    }
    state.relayTunedChannelId = ch.id;
    try { video.load(); } catch (_) {}
    api('/api/relay/tune', {
      method: 'POST',
      body: {
        deviceId: state.deviceId,
        channelId: ch.id,
        channelName: ch.name || '',
        streamUrl: url,
        pinSource: Boolean(pinSource),
      },
    })
      .then((res) => {
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        const resolved = res?.mode === 'relay' && res.playlistUrl ? res.playlistUrl : (res?.url || url);
        state.relayPlaylistUrl = res?.mode === 'relay' && res.playlistUrl ? res.playlistUrl : '';
        state.playerActiveUrl = resolved;
        attachStream(resolved, { keepFrame: false });
      })
      .catch(() => {
        if (!state.playerOpen || state.tuneSeq !== seq) return;
        attachStream(url, { keepFrame: false });
      });
  } else {
    attachStream(url, { keepFrame: false });
  }

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

    const bufTimeoutMs = state.playerIsVod ? 30000 : 12000;
    if (!video.paused && !video.ended && video.readyState < 3 && errBox.hidden && !state.reconnectActive) {
      if (!state.bufferingSinceMs) state.bufferingSinceMs = now;
      if (now - state.bufferingSinceMs > bufTimeoutMs) {
        state.bufferingSinceMs = now;
        if (!state.playerIsVod) reportWebChannelSource(ch.id, ch.streamUrl, 'stall');
        scheduleWebReconnect('buffering_timeout');
        return;
      }
    } else {
      state.bufferingSinceMs = 0;
    }

    const stallTimeoutMs = state.playerIsVod ? 18000 : 6000;
    if (readyPlaying && errBox.hidden && !state.reconnectActive) {
      const tCur = video.currentTime || 0;
      if (Math.abs(tCur - state.lastStallTime) < 0.01) {
        if (now - state.lastStallWallMs > stallTimeoutMs) {
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
      if (!state.playerIsVod && !state.multiTvSyncActive) {
        const winSec = getVideoLiveWindowSec(video);
        const isLiveLike = !Number.isFinite(video.duration) || video.duration === Infinity || winSec > 0;
        if (!state.liveSafetyBuffer || !isLiveLike || isIOSDevice()) {
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
    }
  }, 1000);

  // Only log watched history when NOT in Mix mode (honoring Mix rule)
  if (!skipMarkPlayed && ch.id && !state.localOverrideChannels) {
    api(`/api/channels/${encodeURIComponent(ch.id)}/played`, { method: 'POST' }).catch(() => {});
  }
  if (ch.id && !state.localOverrideChannels) {
    api(`/api/channels/${encodeURIComponent(ch.id)}/streams`)
      .then((res) => {
        if (state.tuneSeq !== seq) return;
        state.playerSources = res.sources || [];
        const hasMulti = state.playerSources.length > 1;
        const errNextBtn = $('#player-next-src-btn');
        if (errNextBtn) errNextBtn.hidden = !hasMulti;
        const barSrcBtn = $('#player-sources-btn');
        if (barSrcBtn) barSrcBtn.hidden = !hasMulti;
        if (!vodMode) {
          state.playerCarouselMode = 'sources';
          state.playerCarouselItems = state.playerSources;
          renderPlayerCarousel();
        }
      })
      .catch(() => {});
  } else {
    const errNextBtn = $('#player-next-src-btn');
    if (errNextBtn) errNextBtn.hidden = true;
    const barSrcBtn = $('#player-sources-btn');
    if (barSrcBtn) barSrcBtn.hidden = true;
  }
  state.playerProgressTimer = setInterval(() => {
    if (!state.playerOpen || state.tuneSeq !== seq) {
      clearInterval(state.playerProgressTimer);
      state.playerProgressTimer = null;
      return;
    }
    updatePlayerTimelineUI();
    if (vodMode) flushCurrentVodProgress(false);
  }, 1000);

  if (!vodMode) startMultiTvSync(ch);
  else stopMultiTvSync();
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
  btn.innerHTML = fav ? SVG_ICONS.starFilled : SVG_ICONS.star;
  btn.title = fav ? t('Rimuovi preferito', 'Remove favorite') : t('Aggiungi preferito', 'Add favorite');
  btn.classList.toggle('is-active', fav);
}

function updatePlayerChromeVisibility() {
  const pipBtn = $('#player-pip-btn');
  if (pipBtn) pipBtn.hidden = !document.pictureInPictureEnabled;
  const drawerBtn = $('#player-drawer-toggle');
  if (drawerBtn) drawerBtn.hidden = state.playerIsMix || state.playerIsVod;
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
      const name = getLocalizedCountryName(ch.country, ch.countryName) || ch.country;
      countryLabelEl.innerHTML = `${normalizeFlagCode(ch.country) ? `${flagHtml(ch.country, 'sm')} ` : ''}${esc(String(name || '').toUpperCase())}`;
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
  clearTimeout(state.syncTimer);
  state.syncTimer = null;
  state.syncActivePeers = 1;
  state.multiTvSyncActive = false;
  const pill = $('#player-sync-pill');
  if (pill) pill.hidden = true;
  const v = $('#preview-video');
  if (v && Math.abs((v.playbackRate || 1) - 1.0) > 0.002) {
    v.playbackRate = 1.0;
  }
  if (!state.localOverrideChannels) {
    api('/api/sync', {
      method: 'POST',
      body: { deviceId: state.deviceId, channelId: '' },
    }).catch(() => {});
  }
}

function getWebHlsSegmentProgress(video) {
  const details = state.hls?.levels?.[state.hls.currentLevel]?.details;
  const frags = details?.fragments;
  if (!frags || !frags.length) return null;
  const t = video.currentTime || 0;
  let chosen = frags[0];
  for (let i = 0; i < frags.length; i += 1) {
    if (frags[i].start <= t + 0.05) chosen = frags[i];
    else break;
  }
  return {
    details,
    seq: Number(chosen.sn || 0),
    offsetMs: Math.max(0, Math.round((t - (chosen.start || 0)) * 1000)),
    durationMs: Math.max(1000, Math.round((chosen.duration || 4) * 1000)),
  };
}

function startMultiTvSync(ch) {
  stopMultiTvSync();
  if (!state.multiTvSync || !ch?.id || state.localOverrideChannels) return;
  const syncSeq = state.tuneSeq;
  let lastSeekAt = 0;
  let inSyncLocked = false;
  let filteredDelta = 0;
  let hasDelta = false;

  const scheduleNext = (ms) => {
    if (!state.playerOpen || state.tuneSeq !== syncSeq) return;
    state.syncTimer = setTimeout(tick, ms);
  };

  const tick = async () => {
    const v = $('#preview-video');
    if (!state.playerOpen || state.tuneSeq !== syncSeq || !v) return;
    let nextDelay = 2000;
    try {
      const seg = getWebHlsSegmentProgress(v);
      const liveEdgeSec = (v.seekable && v.seekable.length > 0) ? v.seekable.end(v.seekable.length - 1) : 0;
      const liveOffsetMs = liveEdgeSec > 0 ? Math.max(0, Math.round((liveEdgeSec - (v.currentTime || 0)) * 1000)) : 0;
      const t0 = performance.now();
      const resp = await api('/api/sync', {
        method: 'POST',
        body: {
          deviceId: state.deviceId,
          deviceName: `${detectBrowserName()} (${deviceLabel()})`,
          channelId: ch.id,
          isLive: true,
          positionMs: Math.round((v.currentTime || 0) * 1000),
          liveOffsetMs,
          segmentSeq: seg ? seg.seq : 0,
          segmentOffsetMs: seg ? seg.offsetMs : 0,
          segmentDurationMs: seg ? seg.durationMs : 0,
        },
      });
      const halfRttMs = Math.min(400, Math.max(0, Math.round((performance.now() - t0) / 2)));
      const pill = $('#player-sync-pill');
      state.multiTvSyncActive = Boolean(resp.syncActive);
      state.syncActivePeers = Number(resp.peerCount || 1);
      if (resp.syncActive) {
        if (pill) {
          pill.hidden = false;
          pill.innerHTML = `<span class="live-dot-inline"></span>Sync Multi-TV (${resp.peerCount})`;
        }
        if (!resp.isLeader && v.readyState >= 2 && !v.paused) {
          let targetSec = null;
          if (seg && resp.targetSegmentSeq > 0) {
            const matchFrag = seg.details.fragments.find((f) => Number(f.sn) === Number(resp.targetSegmentSeq));
            if (matchFrag) {
              targetSec = (matchFrag.start || 0) + ((resp.targetSegmentOffsetMs || 0) + halfRttMs) / 1000;
            }
          }
          if (targetSec == null && resp.targetPositionMs > 0) {
            targetSec = ((resp.targetPositionMs || 0) + halfRttMs) / 1000;
          }
          if (targetSec != null && Number.isFinite(targetSec)) {
            const rawDeltaMs = (targetSec - (v.currentTime || 0)) * 1000;
            const now = Date.now();
            if (Math.abs(rawDeltaMs) > 450 && now - lastSeekAt >= 3500 && v.seekable && v.seekable.length > 0) {
              const sStart = v.seekable.start(0);
              const sEnd = v.seekable.end(v.seekable.length - 1);
              if (sEnd - sStart >= 6) {
                const safeTarget = Math.max(sStart + 0.5, Math.min(sEnd - 3.0, targetSec));
                if (Math.abs(safeTarget - v.currentTime) > 0.45) {
                  lastSeekAt = now;
                  v.currentTime = safeTarget;
                  v.playbackRate = 1.0;
                  hasDelta = false;
                }
              }
            } else {
              filteredDelta = hasDelta ? (0.5 * rawDeltaMs + 0.5 * filteredDelta) : rawDeltaMs;
              hasDelta = true;
              const absD = Math.abs(filteredDelta);
              inSyncLocked = inSyncLocked ? absD < 130 : absD <= 45;
              const targetRate = inSyncLocked
                ? 1.0
                : (filteredDelta > 300 ? 1.04 : filteredDelta > 120 ? 1.025 : filteredDelta > 45 ? 1.012
                  : filteredDelta < -300 ? 0.96 : filteredDelta < -120 ? 0.975 : filteredDelta < -45 ? 0.988 : 1.0);
              if (Math.abs((v.playbackRate || 1) - targetRate) > 0.003) {
                setVideoPreservesPitch(v, true);
                v.playbackRate = targetRate;
              }
            }
          }
          nextDelay = inSyncLocked ? 1200 : 600;
        } else {
          if (Math.abs((v.playbackRate || 1) - 1.0) > 0.002) {
            v.playbackRate = 1.0;
          }
          nextDelay = 1000;
        }
      } else {
        if (pill) pill.hidden = true;
        inSyncLocked = false;
        hasDelta = false;
        nextDelay = 2000;
      }
    } catch (_) {}
    scheduleNext(nextDelay);
  };
  scheduleNext(250);
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
async function checkAdminAuthStatus() {
  try {
    const res = await api('/api/settings/auth-status');
    state.adminIsDefaultPin = Boolean(res.isDefaultPin);
    state.adminAuthChecked = true;
    if (res.authorized) {
      if (res.isDefaultPin) {
        state.adminUnlocked = false;
        state.adminMustChangePin = true;
        state.adminVerifiedOldPin = state.adminPin || '0000';
      } else {
        state.adminUnlocked = true;
        state.adminMustChangePin = false;
      }
    } else {
      state.adminUnlocked = false;
      state.adminMustChangePin = false;
      if (state.adminPin) {
        state.adminPin = '';
        sessionStorage.removeItem('casatv_admin_pin');
      }
    }
  } catch (_) {
    state.adminAuthChecked = true;
  }
}

async function submitAdminUnlock(pin) {
  const clean = String(pin || '').trim();
  if (!clean) {
    state.adminGateError = t('Inserisci la password amministratore', 'Enter the admin password');
    renderCurrentScreen();
    return;
  }
  state.adminGateBusy = true;
  state.adminGateError = '';
  renderCurrentScreen();
  try {
    const res = await api('/api/settings/verify-pin', { method: 'POST', body: { pin: clean } });
    if (res?.mustChangePassword) {
      state.adminVerifiedOldPin = clean;
      state.adminMustChangePin = true;
      state.adminIsDefaultPin = true;
      state.adminUnlocked = false;
    } else {
      state.adminPin = clean;
      sessionStorage.setItem('casatv_admin_pin', clean);
      state.adminMustChangePin = false;
      state.adminIsDefaultPin = false;
      state.adminUnlocked = true;
      await refreshSystemStatus();
    }
  } catch (err) {
    state.adminGateError = err.message || t('Password non valida', 'Invalid password');
  } finally {
    state.adminGateBusy = false;
    renderCurrentScreen();
  }
}

async function submitAdminChangePassword(oldPin, newPin, confirmPin) {
  const cleanOld = String(oldPin || '').trim();
  const cleanNew = String(newPin || '').trim();
  const cleanConfirm = String(confirmPin || '').trim();
  if (cleanNew.length < 4) {
    state.adminGateError = t('La nuova password deve avere almeno 4 caratteri', 'New password must be at least 4 characters');
    renderCurrentScreen();
    return;
  }
  if (cleanNew === '0000') {
    state.adminGateError = t('Scegli una password diversa da 0000', 'Choose a password other than 0000');
    renderCurrentScreen();
    return;
  }
  if (cleanNew !== cleanConfirm) {
    state.adminGateError = t('Le password non coincidono', 'Passwords do not match');
    renderCurrentScreen();
    return;
  }
  state.adminGateBusy = true;
  state.adminGateError = '';
  renderCurrentScreen();
  try {
    await api('/api/settings/change-pin', {
      method: 'POST',
      body: { oldPin: cleanOld, newPin: cleanNew },
    });
    state.adminPin = cleanNew;
    sessionStorage.setItem('casatv_admin_pin', cleanNew);
    state.adminVerifiedOldPin = '';
    state.adminMustChangePin = false;
    state.adminIsDefaultPin = false;
    state.adminUnlocked = true;
    toast(t('Password amministratore aggiornata!', 'Admin password updated!'));
    await refreshSystemStatus();
  } catch (err) {
    state.adminGateError = err.message || t('Errore aggiornamento password', 'Password update failed');
  } finally {
    state.adminGateBusy = false;
    renderCurrentScreen();
  }
}

function lockAdminArea() {
  state.adminUnlocked = false;
  state.adminMustChangePin = false;
  state.adminVerifiedOldPin = '';
  state.adminGateError = '';
  state.adminPin = '';
  state.system = null;
  sessionStorage.removeItem('casatv_admin_pin');
  renderCurrentScreen();
}

function renderAdminGateCardHtml({ heading, description }) {
  const errHtml = state.adminGateError
    ? `<div class="admin-lock-error">${esc(state.adminGateError)}</div>`
    : '';
  if (state.adminMustChangePin) {
    return `
      <section class="settings-card admin-lock-card">
        <div class="settings-card-head">
          <span class="admin-lock-badge warn">${SVG_ICONS.lock} ${t('Cambio password richiesto', 'Password change required')}</span>
          <h3>${t('Imposta nuova password amministratore', 'Set a new admin password')}</h3>
          <p>${t('La password iniziale 0000 deve essere cambiata prima di accedere alle impostazioni del server.', 'The initial 0000 password must be changed before accessing server settings.')}</p>
        </div>
        ${errHtml}
        <form class="admin-lock-form" id="admin-change-pin-form">
          <input type="password" class="casa-input" id="admin-new-pin" autocomplete="new-password" placeholder="${t('Nuova password (min. 4 caratteri, non 0000)', 'New password (min 4 chars, not 0000)')}" required />
          <input type="password" class="casa-input" id="admin-confirm-pin" autocomplete="new-password" placeholder="${t('Conferma nuova password', 'Confirm new password')}" required />
          <div class="btn-row">
            <button type="submit" class="casa-btn primary is-solid sm" ${state.adminGateBusy ? 'disabled' : ''}>${t('Salva e sblocca', 'Save & unlock')}</button>
          </div>
        </form>
      </section>
    `;
  }
  return `
    <section class="settings-card admin-lock-card">
      <div class="settings-card-head">
        <span class="admin-lock-badge">${SVG_ICONS.lock} ${t('Area Server Protetta', 'Protected Server Area')}</span>
        <h3>${esc(heading)}</h3>
        <p>${esc(description)}</p>
      </div>
      ${state.adminIsDefaultPin ? `<p class="admin-lock-hint">${t('Password iniziale predefinita: 0000 (verrà richiesto di cambiarla al primo accesso).', 'Default initial password: 0000 (you will be asked to change it).')}</p>` : ''}
      ${errHtml}
      <form class="admin-lock-form" id="admin-unlock-form">
        <input type="password" class="casa-input" id="admin-pin-input" autocomplete="current-password" placeholder="${t('Inserisci password server…', 'Enter server password…')}" required />
        <div class="btn-row">
          <button type="submit" class="casa-btn primary is-solid sm" ${state.adminGateBusy ? 'disabled' : ''}>${t('Sblocca', 'Unlock')}</button>
        </div>
      </form>
    </section>
  `;
}

function bindAdminGateEvents(root) {
  $('#admin-unlock-form', root)?.addEventListener('submit', (e) => {
    e.preventDefault();
    submitAdminUnlock($('#admin-pin-input', root)?.value || '');
  });
  $('#admin-change-pin-form', root)?.addEventListener('submit', (e) => {
    e.preventDefault();
    submitAdminChangePassword(
      state.adminVerifiedOldPin || '0000',
      $('#admin-new-pin', root)?.value || '',
      $('#admin-confirm-pin', root)?.value || '',
    );
  });
}

async function refreshSystemStatus() {
  if (!state.adminUnlocked) return;
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
      sub: state.adminUnlocked
        ? t('Liste M3U, catalogo e file locali', 'M3U lists, catalog & local files')
        : t('Liste server protette · M3U locali', 'Protected server lists · Local M3U'),
      icon: `<svg viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>`,
    },
    {
      id: 'server',
      title: t('Server & Sistema', 'Server & System', 'Servidor y Sistema'),
      sub: state.adminUnlocked
        ? t('Rete, risorse e aggiornamenti', 'Network, resources & updates')
        : t('Gestione server protetta da password', 'Password-protected server area'),
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
      state.adminGateError = '';
      if ((state.settingsSection === 'sources' || state.settingsSection === 'server') && !state.adminAuthChecked) {
        checkAdminAuthStatus().then(() => renderSettingsScreen(root));
        return;
      }
      if (state.settingsSection === 'server' && state.adminUnlocked && !state.system) {
        refreshSystemStatus();
      }
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
          <div class="editor-avatar-preview" style="width:52px;height:52px">${renderProfileAvatar(p.avatar, p.name || 'Guest', 52)}</div>
          <div>
            <strong style="font-size:17px">${esc(p.name || 'Ospite')}</strong>
            <div style="font-size:12px;color:var(--muted);display:flex;align-items:center;gap:6px">${flagHtml(p.country, 'sm')} ${esc(p.countryName || p.country || 'IT')} · ${esc((lang).toUpperCase())}</div>
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
          <strong style="font-size:14px;display:inline-flex;align-items:center;gap:6px">${flagHtml(state.tvCountry, 'sm')} ${esc(state.config?.facets?.countries?.find((c) => c.code === state.tvCountry)?.name || state.tvCountry)}</strong>
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
    state.verifiedMixQueue = [];
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
  const adminUnlocked = state.adminUnlocked;

  const serverPlaylistsHtml = adminUnlocked
    ? `
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
          <button type="button" class="casa-btn ghost sm" id="btn-dedupe-playlists">${t('Pulisci duplicati', 'Clean duplicates', 'Limpiar duplicados')}</button>
          <button type="button" class="casa-btn ghost sm" id="btn-export-server-m3u">${t('Esporta .M3U', 'Export .M3U')}</button>
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
      <div class="import-progress-box" id="playlist-import-banner" hidden></div>
      <div id="playlists-rows" style="display:flex;flex-direction:column;gap:8px"></div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Panoramica Catalogo', 'Catalog Overview', 'Resumen del Catálogo')}</h3>
        <p>${t('Canali e statistiche attualmente disponibili sul server CasaTV.', 'Channels and stats currently available on the CasaTV server.')}</p>
      </div>
      <div class="stats-grid">
        <div class="stat-cell"><small>${t('Canali TV', 'Live TV', 'Canales TV')}</small><strong>${fmtNum(stats.liveChannels ?? stats.channels ?? 0)}</strong></div>
        <div class="stat-cell"><small>${t('Flussi Live', 'Live Streams', 'Flujos Live')}</small><strong>${fmtNum(stats.streams || 0)}</strong></div>
        <div class="stat-cell"><small>${t('Film', 'Movies', 'Películas')}</small><strong>${fmtNum(stats.movies || 0)}</strong></div>
        <div class="stat-cell"><small>${t('Serie TV', 'TV Series', 'Series TV')}</small><strong>${fmtNum(stats.series || 0)}</strong></div>
        <div class="stat-cell"><small>${t('Paesi', 'Countries', 'Países')}</small><strong>${fmtNum(stats.countries || 0)}</strong></div>
        <div class="stat-cell"><small>${t('Preferiti', 'Favorites', 'Favoritos')}</small><strong>${fmtNum(stats.favorites || 0)}</strong></div>
      </div>
    </section>`
    : renderAdminGateCardHtml({
        heading: t('Playlist del server protette', 'Protected Server Playlists'),
        description: t('Sblocca con la password amministratore per gestire le liste M3U, i link e le sorgenti del server.', 'Unlock with the admin password to manage server M3U lists, URLs and sources.'),
      });

  detail.innerHTML = `
    ${serverPlaylistsHtml}

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
      const resp = await api('/api/playlists', { method: 'POST', body: { name, url, async: true } });
      $('#add-playlist-box').hidden = true;
      if (resp.async || resp.playlist?.status === 'importing') {
        toast(
          resp.updatedExisting
            ? t('Link già presente: aggiornamento avviato in background!', 'Link already exists: updating in background!')
            : t('Importazione avviata in background!', 'Background import started!'),
        );
        startImportPolling();
      } else {
        const pl = resp.playlist;
        if (pl?.status === 'error' || !pl?.channelCount) {
          toast(`${pl?.name || 'Playlist'}: ${pl?.lastError || t('non funziona', 'not working')}`, 'err');
        } else {
          toast(t('Playlist importata!', 'Playlist imported!'));
        }
        await loadServerConfig();
        await loadChannels({ reset: true });
      }
      drawPlaylists();
    } catch (err) {
      toast(err.message, 'err');
    }
  });

  function sendUploadChunkXhr(url, headers, payload, timeoutMs, onProgress) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', url, true);
      xhr.timeout = timeoutMs;
      Object.entries(headers || {}).forEach(([k, v]) => {
        if (v) xhr.setRequestHeader(k, v);
      });
      xhr.upload.onprogress = (ev) => {
        if (ev.lengthComputable && onProgress) {
          onProgress(ev.loaded, ev.total);
        }
      };
      xhr.onload = () => resolve({ status: xhr.status, text: xhr.responseText || '' });
      xhr.onerror = () => reject(new Error(t('Connessione interrotta durante l’invio', 'Connection lost while uploading')));
      xhr.ontimeout = () => reject(new Error(t('Timeout durante l’invio del file', 'Timeout while uploading file')));
      xhr.onabort = () => reject(new Error(t('Invio annullato', 'Upload aborted')));
      xhr.send(payload);
    });
  }

  async function uploadPlaylistFileChunked(file) {
    const chunkSize = 2 * 1024 * 1024;
    const total = file.size || 0;
    const uploadId = `up_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
    const totalChunks = Math.max(1, Math.ceil(total / chunkSize));
    const base = state.serverUrl && state.serverUrl !== window.location.origin ? state.serverUrl : '';
    const hdrs = {};
    if (state.adminPin) hdrs['X-Admin-Pin'] = state.adminPin;
    const uploadStartMs = Date.now();
    const maxAttempts = 3;

    for (let i = 0; i < totalChunks; i++) {
      const start = i * chunkSize;
      const end = Math.min(total, start + chunkSize);
      const blob = file.slice(start, end);
      const chunkBuf = await blob.arrayBuffer();
      const qs = new URLSearchParams({
        uploadId,
        offset: String(start),
        total: String(total),
        name: file.name,
      });

      for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        let lastUiUpdateMs = 0;
        const updateUi = (sentInChunk, retryNote = '') => {
          const sentTotal = Math.min(total, start + sentInChunk);
          const pct = Math.min(99, Math.round((sentTotal / Math.max(1, total)) * 100));
          const elapsedSec = Math.max(0.2, (Date.now() - uploadStartMs) / 1000);
          const speedBps = sentTotal > 0 ? sentTotal / elapsedSec : 0;
          state.chunkUploadProgress = {
            name: file.name,
            chunk: i + 1,
            totalChunks,
            sentBytes: sentTotal,
            totalBytes: total,
            percent: pct,
            speedBps,
            retryNote,
          };
          renderImportBanner();
        };

        updateUi(0, attempt > 1 ? `${t('Tentativo', 'Attempt')} ${attempt}/${maxAttempts}…` : '');

        try {
          const fd = new FormData();
          fd.append('chunk', new Blob([chunkBuf], { type: 'application/octet-stream' }), 'chunk.bin');
          const { status, text } = await sendUploadChunkXhr(
            `${base}/api/playlists/upload-chunk?${qs.toString()}`,
            hdrs,
            fd,
            30000,
            (loaded) => {
              const now = Date.now();
              if (now - lastUiUpdateMs >= 200) {
                lastUiUpdateMs = now;
                updateUi(Math.min(chunkBuf.byteLength, loaded));
              }
            },
          );
          let data = {};
          try {
            data = text ? JSON.parse(text) : {};
          } catch (_) {}
          if (status < 200 || status >= 300) {
            const err = new Error(data.error || `HTTP ${status}`);
            err.noRetry = status === 400 || status === 401 || status === 403;
            throw err;
          }
          if (Number(data.receivedBytes) !== chunkBuf.byteLength) {
            throw new Error(t('Chunk incompleto ricevuto dal server', 'Incomplete chunk received by server'));
          }
          updateUi(chunkBuf.byteLength);
          break;
        } catch (err) {
          if (err?.noRetry || attempt >= maxAttempts) {
            throw err;
          }
          updateUi(0, `${t('Tentativo', 'Attempt')} ${attempt + 1}/${maxAttempts}…`);
          await new Promise((r) => setTimeout(r, 1000 * attempt));
        }
      }
    }
    state.chunkUploadProgress = null;
    return api('/api/playlists/upload-finish', {
      method: 'POST',
      body: { uploadId, name: file.name },
    });
  }

  $('#server-m3u-file')?.addEventListener('change', async (e) => {
    const files = [...(e.target.files || [])];
    if (!files.length) return;
    toast(`${t('Invio di', 'Uploading')} ${files.length} ${t('file in streaming…', 'files via streaming…')}`);
    let hasError = false;
    let dupCount = 0;
    let startedCount = 0;
    for (const file of files) {
      try {
        const res = await uploadPlaylistFileChunked(file);
        if (res?.duplicate) {
          dupCount++;
        } else {
          startedCount++;
        }
      } catch (err) {
        hasError = true;
        state.chunkUploadProgress = null;
        renderImportBanner();
        toast(`${file.name}: ${err.message}`, 'err');
      }
    }
    e.target.value = '';
    if (!hasError) {
      if (startedCount === 0 && dupCount > 0) {
        toast(t('Playlist identica già presente sul server (nessun duplicato aggiunto).', 'Identical playlist already on server (no duplicate added).'));
      } else if (dupCount > 0) {
        toast(`${startedCount} ${t('importate', 'imported')}, ${dupCount} ${t('duplicate saltate', 'duplicates skipped')}`);
      } else {
        toast(t('File ricevuti! Importazione in background…', 'Files uploaded! Importing in background…'));
      }
    }
    startImportPolling();
    drawPlaylists();
  });

  $('#btn-dedupe-playlists')?.addEventListener('click', async () => {
    const btn = $('#btn-dedupe-playlists');
    if (btn) btn.disabled = true;
    try {
      const res = await api('/api/playlists/deduplicate', { method: 'POST' });
      const removed = Number(res?.removedCount || 0);
      if (removed > 0) {
        toast(t(`Rimosse ${removed} playlist duplicate`, `Removed ${removed} duplicate playlists`));
        await loadServerConfig();
        await loadChannels({ reset: true });
        drawPlaylists();
      } else {
        toast(t('Nessuna playlist duplicata trovata', 'No duplicate playlists found'));
      }
    } catch (err) {
      toast(err.message || t('Errore pulizia duplicati', 'Duplicate cleanup failed'), 'err');
    } finally {
      if (btn) btn.disabled = false;
    }
  });

  $('#btn-export-server-m3u')?.addEventListener('click', async () => {
    try {
      const m3uText = await api('/api/playlist/export.m3u');
      const blob = new Blob([m3uText], { type: 'audio/x-mpegurl;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'casatv-server.m3u';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast(err.message || t('Errore esportazione', 'Export failed'), 'err');
    }
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

  function renderImportBanner() {
    const box = document.getElementById('playlist-import-banner') || $('#playlist-import-banner', detail);
    if (!box) return;
    const up = state.chunkUploadProgress;
    if (up) {
      const speedLabel = up.speedBps > 0 ? ` · ${fmtBytes(up.speedBps)}/s` : '';
      const retryLabel = up.retryNote ? ` · ${esc(up.retryNote)}` : '';
      box.hidden = false;
      box.innerHTML = `
        <div class="import-progress-top">
          <strong>${SVG_ICONS.arrowUp} ${t('Invio file', 'Uploading')}: ${esc(up.name)}</strong>
          <span>${up.percent}%</span>
        </div>
        <div class="import-progress-track"><div class="import-progress-fill" style="width:${up.percent}%"></div></div>
        <div class="import-progress-sub">${t('Parte', 'Part')} ${up.chunk}/${up.totalChunks} · ${fmtBytes(up.sentBytes)} / ${fmtBytes(up.totalBytes)}${speedLabel}${retryLabel}</div>
      `;
      return;
    }
    const job = state.importStatus?.activeJob;
    const queued = state.importStatus?.queuedCount || 0;
    if (!job) {
      box.hidden = true;
      box.innerHTML = '';
      return;
    }
    const pct = Math.max(2, Math.min(100, Number(job.percent) || 0));
    box.hidden = false;
    box.innerHTML = `
      <div class="import-progress-top">
        <strong>${SVG_ICONS.settings} ${esc(job.name)}${queued > 0 ? ` <small style="color:var(--muted)">(+${queued} ${t('in coda', 'queued')})</small>` : ''}</strong>
        <span>${pct}%</span>
      </div>
      <div class="import-progress-track"><div class="import-progress-fill" style="width:${pct}%"></div></div>
      <div class="import-progress-sub">${esc(job.statusMessage || job.phase)} · ${fmtNum(job.channelsImported || 0)} ${t('canali', 'channels')}</div>
    `;
  }

  function startImportPolling() {
    if (state.importPollTimer) return;
    const tick = async () => {
      try {
        const res = await api('/api/playlists/import-status');
        state.importStatus = res;
        renderImportBanner();
        if (res.activeJob || (res.queuedCount || 0) > 0) {
          drawPlaylists({ skipPollTrigger: true });
        } else {
          clearInterval(state.importPollTimer);
          state.importPollTimer = null;
          await loadServerConfig();
          await loadChannels({ reset: true });
          drawPlaylists({ skipPollTrigger: true });
        }
      } catch (_) {
        clearInterval(state.importPollTimer);
        state.importPollTimer = null;
      }
    };
    tick();
    state.importPollTimer = setInterval(tick, 1500);
  }

  function renderPlaylistRowHtml(pl) {
    const isImporting = pl.status === 'importing';
    const isBroken = !isImporting && (pl.status === 'error' || (pl.channelCount || 0) === 0);
    const isWarn = !isImporting && !isBroken && pl.status === 'warn';
    const rowClass = isBroken ? 'is-error' : isWarn ? 'is-warn' : '';
    const pillClass = isImporting ? 'importing' : isBroken ? 'error' : isWarn ? 'warn' : 'ok';
    const statusText = isImporting
      ? t('Importazione…', 'Importing…', 'Importando…')
      : isBroken
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
          ${isImporting ? '' : `<button type="button" class="casa-btn ghost sm" data-pl-check="${esc(pl.id)}">${t('Verifica', 'Check')}</button>`}
          ${!isImporting && pl.url ? `<button type="button" class="casa-btn ghost sm" data-pl-refresh="${esc(pl.id)}">${t('Aggiorna', 'Refresh')}</button>` : ''}
          <button type="button" class="casa-btn danger sm" data-pl-del="${esc(pl.id)}">${t('Elimina', 'Delete')}</button>
        </div>
      </div>
    `;
  }

  function bindPlaylistRowActions(container, onAfterMutate) {
    $$('[data-pl-check]', container).forEach((b) =>
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
          await onAfterMutate();
        } catch (err) {
          toast(err.message, 'err');
          b.disabled = false;
        }
      }),
    );
    $$('[data-pl-refresh]', container).forEach((b) =>
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
          await onAfterMutate();
        } catch (err) {
          toast(err.message, 'err');
          b.disabled = false;
        }
      }),
    );
    $$('[data-pl-del]', container).forEach((b) =>
      b.addEventListener('click', async () => {
        try {
          await api(`/api/playlists/${encodeURIComponent(b.dataset.plDel)}`, { method: 'DELETE' });
          toast(t('Playlist eliminata', 'Playlist deleted'));
          await loadServerConfig();
          await loadChannels({ reset: true });
          await onAfterMutate();
        } catch (err) {
          toast(err.message, 'err');
        }
      }),
    );
  }

  let modalQuery = '';
  let modalFilter = 'all';

  function renderAllPlaylistsModalBody(modal) {
    const items = state.serverPlaylists || [];
    const countTitle = $('#all-playlists-title', modal);
    if (countTitle) {
      countTitle.textContent = `${t('Gestisci tutte le playlist e sorgenti', 'Manage all playlists & sources', 'Gestionar todas las listas')} (${fmtNum(items.length)})`;
    }
    const q = modalQuery.trim().toLowerCase();
    const filtered = items.filter((pl) => {
      const isBroken = pl.status !== 'importing' && (pl.status === 'error' || (pl.channelCount || 0) === 0);
      if (modalFilter === 'ok' && isBroken) return false;
      if (modalFilter === 'error' && !isBroken) return false;
      if (q) {
        const hay = `${pl.name || ''} ${pl.url || ''}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
    const listEl = $('#all-playlists-list', modal);
    const subEl = $('#all-playlists-sub', modal);
    if (subEl) {
      subEl.textContent = `${fmtNum(filtered.length)} / ${fmtNum(items.length)} ${t('visualizzate', 'shown', 'mostradas')}`;
    }
    if (!listEl) return;
    listEl.innerHTML = filtered.length
      ? filtered.map(renderPlaylistRowHtml).join('')
      : `<p style="margin:12px 0;color:var(--muted);font-size:13px">${t('Nessuna playlist trovata con questi filtri.', 'No playlists match this filter.')}</p>`;
    bindPlaylistRowActions(listEl, async () => {
      await drawPlaylists({ skipPollTrigger: true });
      if (!modal.hidden) renderAllPlaylistsModalBody(modal);
    });
  }

  function openAllPlaylistsModal() {
    let modal = document.getElementById('all-playlists-modal');
    if (!modal) {
      modal = document.createElement('div');
      modal.id = 'all-playlists-modal';
      modal.className = 'modal';
      modal.hidden = true;
      modal.innerHTML = `
        <div class="modal-backdrop" data-close-all-playlists></div>
        <div class="modal-card playlists-manager-dialog" role="dialog" aria-modal="true" aria-labelledby="all-playlists-title">
          <div class="country-modal-head">
            <div>
              <h2 id="all-playlists-title">${t('Gestisci tutte le playlist', 'Manage all playlists')}</h2>
              <p id="all-playlists-sub" style="margin:2px 0 0;color:var(--muted);font-size:12px"></p>
            </div>
            <button type="button" class="casa-btn ghost sm" data-close-all-playlists>${t('Chiudi', 'Close', 'Cerrar')}</button>
          </div>
          <div class="playlists-manager-toolbar">
            <input type="search" class="casa-input" id="all-playlists-search" placeholder="${t('Cerca playlist per nome o link…', 'Search playlist by name or URL…')}" autocomplete="off" />
            <div class="btn-row">
              <button type="button" class="casa-btn ghost sm is-active" data-pl-filter="all">${t('Tutte', 'All', 'Todas')}</button>
              <button type="button" class="casa-btn ghost sm" data-pl-filter="ok">${t('Attive', 'Active', 'Activas')}</button>
              <button type="button" class="casa-btn ghost sm" data-pl-filter="error">${t('Con errori', 'Errors', 'Con errores')}</button>
            </div>
          </div>
          <div class="playlists-manager-list" id="all-playlists-list"></div>
        </div>
      `;
      document.body.appendChild(modal);
      $$('[data-close-all-playlists]', modal).forEach((el) =>
        el.addEventListener('click', () => {
          modal.hidden = true;
        }),
      );
      $('#all-playlists-search', modal)?.addEventListener('input', (e) => {
        modalQuery = e.target.value || '';
        renderAllPlaylistsModalBody(modal);
      });
      $$('[data-pl-filter]', modal).forEach((btn) =>
        btn.addEventListener('click', () => {
          modalFilter = btn.dataset.plFilter || 'all';
          $$('[data-pl-filter]', modal).forEach((b) => b.classList.toggle('is-active', b === btn));
          renderAllPlaylistsModalBody(modal);
        }),
      );
    }
    modal.hidden = false;
    renderAllPlaylistsModalBody(modal);
  }

  async function drawPlaylists({ skipPollTrigger = false } = {}) {
    try {
      const res = await api('/api/playlists');
      const items = res.playlists || [];
      state.serverPlaylists = items;
      if (!skipPollTrigger && items.some((pl) => pl.status === 'importing')) {
        startImportPolling();
      }
      renderImportBanner();
      const countLbl = document.getElementById('playlists-count-label');
      const rowsEl = document.getElementById('playlists-rows');
      if (!countLbl || !rowsEl) return;
      countLbl.textContent = `${fmtNum(items.length)} ${t('playlist configurate', 'configured playlists')}`;
      const previewItems = items.slice(0, 5);
      const remainingCount = items.length - previewItems.length;
      rowsEl.innerHTML = items.length
        ? previewItems.map(renderPlaylistRowHtml).join('') +
          (remainingCount > 0
            ? `<button type="button" class="casa-btn ghost sm" id="btn-manage-all-playlists" style="align-self:flex-start;margin-top:4px">${t(
                `Mostra e gestisci tutte (${fmtNum(items.length)})`,
                `Show & manage all (${fmtNum(items.length)})`,
                `Ver y gestionar todas (${fmtNum(items.length)})`,
              )}</button>`
            : '')
        : `<p style="margin:0;color:var(--muted);font-size:12.5px">${t('Nessuna playlist salvata.', 'No saved playlists.')}</p>`;

      bindPlaylistRowActions(rowsEl, () => drawPlaylists({ skipPollTrigger: true }));
      $('#btn-manage-all-playlists', rowsEl)?.addEventListener('click', openAllPlaylistsModal);
      const existingModal = document.getElementById('all-playlists-modal');
      if (existingModal && !existingModal.hidden) {
        renderAllPlaylistsModalBody(existingModal);
      }
    } catch (_) {
      const countLbl = document.getElementById('playlists-count-label');
      if (countLbl) countLbl.textContent = t('Server non raggiungibile', 'Server unreachable');
    }
  }
  if (adminUnlocked) {
    drawPlaylists();
  } else {
    bindAdminGateEvents(detail);
  }
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
  const adminUnlocked = state.adminUnlocked;

  const protectedServerHtml = adminUnlocked
    ? `
    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Sicurezza Amministratore', 'Admin Security', 'Seguridad de Administrador')}</h3>
        <p>${t('Gestisci la password di accesso alle impostazioni del server e ai dispositivi attivi.', 'Manage the password protecting server settings and active user activity.')}</p>
      </div>
      <div class="btn-row" style="margin-bottom:10px">
        <button type="button" class="casa-btn ghost sm" id="btn-toggle-change-pw">${SVG_ICONS.key} ${t('Cambia password', 'Change password')}</button>
        <button type="button" class="casa-btn danger sm" id="btn-lock-admin-area">${SVG_ICONS.lock} ${t('Blocca area server', 'Lock server area')}</button>
      </div>
      <form id="admin-inline-pw-form" hidden style="display:flex;flex-direction:column;gap:8px;max-width:420px;padding-top:6px">
        <input type="password" class="casa-input" id="inline-old-pw" autocomplete="current-password" placeholder="${t('Password attuale', 'Current password')}" required />
        <input type="password" class="casa-input" id="inline-new-pw" autocomplete="new-password" placeholder="${t('Nuova password (min. 4 caratteri)', 'New password (min 4 chars)')}" required />
        <input type="password" class="casa-input" id="inline-confirm-pw" autocomplete="new-password" placeholder="${t('Conferma nuova password', 'Confirm new password')}" required />
        <div class="btn-row">
          <button type="submit" class="casa-btn primary sm">${t('Salva password', 'Save password')}</button>
        </div>
      </form>
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
                .map((a) => `<a class="casa-btn ghost sm" href="${esc(a.url)}" target="_blank" rel="noopener">${SVG_ICONS.arrowDown} ${esc(a.name)}</a>`)
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
                <span style="font-size:11.5px;color:${d.state === 'playing' ? 'var(--accent)' : 'var(--dim)'}">${d.state === 'playing' && d.channel ? `${SVG_ICONS.play} ${esc(d.channel)}` : t('inattivo', 'idle')}</span>
              </div>
            `,
                )
                .join('')
            : `<p style="margin:0;color:var(--dim);font-size:12px">${t('Nessun dispositivo rilevato.', 'No devices detected.')}</p>`
        }
      </div>
    </section>

    <section class="settings-card">
      <div class="settings-card-head">
        <h3>${t('Diagnostica Buffering (ultime 24h)', 'Buffering Diagnostics (last 24h)', 'Diagnóstico de Buffering (24h)')}</h3>
        <p>${t('Registra quando e perché un canale va in buffering su TV o browser.', 'Records when and why a channel buffers on TV or browser.')}</p>
      </div>
      <div class="btn-row" style="margin-bottom:8px">
        <button type="button" class="casa-btn ghost sm" id="btn-load-buf-diag">${SVG_ICONS.refresh || SVG_ICONS.settings} ${t('Aggiorna log buffering', 'Refresh buffering logs')}</button>
      </div>
      <div id="buf-diag-container" style="display:flex;flex-direction:column;gap:6px;max-height:260px;overflow-y:auto">
        <p style="margin:0;color:var(--dim);font-size:12px">${t('Premi "Aggiorna log buffering" per analizzare gli eventi.', 'Press "Refresh buffering logs" to inspect events.')}</p>
      </div>
    </section>`
    : renderAdminGateCardHtml({
        heading: t('Impostazioni Server & Dispositivi', 'Server Settings & Connected Users'),
        description: t('Inserisci la password amministratore per visualizzare cosa stanno guardando gli utenti, le risorse e i controlli del server.', 'Enter the admin password to view connected users, playback activity, and server controls.'),
      });

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
        adminUnlocked && urls.length
          ? `<div style="display:flex;flex-direction:column;gap:4px">${urls
              .map((u) => `<div style="font-size:11.5px;color:var(--muted);font-family:var(--mono)"><span style="color:var(--dim)">${esc(u.name || 'net')}:</span> ${esc(u.url)}</div>`)
              .join('')}</div>`
          : ''
      }
      <div class="btn-row">
        <button type="button" class="casa-btn primary sm" id="btn-open-server-picker">${t('Cambia server', 'Change server')}</button>
      </div>
    </section>

    ${protectedServerHtml}
  `;

  $('#btn-open-server-picker')?.addEventListener('click', () => {
    $('#manual-server-input').value = state.serverUrl;
    $('#server-modal').hidden = false;
  });
  if (!adminUnlocked) {
    bindAdminGateEvents(detail);
    return;
  }
  $('#btn-toggle-change-pw')?.addEventListener('click', () => {
    const f = $('#admin-inline-pw-form', detail);
    if (f) f.hidden = !f.hidden;
  });
  $('#btn-lock-admin-area')?.addEventListener('click', lockAdminArea);
  $('#admin-inline-pw-form', detail)?.addEventListener('submit', (e) => {
    e.preventDefault();
    submitAdminChangePassword(
      $('#inline-old-pw', detail)?.value || '',
      $('#inline-new-pw', detail)?.value || '',
      $('#inline-confirm-pw', detail)?.value || '',
    );
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
  const loadBufferingDiagUi = async () => {
    const box = $('#buf-diag-container', detail);
    if (!box) return;
    box.innerHTML = `<p style="margin:0;color:var(--muted);font-size:12px">${t('Caricamento diagnostica…', 'Loading diagnostics…')}</p>`;
    try {
      const data = await api('/api/diagnostics/buffering?hours=24&limit=25');
      const events = data.events || [];
      if (!events.length) {
        box.innerHTML = `<p style="margin:0;color:var(--dim);font-size:12px">${t('Nessun episodio di buffering registrato nelle ultime 24h.', 'No buffering events recorded in the last 24h.')}</p>`;
        return;
      }
      box.innerHTML = events
        .map((ev) => {
          const dt = new Date(ev.tsMs || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
          const dur = ev.details?.bufferingDurationMs || ev.details?.elapsedMs || '';
          const durTxt = dur ? ` · ${dur}ms` : '';
          return `
            <div class="device-row" style="align-items:flex-start;flex-direction:column;gap:3px">
              <div style="display:flex;justify-content:space-between;width:100%;gap:8px">
                <strong>${esc(ev.channelName || ev.channelId || 'Canale')}</strong>
                <small style="color:var(--dim)">${esc(dt)} · ${esc(ev.deviceName || ev.source)}</small>
              </div>
              <div style="font-size:12px;color:var(--amber)">${esc(ev.probableCause || ev.message || ev.code)}${esc(durTxt)}</div>
            </div>
          `;
        })
        .join('');
    } catch (err) {
      box.innerHTML = `<p style="margin:0;color:var(--red);font-size:12px">${esc(err.message)}</p>`;
    }
  };
  $('#btn-load-buf-diag', detail)?.addEventListener('click', loadBufferingDiagUi);
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
        [t('Lista', 'Drawer'), t('Apre il menu laterale dei canali senza interrompere la visione', 'Opens the quick channel drawer')],
        ['PiP', t('Picture-in-Picture per continuare a guardare in finestra mobile', 'Floating Picture-in-Picture window')],
        ['Mix', t('Mappamondo interattivo multi-regione con zapping casuale verificato', 'Interactive World Map with verified Mix zapping')],
      ]
    : [
        ['SX', t('Apre il menù rapido canali laterale con anteprima live', 'Opens quick left channel drawer with live preview')],
        ['SU / GIÙ', t('Cambia canale (o scorre il menù laterale)', 'Changes channel (or browses left drawer)')],
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
  $('#topbar-recordings-btn')?.addEventListener('click', openRecordingsModal);
  $$('[data-close-recordings-modal]').forEach((el) =>
    el.addEventListener('click', () => {
      $('#recordings-modal').hidden = true;
    }),
  );
  $('#recordings-refresh-btn')?.addEventListener('click', openRecordingsModal);
  $('#recordings-list')?.addEventListener('click', async (e) => {
    const playBtn = e.target.closest('[data-rec-play]');
    if (playBtn) {
      const rec = state.recordings[Number(playBtn.dataset.recPlay)];
      if (!rec) return;
      $('#recordings-modal').hidden = true;
      openPlayer(
        {
          id: `rec_${rec.id}`,
          name: rec.channelName || 'Registrazione',
          streamUrl: `${state.serverUrl}/api/recordings/${encodeURIComponent(rec.id)}/master.m3u8`,
          isVod: true,
          contentType: 'movie',
          subtitle: t('Registrazione', 'Recording'),
        },
        [],
        { isMix: false },
      );
      return;
    }
    const stopBtn = e.target.closest('[data-rec-stop]');
    if (stopBtn) {
      const rec = state.recordings[Number(stopBtn.dataset.recStop)];
      if (rec) {
        await api(`/api/recordings/${encodeURIComponent(rec.id)}/stop`, { method: 'POST', body: {} }).catch(() => {});
        openRecordingsModal();
      }
      return;
    }
    const delBtn = e.target.closest('[data-rec-del]');
    if (delBtn) {
      const rec = state.recordings[Number(delBtn.dataset.recDel)];
      if (rec) {
        await api(`/api/recordings/${encodeURIComponent(rec.id)}`, { method: 'DELETE' }).catch(() => {});
        openRecordingsModal();
      }
    }
  });
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
  $('#player-related-btn')?.addEventListener('click', () => toggleRightDrawer());
  $('#right-drawer-close-btn')?.addEventListener('click', () => toggleRightDrawer(false));
  $('#player-next-ep-card')?.addEventListener('click', () => {
    const nextEp = state.nextEpisodeCandidate;
    hideNextEpisodePrompt();
    if (nextEp) tunePlayerChannel(nextEp, { isMix: false });
  });
  const recBtn = $('#player-rec-btn');
  if (recBtn) {
    recBtn.addEventListener('pointerdown', (e) => {
      e.stopPropagation();
      if (state.activeRecording) return;
      beginLiveRecHold();
    });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach((evName) => {
      recBtn.addEventListener(evName, () => cancelLiveRecHold());
    });
    recBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (state.activeRecording) stopActiveLiveRecording();
    });
  }
  $$('[data-close-series-modal]').forEach((el) => el.addEventListener('click', closeSeriesModal));
  $('#series-season-select')?.addEventListener('change', (e) => {
    state.seriesSeason = Number(e.target.value) || 1;
    renderSeriesModal();
  });
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
  $('#player-live-playpause-btn')?.addEventListener('click', () => {
    const v = $('#preview-video');
    if (v) {
      if (v.paused) v.play().catch(() => {});
      else v.pause();
      updatePlayerTimelineUI();
    }
  });
  $('#player-playpause-btn')?.addEventListener('click', () => {
    const v = $('#preview-video');
    if (v) {
      if (v.paused) v.play().catch(() => {});
      else {
        v.pause();
        flushCurrentVodProgress(true);
      }
      updatePlayerTimelineUI();
    }
  });
  $('#player-vod-rew-5m')?.addEventListener('click', () => seekVodBySeconds(-300));
  $('#player-vod-rew-10s')?.addEventListener('click', () => seekVodBySeconds(-10));
  $('#player-vod-fwd-10s')?.addEventListener('click', () => seekVodBySeconds(10));
  $('#player-vod-fwd-5m')?.addEventListener('click', () => seekVodBySeconds(300));
  $('#player-speed-btn')?.addEventListener('click', () => {
    const speeds = [1, 1.25, 1.5, 2];
    const nextIdx = (speeds.indexOf(state.playerPlaybackRate || 1) + 1) % speeds.length;
    const rate = speeds[nextIdx];
    state.playerPlaybackRate = rate;
    const v = $('#preview-video');
    if (v) {
      setVideoPreservesPitch(v, true);
      v.playbackRate = rate;
    }
    $('#player-speed-btn').textContent = `${rate}x`;
  });

  const renderTracksPopover = () => {
    const audioEl = $('#player-audio-tracks-list');
    const subEl = $('#player-sub-tracks-list');
    if (!audioEl || !subEl) return;
    const hls = state.hls;
    const v = $('#preview-video');
    const audioTracks = hls?.audioTracks || [];
    if (audioTracks.length) {
      audioEl.innerHTML = audioTracks
        .map((tr, i) => `<button type="button" class="tracks-opt ${hls.audioTrack === i ? 'is-active' : ''}" data-audio-idx="${i}">${hls.audioTrack === i ? `${SVG_ICONS.check} ` : ''}${esc(tr.name || tr.lang || `Audio ${i + 1}`)}</button>`)
        .join('');
    } else {
      audioEl.innerHTML = `<button type="button" class="tracks-opt is-active">${SVG_ICONS.check} ${t('Originale', 'Original', 'Original')}</button>`;
    }
    const hlsSubs = hls?.subtitleTracks || [];
    const nativeSubs = Array.from(v?.textTracks || []);
    if (hlsSubs.length) {
      const offBtn = `<button type="button" class="tracks-opt ${hls.subtitleTrack === -1 ? 'is-active' : ''}" data-sub-idx="-1">${hls.subtitleTrack === -1 ? `${SVG_ICONS.check} ` : ''}${t('Disattivati', 'Off', 'Desactivados')}</button>`;
      subEl.innerHTML = offBtn + hlsSubs
        .map((tr, i) => `<button type="button" class="tracks-opt ${hls.subtitleTrack === i ? 'is-active' : ''}" data-sub-idx="${i}">${hls.subtitleTrack === i ? `${SVG_ICONS.check} ` : ''}${esc(tr.name || tr.lang || `Sub ${i + 1}`)}</button>`)
        .join('');
    } else if (nativeSubs.length) {
      const anyShowing = nativeSubs.some((tr) => tr.mode === 'showing');
      const offBtn = `<button type="button" class="tracks-opt ${!anyShowing ? 'is-active' : ''}" data-nsub-idx="-1">${!anyShowing ? `${SVG_ICONS.check} ` : ''}${t('Disattivati', 'Off', 'Desactivados')}</button>`;
      subEl.innerHTML = offBtn + nativeSubs
        .map((tr, i) => `<button type="button" class="tracks-opt ${tr.mode === 'showing' ? 'is-active' : ''}" data-nsub-idx="${i}">${tr.mode === 'showing' ? `${SVG_ICONS.check} ` : ''}${esc(tr.label || tr.language || `Sub ${i + 1}`)}</button>`)
        .join('');
    } else {
      subEl.innerHTML = `<button type="button" class="tracks-opt is-active">${SVG_ICONS.check} ${t('Disattivati', 'Off', 'Desactivados')}</button>`;
    }
  };
  $('#player-tracks-btn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    const pop = $('#player-tracks-popover');
    if (!pop) return;
    if (pop.hidden) {
      renderTracksPopover();
      pop.hidden = false;
    } else {
      pop.hidden = true;
    }
  });
  $('#player-tracks-popover')?.addEventListener('click', (e) => {
    e.stopPropagation();
    const aBtn = e.target.closest('[data-audio-idx]');
    if (aBtn && state.hls) {
      state.hls.audioTrack = Number(aBtn.dataset.audioIdx);
      renderTracksPopover();
      return;
    }
    const sBtn = e.target.closest('[data-sub-idx]');
    if (sBtn && state.hls) {
      const idx = Number(sBtn.dataset.subIdx);
      state.hls.subtitleTrack = idx;
      state.hls.subtitleDisplay = idx >= 0;
      renderTracksPopover();
      return;
    }
    const nBtn = e.target.closest('[data-nsub-idx]');
    if (nBtn) {
      const idx = Number(nBtn.dataset.nsubIdx);
      const tracks = Array.from($('#preview-video')?.textTracks || []);
      tracks.forEach((tr, i) => { tr.mode = i === idx ? 'showing' : 'disabled'; });
      renderTracksPopover();
    }
  });

  // Liquid Glass Scrubber interaction (VOD click/drag + hover preview timestamp)
  const scrubber = $('#player-scrubber');
  const scrubTip = $('#player-scrubber-tip');
  if (scrubber) {
    let dragging = false;
    const seekAtClientX = (clientX, commit = true) => {
      const v = $('#preview-video');
      if (!state.playerIsVod || !v || !Number.isFinite(v.duration) || v.duration <= 0) return;
      const rect = scrubber.getBoundingClientRect();
      const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
      const targetSec = ratio * v.duration;
      if (scrubTip) {
        scrubTip.hidden = false;
        scrubTip.style.left = `${(ratio * 100).toFixed(2)}%`;
        scrubTip.textContent = formatDurationSec(targetSec);
      }
      if (commit) {
        v.currentTime = targetSec;
        updatePlayerTimelineUI();
      }
    };
    scrubber.addEventListener('pointerdown', (e) => {
      if (!state.playerIsVod) return;
      dragging = true;
      scrubber.setPointerCapture?.(e.pointerId);
      seekAtClientX(e.clientX, true);
      wakePlayerControls();
    });
    scrubber.addEventListener('pointermove', (e) => {
      if (!state.playerIsVod) return;
      seekAtClientX(e.clientX, dragging);
    });
    const endDrag = () => {
      dragging = false;
      if (scrubTip) scrubTip.hidden = true;
    };
    scrubber.addEventListener('pointerup', endDrag);
    scrubber.addEventListener('pointercancel', endDrag);
    scrubber.addEventListener('mouseleave', () => {
      if (!dragging && scrubTip) scrubTip.hidden = true;
    });
  }
  $('#player-fav-btn')?.addEventListener('click', () => toggleFavorite(state.playerChannel));
  $('#player-retry-btn')?.addEventListener('click', () => tunePlayerChannel(state.playerChannel));
  const toggleSourcePopover = (forceOpen = false) => {
    const pop = $('#player-source-popover');
    if (!pop) return;
    if (!forceOpen && !pop.hidden) {
      pop.hidden = true;
      return;
    }
    const srcs = state.playerSources || [];
    if (srcs.length <= 1) return;
    const curUrl = state.playerActiveUrl || state.playerChannel?.streamUrl || '';
    pop.innerHTML = `
      <h4>${t('Scegli sorgente', 'Choose source', 'Elegir fuente')}</h4>
      <div class="tracks-list">
        ${srcs
          .map((s, idx) => {
            const isAct = idx === state.playerSourceIdx || (curUrl && s.url === curUrl);
            const title = s.quality || s.label || `Sorgente ${idx + 1}`;
            const meta = [s.protocol, s.host, s.latencyMs != null ? `${s.latencyMs} ms` : ''].filter(Boolean).join(' · ');
            return `
              <button type="button" class="src-opt-row ${isAct ? 'is-active' : ''}" data-pick-src="${idx}">
                <div>
                  <div>${esc(title)}</div>
                  ${meta ? `<div class="src-opt-meta">${esc(meta)}</div>` : ''}
                </div>
                ${s.recommended ? `<span class="src-rec-pill">${t('Migliore', 'Best', 'Mejor')}</span>` : ''}
              </button>
            `;
          })
          .join('')}
      </div>
    `;
    pop.hidden = false;
    const tracksPop = $('#player-tracks-popover');
    if (tracksPop) tracksPop.hidden = true;
    $$('[data-pick-src]', pop).forEach((btn) => {
      btn.addEventListener('click', (ev) => {
        ev.stopPropagation();
        const idx = Number(btn.dataset.pickSrc);
        const picked = state.playerSources[idx];
        if (!picked) return;
        state.playerSourceIdx = idx;
        pop.hidden = true;
        reportWebChannelSource(state.playerChannel?.id, picked.url, 'manual_select');
        tunePlayerChannel({ ...state.playerChannel, streamUrl: picked.url }, { preserveSourceIdx: true, pinSource: true });
      });
    });
  };
  $('#player-sources-btn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (!state.playerIsVod && state.playerSources.length > 0) {
      state.playerCarouselMode = 'sources';
      state.playerCarouselItems = state.playerSources;
      toggleRightDrawer(true);
    } else {
      toggleSourcePopover();
    }
  });
  $('#player-next-src-btn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (state.playerSources.length > 1) {
      toggleSourcePopover(true);
    }
  });
  $('#player-mute-btn')?.addEventListener('click', () => {
    const v = $('#preview-video');
    v.muted = !v.muted;
    $('#player-mute-btn').innerHTML = v.muted ? SVG_ICONS.volumeOff : SVG_ICONS.volumeOn;
  });
  $('#player-fullscreen-btn')?.addEventListener('click', () => {
    const v = $('#preview-video');
    if (isIOSDevice()) {
      try {
        if (v?.webkitDisplayingFullscreen) v.webkitExitFullscreen?.();
        else if (v?.webkitSupportsFullscreen || v?.webkitEnterFullscreen) v.webkitEnterFullscreen?.();
      } catch (_) {}
      return;
    }
    const stage = $('#player-stage');
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    else stage.requestFullscreen?.().catch(() => v?.webkitEnterFullscreen?.());
  });
  $('#preview-video')?.addEventListener('webkitendfullscreen', () => {
    const v = $('#preview-video');
    if (isIOSDevice() && state.playerOpen && v?.webkitPresentationMode !== 'picture-in-picture') {
      closePlayer();
    }
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
        if (state.rightDrawerOpen) toggleRightDrawer(false);
        else if (state.drawerOpen) togglePlayerDrawer(false);
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
      if (state.rightDrawerOpen) {
        if (e.key === 'ArrowLeft') {
          e.preventDefault();
          toggleRightDrawer(false);
        } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault();
          const items = state.playerCarouselItems || [];
          const maxIdx = Math.max(0, items.length - 1);
          const next = Math.max(0, Math.min(maxIdx, state.rightDrawerCursorIdx + (e.key === 'ArrowDown' ? 1 : -1)));
          state.rightDrawerCursorIdx = next;
          $$('#player-right-list .drawer-item').forEach((el) => {
            el.classList.toggle('is-cursor', Number(el.dataset.rdIdx) === next);
          });
          $(`#player-right-list .drawer-item[data-rd-idx="${next}"]`)?.focus();
          $(`#player-right-list .drawer-item[data-rd-idx="${next}"]`)?.scrollIntoView({ block: 'nearest' });
        }
        return;
      }
      if (e.key === 'Enter') {
        if ($('#player-next-ep-card') && !$('#player-next-ep-card').hidden && state.nextEpisodeCandidate) {
          e.preventDefault();
          const nextEp = state.nextEpisodeCandidate;
          hideNextEpisodePrompt();
          tunePlayerChannel(nextEp, { isMix: false });
          return;
        }
        if (!state.playerIsVod && !state.drawerOpen && !state.rightDrawerOpen) {
          if (e.repeat) return;
          e.preventDefault();
          beginLiveRecHold();
          return;
        }
      }
      if (e.key === ' ' && state.playerIsVod) {
        e.preventDefault();
        $('#player-playpause-btn')?.click();
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        if (state.playerIsVod) seekVodBySeconds(e.shiftKey ? -300 : -10);
        else if (!state.playerIsMix) togglePlayerDrawer(true);
        else stepNormalChannel(-1);
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        if (state.playerIsVod) seekVodBySeconds(e.shiftKey ? 300 : 10);
        else if (state.playerIsMix) stepNormalChannel(1);
        else toggleRightDrawer(true);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        if (state.playerIsVod) seekVodBySeconds(300);
        else stepNormalChannel(1);
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        if (state.playerIsVod) {
          toggleRightDrawer();
        } else {
          stepNormalChannel(-1);
        }
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
      if (!$('#recordings-modal')?.hidden) {
        $('#recordings-modal').hidden = true;
        return;
      }
      if (state.vodLanguageMenuOpen) {
        state.vodLanguageMenuOpen = false;
        const menu = $('#vod-language-menu');
        const btn = $('#vod-language-btn');
        if (menu) menu.hidden = true;
        if (btn) btn.setAttribute('aria-expanded', 'false');
        return;
      }
      if (state.vodPlatformMenuOpen) {
        state.vodPlatformMenuOpen = false;
        const menu = $('#vod-platform-menu');
        const btn = $('#vod-platform-btn');
        if (menu) menu.hidden = true;
        if (btn) btn.setAttribute('aria-expanded', 'false');
        return;
      }
      if (!$('#mix-filters-overlay')?.hidden) closeMixFiltersOverlay();
      else if (!$('#series-modal')?.hidden) closeSeriesModal();
      else if (!$('#mix-modal').hidden) $('#mix-modal').hidden = true;
      else if (!$('#country-modal').hidden) $('#country-modal').hidden = true;
      else if (!$('#server-modal').hidden) $('#server-modal').hidden = true;
      else if (!$('#profile-modal').hidden && state.activeProfile) closeProfileGate();
    }
  });

  window.addEventListener('keyup', (e) => {
    if (e.key === 'Enter' || e.key === 'Escape') {
      cancelLiveRecHold();
    }
  });

  document.addEventListener('click', (e) => {
    const ctxMenu = $('#card-context-menu');
    if (ctxMenu && !ctxMenu.contains(e.target)) {
      closeCardContextMenu();
    }
    const srcPop = $('#player-source-popover');
    if (srcPop && !srcPop.hidden && !e.target.closest('#player-sources-wrap') && !e.target.closest('#player-next-src-btn')) {
      srcPop.hidden = true;
    }
    const tracksPop = $('#player-tracks-popover');
    if (tracksPop && !tracksPop.hidden && !e.target.closest('#player-tracks-wrap')) {
      tracksPop.hidden = true;
    }
    if (state.vodLanguageMenuOpen && !e.target.closest('#vod-language-wrap')) {
      state.vodLanguageMenuOpen = false;
      const menu = $('#vod-language-menu');
      const btn = $('#vod-language-btn');
      if (menu) menu.hidden = true;
      if (btn) btn.setAttribute('aria-expanded', 'false');
    }
    if (state.vodPlatformMenuOpen && !e.target.closest('#vod-platform-wrap')) {
      state.vodPlatformMenuOpen = false;
      const menu = $('#vod-platform-menu');
      const btn = $('#vod-platform-btn');
      if (menu) menu.hidden = true;
      if (btn) btn.setAttribute('aria-expanded', 'false');
    }
  });

  setupModalScrollContainment();
}

function syncModalScrollLock() {
  const hasOpenOverlay = $$('.modal, .player-overlay').some((el) => !el.hidden);
  document.documentElement.classList.toggle('modal-scroll-lock', hasOpenOverlay);
  document.body.classList.toggle('modal-scroll-lock', hasOpenOverlay);
}

function setupModalScrollContainment() {
  const observer = new MutationObserver(() => syncModalScrollLock());
  $$('.modal, .player-overlay').forEach((el) => {
    observer.observe(el, { attributes: true, attributeFilter: ['hidden'] });
  });
  syncModalScrollLock();

  const countriesList = $('#modal-countries-list');
  if (countriesList) {
    countriesList.addEventListener(
      'wheel',
      (e) => {
        const atTop = countriesList.scrollTop <= 0 && e.deltaY < 0;
        const atBottom = countriesList.scrollTop + countriesList.clientHeight >= countriesList.scrollHeight - 1 && e.deltaY > 0;
        if (atTop || atBottom) e.preventDefault();
      },
      { passive: false },
    );
  }
  const countryBackdrop = $('#country-modal .modal-backdrop');
  if (countryBackdrop) {
    countryBackdrop.addEventListener('wheel', (e) => e.preventDefault(), { passive: false });
  }
  const countryDialog = $('#country-modal .country-dialog');
  if (countryDialog) {
    countryDialog.addEventListener(
      'wheel',
      (e) => {
        if (!e.target.closest('#modal-countries-list')) {
          e.preventDefault();
        }
      },
      { passive: false },
    );
  }
}

/* ---------------- App Boot ---------------- */
async function bootData() {
  try {
    await Promise.all([loadServerConfig(), loadProfiles(), checkAdminAuthStatus()]);
    updateShellChrome();
    await Promise.all([loadChannels({ reset: true }), loadFavorites()]);
    if (state.adminUnlocked) {
      await refreshSystemStatus();
    }
    if (state.systemPollTimer) clearInterval(state.systemPollTimer);
    state.systemPollTimer = setInterval(() => {
      if (state.page === 'settings' && state.adminUnlocked) refreshSystemStatus();
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
if (initialHash === 'favorites' || initialHash === 'settings' || initialHash === 'vod') state.page = initialHash;
updateShellChrome();
bootData();
