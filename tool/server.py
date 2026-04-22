import http.server
import socketserver
import sys
import os
import html
import io
import time

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
USE_PASSWORD = len(sys.argv) > 2 and sys.argv[2].lower() == "true"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else ""

import secrets
VALID_TOKENS = set()

# ── 三層懲罰系統 ────────────────────────────────────────────────────
# 🟢 第一層（1~2次）：只顯示錯誤，不冷卻
# 🟡 第二層（3~4次）：固定短冷卻 SOFT_COOLDOWN 秒，每次錯誤重新計時
# 🔴 第三層（≥5次）：固定上限冷卻 HARD_COOLDOWN 秒，不再增加
SOFT_COOLDOWN = 5    # 秒（第二層）
HARD_COOLDOWN = 30   # 秒（第三層上限）
SOFT_THRESHOLD = 3   # 達到此次數進入第二層
HARD_THRESHOLD = 5   # 達到此次數進入第三層

FAIL_RECORDS = {}  # { ip: {"count": int, "failed_at": float | None} }

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
            <div class="relative mb-4">
                <input
                    type="password"
                    name="password"
                    id="pwd-input"
                    placeholder="密碼"
                    autofocus
                    class="w-full border border-slate-300 rounded-lg px-4 py-2 pr-11 focus:outline-none focus:ring-2 focus:ring-blue-400 text-slate-800"
                />
                <button
                    type="button"
                    id="toggle-vis"
                    tabindex="-1"
                    aria-label="顯示／隱藏密碼"
                    class="absolute inset-y-0 right-0 flex items-center px-3 text-slate-400 hover:text-slate-600 focus:outline-none"
                >
                    <!-- eye open -->
                    <svg id="icon-eye" xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                        <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.477 0 8.268 2.943 9.542 7-1.274 4.057-5.065 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                    </svg>
                    <!-- eye off -->
                    <svg id="icon-eye-off" xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 hidden" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.477 0-8.268-2.943-9.542-7a9.97 9.97 0 012.516-4.042M9.88 9.88a3 3 0 104.243 4.243M3 3l18 18"/>
                    </svg>
                </button>
            </div>
            <button
                id="submit-btn"
                type="submit"
                class="w-full bg-blue-500 hover:bg-blue-600 text-white font-semibold py-2 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
            >
                進入
            </button>
        </form>
    </div>
    <script>
    // ── 眼睛切換 ──────────────────────────────────────────────────
    (function() {{
        var btn    = document.getElementById('toggle-vis');
        var input  = document.getElementById('pwd-input');
        var eyeOn  = document.getElementById('icon-eye');
        var eyeOff = document.getElementById('icon-eye-off');
        btn.addEventListener('click', function() {{
            var showing = input.type === 'text';
            input.type  = showing ? 'password' : 'text';
            eyeOn.classList.toggle('hidden',  !showing);
            eyeOff.classList.toggle('hidden',  showing);
        }});
    }})();
    // ── 倒數計時 ──────────────────────────────────────────────────
    (function() {{
        var remaining = {remaining};
        if (remaining <= 0) return;
        var btn = document.getElementById('submit-btn');
        var input = document.getElementById('pwd-input');
        btn.disabled = true;
        input.disabled = true;
        function tick() {{
            if (remaining > 0) {{
                btn.textContent = '請等待 ' + remaining + ' 秒...';
                remaining--;
                setTimeout(tick, 1000);
            }} else {{
                btn.disabled = false;
                input.disabled = false;
                btn.textContent = '進入';
                input.focus();
            }}
        }}
        tick();
    }})();
    </script>
</body>
</html>"""

ERROR_BLOCK = """<div class="bg-red-50 border border-red-200 text-red-600 text-sm rounded-lg px-4 py-2 mb-4 text-center">
    密碼錯誤，請再試一次。
</div>"""

COOLDOWN_BLOCK = """<div class="bg-amber-50 border border-amber-200 text-amber-700 text-sm rounded-lg px-4 py-2 mb-4 text-center">
    密碼錯誤，請稍後再試。
