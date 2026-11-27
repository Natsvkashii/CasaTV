const { app, BrowserWindow, Tray, Menu, nativeImage, ipcMain, shell, clipboard, dialog } = require('electron');
const path = require('node:path');
const os = require('node:os');
const fs = require('node:fs');
const { spawn } = require('node:child_process');

let mainWindow = null;
let tray = null;
let serverProcess = null;
let isQuitting = false;
let serverStatus = 'STOPPED'; // 'STARTING', 'RUNNING', 'STOPPED', 'ERROR'
let serverPort = 8787;
const serverLogs = [];

const serverDir = path.resolve(__dirname, '../server');
const serverScript = path.resolve(serverDir, 'src/index.js');

function logServer(msg) {
  const line = `[${new Date().toLocaleTimeString()}] ${msg.trim()}`;
  serverLogs.push(line);
  if (serverLogs.length > 300) serverLogs.shift();
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('server-log', line);
  }
}

function updateStatus(status) {
  serverStatus = status;
  updateTray();
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('server-status', { status: serverStatus, port: serverPort });
  }
}

function getLocalAddresses() {
  const addresses = [];
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] || []) {
      if (net.family === 'IPv4' && !net.internal) {
        addresses.push({
          interface: name,
          ip: net.address,
          url: `http://${net.address}:${serverPort}`,
        });
      }
    }
  }
  return addresses;
}

function startServerProcess() {
  if (serverProcess) {
    logServer('Il server è già in esecuzione o in avvio');
    return;
  }

  updateStatus('STARTING');
  logServer('Avvio server CasaTV...');

  // Spawn using Electron's built-in Node or system node
  const nodeExec = process.execPath;
  const env = {
    ...process.env,
    ELECTRON_RUN_AS_NODE: '1',
    PORT: String(serverPort),
    NODE_ENV: 'production',
  };

  try {
    serverProcess = spawn(nodeExec, [serverScript], {
      cwd: serverDir,
      env,
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    serverProcess.stdout.on('data', (chunk) => {
      const text = chunk.toString();
      logServer(text);
      if (serverStatus !== 'RUNNING' && (text.includes('listening on') || text.includes('Server in ascolto') || text.includes('http://'))) {
        updateStatus('RUNNING');
      }
    });

    serverProcess.stderr.on('data', (chunk) => {
      const text = chunk.toString();
      logServer(`[ERR] ${text}`);
    });

    serverProcess.on('close', (code, signal) => {
      logServer(`Server terminato (codice: ${code}, segnale: ${signal})`);
      serverProcess = null;
      updateStatus('STOPPED');
    });

    serverProcess.on('error', (err) => {
      logServer(`Errore processo server: ${err.message}`);
      serverProcess = null;
      updateStatus('ERROR');
    });

    // Check with ping after 1.5s
    setTimeout(checkServerHealth, 1500);
  } catch (error) {
    logServer(`Impossibile avviare il processo: ${error.message}`);
    updateStatus('ERROR');
  }
}

async function checkServerHealth() {
  try {
    const res = await fetch(`http://127.0.0.1:${serverPort}/api/system`, { signal: AbortSignal.timeout(2000) });
    if (res.ok) {
      updateStatus('RUNNING');
      return true;
    }
  } catch {
    // not ready yet
  }
  return false;
}

function stopServerProcess() {
  const proc = serverProcess;
  if (!proc) {
    updateStatus('STOPPED');
    return;
  }
  logServer('Arresto server in corso...');
  proc.kill('SIGTERM');
  setTimeout(() => {
    if (serverProcess === proc) {
      proc.kill('SIGKILL');
      serverProcess = null;
      updateStatus('STOPPED');
    }
  }, 3000);
}

function restartServerProcess() {
  stopServerProcess();
  setTimeout(() => {
    startServerProcess();
  }, 1000);
}

function getTrayIcon() {
  const iconPath = path.join(__dirname, 'assets', 'tray-icon.png');
  if (fs.existsSync(iconPath)) {
    const img = nativeImage.createFromPath(iconPath);
    if (process.platform === 'darwin') {
      img.setTemplateImage(true);
    }
    return img;
  }
  return nativeImage.createEmpty();
}

function getAppIcon() {
  const iconPath = path.join(__dirname, 'assets', 'icon.png');
  if (fs.existsSync(iconPath)) {
    return nativeImage.createFromPath(iconPath);
  }
  return undefined;
}

function updateTray() {
  if (!tray) return;

  const isRunning = serverStatus === 'RUNNING';
  const statusLabel = isRunning
    ? `🟢 Server: Attivo (Porta ${serverPort})`
    : serverStatus === 'STARTING'
      ? `🟡 Server: In avvio...`
      : `🔴 Server: Fermo`;

  const ips = getLocalAddresses();
  const primaryIp = ips[0] ? ips[0].ip : '127.0.0.1';

  const contextMenu = Menu.buildFromTemplate([
    { label: statusLabel, enabled: false },
    { type: 'separator' },
    {
      label: 'Mostra finestra',
      click: () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
        }
      },
    },
    {
      label: 'Apri Dashboard Web',
      enabled: isRunning,
      click: () => shell.openExternal(`http://127.0.0.1:${serverPort}`),
    },
    {
      label: `Copia indirizzo TV (http://${primaryIp}:${serverPort})`,
      enabled: ips.length > 0,
      click: () => {
        clipboard.writeText(`http://${primaryIp}:${serverPort}`);
      },
    },
    { type: 'separator' },
    isRunning
      ? { label: 'Riavvia Server', click: restartServerProcess }
      : { label: 'Avvia Server', click: startServerProcess },
    isRunning
      ? { label: 'Ferma Server', click: stopServerProcess }
      : null,
    { type: 'separator' },
    {
      label: 'Esci da CasaTV',
      click: () => {
        isQuitting = true;
        stopServerProcess();
        app.quit();
      },
    },
  ].filter(Boolean));

  tray.setToolTip(`CasaTV Server - ${serverStatus}`);
  tray.setContextMenu(contextMenu);
}

