# author:
z.x.l
# contact me:
if you want installer, please contact me: zhoudaniel02@gmail.com
# version:
1.1
## abstract
this project give a convinience way to share file to another device with python http server and cloudflare quick tunnel, user can choose a folder and copy a web link to share the localhost folder. this project also generate a QR code and let mobile to scan it.
## project architecture
the architecture shows in 程式架構圖.png at root folder, the project have a main exe files to use filesharing file,based on the project own library(jdk-25 but only basic env).
fileSharing is also based on that, and it catch cloudflared's stdIO and server's stdIO and shows on frame, finally it generate QR code by zxing module. the server wrote by python and based on the python runtime env with only core part.
fileSharing will open the server on localhost and get a random port between 8000~9000
and uses HTTPS protocol and build a quick tunnel.
## resource
the logo is grab on google; this project used cloudflare 2025.11.1, jdk-25 and python 3.14.0
## env
it only work on Windows OS

## license

### cloudflared
This software downloads and uses cloudflared,
developed by Cloudflare, Inc.
Licensed under the Apache License, Version 2.0.
https://github.com/cloudflare/cloudflared

### Java Runtime (JDK 25)
This software includes Java SE Development Kit 25,
developed by Oracle Corporation.
Licensed under the GNU General Public License v2 with Classpath Exception (GPLv2+CE).
https://openjdk.org/legal/gplv2+ce.html

### Python Runtime
This software includes Python 3.14,
developed by the Python Software Foundation.
Licensed under the Python Software Foundation License Version 2.
https://www.python.org/psf/license/

### ZXing (QR Code)
Licensed under the Apache License, Version 2.0.
https://github.com/zxing/zxing

### Logo
The logo is sourced from Google Images.
Please ensure the original creator's copyright is respected.