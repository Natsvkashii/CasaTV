let pickedFile = null;
let currentServerStatus = 'STOPPED';

const statusBadge = document.getElementById('statusBadge');
const statusText = document.getElementById('statusText');
const btnMinimizeTray = document.getElementById('btnMinimizeTray');
const btnOpenWeb = document.getElementById('btnOpenWeb');
const addressesList = document.getElementById('addressesList');
const btnToggleServer = document.getElementById('btnToggleServer');
const btnRestartServer = document.getElementById('btnRestartServer');

const btnToggleAddForm = document.getElementById('btnToggleAddForm');
const addPlaylistForm = document.getElementById('addPlaylistForm');
const inputPlaylistName = document.getElementById('inputPlaylistName');
const inputPlaylistUrl = document.getElementById('inputPlaylistUrl');
const btnPickFile = document.getElementById('btnPickFile');
const filePickedNotice = document.getElementById('filePickedNotice');
const btnCancelAdd = document.getElementById('btnCancelAdd');
const btnSubmitAdd = document.getElementById('btnSubmitAdd');
const playlistsContainer = document.getElementById('playlistsContainer');

const chkAutoLaunch = document.getElementById('chkAutoLaunch');
const logsConsole = document.getElementById('logsConsole');

function appendLog(line) {
  if (!logsConsole) return;
  const item = document.createElement('div');
  item.textContent = line;
  logsConsole.appendChild(item);
  logsConsole.scrollTop = logsConsole.scrollHeight;
}

function setServerStatus(status, port = 8787) {
  currentServerStatus = status;
  statusBadge.className = 'status-badge';

  if (status === 'RUNNING') {
    statusBadge.classList.add('running');
    statusText.textContent = `Attivo (:${port})`;
    btnToggleServer.textContent = 'Ferma Server';
    btnToggleServer.className = 'btn btn-danger';
    btnRestartServer.disabled = false;
  } else if (status === 'STARTING') {
    statusBadge.classList.add('starting');
    statusText.textContent = 'In avvio...';
    btnToggleServer.textContent = 'In avvio...';
    btnToggleServer.className = 'btn btn-secondary';
    btnRestartServer.disabled = true;
  } else {
    statusBadge.classList.add('stopped');
    statusText.textContent = 'Fermo';
    btnToggleServer.textContent = 'Avvia Server';
    btnToggleServer.className = 'btn btn-primary';
    btnRestartServer.disabled = true;
  }
}

async function loadNetworkAddresses() {
  const bindCopyButtons = () => {
    addressesList.querySelectorAll('.btn-copy').forEach((btn) => {
      btn.addEventListener('click', () => {
        const url = btn.getAttribute('data-url');
        window.casatv.copyToClipboard(url);
        const originalText = btn.textContent;
        btn.textContent = 'Copiato!';
        btn.classList.add('copied');
        setTimeout(() => {
          btn.textContent = originalText;
          btn.classList.remove('copied');
        }, 1800);
      });
    });
  };

  try {
    const addresses = await window.casatv.getNetworkAddresses();
    if (!addresses || addresses.length === 0) {
      addressesList.innerHTML = `
        <div class="address-item">
          <div class="address-info">
            <span class="interface-pill">LOCAL</span>
            <span class="address-url">http://localhost:8787</span>
          </div>
          <button class="btn-copy" data-url="http://localhost:8787">Copia</button>
        </div>
      `;
      bindCopyButtons();
      return;
    }

    addressesList.innerHTML = addresses
      .map(
        (addr) => `
        <div class="address-item">
          <div class="address-info">
            <span class="interface-pill">${escapeHtml(addr.interface)}</span>
            <span class="address-url">${escapeHtml(addr.url)}</span>
          </div>
          <button class="btn-copy" data-url="${escapeHtml(addr.url)}">Copia</button>
        </div>
      `,
      )
      .join('');

    bindCopyButtons();
  } catch (err) {
    addressesList.innerHTML = `<div class="address-item">Errore caricamento indirizzi</div>`;
  }
}

async function loadPlaylists() {
  try {
    const res = await window.casatv.getPlaylists();
    const playlists = res && res.playlists ? res.playlists : [];
    renderPlaylists(playlists);
  } catch {
    playlistsContainer.innerHTML = `<div class="empty-state">Impossibile contattare il server (server non avviato).</div>`;
  }
}

