# WallPix Cloud Server

Chhota zero-dependency Node.js server — Admin app wallpapers upload karta hai,
User app wale phones catalog + images download karte hain.

## Start (Termux)

```bash
cd ~/projects/wallpix/server
./start.sh
# listening: http://0.0.0.0:8080
# admin key: wallpix-admin
```

Check: browser me `http://127.0.0.1:8080` kholo — cloud gallery dikhegi.

Port / key change:

```bash
PORT=9000 ADMIN_KEY=mysecretkey ./start.sh
```

> Termux ko band hone se bachane ke liye: `termux-wake-lock`

## App me connect karo

**Admin app** → top-right `⋮` → `Cloud sync settings`
- Server URL: `http://192.168.1.5:8080` (apne phone ka WiFi IP)
- Admin key: `wallpix-admin`
- Save → `Push all to cloud`

**User app** → header ka cloud icon → same URL → Save

## Kaun sa URL daalein?

| Scenario | URL |
|---|---|
| Server usi phone ke Termux me | `http://127.0.0.1:8080` |
| Same WiFi ke doosre phone | `http://<server-phone-ka-IP>:8080` (e.g. `http://192.168.1.5:8080`) |
| Internet (alag network) | HTTPS tunnel ya hosted deploy — neeche dekho |

Phone ka IP: `ifconfig wlan0 | grep inet`

### Public URL (internet)

**Option A — quick tunnel (test ke liye):**
```bash
pkg install cloudflared
cloudflared tunnel --url http://localhost:8080
```
Jo `https://xxxx.trycloudflare.com` URL mile wo app me daalo. Tunnel band hote hi URL
badal jata hai.

**Option B — permanent hosting:** is folder ko Render / Railway / Fly.io / VPS pe
deploy karo (`npm start`, PORT env). Images survive karne ke liye persistent disk
mount karo (`DATA_DIR=/data`, e.g. Dockerfile me already set) — warna redeploy par
images chali jayengi.

## Security

- `ADMIN_KEY` change kar do public deploy se pehle.
- Public internet pe HTTP use mat karo — HTTPS tunnel/hosting use karo (key plaintext
  me jati hai warna).
- Server par koi login/user system nahi hai: URL jiske paas hai wo wallpapers dekh
  sakta hai. Personal use ke liye theek hai.

## API

| Method | Path | Kaam |
|---|---|---|
| GET | `/api/wallpapers` | catalog JSON |
| POST | `/api/wallpapers?id&title&category&featured&created` | image bytes upload (X-Admin-Key) |
| POST | `/api/thumbs?id` | thumbnail bytes (X-Admin-Key) |
| POST | `/api/meta?id&title&category&featured` | metadata update (X-Admin-Key) |
| DELETE | `/api/wallpapers/:id` | delete (X-Admin-Key) |
| GET | `/files/:id.jpg`, `/thumbs/:id.jpg` | images |

Data `data/wallpapers.json`, `data/images/`, `data/thumbs/` me rehta hai.
