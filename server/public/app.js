/* CasaTV Web Client 1.3.1 */

const app = document.getElementById('app');
const toasts = document.getElementById('toasts');

// Modals & Player Elements
const playerModal = document.getElementById('player-modal');
const previewVideo = document.getElementById('preview-video');
const playerLoading = document.getElementById('player-loading');
const playerError = document.getElementById('player-error');
const playerErrorMsg = document.getElementById('player-error-msg');
const playerRetryBtn = document.getElementById('player-retry-btn');
const playerNextSrcBtn = document.getElementById('player-next-src-btn');

const movieModal = document.getElementById('movie-modal');
const movieModalBody = document.getElementById('movie-modal-body');
const profileModal = document.getElementById('profile-modal');
const modalProfilesGrid = document.getElementById('modal-profiles-grid');
const pinModal = document.getElementById('pin-modal');
const countryModal = document.getElementById('country-modal');

// Shared State
const state = {
  config: null,
  page: 'home',
  deviceId: getOrCreateDeviceId(),
  profiles: [],
  activeProfile: null,
  tvCountry: 'IT',
  adminUnlocked: sessionStorage.getItem('casatv_admin_unlocked') === 'true',
  
  // Home Data
  homeRows: [],
  
  // TV Mode Data
  tvChannels: [],
  tvCurrentChannel: null,
  
  // Channels Data
  channels: {
    items: [],
    total: 0,
    offset: 0,
    limit: 60,
    filters: {
      q: '',
      country: '',
      category: '',
      language: '',
      favorite: false,
    },
    loading: false,
  },
  
  // Library Data
  library: {
    items: [],
    filter: 'all',
    movies: 0,
    episodes: 0,
  },
  
  // System / Admin Data
  system: null,
  adminPoll: null,
  
  // Player State
  hls: null,
  playerChannel: null,
  playerSources: [],
  playerSourceIndex: 0,
  playbackState: 'idle',
  playbackChannel: '',
};

/* ---------------- Helper Utilities ---------------- */
function getOrCreateDeviceId() {
  let id = localStorage.getItem('casatv_device_id');
  if (!id) {
    id = 'web-' + Math.random().toString(36).slice(2, 10) + '-' + Date.now().toString(36);
    localStorage.setItem('casatv_device_id', id);
  }
  return id;
}

function getBrowserName() {
  const ua = navigator.userAgent;
  if (/mobile/i.test(ua)) return 'Mobile Browser';
  if (/chrome|crios/i.test(ua)) return 'Chrome';
  if (/safari/i.test(ua)) return 'Safari';
  if (/firefox/i.test(ua)) return 'Firefox';
  return 'Web Browser';
}

function isMobile() {
  return window.innerWidth <= 980 || /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
}

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const esc = (value) =>
  String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[c]);

const fmtNum = (n) => new Intl.NumberFormat('it-IT').format(Number(n) || 0);
const fmtDuration = (sec) => {
  if (!sec) return '';
  const m = Math.round(sec / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem ? `${h}h ${rem}m` : `${h}h`;
};

function toast(msg, type = 'ok', duration = 3500) {
  if (!toasts) return;
  const t = document.createElement('div');
  t.className = `toast ${type}`;
  t.innerHTML = `<strong>${esc(msg)}</strong>`;
  toasts.appendChild(t);
  setTimeout(() => {
    t.style.opacity = '0';
    t.style.transform = 'translateY(8px)';
    setTimeout(() => t.remove(), 250);
  }, duration);
}

/* ---------------- API Fetch Client ---------------- */
async function api(path, opts = {}) {
  const headers = {
    'Accept': 'application/json',
    'X-Device-Id': state.deviceId,
    'X-Device-Name': `${getBrowserName()} (${isMobile() ? 'Mobile' : 'Desktop'})`,
    'X-Device-Platform': isMobile() ? 'Mobile Web' : 'Desktop Web',
    'X-App-Version': '1.3.1',
    'X-Playback-State': state.playbackState,
    'X-Playback-Channel': state.playbackChannel,
    ...(opts.headers || {}),
  };
  if (state.activeProfile?.id) {
    headers['X-Profile-Id'] = state.activeProfile.id;
  }
  if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.body);
  }
  const res = await fetch(path, { credentials: 'same-origin', ...opts, headers });
  if (!res.ok) {
    let errText = `Errore HTTP ${res.status}`;
    try {
      const errJson = await res.json();
      if (errJson.error) errText = errJson.error;
    } catch (_) {}
    const err = new Error(errText);
    err.status = res.status;
    throw err;
  }
  if (res.status === 204) return null;
  return res.json();
}

/* ---------------- Heartbeat & Background Polling ---------------- */
setInterval(async () => {
  try {
    await api('/api/clients/heartbeat', {
      method: 'POST',
      body: {
        id: state.deviceId,
        name: `${getBrowserName()} (${isMobile() ? 'Mobile' : 'Desktop'})`,
        platform: isMobile() ? 'Mobile Web' : 'Desktop Web',
        appVersion: '1.3.1',
        profileId: state.activeProfile?.id || '',
        profileName: state.activeProfile?.name || '',
        state: state.playbackState,
        channel: state.playbackChannel,
      },
    });
  } catch (_) {}
}, 25000);

/* ---------------- Profiles & First Gate ---------------- */
const PROFILE_AVATARS = [
  '🦁', '🐯', '🐼', '🦊', '🐶', '🐱', '🐻', '🐨',
  '🐮', '🐷', '🐸', '🐵', '🦉', '🦄', '🚀', '🛸',
  '🎮', '🎬', '🍿', '📺'
];

async function loadProfiles() {
  try {
    const data = await api('/api/profiles');
    state.profiles = data.profiles || [];
    const savedId = localStorage.getItem('casatv_profile_id');
    const matched = state.profiles.find((p) => p.id === savedId);
    state.activeProfile = matched || state.profiles[0] || null;
    if (state.activeProfile) {
      localStorage.setItem('casatv_profile_id', state.activeProfile.id);
      state.tvCountry = state.activeProfile.country || 'IT';
    }
    updateProfileWidgets();
  } catch (err) {
    console.warn('Errore caricamento profili:', err);
  }
}

function updateProfileWidgets() {
  const p = state.activeProfile;
  if (!p) return;
  // Mobile Header
  const mAvatar = $('#mobile-profile-avatar');
  const mName = $('#mobile-profile-name');
  if (mAvatar) mAvatar.textContent = p.avatar || '👤';
  if (mName) mName.textContent = p.name || 'Profilo';
  
  // Desktop Sidebar Card
  const sAvatar = $('#sidebar-profile-avatar');
  const sName = $('#sidebar-profile-name');
  const sCountry = $('#sidebar-profile-country');
  if (sAvatar) sAvatar.textContent = p.avatar || '👤';
  if (sName) sName.textContent = p.name || 'Profilo';
  if (sCountry) sCountry.textContent = `${p.country || 'IT'} · ${p.language?.toUpperCase() || 'IT'}`;

  // Country Buttons
  const flag = countryFlag(state.tvCountry);
  const mFlag = $('#mobile-country-flag');
  const mCode = $('#mobile-country-code');
  if (mFlag) mFlag.textContent = flag;
  if (mCode) mCode.textContent = state.tvCountry;
}

