import urllib.request
import urllib.error
import ssl
import datetime

url = "https://api.trycloudflare.com/tunnel"

ctx = ssl.create_default_context()

req = urllib.request.Request(url, method="POST")

try:
    with urllib.request.urlopen(req, context=ctx) as res:
        print("✅ 未被限流，通道可正常建立")
except urllib.error.HTTPError as e:
    if e.code == 429:
        retry_after = e.headers.get("Retry-After")
        if retry_after:
            seconds = int(retry_after)
            unblock_time = datetime.datetime.now() + datetime.timedelta(seconds=seconds)
            print(f"🚫 目前被 Cloudflare 限流 (error 1015)")
            print(f"⏱  剩餘封鎖時間：{seconds} 秒（約 {seconds // 60} 分 {seconds % 60} 秒）")
            print(f"🕐 預計解封時間：{unblock_time.strftime('%H:%M:%S')}")
        else:
            print(f"🚫 被限流，但無 Retry-After 標頭")
    else:
        print(f"❌ 其他錯誤：HTTP {e.code}")
except Exception as e:
    print(f"❌ 請求失敗：{e}")
