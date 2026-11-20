import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from './config.js';
import {
  channelDto,
  clearGuestProfileData,
  createProfile,
  db,
  deleteProfile,
  ensureGuestProfile,
  getAllSettings,
  getChannelRow,
  getChannelRowByNumber,
  getDefaultProfile,
  getFacets,
  getHomeRows,
  getNowNext,
  getPlaylist,
  getProfile,
  getSetting,
  getStats,
  listChannelRows,
  listChannels,
  listPlaylists,
  listProfiles,
  markProfilePlayed,
  PROFILE_AVATARS,
  PROFILE_LANGUAGES,
  profileDto,
  setProfileChannelState,
  setSetting,
  togglePlaylist,
  deletePlaylist,
  updateProfile,
} from './db.js';
import { generateM3u } from './m3u.js';
import { checkPlaylist, importPlaylist } from './catalog.js';
import {
  addSource,
  epgChannel,
  epgGuide,
  epgState,
  generateXmltv,
  getEpgStats,
  hasFreshEpg,
  listSources,
  refreshAllSources,
  refreshSource,
  removeSource,
} from './epg.js';
import {
  getBaseUrl,
  handleProxyToken,
  handleStreamRequest,
  refreshManifest,
} from './proxy.js';
import { getLogo } from './logos.js';
import {
  checkChannel,
  getLastScan,
  scanState,
  startScanInBackground,
  stopScan,
} from './scan.js';
import { ingestState, runIngest } from './catalog.js';
import {
  listClients,
  removeClient,
  systemSnapshot,
  touchClient,
  versionInfo,
} from './system.js';
import {
  categorySlug,
  cleanChannelName,
  isHttpUrl,
  normalizeId,
  parseQuality,
  qualityRank,
  sha1,
  slugify,
} from './util.js';

function lanUrls() {
  const interfaces = os.networkInterfaces();
  const urls = [];
  for (const [name, addresses] of Object.entries(interfaces)) {
    for (const address of addresses || []) {
      if (address.family !== 'IPv4' || address.internal) continue;
      if (address.address.startsWith('169.254.')) continue;
      urls.push({
        name,
        address: address.address,
        url: `http://${address.address}:${config.port}`,
      });
    }
  }
  const rank = (name) => {
    const n = name.toLowerCase();
    if (n.startsWith('en')) return 0;
    if (n.startsWith('eth')) return 1;
    if (n.startsWith('bridge')) return 4;
    return 2;
  };
  urls.sort((a, b) => rank(a.name) - rank(b.name) || a.name.localeCompare(b.name));
  return urls;
}

function bestBaseUrl(req) {
  if (config.publicUrl) return config.publicUrl;
  if (req) return getBaseUrl(req);
  const urls = lanUrls();
  return urls[0]?.url || `http://localhost:${config.port}`;
}

function parseNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function requestProfile(req) {
  const headerId =
    req?.headers?.['x-profile-id'] ||
    req?.headers?.['X-Profile-Id'] ||
    req?.query?.profile;
  const selected = headerId ? getProfile(headerId) : null;
  return selected || getDefaultProfile();
}

function profileContext(req) {
  const profile = requestProfile(req);
  return {
    profile,
    profileId: profile?.id || null,
    lang: profile?.language || 'it',
  };
}

function headerValue(value) {
  if (Array.isArray(value)) return String(value[0] || '');
  return value == null ? '' : String(value);
}

