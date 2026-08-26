#!/usr/bin/env python3
"""Serve the debug APK on the LAN so a phone can pull it over Wi-Fi.

    python3 tools/serve_apk.py

Open the printed URL on the phone, tap the file, install. No cable, no Drive.
Android will ask once to allow installs from the browser.
"""
import http.server, os, pathlib, socket, socketserver, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APK = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8787


def lan_ip() -> str:
    for iface in ("en0", "en1"):
        out = subprocess.run(["ipconfig", "getifaddr", iface],
                             capture_output=True, text=True).stdout.strip()
        if out:
            return out
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.connect(("8.8.8.8", 80))
    ip = s.getsockname()[0]
    s.close()
    return ip


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/", "/index.html"):
            size = APK.stat().st_size / 1_048_576
            body = f"""<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
<title>Steadyline</title>
<style>
 body{{font-family:system-ui;background:#0A0A0F;color:#fff;margin:0;
      display:flex;flex-direction:column;align-items:center;justify-content:center;
      height:100dvh;gap:20px;padding:24px;text-align:center}}
 a{{background:#6D35C8;color:#fff;text-decoration:none;padding:18px 34px;
    border-radius:50px;font-size:17px;font-weight:600}}
 p{{color:#9AA0AA;font-size:14px;margin:0}}
</style>
<h1>Steadyline</h1>
<p>debug build &middot; {size:.1f} MB</p>
<a href="/app-debug.apk" download>Download APK</a>
<p>Allow installs from your browser if Android asks.</p>"""
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body.encode())
            return

        if self.path.startswith("/app-debug.apk"):
            if not APK.exists():
                self.send_error(404, "APK not built yet")
                return
            data = APK.read_bytes()
            self.send_response(200)
            # The MIME type Android expects, so the browser offers to install.
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Content-Disposition", 'attachment; filename="steadyline.apk"')
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)
            return

        self.send_error(404)

    def log_message(self, fmt, *args):
        print("  " + fmt % args, flush=True)


if not APK.exists():
    sys.exit(f"No APK at {APK}\nBuild it first:\n"
             f"  export JAVA_HOME=$(/usr/libexec/java_home -v 21)\n"
             f"  cd android && ./gradlew :app:assembleDebug")

socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("", PORT), Handler) as httpd:
    print(f"\n  On your phone open:  http://{lan_ip()}:{PORT}\n")
    print(f"  serving {APK.stat().st_size / 1_048_576:.1f} MB — ctrl-C to stop\n")
    httpd.serve_forever()
