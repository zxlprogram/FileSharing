@echo off
for /l %%i in (1,1,50) do (
    echo [%%i/50] building quick tunnel...
    curl.exe -s -w "HTTP %%{http_code}\n" -o NUL -X POST https://api.trycloudflare.com/tunnel
)
echo 完成
pause