function openProfileModal() {
  if (!profileModal) return;
  modalProfilesGrid.innerHTML = state.profiles
    .map(
      (p) => `
      <div class="profile-card-item ${p.id === state.activeProfile?.id ? 'is-active' : ''}" data-profile-id="${esc(p.id)}">
        <div class="profile-avatar-circle">${esc(p.avatar || '👤')}</div>
        <span class="p-name">${esc(p.name)}</span>
        <small class="muted">${esc(p.country || 'IT')}</small>
      </div>
    `,
    )
    .join('');

  $$('.profile-card-item', modalProfilesGrid).forEach((el) => {
    el.addEventListener('click', () => {
      const pid = el.dataset.profileId;
      const target = state.profiles.find((p) => p.id === pid);
      if (target) {
        state.activeProfile = target;
        localStorage.setItem('casatv_profile_id', target.id);
        if (target.country) state.tvCountry = target.country;
        updateProfileWidgets();
        closeProfileModal();
        toast(`Profilo attivo: ${target.name}`, 'ok');
        render();
      }
    });
  });

  profileModal.hidden = false;
}

function closeProfileModal() {
  if (profileModal) profileModal.hidden = true;
}

/* ---------------- Country Helpers & Selector Modal ---------------- */
function countryFlag(code) {
  if (!code || code.length !== 2) return '🌐';
  const c = code.toUpperCase();
  return String.fromCodePoint(
    c.charCodeAt(0) + 127397,
    c.charCodeAt(1) + 127397,
  );
}

function openCountryModal() {
  if (!countryModal) return;
  const listEl = $('#modal-countries-list');
  const inputEl = $('#country-search-input');
  
  const allCountries = (state.config?.facets?.countries || []).slice();
  if (!allCountries.some((c) => c.code === 'IT')) {
    allCountries.unshift({ code: 'IT', name: 'Italia' });
  }

  function renderList(filter = '') {
    const q = filter.trim().toLowerCase();
    const filtered = allCountries.filter(
      (c) => c.code.toLowerCase().includes(q) || c.name.toLowerCase().includes(q),
    );
    listEl.innerHTML = filtered
      .map(
        (c) => `
        <button class="country-row-btn ${c.code === state.tvCountry ? 'is-active' : ''}" data-code="${esc(c.code)}">
          <span>${countryFlag(c.code)} ${esc(c.name)}</span>
          <span class="muted small">${esc(c.code)}</span>
        </button>
      `,
      )
      .join('');

    $$('.country-row-btn', listEl).forEach((btn) => {
      btn.addEventListener('click', () => {
        state.tvCountry = btn.dataset.code;
        localStorage.setItem('casatv_tv_country', state.tvCountry);
        updateProfileWidgets();
        closeCountryModal();
        toast(`Paese TV impostato: ${state.tvCountry}`, 'ok');
        if (state.page === 'home' || state.page === 'tv') {
          render();
        }
      });
    });
  }

  renderList();
  if (inputEl) {
    inputEl.value = '';
    inputEl.oninput = () => renderList(inputEl.value);
  }
  countryModal.hidden = false;
}

function closeCountryModal() {
  if (countryModal) countryModal.hidden = true;
}

/* ---------------- PIN Authentication Modal ---------------- */
let pinResolve = null;

function promptAdminPin() {
  return new Promise((resolve) => {
    if (state.adminUnlocked) {
      resolve(true);
      return;
    }
    pinResolve = resolve;
    const digits = $$('.pin-digit', pinModal);
    digits.forEach((d) => (d.value = ''));
    $('#pin-error-msg').hidden = true;
    pinModal.hidden = false;
    setTimeout(() => digits[0]?.focus(), 100);
  });
}

function closePinModal(success = false) {
  if (pinModal) pinModal.hidden = true;
  if (pinResolve) {
    pinResolve(success);
    pinResolve = null;
  }
}

function setupPinModalEvents() {
  const digits = $$('.pin-digit', pinModal);
  const errorMsg = $('#pin-error-msg');
  const pinInputsWrap = $('#pin-inputs');
  const form = $('#pin-form');

  digits.forEach((input, idx) => {
    input.addEventListener('input', () => {
      input.value = input.value.replace(/[^0-9]/g, '').slice(-1);
      if (input.value && idx < 3) {
        digits[idx + 1].focus();
      }
    });

    input.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' && !input.value && idx > 0) {
        digits[idx - 1].focus();
      }
    });
  });

  // Touch Keypad
  $$('.kp-btn', pinModal).forEach((btn) => {
    btn.addEventListener('click', () => {
      const val = btn.dataset.val;
      const action = btn.dataset.action;
      const emptyIdx = digits.findIndex((d) => !d.value);

      if (val !== undefined) {
        if (emptyIdx !== -1) {
          digits[emptyIdx].value = val;
          if (emptyIdx < 3) digits[emptyIdx + 1].focus();
        }
      } else if (action === 'clear') {
        digits.forEach((d) => (d.value = ''));
        digits[0].focus();
      } else if (action === 'back') {
        const lastFilled = [...digits].reverse().find((d) => d.value);
        if (lastFilled) {
          lastFilled.value = '';
          lastFilled.focus();
        }
      }
    });
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const pin = digits.map((d) => d.value).join('');
    if (pin.length !== 4) {
      errorMsg.textContent = 'Inserisci tutte e 4 le cifre del PIN.';
      errorMsg.hidden = false;
      return;
    }

    try {
      await api('/api/settings/verify-pin', {
        method: 'POST',
        body: { pin },
      });
      state.adminUnlocked = true;
      sessionStorage.setItem('casatv_admin_unlocked', 'true');
      toast('Accesso consentito alla Gestione Server', 'ok');
      closePinModal(true);
      window.location.hash = '#/admin';
    } catch (err) {
      pinInputsWrap.classList.add('shake');
      setTimeout(() => pinInputsWrap.classList.remove('shake'), 450);
      errorMsg.textContent = 'PIN non valido. Riprova (PIN predefinito: 0000).';
      errorMsg.hidden = false;
      digits.forEach((d) => (d.value = ''));
      digits[0].focus();
    }
  });
}

