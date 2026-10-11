#!/bin/sh
# No-cache server: browsers otherwise keep stale ES modules after edits.
cd "$(dirname "$0")" && echo "http://localhost:8765" && python3 -c '
import http.server
class H(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()
http.server.ThreadingHTTPServer(("", 8765), H).serve_forever()
'
