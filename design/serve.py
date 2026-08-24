#!/usr/bin/env python3
"""Static server for design/ that never lets the browser cache.

python -m http.server caches aggressively, which means an edit you just made
silently does not show up — on desktop and especially on the phone.
"""
import functools, http.server, os, socketserver, sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, fmt, *args):
        if "GET" in (fmt % args) and " 200 " not in (fmt % args):
            super().log_message(fmt, *args)


socketserver.TCPServer.allow_reuse_address = True
ROOT = os.path.dirname(os.path.abspath(__file__))
handler = functools.partial(NoCache, directory=ROOT)
with socketserver.TCPServer(("", PORT), handler) as httpd:
    print(f"design/ on http://localhost:{PORT}  (no-cache)")
    httpd.serve_forever()
