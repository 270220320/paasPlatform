# -*- coding: utf-8 -*-
"""用 security-tokens 返回的凭证做 SigV4 签名 PUT，验证固件上传的大文件传输链路。

模拟浏览器 AWS SDK 行为：UNSIGNED-PAYLOAD、Host 参与 SigV4 签名。
用法: python diag_firmware_put_test.py <size_mb>
"""
import datetime
import hashlib
import hmac
import sys
import time
import urllib.request
import urllib.error

ENDPOINT = "http://192.168.76.141:30802"
BUCKET = "file-storage-privatization"
OBJECT = "diag-fw-put-test.bin"
ACCESS_KEY = "admin"
SECRET_KEY = "olEjjLkAYi157753029"
REGION = "us-east-1"

SIZE_MB = int(sys.argv[1]) if len(sys.argv) > 1 else 200
CHUNK = b"D" * (1024 * 1024)  # 1MB


def sign_put(content_sha256="UNSIGNED-PAYLOAD"):
    now = datetime.datetime.now(datetime.timezone.utc)
    datestamp = now.strftime("%Y%m%d")
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    host = ENDPOINT.split("//", 1)[1]
    canonical_uri = f"/{BUCKET}/{OBJECT}"
    canonical_headers = f"host:{host}\nx-amz-content-sha256:{content_sha256}\nx-amz-date:{amz_date}\n"
    signed_headers = "host;x-amz-content-sha256;x-amz-date"
    canonical_request = "\n".join([
        "PUT", canonical_uri, "", canonical_headers, signed_headers, content_sha256,
    ])
    scope = f"{datestamp}/{REGION}/s3/aws4_request"
    string_to_sign = "\n".join([
        "AWS4-HMAC-SHA256", amz_date, scope,
        hashlib.sha256(canonical_request.encode()).hexdigest(),
    ])

    def _hmac(key, msg):
        return hmac.new(key, msg.encode(), hashlib.sha256).digest()

    k = _hmac(("AWS4" + SECRET_KEY).encode(), datestamp)
    k = _hmac(k, REGION)
    k = _hmac(k, "s3")
    k = _hmac(k, "aws4_request")
    signature = hmac.new(k, string_to_sign.encode(), hashlib.sha256).hexdigest()
    return {
        "Authorization": (
            f"AWS4-HMAC-SHA256 Credential={ACCESS_KEY}/{scope}, "
            f"SignedHeaders={signed_headers}, Signature={signature}"
        ),
        "x-amz-date": amz_date,
        "x-amz-content-sha256": content_sha256,
    }


def main():
    headers = sign_put()
    headers["Content-Type"] = "application/octet-stream"
    url = f"{ENDPOINT}/{BUCKET}/{OBJECT}"
    total = SIZE_MB * len(CHUNK)
    print(f"PUT {url}  size={SIZE_MB}MB  ...", flush=True)
    body = (CHUNK * SIZE_MB)
    start = time.time()
    req = urllib.request.Request(url, data=body, headers=headers, method="PUT")
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            elapsed = time.time() - start
            print(f"OK http={resp.status} time={elapsed:.1f}s speed={total/elapsed/1024/1024:.1f}MB/s")
    except urllib.error.HTTPError as e:
        elapsed = time.time() - start
        detail = e.read()[:500].decode("utf-8", "replace")
        print(f"HTTPError http={e.code} time={elapsed:.1f}s sent_after={total}B")
        print("body:", detail)
    except Exception as e:  # noqa: BLE001 - 诊断脚本需捕获所有传输层异常
        elapsed = time.time() - start
        print(f"FAILED time={elapsed:.1f}s -> {type(e).__name__}: {e}")


if __name__ == "__main__":
    main()
