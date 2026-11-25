# WallPix 🖼️

Do alag Android apps, ek codebase se:

| App | Kaam |
|---|---|
| **WallPix Admin** | wallpapers import karo, title/category/featured, cloud pe upload |
| **WallPix** (User) | wallpapers browse karo, preview, Home/Lock/Both set, favorites |

Cloud sync: admin kisi bhi phone se upload kare, user app wale sab phones pe
(download ke baad) dikhega.

## Downloads

GitHub Releases: https://github.com/Prince43435/wallpix/releases

- `WallPix-Admin-v1.1.apk`
- `WallPix-User-v1.1.apk`

Admin ka default PIN **1234** (Admin panel → ⋮ → Change PIN).

## Cloud server (sync ke liye zaroori)

```bash
cd ~/projects/wallpix/server
./start.sh
# http://0.0.0.0:8080  |  admin key: wallpix-admin
```

Browser me `http://127.0.0.1:8080` kholke gallery check karo.

Setup:
1. Admin app → `⋮` → **Cloud sync settings** → URL + key → Save → **Push all to cloud**
2. User app → header cloud icon → wahi URL → Save

URLs:
- Usi phone: `http://127.0.0.1:8080`
- Same WiFi: `http://<server-phone-IP>:8080`
- Internet: `server/README.md` dekho (cloudflared tunnel / hosting)

## Admin app

| Feature | Kaise |
|---|---|
| Wallpaper add | `Add wallpaper` → gallery → title + category |
| Bulk add | `Bulk add` → multiple images |
| Featured | wallpaper tap → `Mark as featured` |
| Rename / Delete | wallpaper tap → menu |
| Cloud | `⋮` → settings / `Push all to cloud` |
| PIN | `⋮` → `Change PIN` |
| Auto-lock | app background me jaate hi lock |

## User app

- Category chips + **Favorites**
- Cloud wallpapers local wale ke saath merge (cloud duplicate nahi dikhta)
- Tap → full screen → **Home / Lock / Both**
- Long-press → quick actions
- Cloud icon → server URL

## Build (no Gradle)

```bash
python3 build.sh          # both APKs -> dist/
python3 build.sh admin
python3 build.sh user
```

Pipeline: `aapt2 → javac → d8 → apksigner`.
Requirements: `aapt2`, `d8`, `apksigner`, `javac`, `jar`, `keytool`.
`ANDROID_JAR` env se android.jar path de sakte ho (default `libs/android.jar`).

GitHub Actions har push pe dono APKs build karta hai; `v*` tag release banata hai.

## Structure

```
src/            Java sources (com.wallpix.app)
res/            layouts, drawables, strings
manifest/       AndroidManifest-admin.xml, AndroidManifest-user.xml
server/         zero-dependency Node.js cloud server
.github/        CI workflow
```

## Notes

- Admin aur User alag packages hain (`com.wallpix.admin`, `com.wallpix.user`),
  isliye dono ek saath install ho sakte hain. Local storage alag hota hai —
  sync cloud se hota hai.
- Server URL jiske paas hai wo wallpapers dekh sakta hai. Public deploy pe
  `ADMIN_KEY` badlo aur HTTPS use karo.