export async function registerApi(app) {
  // Any API call carrying the device headers keeps the client marked online.
  // The app sends them on every request, so the panel can show who is connected
  // even between explicit heartbeats.
  app.addHook('onRequest', async (req) => {
    if (!req.url.startsWith('/api/')) return;
    const deviceId = headerValue(req.headers['x-device-id']);
    if (!deviceId) return;
    const profile = requestProfile(req);
    touchClient(req, {
      deviceId,
      name: headerValue(req.headers['x-device-name']),
      platform: headerValue(req.headers['x-device-platform']),
      appVersion: headerValue(req.headers['x-app-version']),
      profileId: profile?.id || '',
      profileName: profile?.name || '',
      state: headerValue(req.headers['x-playback-state']),
      channel: headerValue(req.headers['x-playback-channel']),
    });
  });

  app.get('/healthz', async () => ({
    ok: true,
    name: getSetting('server_name', config.serverName),
    version: config.version,
    channels: getStats().channels,
    streams: getStats().streams,
    ingest: ingestState.running ? 'running' : 'idle',
    scan: scanState.running ? 'running' : 'idle',
    epg: hasFreshEpg(),
  }));

  app.get('/api/system', async (req) => {
    const clientVersion =
      headerValue(req.headers['x-app-version']) || String(req.query.appVersion || '');
    return {
      ...systemSnapshot(),
      update: versionInfo(clientVersion),
    };
  });

  app.get('/api/version', async (req) => {
    const clientVersion =
      headerValue(req.headers['x-app-version']) || String(req.query.appVersion || '');
    return versionInfo(clientVersion);
  });

  app.get('/api/update/casatv-tv.apk', async (req, reply) => {
    const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
    const candidates = [
      path.join(rootDir, 'dist/casatv-tv-1.5.0.apk'),
      path.join(rootDir, 'casatv-tv-1.5.0.apk'),
      path.join(rootDir, 'dist/casatv-tv.apk'),
    ];
    for (const file of candidates) {
      if (fs.existsSync(file)) {
        reply.header('Content-Type', 'application/vnd.android.package-archive');
        reply.header('Content-Disposition', 'attachment; filename="casatv-tv.apk"');
        return reply.send(fs.createReadStream(file));
      }
    }
    return reply.code(404).send({ error: 'APK di aggiornamento per TV non trovato' });
  });

  app.get('/api/clients', async () => ({ clients: listClients() }));

  app.post('/api/clients/heartbeat', async (req) => {
    const body = req.body || {};
    const profile = requestProfile(req);
    const client = touchClient(req, {
      ...body,
      profileId: body.profileId || profile?.id || '',
      profileName: body.profileName || profile?.name || '',
    });
    return {
      ok: true,
      client,
      serverTime: Math.floor(Date.now() / 1000),
      version: config.version,
      lastIngest: Number(getSetting('last_ingest', 0)) || null,
      update: versionInfo(body.appVersion),
    };
  });

  app.delete('/api/clients/:id', async (req) => ({
    removed: removeClient(req.params.id),
  }));

  const channelSyncHub = new Map();

  app.post('/api/sync', async (req) => {
    const body = req.body || {};
    const deviceId = body.deviceId || req.headers['x-device-id'] || `dev-${req.ip || 'unknown'}`;
    const deviceName = body.deviceName || req.headers['x-device-name'] || 'Android TV';
    const channelId = (body.channelId || '').trim();
    const isLive = body.isLive !== false;
    const positionMs = Number(body.positionMs || 0);
    const liveOffsetMs = Number(body.liveOffsetMs || 0);
    const now = Date.now();

    if (!channelId) {
      for (const devMap of channelSyncHub.values()) {
        devMap.delete(deviceId);
      }
      return { ok: true, syncActive: false, serverTimeMs: now };
    }

    for (const [chId, devMap] of channelSyncHub.entries()) {
      if (chId !== channelId) devMap.delete(deviceId);
    }

    if (!channelSyncHub.has(channelId)) {
      channelSyncHub.set(channelId, new Map());
    }
    const devMap = channelSyncHub.get(channelId);

    for (const [id, dev] of devMap.entries()) {
      if (now - dev.lastReportAt > 4000) devMap.delete(id);
    }

    const segmentSeq = Number(body.segmentSeq || 0);
    const segmentOffsetMs = Number(body.segmentOffsetMs || 0);
    const segmentDurationMs = Number(body.segmentDurationMs || 0);

    const existing = devMap.get(deviceId);
    const entry = {
      deviceId,
      deviceName,
      isLive,
      positionMs,
      liveOffsetMs,
      segmentSeq,
      segmentOffsetMs,
      segmentDurationMs,
      firstSeenAt: existing ? existing.firstSeenAt : now,
      lastReportAt: now,
    };
    devMap.set(deviceId, entry);

    const peers = [...devMap.values()].sort((a, b) => a.firstSeenAt - b.firstSeenAt);
    const leader = peers[0] || entry;
    const isLeader = leader.deviceId === deviceId;
    const syncActive = peers.length >= 2;

    const elapsedSinceLeaderReport = Math.max(0, now - leader.lastReportAt);
    const projectedPositionMs = leader.positionMs + elapsedSinceLeaderReport;
    const dur = Math.max(1000, leader.segmentDurationMs || 0);
    const rawProjectedOffset = (leader.segmentOffsetMs || 0) + elapsedSinceLeaderReport;
    const projectedSeq = leader.segmentSeq || 0;
    const projectedOffset = rawProjectedOffset;

    return {
      ok: true,
      serverTimeMs: now,
      syncActive,
      isLeader,
      peerCount: peers.length,
      leaderId: leader.deviceId,
      leaderName: leader.deviceName,
      targetLiveOffsetMs: leader.liveOffsetMs,
      targetPositionMs: projectedPositionMs,
      targetSegmentSeq: projectedSeq,
      targetSegmentOffsetMs: projectedOffset,
      targetSegmentDurationMs: leader.segmentDurationMs || 0,
      peers: peers.map((p) => ({
        id: p.deviceId,
        name: p.deviceName,
        isLeader: p.deviceId === leader.deviceId,
      })),
    };
  });

  app.get('/api/sync/:channelId', async (req) => {
    const channelId = req.params.channelId;
    const now = Date.now();
    const devMap = channelSyncHub.get(channelId) || new Map();
    for (const [id, dev] of devMap.entries()) {
      if (now - dev.lastReportAt > 4000) devMap.delete(id);
    }
    const peers = [...devMap.values()].sort((a, b) => a.firstSeenAt - b.firstSeenAt);
    return {
      ok: true,
      channelId,
      syncActive: peers.length >= 2,
      peerCount: peers.length,
      leaderName: peers[0]?.deviceName || '',
    };
  });

  app.get('/api/config', async (req) => {
    const { profile, profileId, lang } = profileContext(req);
    const stats = getStats(profileId);
    const facets = getFacets(lang);
    const urls = lanUrls();
    const base = bestBaseUrl(req);
    const epg = getEpgStats();
    const settings = getAllSettings();
    return {
      name: settings.server_name || config.serverName,
      version: config.version,
      serverTime: Math.floor(Date.now() / 1000),
      baseUrl: base,
      urls,
      stats,
      facets,
      defaultCountry: settings.default_country || config.defaultCountry,
      activeProfile: profileDto(profile, lang),
      features: {
        epg: hasFreshEpg(),
        scan: true,
        proxy: true,
        discovery: true,
        profiles: true,
        tvMode: true,
      },
      epg: {
        sources: epg.sources.length,
        programs: epg.programs,
        matchedChannels: epg.matchedChannels,
        active: epg.sources.some((s) => s.enabled && s.last_status === 'ok'),
      },
      playlists: {
        all: `${base}/playlist.m3u`,
        italy: `${base}/playlist.m3u?country=${settings.default_country || config.defaultCountry}`,
        epg: `${base}/epg.xml`,
        epgGz: `${base}/epg.xml.gz`,
      },
      settings: {
        autoScan: settings.auto_scan === '1',
        scanIntervalHours: Number(settings.scan_interval_hours || 24),
        epgAutoRefresh: settings.epg_auto_refresh === '1',
        hideNsfw: settings.hide_nsfw === '1',
      },
      catalog: {
        source: settings.catalog_url || config.sourceIndexUrl,
        defaultSource: config.sourceIndexUrl,
        lastIngest: Number(settings.last_ingest || 0) || null,
      },
      ingest: ingestState,
      scan: { ...scanState, last: getLastScan() },
    };
  });

  app.get('/api/profiles', async (req) => {
    const { lang } = profileContext(req);
    return {
      profiles: listProfiles(lang),
      avatars: PROFILE_AVATARS,
      languages: PROFILE_LANGUAGES,
    };
  });

  app.get('/api/profiles/:id', async (req, reply) => {
    const profile = getProfile(req.params.id);
    if (!profile) {
      reply.code(404);
      return { error: 'Profilo non trovato' };
    }
    return profileDto(profile);
  });

  app.post('/api/profiles', async (req, reply) => {
    try {
      const profile = createProfile(req.body || {});
      reply.code(201);
      return profileDto(profile);
    } catch (error) {
      reply.code(error.status || 400);
      return { error: error.message };
    }
  });

  app.patch('/api/profiles/:id', async (req, reply) => {
    try {
      const profile = updateProfile(req.params.id, req.body || {});
      if (!profile) {
        reply.code(404);
        return { error: 'Profilo non trovato' };
      }
      return profileDto(profile);
    } catch (error) {
      reply.code(error.status || 400);
      return { error: error.message };
    }
  });

  app.delete('/api/profiles/:id', async (req, reply) => {
    try {
      const removed = deleteProfile(req.params.id);
      if (!removed) {
        reply.code(404);
        return { error: 'Profilo non trovato' };
      }
      return { ok: true };
    } catch (error) {
      reply.code(error.status || 400);
      return { error: error.message };
    }
  });

  ensureGuestProfile();
  clearGuestProfileData();

  app.post('/api/profiles/guest/clear', async () => {
    clearGuestProfileData();
    return { ok: true, message: 'Dati profilo ospite azzerati' };
  });

  app.get('/api/home', async (req) => {
    const base = getBaseUrl(req);
    const { profile, lang } = profileContext(req);
    const limit = Math.max(4, Math.min(Number(req.query.limit) || 18, 40));
    return { rows: getHomeRows(base, limit, profile), language: lang };
  });

  app.get('/api/channels', async (req) => {
    const base = getBaseUrl(req);
    const { profileId, lang } = profileContext(req);
    return listChannels(
      {
        q: req.query.q,
        country: req.query.country,
        category: req.query.category,
        language: req.query.language,
        quality: req.query.quality,
        favorite: req.query.favorite,
        hidden: req.query.hidden,
        status: req.query.status,
        number: req.query.number,
        sort: req.query.sort,
        limit: req.query.limit,
        offset: req.query.offset,
        profileId,
      },
      base,
      lang,
    );
  });

  app.get('/api/channels/by-number/:number', async (req, reply) => {
    const { profileId, lang } = profileContext(req);
    const row = getChannelRowByNumber(req.params.number, profileId);
    if (!row) {
      reply.code(404);
      return { error: 'Canale non trovato' };
    }
    const dto = channelDto(row, getBaseUrl(req), { withEpg: true, lang });
    return dto;
  });

  app.get('/api/channels/:id', async (req, reply) => {
    const { profileId, lang } = profileContext(req);
    const row = getChannelRow(req.params.id, profileId);
    if (!row) {
      reply.code(404);
      return { error: 'Canale non trovato' };
    }
    return channelDto(row, getBaseUrl(req), { withEpg: true, lang });
  });

  app.get('/api/channels/:id/epg', async (req) => epgChannel(req.params.id));

  app.get('/api/channels/:id/check', async (req, reply) => {
    const { profileId, lang } = profileContext(req);
    const row = getChannelRow(req.params.id, profileId);
    if (!row) {
      reply.code(404);
      return { error: 'Canale non trovato' };
    }
    await checkChannel(row.id);
    return channelDto(getChannelRow(row.id, profileId), getBaseUrl(req), {
      withEpg: true,
      lang,
    });
  });

  app.post('/api/channels/:id/played', async (req, reply) => {
    const id = req.params.id;
    const { profileId } = profileContext(req);
    if (!markProfilePlayed(profileId, id)) {
      reply.code(404);
      return { error: 'Canale non trovato' };
    }
    return { ok: true };
  });

  app.patch('/api/channels/:id', async (req, reply) => {
    const id = req.params.id;
    const { profileId, lang } = profileContext(req);
    const row = getChannelRow(id, profileId);
    if (!row) {
      reply.code(404);
      return { error: 'Canale non trovato' };
    }
    const body = req.body || {};
    const updates = [];
    const params = [];
    const profileUpdates = {};

    if (body.favorite !== undefined) {
      profileUpdates.favorite = !!body.favorite;
    }
    if (body.hidden !== undefined) {
      profileUpdates.hidden = !!body.hidden;
    }
    if (body.number !== undefined && body.number !== null) {
      const number = Number(body.number);
      if (!Number.isInteger(number) || number < 1 || number > 99999) {
        reply.code(400);
        return { error: 'Numero canale non valido' };
      }
      const clash = db
        .prepare('SELECT id FROM channels WHERE number = ? AND id <> ?')
        .get(number, id);
      if (clash) {
        reply.code(409);
        return { error: 'Numero già assegnato a un altro canale' };
      }
      updates.push('number = ?');
      params.push(number);
    }
    if (body.preferredStreamId !== undefined) {
      if (body.preferredStreamId === null || body.preferredStreamId === '') {
        updates.push('preferred_stream_id = NULL');
      } else {
        const stream = db
          .prepare('SELECT id FROM streams WHERE id = ? AND channel_id = ?')
          .get(body.preferredStreamId, id);
        if (!stream) {
          reply.code(400);
          return { error: 'Sorgente non appartenente al canale' };
        }
        updates.push('preferred_stream_id = ?');
        params.push(body.preferredStreamId);
      }
    }
    if (!updates.length && !Object.keys(profileUpdates).length) {
      reply.code(400);
      return { error: 'Nessuna modifica' };
    }
    if (Object.keys(profileUpdates).length) {
      setProfileChannelState(profileId, id, profileUpdates);
    }
    if (updates.length) {
      updates.push('updated_at = ?');
      params.push(Math.floor(Date.now() / 1000), id);
      db.prepare(`UPDATE channels SET ${updates.join(', ')} WHERE id = ?`).run(...params);
    }
    return channelDto(getChannelRow(id, profileId), getBaseUrl(req), {
      withEpg: true,
      lang,
    });
  });

  app.get('/api/channels/:id/streams', async (req, reply) => {
    const { profileId } = profileContext(req);
    const row = getChannelRow(req.params.id, profileId);
    if (!row) {
      reply.code(404);
      return { error: 'Canale non trovato' };
    }
    const base = getBaseUrl(req);
    const streams = db
      .prepare(
        `SELECT id, quality, feed, status, status_checked_at, status_ms, last_ok_at,
                url, user_agent, referrer, priority, enabled
         FROM streams WHERE channel_id = ? AND enabled = 1
         ORDER BY (status = 'online') DESC, quality_rank DESC, priority ASC, id ASC
         LIMIT 12`,
      )
      .all(row.id);
    return {
      channel: row.id,
      sources: streams.map((stream) => ({
        id: stream.id,
        quality: stream.quality,
        feed: stream.feed,
        status: stream.status,
        url: `${base}/stream/${encodeURIComponent(row.id)}/playlist.m3u8?source=${encodeURIComponent(
          stream.id,
        )}`,
      })),
      streams,
    };
  });

  app.get('/api/facets', async (req) => {
    const { lang } = profileContext(req);
    return getFacets(lang);
  });

  app.addContentTypeParser('multipart/form-data', { parseAs: 'buffer', bodyLimit: 64 * 1024 * 1024 }, async (req, body) => ({
    _rawMultipart: body,
    _contentType: req.headers['content-type'] || '',
  }));

  app.get('/api/playlists', async () => ({
    ok: true,
    playlists: listPlaylists(),
  }));

  app.post('/api/playlists', { bodyLimit: 64 * 1024 * 1024 }, async (req, reply) => {
    const body = req.body || {};
    const url = String(body.url || '').trim();
    const content = body.content != null ? String(body.content) : null;
    const name = String(body.name || '').trim();
    if (!url && content == null) {
      reply.code(400);
      return { ok: false, error: 'URL o contenuto della playlist mancante' };
    }
    try {
      const playlist = await importPlaylist({ name, url, text: content });
      reply.code(201);
      return { ok: true, playlist };
    } catch (err) {
      reply.code(400);
      return { ok: false, error: err.message };
    }
  });

  app.post('/api/playlists/upload', { bodyLimit: 64 * 1024 * 1024 }, async (req, reply) => {
    try {
      let text = '';
      let fileName = '';
      let customName = String(req.query?.name || '').trim();
      if (req.body && req.body._rawMultipart) {
        const form = await new Response(req.body._rawMultipart, {
          headers: { 'content-type': req.body._contentType },
        }).formData();
        const file = form.get('file');
        const nameField = String(form.get('name') || '').trim();
        if (nameField) customName = nameField;
        if (file && typeof file.text === 'function') {
          fileName = file.name || 'playlist.m3u';
          text = await file.text();
        } else if (typeof file === 'string') {
          text = file;
        } else {
          reply.code(400);
          return { ok: false, error: 'Nessun file ricevuto' };
        }
      } else {
        reply.code(400);
        return { ok: false, error: 'Formato multipart non valido' };
      }
      const playlist = await importPlaylist({
        name: customName || fileName,
        url: '',
        text,
      });
      reply.code(201);
      return { ok: true, playlist };
    } catch (err) {
      reply.code(400);
      return { ok: false, error: err.message || 'Errore caricamento file M3U' };
    }
  });

  app.delete('/api/playlists/:id', async (req, reply) => {
    deletePlaylist(req.params.id);
    return { ok: true };
  });

  app.post('/api/playlists/:id/check', async (req, reply) => {
    const playlist = await checkPlaylist(req.params.id);
    if (!playlist) {
      reply.code(404);
      return { ok: false, error: 'Playlist non trovata' };
    }
    return { ok: true, playlist };
  });

  app.post('/api/playlists/:id/refresh', async (req, reply) => {
    const p = getPlaylist(req.params.id);
    if (!p) {
      reply.code(404);
      return { ok: false, error: 'Playlist non trovata' };
    }
    if (!p.url) {
      const checked = await checkPlaylist(p.id);
      return { ok: true, channelCount: checked?.channelCount || 0, playlist: checked };
    }
    try {
      const playlist = await importPlaylist({ id: p.id, name: p.name, url: p.url });
      return { ok: true, channelCount: playlist?.channelCount || 0, playlist };
    } catch (err) {
      reply.code(400);
      return { ok: false, error: err.message };
    }
  });

  app.patch('/api/playlists/:id', async (req, reply) => {
    const body = req.body || {};
    if (typeof body.enabled === 'boolean') {
      togglePlaylist(req.params.id, body.enabled);
    }
    return { ok: true };
  });

  app.get('/api/library', async () => ({
    items: [],
    movies: 0,
    episodes: 0,
    serverTime: Math.floor(Date.now() / 1000),
  }));

  app.get('/playlist.m3u', async (req, reply) => {
    const base = getBaseUrl(req);
    const query = req.query;
    const { profileId } = profileContext(req);
    const result = listChannelRows({
      q: query.q,
      country: query.country,
      category: query.category,
      language: query.language,
      quality: query.quality,
      favorite: query.favorite,
      hidden: query.hidden || 0,
      status: query.status,
      sort: query.sort || 'number',
      limit: 20000,
      maxLimit: 20000,
      profileId,
    });
    const body = generateM3u(result.rows, {
      baseUrl: base,
      groupBy: query.groupBy || 'country',
      includeEpg: hasFreshEpg(),
    });
    reply
      .header('content-type', 'audio/x-mpegurl; charset=utf-8')
      .header('content-disposition', 'inline; filename="casatv.m3u"')
      .header('cache-control', 'no-cache');
    return body;
  });

  app.get('/stream/:id', (req, reply) => handleStreamRequest(req, reply, req.params.id));
  app.get('/stream/:id/playlist.m3u8', (req, reply) =>
    handleStreamRequest(req, reply, req.params.id),
  );
  app.get('/stream/:id/*', (req, reply) =>
    handleStreamRequest(req, reply, req.params.id),
  );
  app.get('/p/:token', (req, reply) => handleProxyToken(req, reply));
  app.get('/logo/:id', (req, reply) => getLogo(req, reply, req.params.id));

  app.get('/epg.xml', async (req, reply) => {
    const base = getBaseUrl(req);
    const generated = generateXmltv(base);
    const xml =
      generated.xml ||
      '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE tv SYSTEM "xmltv.dtd">\n<tv generator-info-name="CasaTV"></tv>\n';
    reply
      .header('content-type', 'application/xml; charset=utf-8')
      .header('cache-control', 'public, max-age=300');
    return xml;
  });

  app.get('/epg.xml.gz', async (req, reply) => {
    const base = getBaseUrl(req);
    const generated = generateXmltv(base);
    if (!generated.gz) {
      reply.code(404);
      return 'EPG non disponibile';
    }
    reply
      .header('content-type', 'application/gzip')
      .header('content-disposition', 'inline; filename="epg.xml.gz"')
      .header('cache-control', 'public, max-age=300');
    return generated.gz;
  });

  app.get('/api/epg/sources', async () => ({ sources: listSources() }));

  app.post('/api/epg/sources', async (req, reply) => {
    try {
      const source = addSource(req.body?.name, req.body?.url);
      reply.code(201);
      return source;
    } catch (error) {
      reply.code(400);
      return { error: error.message };
    }
  });

  app.delete('/api/epg/sources/:id', async (req) => {
    removeSource(req.params.id);
    return { ok: true };
  });

  app.post('/api/epg/sources/:id/refresh', async (req, reply) => {
    try {
      const result = await refreshSource(req.params.id);
      return result;
    } catch (error) {
      reply.code(502);
      return { error: error.message };
    }
  });

  app.post('/api/epg/refresh', async () => {
    refreshAllSources({ force: true }).catch(() => {});
    return { started: true, state: epgState };
  });

  app.get('/api/epg/status', async () => ({
    state: epgState,
    stats: getEpgStats(),
    active: hasFreshEpg(),
  }));

  app.get('/api/epg/guide', async (req) =>
    epgGuide({
      country: req.query.country,
      hours: req.query.hours,
      limit: req.query.limit,
    }),
  );

  app.get('/api/epg/channel/:id', async (req) => epgChannel(req.params.id));

  app.get('/api/scan/status', async () => ({
    ...scanState,
    last: getLastScan(),
  }));

  app.post('/api/scan', async (req, reply) => {
    if (scanState.running) {
      return { started: false, state: scanState };
    }
    const mode = ['quick', 'full', 'stale'].includes(req.body?.mode)
      ? req.body.mode
      : 'quick';
    const limit = req.body?.limit ? Number(req.body.limit) : null;
    startScanInBackground({ mode, limit });
    reply.code(202);
    return { started: true, state: scanState };
  });

  app.post('/api/scan/stop', async () => {
    stopScan();
    return { stopping: true, state: scanState };
  });

  app.post('/api/refresh', async (req, reply) => {
    if (ingestState.running) {
      return { started: false, state: ingestState };
    }
    const body = req.body || {};
    if (body.url !== undefined) {
      const url = String(body.url || '').trim() || config.sourceIndexUrl;
      if (!isHttpUrl(url)) {
        reply.code(400);
        return { error: 'URL della playlist M3U non valido' };
      }
      setSetting('catalog_url', url);
    }
    setTimeout(() => {
      runIngest({ force: true, reason: 'admin' }).catch((error) =>
        console.error('[catalog] refresh fallito:', error.message),
      );
    }, 20);
    reply.code(202);
    return { started: true, state: ingestState };
  });

  app.get('/api/catalog', async () => {
    const settings = getAllSettings();
    return {
      source: settings.catalog_url || config.sourceIndexUrl,
      defaultSource: config.sourceIndexUrl,
      lastIngest: Number(settings.last_ingest || 0) || null,
      state: ingestState,
    };
  });

  app.get('/api/refresh/status', async () => ({ state: ingestState }));

  app.post('/api/settings/verify-pin', async (req, reply) => {
    const body = req.body || {};
    const pin = String(body.pin || '').trim();
    const currentPin = getSetting('admin_pin', '0000');
    if (pin === currentPin) {
      return { ok: true, message: 'PIN corretto' };
    }
    reply.code(401);
    return { ok: false, error: 'PIN non valido' };
  });

  app.post('/api/settings/change-pin', async (req, reply) => {
    const body = req.body || {};
    const oldPin = String(body.oldPin || '').trim();
    const newPin = String(body.newPin || '').trim();
    const currentPin = getSetting('admin_pin', '0000');
    if (oldPin !== currentPin) {
      reply.code(401);
      return { ok: false, error: 'Il PIN attuale non è corretto' };
    }
    if (!/^\d{4}$/.test(newPin)) {
      reply.code(400);
      return { ok: false, error: 'Il nuovo PIN deve contenere esattamente 4 cifre numeriche' };
    }
    setSetting('admin_pin', newPin);
    return { ok: true, message: 'PIN aggiornato con successo' };
  });

  app.patch('/api/settings', async (req, reply) => {
    const body = req.body || {};
    const allowed = new Set([
      'server_name',
      'default_country',
      'catalog_url',
      'auto_scan',
      'scan_interval_hours',
      'epg_auto_refresh',
      'hide_nsfw',
      'admin_pin',
    ]);
    let changed = 0;
    for (const [key, raw] of Object.entries(body)) {
      if (!allowed.has(key)) continue;
      let value = raw;
      if (key === 'default_country') {
        value = String(raw || '').toUpperCase();
        if (!/^[A-Z]{2}$/.test(value)) {
          reply.code(400);
          return { error: 'Paese non valido' };
        }
      } else if (key === 'catalog_url') {
        value = String(raw || '').trim() || config.sourceIndexUrl;
        if (!isHttpUrl(value)) {
          reply.code(400);
          return { error: 'URL della playlist M3U non valido' };
        }
      } else if (['auto_scan', 'epg_auto_refresh', 'hide_nsfw'].includes(key)) {
        value = raw ? '1' : '0';
      } else if (key === 'scan_interval_hours') {
        value = String(Math.max(1, Math.min(Number(raw) || 24, 720)));
      } else if (key === 'server_name') {
        value = String(raw || '').slice(0, 60) || config.serverName;
      } else if (key === 'admin_pin') {
        value = String(raw || '').trim();
        if (!/^\d{4}$/.test(value)) {
          reply.code(400);
          return { error: 'Il PIN deve contenere 4 cifre' };
        }
      }
      setSetting(key, value);
      changed += 1;
    }
    if (!changed) {
      reply.code(400);
      return { error: 'Nessuna impostazione valida' };
    }
    return { ok: true, settings: getAllSettings() };
  });

  app.post('/api/admin/channels', async (req, reply) => {
    const body = req.body || {};
    if (!body.name || !body.url) {
      reply.code(400);
      return { error: 'Nome e URL sono obbligatori' };
    }
    if (!isHttpUrl(body.url)) {
      reply.code(400);
      return { error: 'URL non valido' };
    }
    const name = cleanChannelName(body.name);
    const id = `custom-${slugify(name) || 'canale'}-${sha1(body.url).slice(0, 8)}`;
    if (getChannelRow(id)) {
      reply.code(409);
      return { error: 'Canale già esistente' };
    }
    const now = Math.floor(Date.now() / 1000);
    const number =
      body.number != null && Number.isFinite(Number(body.number))
        ? Number(body.number)
        : (db.prepare('SELECT MAX(number) AS n FROM channels').get().n || 0) + 1;
    const categories = Array.isArray(body.categories) && body.categories.length
      ? [...new Set(body.categories.map((c) => categorySlug(c)))]
      : ['general'];
    const country = String(body.country || '').toUpperCase().slice(0, 2) || null;
    const quality = parseQuality(body.quality);
    db.prepare(
      `INSERT INTO channels (
        id, name, alt_names, country, group_title, categories, languages, logo,
        number, enabled, favorite, hidden, custom, status, created_at, updated_at
      ) VALUES (?, ?, '[]', ?, ?, ?, '[]', ?, ?, 1, 0, 0, 1, 'unknown', ?, ?)`,
    ).run(
      id,
      name,
      country,
      categories.join(';'),
      JSON.stringify(categories),
      body.logo || null,
      number,
      now,
      now,
    );
    db.prepare(
      `INSERT INTO streams (id, channel_id, url, quality, quality_rank, user_agent,
        referrer, priority, enabled, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 'unknown')`,
    ).run(
      `custom-${sha1(body.url).slice(0, 16)}`,
      id,
      body.url,
      quality,
      qualityRank(quality),
      body.userAgent || null,
      body.referrer || null,
      0,
    );
    reply.code(201);
    return channelDto(getChannelRow(id), getBaseUrl(req), { withEpg: false });
  });

  app.get('/api/admin/stats', async () => ({
    stats: getStats(),
    ingest: ingestState,
    scan: { ...scanState, last: getLastScan() },
    epg: { state: epgState, ...getEpgStats() },
    settings: getAllSettings(),
  }));
}
