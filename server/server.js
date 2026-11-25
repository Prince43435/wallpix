#!/usr/bin/env node
/*
 * WallPix cloud server — zero dependencies.
 *   GET    /                     simple web gallery (for checking)
 *   GET    /health               { ok, count }
 *   GET    /api/wallpapers       { wallpapers: [...] }
 *   POST   /api/wallpapers       image bytes + ?id&title&category&featured&created
 *   POST   /api/thumbs           thumb bytes + ?id
 *   POST   /api/meta             ?id&title&category&featured  (no body)
 *   DELETE /api/wallpapers/:id   auth via X-Admin-Key header or ?key=
 *
 * Env: PORT (default 8080), ADMIN_KEY (default wallpix-admin)
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT || '8080', 10);
const ADMIN_KEY = process.env.ADMIN_KEY || 'wallpix-admin';
const ROOT = __dirname;
const DATA = process.env.DATA_DIR
  ? path.resolve(process.env.DATA_DIR)
  : path.join(ROOT, 'data');
const IMG_DIR = path.join(DATA, 'images');
const THUMB_DIR = path.join(DATA, 'thumbs');
const INDEX = path.join(DATA, 'wallpapers.json');
const MAX_BODY = 30 * 1024 * 1024;

fs.mkdirSync(IMG_DIR, { recursive: true });
fs.mkdirSync(THUMB_DIR, { recursive: true });

let catalog = load();

function load() {
  try {
    const raw = fs.readFileSync(INDEX, 'utf8');
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch (_) {
    return [];
  }
}

function persist() {
  const tmp = INDEX + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(catalog, null, 2));
  fs.renameSync(tmp, INDEX);
}

function json(res, code, obj) {
  const body = Buffer.from(JSON.stringify(obj));
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Access-Control-Allow-Origin': '*'
  });
  res.end(body);
}

function html(res, code, body) {
  const b = Buffer.from(body);
  res.writeHead(code, { 'Content-Type': 'text/html; charset=utf-8', 'Content-Length': b.length });
  res.end(b);
}

function authorized(req, url) {
  const key = req.headers['x-admin-key'] || url.searchParams.get('key');
  return String(key || '') === ADMIN_KEY;
}

function safeId(raw) {
  return String(raw || '').replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 80);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(new Error('Body too large'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function writeAtomic(file, buf) {
  const tmp = file + '.part';
  fs.writeFileSync(tmp, buf);
  fs.renameSync(tmp, file);
}

function sortedCatalog() {
  return [...catalog].sort((a, b) => {
    if (!!a.featured !== !!b.featured) return a.featured ? -1 : 1;
    return (b.created || 0) - (a.created || 0);
  });
}

function serveFile(res, file, type) {
  fs.stat(file, (err, st) => {
    if (err || !st.isFile()) {
      json(res, 404, { error: 'Not found' });
      return;
    }
    res.writeHead(200, {
      'Content-Type': type,
      'Content-Length': st.size,
      'Cache-Control': 'public, max-age=300',
      'Access-Control-Allow-Origin': '*'
    });
    fs.createReadStream(file).pipe(res);
  });
}

function upsert(id) {
  let item = catalog.find((x) => x.id === id);
  if (!item) {
    item = { id, title: 'Wallpaper', category: 'Other', featured: false, created: Date.now() };
    catalog.push(item);
  }
  return item;
}

function galleryPage() {
  const items = sortedCatalog();
  const cards = items.map((w) => `
    <div class="card">
      <img src="${w.image}" loading="lazy" alt="">
      <div class="meta">
        <div class="title">${escapeHtml(w.title || 'Wallpaper')}</div>
        <div class="cat">${escapeHtml(w.category || 'Other')}${w.featured ? ' • ★' : ''}</div>
      </div>
    </div>`).join('');
  return `<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>WallPix Cloud</title>
<style>
  *{box-sizing:border-box}body{margin:0;background:#0b0d14;color:#f3f6ff;font-family:system-ui,sans-serif;padding:24px}
  h1{font-size:24px;margin:0 0 4px}p{color:#93a0bd;margin:0 0 20px;font-size:13px}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:14px}
  .card{background:#151a28;border:1px solid #2a3247;border-radius:16px;overflow:hidden}
  .card img{width:100%;height:230px;object-fit:cover;display:block;background:#1e2537}
  .meta{padding:10px 12px}.title{font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .cat{font-size:11px;color:#93a0bd;margin-top:2px}
  .empty{padding:60px 0;text-align:center;color:#93a0bd}
</style></head>
<body>
  <h1>WallPix Cloud</h1>
  <p>${items.length} wallpaper${items.length === 1 ? '' : 's'} • server is running on port ${PORT}</p>
  <div class="grid">${cards || '<div class="empty">No wallpapers uploaded yet.</div>'}</div>
</body></html>`;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;

  try {
    if (req.method === 'GET' && (p === '/' || p === '/gallery')) {
      html(res, 200, galleryPage());
      return;
    }
    if (req.method === 'GET' && p === '/health') {
      json(res, 200, { ok: true, count: catalog.length, service: 'wallpix' });
      return;
    }
    if (req.method === 'GET' && p === '/api/wallpapers') {
      json(res, 200, { wallpapers: sortedCatalog() });
      return;
    }
    if (req.method === 'GET' && p.startsWith('/files/')) {
      const name = path.basename(p);
      if (!/^[a-zA-Z0-9_-]+\.jpg$/.test(name)) { json(res, 400, { error: 'Bad name' }); return; }
      serveFile(res, path.join(IMG_DIR, name), 'image/jpeg');
      return;
    }
    if (req.method === 'GET' && p.startsWith('/thumbs/')) {
      const name = path.basename(p);
      if (!/^[a-zA-Z0-9_-]+\.jpg$/.test(name)) { json(res, 400, { error: 'Bad name' }); return; }
      serveFile(res, path.join(THUMB_DIR, name), 'image/jpeg');
      return;
    }

    if (req.method === 'POST' && p === '/api/wallpapers') {
      if (!authorized(req, url)) { json(res, 401, { error: 'Bad admin key' }); return; }
      const id = safeId(url.searchParams.get('id'));
      if (!id) { json(res, 400, { error: 'Missing id' }); return; }
      const body = await readBody(req);
      if (body.length < 100) { json(res, 400, { error: 'Empty image' }); return; }
      writeAtomic(path.join(IMG_DIR, id + '.jpg'), body);

      const item = upsert(id);
      item.title = (url.searchParams.get('title') || 'Wallpaper').slice(0, 80);
      item.category = (url.searchParams.get('category') || 'Other').slice(0, 40);
      item.featured = url.searchParams.get('featured') === '1';
      const created = parseInt(url.searchParams.get('created') || '0', 10);
      item.created = created > 0 ? created : Date.now();
      item.image = '/files/' + id + '.jpg';
      persist();
      json(res, 200, { ok: true, wallpaper: item });
      return;
    }

    if (req.method === 'POST' && p === '/api/thumbs') {
      if (!authorized(req, url)) { json(res, 401, { error: 'Bad admin key' }); return; }
      const id = safeId(url.searchParams.get('id'));
      if (!id) { json(res, 400, { error: 'Missing id' }); return; }
      const body = await readBody(req);
      if (body.length < 100) { json(res, 400, { error: 'Empty thumb' }); return; }
      writeAtomic(path.join(THUMB_DIR, id + '.jpg'), body);
      const item = upsert(id);
      item.thumb = '/thumbs/' + id + '.jpg';
      persist();
      json(res, 200, { ok: true });
      return;
    }

    if (req.method === 'POST' && p === '/api/meta') {
      if (!authorized(req, url)) { json(res, 401, { error: 'Bad admin key' }); return; }
      const id = safeId(url.searchParams.get('id'));
      const item = catalog.find((x) => x.id === id);
      if (!item) { json(res, 404, { error: 'Not found' }); return; }
      if (url.searchParams.has('title')) item.title = url.searchParams.get('title').slice(0, 80);
      if (url.searchParams.has('category')) item.category = url.searchParams.get('category').slice(0, 40);
      if (url.searchParams.has('featured')) item.featured = url.searchParams.get('featured') === '1';
      persist();
      json(res, 200, { ok: true, wallpaper: item });
      return;
    }

    if (req.method === 'DELETE' && p.startsWith('/api/wallpapers/')) {
      if (!authorized(req, url)) { json(res, 401, { error: 'Bad admin key' }); return; }
      const id = safeId(p.substring('/api/wallpapers/'.length));
      catalog = catalog.filter((x) => x.id !== id);
      persist();
      for (const f of [path.join(IMG_DIR, id + '.jpg'), path.join(THUMB_DIR, id + '.jpg')]) {
        try { fs.unlinkSync(f); } catch (_) {}
      }
      json(res, 200, { ok: true });
      return;
    }

    if (req.method === 'OPTIONS') {
      res.writeHead(204, {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'X-Admin-Key, Content-Type',
        'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS'
      });
      res.end();
      return;
    }

    json(res, 404, { error: 'Unknown endpoint' });
  } catch (e) {
    console.error(e);
    json(res, 500, { error: e.message || 'Server error' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('WallPix cloud server');
  console.log('  listening : http://0.0.0.0:' + PORT);
  console.log('  admin key : ' + ADMIN_KEY);
  console.log('  data      : ' + DATA);
});