/* ---------------- Video Player & HLS Stream ---------------- */
function initPlayer() {
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }
  previewVideo.pause();
  previewVideo.removeAttribute('src');
  previewVideo.load();
  playerLoading.hidden = false;
  playerError.hidden = true;
}

function playStream(url, channel = null) {
  if (!url) return;
  initPlayer();
  state.playerChannel = channel;
  state.playbackState = 'playing';
  state.playbackChannel = channel?.name || 'Canale TV';

  // Fill Header
  $('#player-title').textContent = channel?.name || 'Canale TV';
  $('#player-number').textContent = channel?.number ? `CANALE ${channel.number}` : '';
  $('#player-sub').textContent = channel?.category || channel?.country || '';
  $('#player-open').href = url;
  
  const logoEl = $('#player-logo');
  if (channel?.logo) {
    logoEl.innerHTML = `<img src="${esc(channel.logo)}" alt="${esc(channel.name)}" />`;
  } else {
    logoEl.innerHTML = `📺`;
  }

  // Update Favorite button
  const favBtn = $('#player-fav');
  favBtn.textContent = channel?.favorite ? '★ Preferito' : '☆ Preferito';
  favBtn.onclick = () => toggleChannelFavorite(channel);

  playerModal.hidden = false;

  const onLoaded = () => {
    playerLoading.hidden = true;
    playerError.hidden = true;
  };

  const onError = () => {
    playerLoading.hidden = true;
    playerError.hidden = false;
    playerErrorMsg.textContent = 'Flusso video non raggiungibile o formato non supportato dal browser.';
  };

  if (window.Hls && window.Hls.isSupported() && url.includes('.m3u8')) {
    const hls = new window.Hls({ enableWorker: true, lowLatencyMode: true });
    state.hls = hls;
    hls.loadSource(url);
    hls.attachMedia(previewVideo);
    hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
      previewVideo.play().catch(() => {});
      onLoaded();
    });
    hls.on(window.Hls.Events.ERROR, (_, data) => {
      if (data.fatal) {
        switch (data.type) {
          case window.Hls.ErrorTypes.NETWORK_ERROR:
            hls.startLoad();
            break;
          case window.Hls.ErrorTypes.MEDIA_ERROR:
            hls.recoverMediaError();
            break;
          default:
            hls.destroy();
            onError();
            break;
        }
      }
    });
  } else {
    previewVideo.src = url;
    previewVideo.onloadedmetadata = onLoaded;
    previewVideo.onerror = onError;
    previewVideo.play().catch(() => {});
  }

  // Fetch sources for failover
  if (channel?.id) {
    fetchChannelSources(channel.id);
  }
}

async function fetchChannelSources(channelId) {
  try {
    const data = await api(`/api/channels/${channelId}/streams`);
    state.playerSources = data.sources || [];
    const bar = $('#player-sources');
    const chips = $('#player-sources-chips');
    if (state.playerSources.length > 1) {
      chips.innerHTML = state.playerSources
        .map(
          (s, i) => `
          <button class="source-chip ${i === 0 ? 'active' : ''}" data-idx="${i}">
            ${esc(s.quality || 'Auto')} · ${esc(s.format || 'HLS')}
          </button>
        `,
        )
        .join('');
      $$('.source-chip', chips).forEach((chip) => {
        chip.addEventListener('click', () => {
          const idx = Number(chip.dataset.idx);
          switchPlayerSource(idx);
        });
      });
      bar.hidden = false;
    } else {
      bar.hidden = true;
    }
  } catch (_) {
    $('#player-sources').hidden = true;
  }
}

function switchPlayerSource(idx) {
  const src = state.playerSources[idx];
  if (!src) return;
  state.playerSourceIndex = idx;
  $$('#player-sources-chips .source-chip').forEach((c, i) => {
    c.classList.toggle('active', i === idx);
  });
  playStream(src.url, state.playerChannel);
}

function closePlayerModal() {
  initPlayer();
  state.playbackState = 'idle';
  state.playbackChannel = '';
  playerModal.hidden = true;
}

playerRetryBtn?.addEventListener('click', () => {
  if (state.playerChannel) {
    const url = state.playerSources[state.playerSourceIndex]?.url || state.playerChannel.streamUrl;
    playStream(url, state.playerChannel);
  }
});

playerNextSrcBtn?.addEventListener('click', () => {
  if (state.playerSources.length > 1) {
    const nextIdx = (state.playerSourceIndex + 1) % state.playerSources.length;
    switchPlayerSource(nextIdx);
  }
});

$('#player-fullscreen-btn')?.addEventListener('click', () => {
  if (previewVideo.requestFullscreen) previewVideo.requestFullscreen();
  else if (previewVideo.webkitRequestFullscreen) previewVideo.webkitRequestFullscreen();
});

/* ---------------- Favorite Toggle ---------------- */
async function toggleChannelFavorite(ch) {
  if (!ch) return;
  const next = !ch.favorite;
  try {
    await api(`/api/channels/${ch.id}`, {
      method: 'PATCH',
      body: { favorite: next },
    });
    ch.favorite = next;
    const favBtn = $('#player-fav');
    if (favBtn) favBtn.textContent = next ? '★ Preferito' : '☆ Preferito';
    toast(next ? 'Canale aggiunto ai preferiti ⭐' : 'Canale rimosso dai preferiti', 'ok');
    if (state.page === 'home' || state.page === 'channels') {
      render();
    }
  } catch (err) {
    toast('Impossibile salvare il preferito', 'err');
  }
}

/* ---------------- Movie / Library Modal ---------------- */
function openMovieModal(item) {
  if (!movieModal || !item) return;
  const isSeries = item.type === 'series';
  const durationText = fmtDuration(item.duration);
  const streamUrl = `/api/library/${item.id}/stream`;

  movieModalBody.innerHTML = `
    <div class="movie-detail-view">
      <div class="movie-detail-poster">
        <img src="${esc(item.posterUrl || '/favicon.svg')}" alt="${esc(item.title)}" onerror="this.src='/favicon.svg'" />
      </div>
      <div class="movie-detail-content">
        <h2>${esc(item.title)}</h2>
        <div class="movie-detail-meta">
          <span>${esc(item.year || '')}</span>
          ${durationText ? `<span>• ${durationText}</span>` : ''}
          ${item.quality ? `<span class="badge ok">${esc(item.quality)}</span>` : ''}
          ${item.rating ? `<span class="badge warn">★ ${item.rating}</span>` : ''}
        </div>
        ${
          item.genres?.length
            ? `<div class="movie-detail-genres">${item.genres
                .map((g) => `<span class="genre-tag">${esc(g)}</span>`)
                .join('')}</div>`
            : ''
        }
        <div class="movie-detail-plot">${esc(item.plot || 'Nessuna trama disponibile per questo titolo.')}</div>
        <div class="movie-detail-actions">
          <button class="btn primary" id="btn-watch-movie" style="padding:12px 24px;font-size:15px">
            ▶ Guarda ora (${isSeries ? 'Episodio' : 'Film'})
          </button>
        </div>
      </div>
    </div>
  `;

  $('#btn-watch-movie')?.addEventListener('click', () => {
    closeMovieModal();
    playStream(streamUrl, {
      name: item.title,
      category: isSeries ? 'Serie TV' : 'Film',
      logo: item.posterUrl,
    });
  });

  movieModal.hidden = false;
}

