#!/usr/bin/env python3
"""A fake Pocket AdBlock board for testing the app's login and web view.

Answers like the firmware: GET / without auth, /api/* behind HTTP Basic auth with
realm="sinkhole". Its page polls /api/status with fetch() and no credentials, exactly
like the real web UI, so it shows whether the app's web view logs in by itself.

Test-only credentials (never a real board's): admin / test-password-123

    python tools/fake_board.py [port]          # default 8080
    adb reverse tcp:8080 tcp:8080              # phone over USB, then type 127.0.0.1:8080
"""
import base64
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

USER, PASSWORD = "admin", "test-password-123"
EXPECTED = "Basic " + base64.b64encode(f"{USER}:{PASSWORD}".encode()).decode()

PAGE = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
<title>Fake board</title>
<style>body{font:16px system-ui;background:#0b0f15;color:#e7edf5;padding:20px}
b{color:#34d399}.bad{color:#fb7185}</style>
<h2>Fake board</h2><p id=s>loading…</p><p id=n></p>
<script>
let n=0;
async function poll(){
  try{const r=await fetch('/api/status');const j=await r.json();n++;
    document.getElementById('s').innerHTML='<b>fetch OK</b>: '+j.hostname+' v'+j.version;
    document.getElementById('n').textContent='polls: '+n}
  catch(e){document.getElementById('s').innerHTML='<span class=bad>fetch failed: '+e+'</span>'}}
poll();setInterval(poll,2000);
</script>"""

STATUS = {"version": "0.4.2", "edition": "pro", "hostname": "fake-board", "uptime": 1,
          "defaultPassword": False, "wifi": {"ssid": "Test", "ip": "127.0.0.1"},
          "dns": {"blocking": True, "resumeIn": 0, "blocked": 25, "allowed": 75},
          "blocklist": {"count": 140212}}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/":
            return self.send(200, PAGE.encode(), "text/html; charset=utf-8")
        if self.path.startswith("/api/"):
            got = self.headers.get("Authorization")
            if got != EXPECTED:
                print(f"401 {self.path} (auth {'missing' if got is None else 'wrong'})")
                self.send_response(401)
                self.send_header("WWW-Authenticate", 'Basic realm="sinkhole", charset="UTF-8"')
                self.send_header("Content-Length", "23")
                self.end_headers()
                self.wfile.write(b"authentication required")
                return
            print(f"200 {self.path}")
            return self.send(200, json.dumps(STATUS).encode(), "application/json")
        self.send(404, b"not found", "text/plain")

    def do_POST(self):
        # POST /api/blocking mode=on|off|pause&minutes=N, like web_server.cpp postBlocking().
        if self.path != "/api/blocking":
            return self.send(404, b"not found", "text/plain")
        if self.headers.get("Authorization") != EXPECTED:
            return self.send(401, b"authentication required", "text/plain")
        if self.headers.get("X-Requested-With") != "sinkhole":
            return self.send(403, b'{"ok":false,"message":"missing X-Requested-With header"}', "application/json")
        form = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode())
        mode = form.get("mode", [""])[0]
        dns = STATUS["dns"]
        if mode == "pause":
            minutes = int(form.get("minutes", ["0"])[0] or 0)
            if not 1 <= minutes <= 1440:
                return self.send(400, b'{"ok":false,"message":"minutes: 1..1440"}', "application/json")
            dns["blocking"], dns["resumeIn"] = False, minutes * 60
        else:
            dns["blocking"], dns["resumeIn"] = mode == "on", 0
        print(f"200 POST /api/blocking {mode} {form.get('minutes', [''])[0]}")
        self.send(200, b'{"ok":true,"message":"ok"}', "application/json")

    def send(self, code, body, ctype):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    print(f"fake board on http://127.0.0.1:{port}/ (user {USER})", flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