function createTray() {
  const icon = getTrayIcon();
  tray = new Tray(icon);
  tray.setToolTip('CasaTV Server');

  tray.on('click', () => {
    if (!mainWindow) return;
    if (mainWindow.isVisible()) {
      mainWindow.hide();
    } else {
      mainWindow.show();
      mainWindow.focus();
    }
  });

  tray.on('double-click', () => {
    if (!mainWindow) return;
    mainWindow.show();
    mainWindow.focus();
  });

  updateTray();
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 540,
    height: 720,
    minWidth: 480,
    minHeight: 580,
    backgroundColor: '#0b1118',
    show: true,
    icon: getAppIcon(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.loadFile(path.join(__dirname, 'ui', 'index.html'));

  mainWindow.on('close', (event) => {
    if (!isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
    return false;
  });
}

// Single instance lock
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    createWindow();
    createTray();

    // Check if server already running, or auto-start it
    const running = await checkServerHealth();
    if (!running) {
      startServerProcess();
    } else {
      updateStatus('RUNNING');
    }
  });

  app.on('before-quit', () => {
    isQuitting = true;
    stopServerProcess();
  });

  app.on('window-all-closed', () => {
    // Keep app alive in tray on all platforms (Win, Mac, Linux)
  });

  app.on('activate', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

// IPC Handlers
ipcMain.handle('get-server-status', async () => {
  await checkServerHealth();
  return {
    status: serverStatus,
    port: serverPort,
    logs: serverLogs.slice(-100),
  };
});

ipcMain.handle('start-server', () => {
  startServerProcess();
  return { ok: true };
});

ipcMain.handle('stop-server', () => {
  stopServerProcess();
  return { ok: true };
});

ipcMain.handle('restart-server', () => {
  restartServerProcess();
  return { ok: true };
});

ipcMain.handle('get-network-addresses', () => {
  return getLocalAddresses();
});

ipcMain.handle('open-browser', (_e, url) => {
  shell.openExternal(url || `http://127.0.0.1:${serverPort}`);
  return true;
});

ipcMain.handle('copy-to-clipboard', (_e, text) => {
  clipboard.writeText(text);
  return true;
});

ipcMain.handle('minimize-to-tray', () => {
  if (mainWindow) {
    mainWindow.hide();
  }
  return true;
});

ipcMain.handle('get-autolaunch', () => {
  return app.getLoginItemSettings().openAtLogin;
});

ipcMain.handle('set-autolaunch', (_e, enabled) => {
  app.setLoginItemSettings({ openAtLogin: Boolean(enabled) });
  return app.getLoginItemSettings().openAtLogin;
});

ipcMain.handle('pick-m3u-file', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: 'Seleziona Playlist M3U o M3U8',
    properties: ['openFile'],
    filters: [
      { name: 'Playlist M3U (*.m3u, *.m3u8)', extensions: ['m3u', 'm3u8', 'txt'] },
      { name: 'Tutti i file', extensions: ['*'] },
    ],
  });

  if (res.canceled || !res.filePaths.length) {
    return null;
  }

  const filePath = res.filePaths[0];
  const fileName = path.basename(filePath);
  const content = await fs.promises.readFile(filePath, 'utf-8');
  return { filePath, fileName, content };
});

ipcMain.handle('get-playlists', async () => {
  try {
    const res = await fetch(`http://127.0.0.1:${serverPort}/api/playlists`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    return { ok: false, error: err.message, playlists: [] };
  }
});

ipcMain.handle('add-playlist', async (_e, payload) => {
  try {
    const res = await fetch(`http://127.0.0.1:${serverPort}/api/playlists`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    return { ok: res.ok, ...data };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});

ipcMain.handle('delete-playlist', async (_e, id) => {
  try {
    const res = await fetch(`http://127.0.0.1:${serverPort}/api/playlists/${id}`, {
      method: 'DELETE',
    });
    const data = await res.json();
    return { ok: res.ok, ...data };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});

ipcMain.handle('refresh-playlist', async (_e, id) => {
  try {
    const res = await fetch(`http://127.0.0.1:${serverPort}/api/playlists/${id}/refresh`, {
      method: 'POST',
    });
    const data = await res.json();
    return { ok: res.ok, ...data };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});
