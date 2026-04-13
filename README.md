# File Sharing
> Share files to any device with a single URL and QR code — no configuration needed.

## Author
z.x.l

## Contact
For installer requests or questions: zhoudaniel02@gmail.com  
GitHub: https://github.com/zxlprogram

---

## Abstract
File Sharing provides a convenient way to share a local folder with any device on the internet. The application starts a Python HTTP server on localhost, then creates a Cloudflare Quick Tunnel to expose it publicly. A URL and QR code are generated automatically so mobile devices can access the shared folder by scanning the code.

## Requirements
- Windows x64 (64-bit)
- Internet connection (required for Cloudflare tunnel)

## Installation
1. Download `FileSharing_Installer.exe` from the releases page
2. Run the installer and follow the on-screen steps
3. Launch `檔案分享.exe` from the desktop shortcut or Start Menu

## Usage
1. Launch `檔案分享.exe`
2. Click **Select Folder** and choose the folder you want to share
3. (Optional) Check **啟用密碼保護** and enter a password to restrict access. Visitors will be required to enter the password before they can browse any files
4. A loading screen is displayed while the server and tunnel are initializing
5. Once the tunnel is established, copy the generated URL or scan the QR code with your mobile device
6. Close the application to stop sharing

## Architecture
The application consists of two main components:

- **檔案分享.exe** — The main GUI, built with Java (Swing). It manages the server and tunnel processes, captures their standard output, and generates the QR code using the ZXing library. The interface supports two display modes switchable at runtime.
- **server.py** — A lightweight Python HTTP server that serves the selected folder over localhost on a random port between 8000 and 8999. When password protection is enabled, the server issues a session token via cookie upon successful login and enforces a per-IP cooldown after each failed attempt.

On startup, the application shows a loading screen while both processes initialize. Once the tunnel is ready, the interface switches automatically to the main view.

See `程式架構圖.png` in the root folder for a visual overview of the architecture.

## Interface Modes
The toolbar at the top of the window provides the following controls:

- **Copy URL & Show QR Code** — Copies the tunnel URL to the clipboard and opens a QR code window
- **Switch Mode** — Toggles between Console Mode and UI Mode

### Console Mode
Displays the raw standard output of both the Python server and the Cloudflare tunnel process as scrollable black-on-green terminal logs.

### UI Mode
Provides a structured dashboard view:
- **Left panel** — Shows the total visitor count and a file tree rooted at the shared folder. Selecting any file or folder displays how many times it has been accessed.
- **Right panel** — Shows the current tunnel connection state (`connecting`, `connected`,`too much request` or `network error`) as a scrollable text field. it will show the cold-down time when cloudflare got too much request from your IP(maybe 30~40 request)

## Password Protection
When password protection is enabled, the server serves a login page to any unauthenticated visitor. A session token is issued via an `HttpOnly` cookie upon successful login. The token remains valid for the duration of the current session and is invalidated when the application is closed.

To prevent brute-force attempts, each failed login triggers a per-IP cooldown. During the cooldown period the login form is disabled and a countdown timer is shown.

## Limitations
- Cloudflare Quick Tunnel has a maximum of 200 concurrent requests
- The tunnel URL is randomly generated and changes every session
- Quick Tunnel does not support Server-Sent Events (SSE)
- Requires an active internet connection at all times
- Not intended for production or high-traffic use

## Version
1.5.1

## Known Issues / Bug Reports
If you encounter any bugs, please contact: zhoudaniel02@gmail.com

---

## License

### This Application
This software is provided as-is for personal and non-commercial use.  
Copyright (c) 2025–2026 z.x.l

### cloudflared 2025.11.1
This software uses cloudflared, developed by Cloudflare, Inc.  
Licensed under the Apache License, Version 2.0.  
Source: https://github.com/cloudflare/cloudflared  
License: https://www.apache.org/licenses/LICENSE-2.0

### OpenJDK 21 (Eclipse Temurin)
This software is distributed with a custom JRE built from Eclipse Temurin 21.  
Copyright (c) Eclipse Foundation and contributors.  
Licensed under the GNU General Public License v2 with Classpath Exception.  
Source: https://adoptium.net  
License: https://openjdk.org/legal/gplv2+ce.html

### Python 3.14.0 (Embeddable)
This software is distributed with the Python embeddable runtime.  
Copyright (c) Python Software Foundation.  
Licensed under the PSF License Agreement.  
Source: https://www.python.org  
License: https://www.python.org/psf/license/

### ZXing 3.5.4
This software uses the ZXing library for QR code generation.  
Copyright (c) ZXing authors.  
Licensed under the Apache License, Version 2.0.  
Source: https://github.com/zxing/zxing  
License: https://www.apache.org/licenses/LICENSE-2.0

### Tailwind CSS
This software's server UI loads Tailwind CSS from a CDN at runtime.  
Copyright (c) Tailwind Labs, Inc.  
Licensed under the MIT License.  
Source: https://tailwindcss.com  
License: https://github.com/tailwindlabs/tailwindcss/blob/master/LICENSE

### Logo
The application logo is sourced from the internet.  
If you are the original creator and have concerns, please contact: zhoudaniel02@gmail.com