function renderPlaylists(playlists) {
  if (!playlists || playlists.length === 0) {
    playlistsContainer.innerHTML = `<div class="empty-state">Nessuna playlist configurata.<br>Aggiungi una playlist M3U con il pulsante in alto.</div>`;
    return;
  }

  playlistsContainer.innerHTML = playlists
    .map((p) => {
      const channelCount = p.channel_count ?? p.channelCount ?? 0;
      return `
      <div class="playlist-item" data-id="${p.id}">
        <div class="playlist-main">
          <div class="playlist-name-row">
            <span class="playlist-title">${escapeHtml(p.name || 'Playlist')}</span>
            <span class="channel-count-badge">${channelCount} canali</span>
          </div>
          <div class="playlist-url-text" title="${escapeHtml(p.url || '')}">${escapeHtml(p.url || 'File locale')}</div>
        </div>
        <div class="playlist-actions">
          <button class="btn-sm btn-secondary btn-refresh" data-id="${p.id}" title="Ricarica canali">&#x21bb;</button>
          <button class="btn-sm btn-danger btn-delete" data-id="${p.id}" title="Elimina">&#x2715;</button>
        </div>
      </div>
    `;
    })
    .join('');

  playlistsContainer.querySelectorAll('.btn-refresh').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const id = btn.getAttribute('data-id');
      btn.disabled = true;
      btn.textContent = '...';
      await window.casatv.refreshPlaylist(id);
      await loadPlaylists();
    });
  });

  playlistsContainer.querySelectorAll('.btn-delete').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const id = btn.getAttribute('data-id');
      if (confirm('Sei sicuro di voler eliminare questa playlist e tutti i suoi canali?')) {
        btn.disabled = true;
        await window.casatv.deletePlaylist(id);
        await loadPlaylists();
      }
    });
  });
}

function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// Event Listeners
btnToggleServer.addEventListener('click', async () => {
  if (currentServerStatus === 'RUNNING') {
    await window.casatv.stopServer();
  } else {
    await window.casatv.startServer();
  }
});

btnRestartServer.addEventListener('click', async () => {
  await window.casatv.restartServer();
});

btnOpenWeb.addEventListener('click', () => {
  window.casatv.openBrowser('http://127.0.0.1:8787');
});

btnMinimizeTray.addEventListener('click', () => {
  window.casatv.minimizeToTray();
});

btnToggleAddForm.addEventListener('click', () => {
  addPlaylistForm.classList.toggle('hidden');
});

btnCancelAdd.addEventListener('click', () => {
  addPlaylistForm.classList.add('hidden');
  resetAddForm();
});

btnPickFile.addEventListener('click', async () => {
  const file = await window.casatv.pickM3uFile();
  if (file) {
    pickedFile = file;
    inputPlaylistUrl.value = file.filePath;
    if (!inputPlaylistName.value.trim()) {
      inputPlaylistName.value = file.fileName.replace(/\.(m3u8?|txt)$/i, '');
    }
    filePickedNotice.textContent = `✓ File selezionato: ${file.fileName} (${(file.content.length / 1024).toFixed(1)} KB)`;
    filePickedNotice.classList.remove('hidden');
  }
});

function resetAddForm() {
  pickedFile = null;
  inputPlaylistName.value = '';
  inputPlaylistUrl.value = '';
  filePickedNotice.textContent = '';
  filePickedNotice.classList.add('hidden');
}

btnSubmitAdd.addEventListener('click', async () => {
  const name = inputPlaylistName.value.trim() || 'Mia Playlist';
  const url = inputPlaylistUrl.value.trim();

  if (!url && !pickedFile) {
    alert('Inserisci un URL o seleziona un file M3U.');
    return;
  }

  btnSubmitAdd.disabled = true;
  btnSubmitAdd.textContent = 'Importazione in corso...';

  try {
    const payload = {
      name,
      url: pickedFile ? `file://${pickedFile.fileName}` : url,
      text: pickedFile ? pickedFile.content : undefined,
    };

    const res = await window.casatv.addPlaylist(payload);
    if (res && res.ok !== false) {
      resetAddForm();
      addPlaylistForm.classList.add('hidden');
      await loadPlaylists();
    } else {
      alert(`Errore importazione: ${res?.error || 'Impossibile scaricare la playlist'}`);
    }
  } catch (err) {
    alert(`Errore: ${err.message}`);
  } finally {
    btnSubmitAdd.disabled = false;
    btnSubmitAdd.textContent = 'Importa Playlist';
  }
});

chkAutoLaunch.addEventListener('change', async () => {
  await window.casatv.setAutoLaunch(chkAutoLaunch.checked);
});

// App Initialization
async function init() {
  const statusInfo = await window.casatv.getServerStatus();
  setServerStatus(statusInfo.status, statusInfo.port);
  if (statusInfo.logs) {
    statusInfo.logs.forEach(appendLog);
  }

  await loadNetworkAddresses();
  await loadPlaylists();

  try {
    const autoLaunch = await window.casatv.getAutoLaunch();
    chkAutoLaunch.checked = Boolean(autoLaunch);
  } catch {
    // ignore
  }

  window.casatv.onServerStatus((info) => {
    setServerStatus(info.status, info.port);
    if (info.status === 'RUNNING') {
      loadPlaylists();
    }
  });

  window.casatv.onServerLog((line) => {
    appendLog(line);
  });
}

init();
