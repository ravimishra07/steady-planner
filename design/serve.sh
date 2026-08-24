#!/bin/sh
# Serve design/ (no-cache) and open the app + canvas.
cd "$(dirname "$0")"
PORT=8765
APP="http://localhost:${PORT}/app.html"
CANVAS="http://localhost:${PORT}/canvas-view.html"

if lsof -ti:"$PORT" >/dev/null 2>&1; then
  echo "Port $PORT already in use — opening browser…"
  open "$APP" 2>/dev/null || xdg-open "$APP" 2>/dev/null
  exit 0
fi

(sleep 0.8 && { open "$APP" 2>/dev/null || xdg-open "$APP" 2>/dev/null; }) &
echo "App:    $APP"
echo "Canvas: $CANVAS"
exec python3 serve.py "$PORT"
