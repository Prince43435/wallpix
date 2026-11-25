#!/data/data/com.termux/files/usr/bin/bash
# Start the WallPix cloud server in Termux.
#   ./start.sh              -> port 8080, admin key wallpix-admin
#   PORT=9000 ADMIN_KEY=mysecret ./start.sh
cd "$(dirname "$0")" || exit 1
exec node server.js
