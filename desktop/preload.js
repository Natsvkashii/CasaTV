const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('casatv', {
  getServerStatus: () => ipcRenderer.invoke('get-server-status'),
  startServer: () => ipcRenderer.invoke('start-server'),
  stopServer: () => ipcRenderer.invoke('stop-server'),
  restartServer: () => ipcRenderer.invoke('restart-server'),
  getNetworkAddresses: () => ipcRenderer.invoke('get-network-addresses'),
  openBrowser: (url) => ipcRenderer.invoke('open-browser', url),
  copyToClipboard: (text) => ipcRenderer.invoke('copy-to-clipboard', text),
  getPlaylists: () => ipcRenderer.invoke('get-playlists'),
  addPlaylist: (payload) => ipcRenderer.invoke('add-playlist', payload),
  deletePlaylist: (id) => ipcRenderer.invoke('delete-playlist', id),
  refreshPlaylist: (id) => ipcRenderer.invoke('refresh-playlist', id),
  pickM3uFile: () => ipcRenderer.invoke('pick-m3u-file'),
  getAutoLaunch: () => ipcRenderer.invoke('get-autolaunch'),
  setAutoLaunch: (enabled) => ipcRenderer.invoke('set-autolaunch', enabled),
  minimizeToTray: () => ipcRenderer.invoke('minimize-to-tray'),
  onServerStatus: (callback) => {
    const handler = (_event, data) => callback(data);
    ipcRenderer.on('server-status', handler);
    return () => ipcRenderer.removeListener('server-status', handler);
  },
  onServerLog: (callback) => {
    const handler = (_event, line) => callback(line);
    ipcRenderer.on('server-log', handler);
    return () => ipcRenderer.removeListener('server-log', handler);
  },
});
