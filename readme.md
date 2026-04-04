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
3. The application will start the server and establish a Cloudflare tunnel
4. Copy the generated URL or scan the QR code with your mobile device
5. Close the application to stop sharing

## Architecture
The application consists of two main components:

- **檔案分享.exe** — The main GUI, built with Java (Swing). It manages the server and tunnel processes, captures their standard output, displays logs in the interface, and generates the QR code using the ZXing library.
- **server.py** — A lightweight Python HTTP server that serves the selected folder over localhost on a random port between 8000 and 9000.

On startup, the application launches both processes, parses the tunnel output to extract the public URL, and generates a QR code using the ZXing module.

See `程式架構圖.png` in the root folder for a visual overview of the architecture.

## Limitations
- Cloudflare Quick Tunnel has a maximum of 200 concurrent requests
- The tunnel URL is randomly generated and changes every session
- Quick Tunnel does not support Server-Sent Events (SSE)
- Requires an active internet connection at all times
- Not intended for production or high-traffic use

## Version
1.3

## Known Issues / Bug Reports
If you encounter any bugs, please contact: zhoudaniel02@gmail.com

---

## License

### This Application
This software is provided as-is for personal and non-commercial use.  
Copyright (c) 2025 z.x.l

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

### Logo
The application logo is sourced from the internet.  
If you are the original creator and have concerns, please contact: zhoudaniel02@gmail.com