function closeMovieModal() {
  if (movieModal) movieModal.hidden = true;
}

/* ---------------- Render Pages ---------------- */

/* 1. HOME SCREEN */
async function renderHome() {
  app.innerHTML = `
    <div class="home-container">
      <div class="tv-mode-hero">
        <div class="tv-mode-content">
          <span class="tv-mode-tag">
            <svg viewBox="0 0 24 24" width="16" height="16"><rect x="2" y="7" width="20" height="14" rx="2"></rect><path d="M17 2l-5 5-5-5"></path></svg>
            Modo TV Tradizionale
          </span>
          <h2>I Canali Nazionali in TV Mode (${esc(state.tvCountry)})</h2>
          <p>Naviga e fai zapping tra i canali ordinati per numero con tasti su/giù e sintonizzazione diretta, proprio come sulla TV di casa.</p>
          <div class="tv-mode-actions">
            <a href="#/tv" class="btn primary">▶ Avvia Modo TV</a>
            <button class="btn ghost" id="home-switch-country">Cambia Paese (${countryFlag(state.tvCountry)} ${state.tvCountry})</button>
          </div>
        </div>
        <div class="tv-mode-visual">
          <div class="tv-mode-screen-mock">
            <svg viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          </div>
          <small class="muted">Zapping immediato</small>
        </div>
      </div>

      <div id="home-carousels-container">
        <div class="loading-screen" style="height:200px"><div class="spinner"></div><p>Carico sezioni Home…</p></div>
      </div>
    </div>
  `;

  $('#home-switch-country')?.addEventListener('click', openCountryModal);

  try {
    const data = await api('/api/home');
    state.homeRows = data.rows || [];
    const container = $('#home-carousels-container');
    if (!container) return;

    if (!state.homeRows.length) {
      container.innerHTML = `
        <div class="panel panel-pad" style="text-align:center">
          <h3>Nessun canale caricato</h3>
          <p class="muted">Importa una playlist M3U o configura il catalogo da Impostazioni > Gestione Server.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = state.homeRows
      .map(
        (row) => `
        <section class="carousel-section" data-row-id="${esc(row.id)}">
          <div class="carousel-head">
            <h3>${esc(row.title)}</h3>
            <a href="#/channels" class="see-all">Vedi tutti &rarr;</a>
          </div>
          <div class="carousel-track">
            ${(row.channels || [])
              .map((ch) => renderChannelCardHtml(ch))
              .join('')}
          </div>
        </section>
      `,
      )
      .join('');

    // Attach card clicks
    $$('.channel-card', container).forEach((card) => {
      card.addEventListener('click', (e) => {
        if (e.target.closest('.card-fav-btn')) return;
        const id = card.dataset.id;
        const streamUrl = card.dataset.stream;
        const name = card.dataset.name;
        const logo = card.dataset.logo;
        const num = card.dataset.num;
        playStream(streamUrl, { id, name, logo, number: num, streamUrl });
      });
    });

    $$('.card-fav-btn', container).forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.dataset.id;
        toggleChannelFavorite({ id, favorite: btn.classList.contains('is-fav') });
      });
    });
  } catch (err) {
    console.error('Errore Home:', err);
  }
}

function renderChannelCardHtml(ch) {
  const isFav = Boolean(ch.favorite);
  return `
    <div class="channel-card" data-id="${esc(ch.id)}" data-stream="${esc(ch.streamUrl)}" data-name="${esc(ch.name)}" data-logo="${esc(ch.logo || '')}" data-num="${esc(ch.number || '')}">
      <div class="card-thumb">
        ${ch.number ? `<span class="num-badge">${esc(ch.number)}</span>` : ''}
        ${ch.quality ? `<span class="quality-badge">${esc(ch.quality)}</span>` : ''}
        ${ch.logo ? `<img src="${esc(ch.logo)}" alt="${esc(ch.name)}" loading="lazy" onerror="this.remove()" />` : `<span>📺</span>`}
      </div>
      <div class="card-info">
        <h4 class="card-title">${esc(ch.name)}</h4>
        <div class="card-sub">
          <span>${esc(ch.category || ch.country || '')}</span>
          <button class="card-fav-btn ${isFav ? 'is-fav' : ''}" data-id="${esc(ch.id)}" title="Preferito">
            ${isFav ? '★' : '☆'}
          </button>
        </div>
      </div>
    </div>
  `;
}

/* 2. TV MODE (Zapping Traditional TV) */
async function renderTvMode() {
  app.innerHTML = `
    <div class="tv-mode-container">
      <div class="tv-player-area">
        <div class="tv-video-wrap">
          <video id="tv-mode-video" playsinline autoplay controls></video>
          <div class="player-loading" id="tv-video-loading"><div class="spinner"></div></div>
        </div>
        <div class="tv-channel-bar">
          <div class="tv-channel-meta">
            <span class="ch-num" id="tv-bar-num">—</span>
            <div class="ch-logo" id="tv-bar-logo"></div>
            <div class="ch-title-wrap">
              <h3 class="ch-title" id="tv-bar-title">Seleziona un canale</h3>
              <p class="ch-now" id="tv-bar-now">Paese: ${countryFlag(state.tvCountry)} ${esc(state.tvCountry)}</p>
            </div>
          </div>
          <div class="tv-channel-actions">
            <button class="btn ghost sm" id="tv-bar-fav">⭐ Preferito</button>
            <button class="btn ghost sm" id="tv-bar-country">Paese</button>
          </div>
        </div>
      </div>

      <div class="tv-zapping-panel">
        <div class="tv-zap-head">
          <h3>Guida Canali (${countryFlag(state.tvCountry)} ${esc(state.tvCountry)})</h3>
          <span class="muted small" id="tv-zap-count">Caricamento…</span>
        </div>
        <div class="tv-zap-controls">
          <button class="btn ghost sm" id="tv-zap-prev">▲ Canale Prec.</button>
          <button class="btn ghost sm" id="tv-zap-next">▼ Canale Succ.</button>
        </div>
        <div class="tv-zap-channels-list" id="tv-zap-list">
          <div class="loading-screen" style="height:140px"><div class="spinner"></div></div>
        </div>
      </div>
    </div>
  `;

  $('#tv-bar-country')?.addEventListener('click', openCountryModal);

  const video = $('#tv-mode-video');
  const loading = $('#tv-video-loading');

  // Load TV channels for the selected country
  try {
    const data = await api(`/api/channels?country=${encodeURIComponent(state.tvCountry)}&limit=150`);
    state.tvChannels = data.items || data.channels || [];
    $('#tv-zap-count').textContent = `${state.tvChannels.length} canali`;

    const listEl = $('#tv-zap-list');
    if (!state.tvChannels.length) {
      listEl.innerHTML = `<p class="muted" style="padding:16px;text-align:center">Nessun canale per ${esc(state.tvCountry)}. Prova a cambiare paese.</p>`;
      loading.hidden = true;
      return;
    }

    listEl.innerHTML = state.tvChannels
      .map(
        (ch) => `
        <div class="tv-zap-item" data-id="${esc(ch.id)}">
          <span class="num">${esc(ch.number || '—')}</span>
          <div class="logo">${ch.logo ? `<img src="${esc(ch.logo)}" alt="${esc(ch.name)}" onerror="this.remove()" />` : '📺'}</div>
          <span class="name">${esc(ch.name)}</span>
        </div>
      `,
      )
      .join('');

    function tuneChannel(ch) {
      if (!ch) return;
      state.tvCurrentChannel = ch;
      state.playbackState = 'playing';
      state.playbackChannel = ch.name;

      $('#tv-bar-num').textContent = ch.number || '—';
      $('#tv-bar-title').textContent = ch.name;
      $('#tv-bar-now').textContent = ch.category || 'In onda';
      $('#tv-bar-logo').innerHTML = ch.logo ? `<img src="${esc(ch.logo)}" alt="${esc(ch.name)}" />` : '📺';

      $$('.tv-zap-item', listEl).forEach((item) => {
        item.classList.toggle('is-active', item.dataset.id === ch.id);
      });

      loading.hidden = false;
      if (window.Hls && window.Hls.isSupported() && ch.streamUrl.includes('.m3u8')) {
        if (state.hls) state.hls.destroy();
        const hls = new window.Hls({ enableWorker: true });
        state.hls = hls;
        hls.loadSource(ch.streamUrl);
        hls.attachMedia(video);
        hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
          video.play().catch(() => {});
          loading.hidden = true;
        });
      } else {
        video.src = ch.streamUrl;
        video.onloadedmetadata = () => (loading.hidden = true);
        video.play().catch(() => {});
      }
    }

    $$('.tv-zap-item', listEl).forEach((el) => {
      el.addEventListener('click', () => {
        const ch = state.tvChannels.find((c) => c.id === el.dataset.id);
        tuneChannel(ch);
      });
    });

    $('#tv-zap-prev')?.addEventListener('click', () => {
      const idx = state.tvChannels.findIndex((c) => c.id === state.tvCurrentChannel?.id);
      const nextIdx = idx <= 0 ? state.tvChannels.length - 1 : idx - 1;
      tuneChannel(state.tvChannels[nextIdx]);
    });

    $('#tv-zap-next')?.addEventListener('click', () => {
      const idx = state.tvChannels.findIndex((c) => c.id === state.tvCurrentChannel?.id);
      const nextIdx = idx === -1 || idx >= state.tvChannels.length - 1 ? 0 : idx + 1;
      tuneChannel(state.tvChannels[nextIdx]);
    });

    // Tune first channel
    if (state.tvChannels[0]) {
      tuneChannel(state.tvChannels[0]);
    }
  } catch (err) {
    console.error('Errore TV Mode:', err);
  }
}

/* 3. CANALI SCREEN */
async function renderChannels() {
  const facets = state.config?.facets || {};
  app.innerHTML = `
    <div class="channels-layout">
      ${pageHead('Canali TV', 'Catalogo completo canali IPTV con filtri rapidi e streaming.')}
      <div class="filters-bar">
        <input type="search" id="c-filter-q" class="input" placeholder="Cerca canale per nome..." value="${esc(state.channels.filters.q)}" />
        <select id="c-filter-country" class="select">
          <option value="">Tutti i paesi</option>
          ${(facets.countries || []).map((c) => `<option value="${esc(c.code)}" ${c.code === state.channels.filters.country ? 'selected' : ''}>${countryFlag(c.code)} ${esc(c.name)}</option>`).join('')}
        </select>
        <select id="c-filter-cat" class="select">
          <option value="">Tutte le categorie</option>
          ${(facets.categories || []).map((c) => `<option value="${esc(c.id)}" ${c.id === state.channels.filters.category ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}
        </select>
        <select id="c-filter-lang" class="select">
          <option value="">Tutte le lingue</option>
          ${(facets.languages || []).map((l) => `<option value="${esc(l.code)}" ${l.code === state.channels.filters.language ? 'selected' : ''}>${esc(l.name)}</option>`).join('')}
        </select>
        <label class="btn ghost sm" style="display:flex;align-items:center;gap:6px">
          <input type="checkbox" id="c-filter-fav" ${state.channels.filters.favorite ? 'checked' : ''} />
          ⭐ Preferiti
        </label>
      </div>

      <div class="channels-grid-view" id="channels-grid">
        <div class="loading-screen"><div class="spinner"></div><p>Carico canali…</p></div>
      </div>
    </div>
  `;

  const grid = $('#channels-grid');
  
  async function loadChannelList() {
    grid.innerHTML = `<div class="loading-screen" style="grid-column:1/-1"><div class="spinner"></div></div>`;
    const params = new URLSearchParams();
    if (state.channels.filters.q) params.set('q', state.channels.filters.q);
    if (state.channels.filters.country) params.set('country', state.channels.filters.country);
    if (state.channels.filters.category) params.set('category', state.channels.filters.category);
    if (state.channels.filters.language) params.set('language', state.channels.filters.language);
    if (state.channels.filters.favorite) params.set('favorite', 'true');
    params.set('limit', '80');

    try {
      const data = await api(`/api/channels?${params.toString()}`);
      state.channels.items = data.items || data.channels || [];
      if (!state.channels.items.length) {
        grid.innerHTML = `<div class="panel panel-pad" style="grid-column:1/-1;text-align:center"><p class="muted">Nessun canale corrispondente ai filtri.</p></div>`;
        return;
      }
      grid.innerHTML = state.channels.items.map((ch) => renderChannelCardHtml(ch)).join('');

      $$('.channel-card', grid).forEach((card) => {
        card.addEventListener('click', (e) => {
          if (e.target.closest('.card-fav-btn')) return;
          const id = card.dataset.id;
          const streamUrl = card.dataset.stream;
          const name = card.dataset.name;
          const logo = card.dataset.logo;
          const num = card.dataset.num;
          playStream(streamUrl, { id, name, logo, number: num, streamUrl });
        });
      });

      $$('.card-fav-btn', grid).forEach((btn) => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const id = btn.dataset.id;
          toggleChannelFavorite({ id, favorite: btn.classList.contains('is-fav') });
        });
      });
    } catch (err) {
      grid.innerHTML = `<p class="muted">Errore caricamento canali: ${err.message}</p>`;
    }
  }

  $('#c-filter-q').addEventListener('input', (e) => {
    state.channels.filters.q = e.target.value;
    loadChannelList();
  });
  $('#c-filter-country').addEventListener('change', (e) => {
    state.channels.filters.country = e.target.value;
    loadChannelList();
  });
  $('#c-filter-cat').addEventListener('change', (e) => {
    state.channels.filters.category = e.target.value;
    loadChannelList();
  });
  $('#c-filter-lang').addEventListener('change', (e) => {
    state.channels.filters.language = e.target.value;
    loadChannelList();
  });
  $('#c-filter-fav').addEventListener('change', (e) => {
    state.channels.filters.favorite = e.target.checked;
    loadChannelList();
  });

  loadChannelList();
}

/* 4. LIBRERIA SCREEN */
async function renderLibrary() {
  app.innerHTML = `
    <div class="library-layout">
      ${pageHead('Libreria Personale', 'Film e Serie TV caricati dal telefono o dal server locale, con streaming HTTP Range.')}
      <div class="library-tabs">
        <button class="library-tab active" data-filter="all">Tutti</button>
        <button class="library-tab" data-filter="movies">Film</button>
        <button class="library-tab" data-filter="series">Serie TV</button>
      </div>
      <div class="library-grid" id="library-grid">
        <div class="loading-screen"><div class="spinner"></div><p>Carico la libreria…</p></div>
      </div>
    </div>
  `;

  const grid = $('#library-grid');

  async function loadLibraryData() {
    try {
      const data = await api('/api/library');
      state.library.items = data.items || [];
      state.library.movies = data.movies || 0;
      state.library.episodes = data.episodes || 0;
      const countEl = $('#nav-library-count');
      if (countEl) countEl.textContent = state.library.items.length;
      renderLibraryGrid();
    } catch (err) {
      grid.innerHTML = `<div class="panel panel-pad" style="grid-column:1/-1;text-align:center"><p class="muted">La libreria è vuota o supportata dall'app server del telefono.</p></div>`;
    }
  }

  function renderLibraryGrid() {
    const f = state.library.filter;
    const filtered = state.library.items.filter((it) => {
      if (f === 'movies') return it.type === 'movie';
      if (f === 'series') return it.type === 'series';
      return true;
    });

    if (!filtered.length) {
      grid.innerHTML = `<div class="panel panel-pad" style="grid-column:1/-1;text-align:center"><p class="muted">Nessun video trovato in questa sezione.</p></div>`;
      return;
    }

    grid.innerHTML = filtered
      .map(
        (it) => `
        <div class="poster-card" data-id="${esc(it.id)}">
          <div class="poster-img">
            <span class="type-badge">${it.type === 'series' ? 'Serie' : 'Film'}</span>
            <img src="${esc(it.posterUrl || '/favicon.svg')}" alt="${esc(it.title)}" onerror="this.src='/favicon.svg'" />
          </div>
          <div class="poster-info">
            <h4 class="poster-title">${esc(it.title)}</h4>
            <div class="poster-meta">
              <span>${esc(it.year || '')}</span>
              ${it.rating ? `<span>★ ${it.rating}</span>` : ''}
            </div>
          </div>
        </div>
      `,
      )
      .join('');

    $$('.poster-card', grid).forEach((card) => {
      card.addEventListener('click', () => {
        const id = card.dataset.id;
        const item = state.library.items.find((it) => it.id === id);
        if (item) openMovieModal(item);
      });
    });
  }

  $$('.library-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      $$('.library-tab').forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');
      state.library.filter = tab.dataset.filter;
      renderLibraryGrid();
    });
  });

  loadLibraryData();
}

