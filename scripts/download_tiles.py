#!/usr/bin/env python3
"""
离线地图 XYZ 瓦片下载器（供司空2私有化部署使用）

数据源: ESRI World Imagery（免key、无水印、WGS84无偏移、国内可直连）
输出:   标准 XYZ(谷歌方案) 目录 {out}/{z}/{x}/{y}.jpg
        可直接放入 /var/lib/flighthub2/asset-map/png-tiles/ 发布

用法:
  python download_tiles.py --bbox 113.85,22.45,114.15,22.65 --zoom-min 10 --zoom-max 16 --out D:\tiles
  python download_tiles.py --bbox lon_min,lat_min,lon_max,lat_max --zoom-max 17

坐标格式: 经度,纬度,经度,纬度（西/南/东/北）
"""
import argparse
import math
import os
import sys
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

SOURCES = {
    # ESRI: URL 参数顺序是 z/y/x，无水印无偏移，免 key
    "esri": ("https://server.arcgisonline.com/ArcGIS/rest/services/"
             "World_Imagery/MapServer/tile/{z}/{y}/{x}", ".jpg"),
    # 谷歌卫星: 需代理，分辨率通常最高
    "google": ("https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}", ".jpg"),
}

HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}


def latlon_to_tile(lon: float, lat: float, z: int):
    """经纬度 -> 瓦片号（标准 XYZ/谷歌方案，原点左上）"""
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    lat_rad = math.radians(lat)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return min(max(x, 0), n - 1), min(max(y, 0), n - 1)


def tile_count(bbox, z):
    x0, y0 = latlon_to_tile(bbox[0], bbox[3], z)  # 左上角(西,北)
    x1, y1 = latlon_to_tile(bbox[2], bbox[1], z)  # 右下角(东,南)
    return x0, y0, x1, y1, (x1 - x0 + 1) * (y1 - y0 + 1)


def download_one(url: str, path: str, retries: int = 3):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return "skip"  # 断点续传
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
            if len(data) < 100:  # 空瓦片/错误响应
                return "empty"
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.write(data)
            return "ok"
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return "404"  # 该级别无数据，跳过
            time.sleep(1 + i)
        except Exception:
            time.sleep(1 + i)
    return "fail"


def main():
    ap = argparse.ArgumentParser(description="XYZ 瓦片下载器(无水印)")
    ap.add_argument("--bbox", required=True,
                    help="经度,纬度,经度,纬度 例: 113.85,22.45,114.15,22.65")
    ap.add_argument("--zoom-min", type=int, default=10)
    ap.add_argument("--zoom-max", type=int, default=16)
    ap.add_argument("--out", default=os.path.join(os.getcwd(), "tiles"))
    ap.add_argument("--source", choices=list(SOURCES), default="esri")
    ap.add_argument("--workers", type=int, default=12)
    args = ap.parse_args()

    bbox = [float(v) for v in args.bbox.split(",")]
    if len(bbox) != 4 or not (bbox[0] < bbox[2] and bbox[1] < bbox[3]):
        sys.exit("bbox 格式错误，应为: 西经,南纬,东经,北纬")

    url_tpl, ext = SOURCES[args.source]

    tasks = []
    for z in range(args.zoom_min, args.zoom_max + 1):
        x0, y0, x1, y1, cnt = tile_count(bbox, z)
        print(f"z{z}: {x1 - x0 + 1}列 x {y1 - y0 + 1}行 = {cnt} 张")
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                url = url_tpl.format(z=z, x=x, y=y)
                # 关键: 保存目录按谷歌标准 z/x/y，与 URL 参数顺序无关
                path = os.path.join(args.out, str(z), str(x), f"{y}{ext}")
                tasks.append((url, path))

    print(f"\n共 {len(tasks)} 张，输出到 {args.out}，开始下载...")
    stats = {"ok": 0, "skip": 0, "404": 0, "empty": 0, "fail": 0}
    done = 0
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futs = {pool.submit(download_one, u, p): p for u, p in tasks}
        for fut in as_completed(futs):
            r = fut.result()
            stats[r] += 1
            done += 1
            if done % 200 == 0 or done == len(tasks):
                speed = done / max(time.time() - t0, 0.1)
                print(f"  进度 {done}/{len(tasks)} ({speed:.0f} 张/秒) {stats}")

    print(f"\n完成: {stats}")
    print("上传到司空2: /var/lib/flighthub2/asset-map/png-tiles/ 后执行 publish-map.sh")
    if stats["fail"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