</div>"""


class ModernHandler(http.server.SimpleHTTPRequestHandler):

    def _get_token(self):
        cookie_header = self.headers.get("Cookie", "")
        for part in cookie_header.split(";"):
            part = part.strip()
            if part.startswith("fs_token="):
                return part[len("fs_token="):]
        return None

    def _is_authenticated(self):
        if not USE_PASSWORD:
            return True
        return self._get_token() in VALID_TOKENS

    # ── 取得此 IP 的失敗記錄，不存在則初始化 ────────────────────────
    def _fail_record(self):
        ip = self.client_address[0]
        if ip not in FAIL_RECORDS:
            FAIL_RECORDS[ip] = {"count": 0, "failed_at": None}
        return FAIL_RECORDS[ip]

    # ── 判斷目前所在層級，回傳應套用的 cooldown 秒數 ────────────────
    def _tier_cooldown(self, count):
        if count >= HARD_THRESHOLD:
            return HARD_COOLDOWN   # 🔴 第三層
        elif count >= SOFT_THRESHOLD:
            return SOFT_COOLDOWN   # 🟡 第二層
        else:
            return 0               # 🟢 第一層

    # ── 取得此 IP 剩餘 cooldown 秒數（0 表示不在冷卻中）────────────
    def _cooldown_remaining(self):
        rec = self._fail_record()
        cooldown = self._tier_cooldown(rec["count"])
        if cooldown == 0 or rec["failed_at"] is None:
            return 0
        elapsed = time.time() - rec["failed_at"]
        remaining = cooldown - elapsed
        return max(0, remaining)

    def _serve_login(self, show_error=False, show_cooldown=False):
        remaining = self._cooldown_remaining()
        if show_cooldown or remaining > 0:
            notice = COOLDOWN_BLOCK
        elif show_error:
            notice = ERROR_BLOCK
        else:
            notice = ""
        # 剩餘秒數傳給 JS（無條件進位，讓 UI 和後端同步）
        remaining_int = int(remaining) + (1 if remaining % 1 > 0 else 0)
        body = LOGIN_PAGE.format(notice=notice, remaining=remaining_int).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path == "/__login__":
            ip = self.client_address[0]

            # ── 若還在 cooldown 中，直接拒絕 ──────────────────────────
            if self._cooldown_remaining() > 0:
                self._serve_login(show_cooldown=True)
                return

            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length).decode("utf-8")
            from urllib.parse import parse_qs
            params = parse_qs(body)
            entered = params.get("password", [""])[0]

            if entered == PASSWORD:
                # 登入成功，清除失敗記錄
                FAIL_RECORDS.pop(ip, None)
                token = secrets.token_hex(32)
                VALID_TOKENS.add(token)
                self.send_response(302)
                self.send_header("Location", "/")
                self.send_header("Set-Cookie", f"fs_token={token}; HttpOnly; Path=/")
                self.end_headers()
            else:
                # 登入失敗：更新計數與時間戳
                rec = self._fail_record()
                rec["count"] += 1
                count = rec["count"]
                # 🟢 第一層：不記錄時間，不冷卻
                # 🟡 第二層：每次重新計時（打斷節奏）
                # 🔴 第三層：已達上限，同樣重新計時（但秒數固定不再增加）
                if count >= SOFT_THRESHOLD:
                    rec["failed_at"] = time.time()
                self._serve_login(show_error=(count < SOFT_THRESHOLD),
                                  show_cooldown=(count >= SOFT_THRESHOLD))
        else:
            self.send_error(405, "Method Not Allowed")

    def do_GET(self):
        if USE_PASSWORD and self.path != "/__login__" and not self._is_authenticated():
            self._serve_login()
            return
        super().do_GET()

    def do_HEAD(self):
        if USE_PASSWORD and not self._is_authenticated():
            self._serve_login()
            return
        super().do_HEAD()

    def list_directory(self, path):
        try:
            entries = os.listdir(path)
        except OSError:
            self.send_error(404, "No permission to list directory")
            return None

        entries.sort(key=lambda a: a.lower())
        f = io.BytesIO()
        displaypath = html.escape(self.path)
        shell = f"""<!DOCTYPE html>
<html lang="zh-TW">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Index of {displaypath}</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body {{ background: #f1f5f9; }}
        .file-card {{ transition: all 0.2s; }}
        .file-card:hover {{ transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.05); }}
    </style>
</head>
<body class="p-4 md:p-10">
    <div class="max-w-5xl mx-auto">
        <header class="mb-8 border-b border-slate-300 pb-4 flex items-center justify-between">
            <div>
                <h1 class="text-3xl font-bold text-slate-800">📂 目錄索引</h1>
                <p class="text-slate-500 mt-2 font-mono text-sm">{displaypath}</p>
            </div>
            {"<span class='text-xs bg-green-100 text-green-700 border border-green-300 px-3 py-1 rounded-full font-semibold'>🔒 已驗證</span>" if USE_PASSWORD else ""}
        </header>
        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            <a href=".." class="file-card bg-white p-4 rounded-xl border border-slate-200 flex items-center space-x-3 hover:border-blue-400">
                <span class="text-2xl">⬆️</span>
                <span class="font-medium text-blue-600">回上一層</span>
            </a>
"""
        for name in entries:
            fullname = os.path.join(path, name)
            displayname = linkname = name
            icon = "📄"
            if os.path.isdir(fullname):
                displayname = name + "/"
                linkname = name + "/"
                icon = "📁"
            if name.startswith('.'):
                continue

            shell += f"""
            <a href="{html.escape(linkname)}" class="file-card bg-white p-4 rounded-xl border border-slate-200 flex items-center space-x-3 hover:border-blue-500">
                <span class="text-2xl">{icon}</span>
                <span class="font-medium text-slate-700 truncate" title="{html.escape(displayname)}">{html.escape(displayname)}</span>
            </a>"""

        shell += """
        </div>
        <footer class="mt-12 text-center text-slate-400 text-xs">
            Python http.server · Modern UI Edition
        </footer>
    </div>
</body>
</html>"""

        encoded = shell.encode("utf-8", "ignore")
        f.write(encoded)
        f.seek(0)
        self.send_response(200)
        self.send_header("Content-type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        return f


socketserver.TCPServer.allow_reuse_address = True

with socketserver.TCPServer(("", PORT), ModernHandler) as httpd:
    print(f"Server is running! Open: http://localhost:{PORT}/")
    if USE_PASSWORD:
        print(f"Password protection: ENABLED")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nExit.")