/* 5. IMPOSTAZIONI SCREEN */
function renderSettings() {
  const p = state.activeProfile;
  app.innerHTML = `
    <div class="settings-container">
      ${pageHead('Impostazioni', 'Gestisci il tuo profilo utente e accedi alla configurazione del server.')}

      <!-- Admin Server Gate Banner -->
      <div class="server-admin-banner">
        <div class="banner-copy">
          <h2>🔒 Gestione e Configurazione Server</h2>
          <p>Visualizza i dispositivi collegati in tempo reale (e cosa stanno guardando), le risorse del sistema usate (CPU, RAM, Disco) e modifica le impostazioni interne del server. Richiede il PIN di sicurezza a 4 cifre.</p>
        </div>
        <button class="btn primary" id="btn-open-admin" style="padding:12px 22px;font-size:14.5px;flex:none">
          Accedi alla Gestione Server &rarr;
        </button>
      </div>

      <!-- User Preferences -->
      <section class="panel panel-pad">
        <div class="panel-head">
          <h2>Profilo Utente Attivo</h2>
          <p>Preferenze salvate per la tua TV e sessione browser.</p>
        </div>
        <div class="form-grid" style="display:grid;grid-template-columns:repeat(auto-fit, minmax(220px, 1fr));gap:16px">
          <div class="field">
            <label>Nome Profilo</label>
            <input class="input" id="u-profile-name" value="${esc(p?.name || '')}" />
          </div>
          <div class="field">
            <label>Avatar Emoji</label>
            <select class="select" id="u-profile-avatar">
              ${PROFILE_AVATARS.map((av) => `<option value="${av}" ${av === p?.avatar ? 'selected' : ''}>${av}</option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label>Paese Predefinito</label>
            <select class="select" id="u-profile-country">
              ${(state.config?.facets?.countries || []).map((c) => `<option value="${esc(c.code)}" ${c.code === p?.country ? 'selected' : ''}>${countryFlag(c.code)} ${esc(c.name)}</option>`).join('')}
            </select>
          </div>
        </div>
        <div class="form-actions" style="margin-top:16px">
          <button class="btn primary" id="btn-save-profile">Salva Profilo</button>
          <button class="btn ghost" id="btn-switch-profile">Cambia Utente ("Chi sta guardando?")</button>
        </div>
      </section>
    </div>
  `;

  $('#btn-open-admin')?.addEventListener('click', async () => {
    const ok = await promptAdminPin();
    if (ok) window.location.hash = '#/admin';
  });

  $('#btn-switch-profile')?.addEventListener('click', openProfileModal);

  $('#btn-save-profile')?.addEventListener('click', async () => {
    if (!state.activeProfile) return;
    const name = $('#u-profile-name').value.trim();
    const avatar = $('#u-profile-avatar').value;
    const country = $('#u-profile-country').value;
    try {
      await api(`/api/profiles/${state.activeProfile.id}`, {
        method: 'PATCH',
        body: { name, avatar, country },
      });
      state.activeProfile.name = name;
      state.activeProfile.avatar = avatar;
      state.activeProfile.country = country;
      updateProfileWidgets();
      toast('Profilo aggiornato con successo!', 'ok');
    } catch (err) {
      toast('Errore salvataggio profilo: ' + err.message, 'err');
    }
  });
}

/* 6. GESTIONE SERVER (ADMIN VIEW - PIN PROTECTED) */
async function renderAdmin() {
  if (!state.adminUnlocked) {
    const ok = await promptAdminPin();
    if (!ok) {
      window.location.hash = '#/settings';
      return;
    }
  }

  app.innerHTML = `
    <div class="admin-view">
      ${pageHead(
        'Gestione Server',
        'Configurazione avanzata, monitoraggio dispositivi connessi e stato delle risorse di sistema.',
        `<button class="btn ghost sm" id="btn-lock-admin">🔒 Blocca Sessione</button>`,
      )}

      <!-- Real-time Connected Devices Section -->
      <section class="panel panel-pad">
        <div class="panel-head" style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <h2>Dispositivi Collegati in Tempo Reale</h2>
            <p>TV, cellulari e browser connessi e cosa stanno guardando adesso.</p>
          </div>
          <span class="badge ok" id="admin-device-count">0 connessi</span>
        </div>
        <div id="admin-devices-list">
          <div class="loading-screen" style="height:100px"><div class="spinner"></div></div>
        </div>
      </section>

      <!-- System Resources Gauges -->
      <section class="panel panel-pad">
        <div class="panel-head">
          <h2>Risorse del Sistema (Cellulare Server / Server Locale)</h2>
          <p>Uso in tempo reale di processore, memoria e disco.</p>
        </div>
        <div class="admin-grid" id="admin-resources-grid">
          <div class="loading-screen" style="height:100px"><div class="spinner"></div></div>
        </div>
      </section>

      <!-- Server Configuration & M3U Playlist -->
      <div class="admin-grid">
        <section class="panel panel-pad">
          <div class="panel-head">
            <h2>Configurazione Server</h2>
            <p>Parametri di rete e catalogo IPTV.</p>
          </div>
          <form id="admin-server-form">
            <div class="field" style="margin-bottom:12px">
              <label>Nome Server</label>
              <input class="input" id="adm-server-name" value="${esc(state.config?.name || 'CasaTV')}" />
            </div>
            <div class="field" style="margin-bottom:12px">
              <label>URL Playlist M3U</label>
              <input class="input" id="adm-playlist-url" placeholder="https://…/playlist.m3u" />
            </div>
            <div class="form-actions" style="margin-top:16px;display:flex;gap:8px">
              <button class="btn primary" type="submit">Salva Configurazione</button>
              <button class="btn ghost" type="button" id="adm-refresh-playlist-btn">Aggiorna Playlist Ora</button>
            </div>
          </form>
        </section>

        <!-- Security & Change PIN -->
        <section class="panel panel-pad">
          <div class="panel-head">
            <h2>Sicurezza e Cambio Password PIN</h2>
            <p>Modifica il codice PIN a 4 cifre per accedere a questa sezione.</p>
          </div>
          <form id="admin-pin-change-form">
            <div class="field" style="margin-bottom:12px">
              <label>Vecchio PIN (4 cifre)</label>
              <input type="password" inputmode="numeric" maxlength="4" class="input" id="adm-old-pin" required placeholder="0000" />
            </div>
            <div class="field" style="margin-bottom:12px">
              <label>Nuovo PIN (4 cifre)</label>
              <input type="password" inputmode="numeric" maxlength="4" class="input" id="adm-new-pin" required placeholder="Es. 1234" />
            </div>
            <div class="field" style="margin-bottom:12px">
              <label>Conferma Nuovo PIN</label>
              <input type="password" inputmode="numeric" maxlength="4" class="input" id="adm-confirm-pin" required placeholder="Es. 1234" />
            </div>
            <div class="form-actions" style="margin-top:16px">
              <button class="btn primary" type="submit">Aggiorna PIN di Sicurezza</button>
            </div>
          </form>
        </section>
      </div>
    </div>
  `;

  $('#btn-lock-admin')?.addEventListener('click', () => {
    state.adminUnlocked = false;
    sessionStorage.removeItem('casatv_admin_unlocked');
    toast('Sessione amministratore bloccata', 'ok');
    window.location.hash = '#/settings';
  });

  // Admin PIN Change Form
  $('#admin-pin-change-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const oldPin = $('#adm-old-pin').value.trim();
    const newPin = $('#adm-new-pin').value.trim();
    const confirmPin = $('#adm-confirm-pin').value.trim();

    if (newPin !== confirmPin) {
      toast('Il nuovo PIN e la conferma non coincidono', 'err');
      return;
    }
    if (!/^\d{4}$/.test(newPin)) {
      toast('Il nuovo PIN deve contenere esattamente 4 cifre numeriche', 'err');
      return;
    }

    try {
      await api('/api/settings/change-pin', {
        method: 'POST',
        body: { oldPin, newPin },
      });
      toast('PIN aggiornato con successo!', 'ok');
      $('#adm-old-pin').value = '';
      $('#adm-new-pin').value = '';
      $('#adm-confirm-pin').value = '';
    } catch (err) {
      toast(err.message, 'err');
    }
  });

  // Server Form
  $('#admin-server-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const server_name = $('#adm-server-name').value.trim();
    const catalog_url = $('#adm-playlist-url').value.trim();
    try {
      await api('/api/settings', {
        method: 'PATCH',
        body: { server_name, ...(catalog_url ? { catalog_url } : {}) },
      });
      toast('Impostazioni server salvate!', 'ok');
    } catch (err) {
      toast('Errore: ' + err.message, 'err');
    }
  });

  $('#adm-refresh-playlist-btn')?.addEventListener('click', async () => {
    try {
      await api('/api/refresh', { method: 'POST' });
      toast('Aggiornamento playlist avviato in background!', 'ok');
    } catch (err) {
      toast('Errore: ' + err.message, 'err');
    }
  });

  // Start polling system status every 5 seconds
  async function updateAdminSystemView() {
    try {
      const data = await api('/api/system');
      state.system = data;
      renderLiveDevices(data.devices || []);
      renderResourcesGauges(data.resources || {}, data.alerts || []);
    } catch (_) {}
  }

  function renderLiveDevices(devices) {
    const listEl = $('#admin-devices-list');
    const countBadge = $('#admin-device-count');
    if (!listEl) return;

    if (countBadge) countBadge.textContent = `${devices.length} dispositivi`;
    if (!devices.length) {
      listEl.innerHTML = `<p class="muted" style="padding:12px 0">Nessun dispositivo attualmente collegato.</p>`;
      return;
    }

    listEl.innerHTML = devices
      .map((d) => {
        const isPlaying = d.state === 'playing';
        return `
        <div class="live-device-item">
          <div class="device-main">
            <span class="device-dot ${isPlaying ? 'playing' : ''}"></span>
            <div class="device-info">
              <strong>${esc(d.name || 'Dispositivo')}</strong>
              <small>${esc(d.platform || 'Client')} · IP: ${esc(d.ip || 'locale')} ${d.profileName ? `· Profilo: ${esc(d.profileName)}` : ''}</small>
            </div>
          </div>
          <div class="device-now-playing">
            ${
              isPlaying && d.channel
                ? `<span class="now-playing-badge" title="${esc(d.channel)}">▶ ${esc(d.channel)}</span>`
                : `<span class="badge neutral">Inattivo</span>`
            }
          </div>
        </div>
      `;
      })
      .join('');
  }

  function renderResourcesGauges(res, alerts) {
    const gridEl = $('#admin-resources-grid');
    if (!gridEl) return;

    const cpuPct = Math.round(res.cpu?.percent || 0);
    const memPct = Math.round(res.memory?.percent || 0);
    const diskPct = Math.round(res.disk?.percent || 0);

    const memUsedMb = Math.round((res.memory?.used || 0) / (1024 * 1024));
    const memTotalMb = Math.round((res.memory?.total || 0) / (1024 * 1024));

    const diskFreeGb = Math.round((res.disk?.free || 0) / (1024 * 1024 * 1024));
    const diskTotalGb = Math.round((res.disk?.total || 0) / (1024 * 1024 * 1024));

    gridEl.innerHTML = `
      <div class="gauge-card">
        <div class="gauge-label"><span>Carico CPU</span><span>${cpuPct}%</span></div>
        <div class="gauge-bar"><div class="gauge-fill ${cpuPct > 90 ? 'crit' : cpuPct > 75 ? 'warn' : 'ok'}" style="width:${cpuPct}%"></div></div>
        <div class="gauge-sub">${res.cpu?.cores || 1} Core disponibili</div>
      </div>
      <div class="gauge-card">
        <div class="gauge-label"><span>Memoria RAM</span><span>${memPct}%</span></div>
        <div class="gauge-bar"><div class="gauge-fill ${memPct > 90 ? 'crit' : memPct > 75 ? 'warn' : 'ok'}" style="width:${memPct}%"></div></div>
        <div class="gauge-sub">${memUsedMb} MB / ${memTotalMb} MB usati</div>
      </div>
      <div class="gauge-card">
        <div class="gauge-label"><span>Spazio Disco</span><span>${diskPct}%</span></div>
        <div class="gauge-bar"><div class="gauge-fill ${diskPct > 90 ? 'crit' : diskPct > 75 ? 'warn' : 'ok'}" style="width:${diskPct}%"></div></div>
        <div class="gauge-sub">${diskFreeGb} GB liberi su ${diskTotalGb} GB</div>
      </div>
    `;
  }

  updateAdminSystemView();
  if (state.adminPoll) clearInterval(state.adminPoll);
  state.adminPoll = setInterval(updateAdminSystemView, 5000);
}

function pageHead(title, subtitle, actions = '') {
  return `
    <div class="page-head">
      <div>
        <h1>${esc(title)}</h1>
        <p>${esc(subtitle)}</p>
      </div>
      ${actions ? `<div class="actions">${actions}</div>` : ''}
    </div>
  `;
}

/* ---------------- Main Router & Nav ---------------- */
function route() {
  const hash = window.location.hash.replace(/^#\/?/, '') || 'home';
  state.page = hash;

  if (state.adminPoll && hash !== 'admin') {
    clearInterval(state.adminPoll);
    state.adminPoll = null;
  }

  // Update Navigation Active States
  $$('.nav a, .mobile-bottom-nav a').forEach((a) => {
    const target = a.dataset.nav;
    a.classList.toggle('active', target === hash || (hash === 'admin' && target === 'settings'));
  });

  switch (hash) {
    case 'home':
      renderHome();
      break;
    case 'tv':
      renderTvMode();
      break;
    case 'channels':
      renderChannels();
      break;
    case 'library':
      renderLibrary();
      break;
    case 'settings':
      renderSettings();
      break;
    case 'admin':
      renderAdmin();
      break;
    default:
      renderHome();
      break;
  }
}

function render() {
  route();
}

/* ---------------- Event Listeners & Boot ---------------- */
window.addEventListener('hashchange', route);

// Close modal backdrop clicks
$$('[data-close-modal]').forEach((el) => el.addEventListener('click', closePlayerModal));
$$('[data-close-movie-modal]').forEach((el) => el.addEventListener('click', closeMovieModal));
$$('[data-close-profile-modal]').forEach((el) => el.addEventListener('click', closeProfileModal));
$$('[data-close-pin-modal]').forEach((el) => el.addEventListener('click', () => closePinModal(false)));
$$('[data-close-country-modal]').forEach((el) => el.addEventListener('click', closeCountryModal));

$('#sidebar-profile-btn')?.addEventListener('click', openProfileModal);
$('#mobile-profile-btn')?.addEventListener('click', openProfileModal);
$('#mobile-country-btn')?.addEventListener('click', openCountryModal);
$('#btn-create-profile-prompt')?.addEventListener('click', async () => {
  const name = prompt('Nome del nuovo profilo:');
  if (!name || !name.trim()) return;
  try {
    const created = await api('/api/profiles', {
      method: 'POST',
      body: { name: name.trim(), avatar: '🦁', country: state.tvCountry || 'IT' },
    });
    toast('Profilo creato con successo', 'ok');
    await loadProfiles();
    openProfileModal();
  } catch (err) {
    toast('Errore creazione: ' + err.message, 'err');
  }
});

setupPinModalEvents();

async function init() {
  try {
    const cfg = await api('/api/config');
    state.config = cfg;
    
    // Server status in footer
    const pulse = $('#server-state .pulse');
    if (pulse) pulse.classList.add('on');
    const sUrl = $('#server-url');
    if (sUrl) sUrl.textContent = cfg.baseUrl || window.location.origin;

    const count = $('#nav-channel-count');
    if (count) count.textContent = fmtNum(cfg.stats?.channels || 0);

    await loadProfiles();
    route();
  } catch (err) {
    app.innerHTML = `
      <div class="panel panel-pad" style="margin:40px auto;max-width:500px;text-align:center">
        <h2>Impossibile connettersi al server CasaTV</h2>
        <p class="muted">${esc(err.message)}</p>
        <button class="btn primary" onclick="location.reload()">Riprova</button>
      </div>
    `;
  }
}

init();
