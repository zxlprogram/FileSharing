import http.server
import socketserver
import sys
import os
import html
import io
import time
import secrets
from urllib.parse import parse_qs

# ─────────────────────────────
# Config
# ─────────────────────────────

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
USE_PASSWORD = len(sys.argv) > 2 and sys.argv[2].lower() == "true"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else ""

VALID_TOKENS = set()

# ─────────────────────────────
# Soft lock state
# ─────────────────────────────

FAIL_COUNT = {}
BLOCK_UNTIL = {}
LAST_FAIL = {}
FAIL_RESET_TIME = 60

# ─────────────────────────────
# UI
# ─────────────────────────────

LOGIN_PAGE = """<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>請輸入密碼</title>
<script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-100 flex items-center justify-center min-h-screen">
<div class="bg-white rounded-2xl shadow-lg p-10 w-full max-w-sm">

<div class="text-center mb-6">
<span class="text-5xl">🔒</span>
<h1 class="text-2xl font-bold text-slate-800 mt-3">存取受保護</h1>
<p class="text-slate-500 text-sm mt-1">請輸入密碼以繼續</p>
</div>

{notice}

<form method="POST" action="/__login__">
<input type="password" name="password" id="pwd-input"
placeholder="密碼"
class="w-full border rounded-lg px-4 py-2 mb-4">

<button id="submit-btn" type="submit"
class="w-full bg-blue-500 text-white py-2 rounded-lg">
進入
</button>
</form>

</div>

<script>
(function() {
    var remaining = {remaining};
    if (remaining <= 0) return;

    var btn = document.getElementById('submit-btn');
    var input = document.getElementById('pwd-input');

    btn.disabled = true;
    input.disabled = true;

    function tick() {
        if (remaining > 0) {
            btn.textContent = '請等待 ' + remaining + ' 秒...';
            remaining--;
            setTimeout(tick, 1000);
        } else {
            btn.disabled = false;
            input.disabled = false;
            btn.textContent = '進入';
        }
    }
    tick();
})();
</script>

</body>
</html>"""

ERROR_BLOCK = """<div class="bg-red-50 border border-red-200 text-red-600 text-sm rounded px-4 py-2 mb-4 text-center">
密碼錯誤
</div>"""

COOLDOWN_BLOCK = """<div class="bg-amber-50 border border-amber-200 text-amber-700 text-sm rounded px-4 py-2 mb-4 text-center">
請稍後再試
</div>"""


# ─────────────────────────────
# Handler
# ─────────────────────────────

class ModernHandler(http.server.SimpleHTTPRequestHandler):

    # ---------- auth ----------
    def _get_token(self):
        cookie = self.headers.get("Cookie", "")
        for part in cookie.split(";"):
            if part.strip().startswith("fs_token="):
                return part.strip().split("=", 1)[1]
        return None

    def _is_auth(self):
        return (not USE_PASSWORD) or (self._get_token() in VALID_TOKENS)

    # ---------- cooldown ----------
    def _cooldown(self):
        ip = self.client_address[0]
        return max(0, BLOCK_UNTIL.get(ip, 0) - time.time())

    # ---------- login page ----------
    def _login_page(self, notice="", remaining=0):
        body = LOGIN_PAGE.format(
            notice=notice,
            remaining=int(remaining)
        ).encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(body)

    # ---------- POST ----------
    def do_POST(self):
        if self.path != "/__login__":
            self.send_error(405)
            return

        ip = self.client_address[0]

        if self._cooldown() > 0:
            self._login_page(COOLDOWN_BLOCK, self._cooldown())
            return

        length = int(self.headers.get("Content-Length", 0))
        data = self.rfile.read(length).decode()
        params = parse_qs(data)
        entered = params.get("password", [""])[0]

        # success
        if entered == PASSWORD:
            FAIL_COUNT.pop(ip, None)
            BLOCK_UNTIL.pop(ip, None)
            LAST_FAIL.pop(ip, None)

            token = secrets.token_hex(32)
            VALID_TOKENS.add(token)

            self.send_response(302)
            self.send_header("Location", "/")
            self.send_header("Set-Cookie", f"fs_token={token}; Path=/; HttpOnly")
            self.end_headers()
            return

        # failure timing reset
        now = time.time()
        if now - LAST_FAIL.get(ip, 0) > FAIL_RESET_TIME:
            FAIL_COUNT[ip] = 0

        LAST_FAIL[ip] = now

        # increment fail
        FAIL_COUNT[ip] = FAIL_COUNT.get(ip, 0) + 1
        c = FAIL_COUNT[ip]

        if c >= 5:
            cooldown = 60
        elif c == 4:
            cooldown = 30
        elif c == 3:
            cooldown = 10
        else:
            cooldown = 1

        BLOCK_UNTIL[ip] = max(
            BLOCK_UNTIL.get(ip, 0),
            time.time() + cooldown
        )

        self._login_page(ERROR_BLOCK, self._cooldown())

    # ---------- GET ----------
    def do_GET(self):
        if self.path == "/__login__":
            self.send_error(405)
            return

        if USE_PASSWORD and not self._is_auth():
            self._login_page("", self._cooldown())
            return

        super().do_GET()


# ─────────────────────────────
# Run
# ─────────────────────────────

socketserver.TCPServer.allow_reuse_address = True

with socketserver.TCPServer(("", PORT), ModernHandler) as httpd:
    print(f"Running on http://localhost:{PORT}")
    httpd.serve_forever()