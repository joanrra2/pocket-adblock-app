#!/usr/bin/env python3
"""A fake Pocket AdBlock board for testing the app's login and web view.

Answers like the firmware: GET / without auth, /api/* behind HTTP Basic auth with
realm="sinkhole". Its page polls /api/status with fetch() and no credentials, exactly
like the real web UI, so it shows whether the app's web view logs in by itself.

Test-only credentials (never a real board's): admin / test-password-123

Like the coming firmware login limit, 3 different wrong passwords in a row make it
answer 429 with Retry-After (30 s, then doubling); the same wrong password repeated
does not count. It also offers an update (0.5.2) to try the app's install and restart.

    python tools/fake_board.py [port]          # default 8080
    adb reverse tcp:8080 tcp:8080              # phone over USB, then type 127.0.0.1:8080
"""
import base64
import json
import math
import sys
import time
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
          "defaultPassword": False, "wifi": {"ssid": "Test", "ip": "127.0.0.1", "rssi": -58},
          "dns": {"blocking": True, "resumeIn": 0, "blocked": 25, "allowed": 75},
          "blocklist": {"count": 140212},
          "update": {"running": False, "lastOk": True, "lastAgo": 3600, "message": "list updated"},
          "firmware": {"latest": "0.5.2", "available": True, "installing": False, "auto": False,
                       "message": "update available"}}

# Login limit state: the different wrong passwords seen, and when logins open again.
WRONG = set()
LOCKED_UNTIL = 0.0
LOCK_SECONDS = 30


# GET /api/settings (only the MQTT part matters to the app). A made-up test broker.
SETTINGS = {"hostname": "fake-board", "mqttUri": "mqtts://broker.example.test:8883",
            "mqttUser": "board", "mqttPrefix": "sinkhole", "haDiscovery": True}


class Handler(BaseHTTPRequestHandler):
    def check_auth(self):
        """True when the request may go on; otherwise the 401/429 answer was sent."""
        global LOCKED_UNTIL, LOCK_SECONDS
        got = self.headers.get("Authorization")
        now = time.monotonic()
        if got is not None and now < LOCKED_UNTIL:
            wait = max(1, math.ceil(LOCKED_UNTIL - now))
            print(f"429 {self.path} (wait {wait} s)")
            self.send(429, b'{"ok":false,"message":"too many attempts"}', "application/json",
                      {"Retry-After": str(wait)})
            return False
        if got == EXPECTED:
            WRONG.clear()
            return True
        if got is not None:
            WRONG.add(got)
            if len(WRONG) >= 3:
                LOCKED_UNTIL, LOCK_SECONDS = now + LOCK_SECONDS, LOCK_SECONDS * 2
                WRONG.clear()
        print(f"401 {self.path} (auth {'missing' if got is None else 'wrong'})")
        self.send(401, b"authentication required", "text/plain",
                  {"WWW-Authenticate": 'Basic realm="sinkhole", charset="UTF-8"'})
        return False

    def do_GET(self):
        if self.path == "/":
            return self.send(200, PAGE.encode(), "text/html; charset=utf-8")
        if self.path.startswith("/api/"):
            if not self.check_auth():
                return
            print(f"200 {self.path}")
            if self.path == "/api/settings":
                return self.send(200, json.dumps(SETTINGS).encode(), "application/json")
            return self.send(200, json.dumps(STATUS).encode(), "application/json")
        self.send(404, b"not found", "text/plain")

    def do_POST(self):
        if self.path not in ("/api/blocking", "/api/firmware/check", "/api/firmware/install", "/api/reboot"):
            return self.send(404, b"not found", "text/plain")
        if not self.check_auth():
            return
        if self.headers.get("X-Requested-With") != "sinkhole":
            return self.send(403, b'{"ok":false,"message":"missing X-Requested-With header"}', "application/json")
        form = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode())
        fw = STATUS["firmware"]
        if self.path == "/api/firmware/check":
            print("200 POST /api/firmware/check")
            return self.send(200, b'{"ok":true,"message":"checking for updates"}', "application/json")
        if self.path == "/api/firmware/install":
            if not fw["available"]:
                return self.send(409, b'{"ok":false,"message":"no update available; check first"}', "application/json")
            fw["installing"] = True
            print("200 POST /api/firmware/install")
            return self.send(200, b'{"ok":true,"message":"installing; the device restarts when done"}', "application/json")
        if self.path == "/api/reboot":
            # A pretend restart: the update (if any) is now installed.
            if fw["installing"]:
                STATUS["version"], fw["installing"], fw["available"] = fw["latest"], False, False
            STATUS["uptime"] = 1
            print("200 POST /api/reboot")
            return self.send(200, b'{"ok":true,"message":"rebooting"}', "application/json")
        # POST /api/blocking mode=on|off|pause&minutes=N, like web_server.cpp postBlocking().
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

    def send(self, code, body, ctype, headers=None):
        self.send_response(code)
        for k, v in (headers or {}).items():
            self.send_header(k, v)
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
