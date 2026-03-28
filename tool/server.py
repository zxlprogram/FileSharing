import http.server
import socketserver
import sys
import os
import html
import io

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000

class ModernHandler(http.server.SimpleHTTPRequestHandler):
    def list_directory(self, path):
        try:
            list = os.listdir(path)
        except OSError:
            self.send_error(404, "No permission to list directory")
            return None
            
        list.sort(key=lambda a: a.lower())
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
                <header class="mb-8 border-b border-slate-300 pb-4">
                    <h1 class="text-3xl font-bold text-slate-800">📂 目錄索引</h1>
                    <p class="text-slate-500 mt-2 font-mono text-sm">{displaypath}</p>
                </header>
                
                <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                    <a href=".." class="file-card bg-white p-4 rounded-xl border border-slate-200 flex items-center space-x-3 hover:border-blue-400">
                        <span class="text-2xl">⬆️</span>
                        <span class="font-medium text-blue-600">回上一層</span>
                    </a>
        """
        for name in list:
            fullname = os.path.join(path, name)
            displayname = linkname = name
            icon = "📄"
            if os.path.isdir(fullname):
                displayname = name + "/"
                linkname = name + "/"
                icon = "📁"
            if name.startswith('.'): continue

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
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nExit.")