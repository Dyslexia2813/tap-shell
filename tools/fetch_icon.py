#!/usr/bin/env python3
"""Скачивает самую большую иконку сайта (PWA-манифест, apple-touch-icon, favicon).

Использование: fetch_icon.py <куда сохранить .png>
Код выхода 1, если подходящей иконки не нашлось.
"""
import io
import json
import sys
import urllib.parse
import urllib.request
from html.parser import HTMLParser

from PIL import Image

SITE = "https://tap.teamdream.online/"
UA = ("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")

FALLBACKS = [
    "apple-touch-icon.png",
    "apple-touch-icon-precomposed.png",
    "icon-512.png",
    "icons/icon-512x512.png",
    "icons/icon-512.png",
    "android-chrome-512x512.png",
    "icon.png",
    "apple-icon.png",
    "favicon.ico",
]


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def declared_size(sizes):
    best = 0
    for part in (sizes or "").split():
        try:
            best = max(best, int(part.lower().split("x")[0]))
        except ValueError:
            pass
    return best


class HeadParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.icons = []
        self.manifest = None

    def handle_starttag(self, tag, attrs):
        if tag != "link":
            return
        a = dict(attrs)
        rel = (a.get("rel") or "").lower().split()
        href = a.get("href")
        if not href:
            return
        if "manifest" in rel:
            self.manifest = href
        elif "icon" in rel or "apple-touch-icon" in rel or "apple-touch-icon-precomposed" in rel:
            self.icons.append((declared_size(a.get("sizes")), href))


def collect_candidates():
    cands = []
    try:
        html = get(SITE).decode("utf-8", "replace")
        p = HeadParser()
        p.feed(html)
        cands += [(s, urllib.parse.urljoin(SITE, h)) for s, h in p.icons]
        if p.manifest:
            murl = urllib.parse.urljoin(SITE, p.manifest)
            try:
                m = json.loads(get(murl).decode("utf-8", "replace"))
                for icon in m.get("icons", []):
                    src = icon.get("src")
                    if src:
                        cands.append((declared_size(icon.get("sizes")),
                                      urllib.parse.urljoin(murl, src)))
            except Exception as e:
                print(f"manifest {murl}: {e}")
    except Exception as e:
        print(f"main page: {e}")
    cands += [(0, urllib.parse.urljoin(SITE, f)) for f in FALLBACKS]

    seen, out = set(), []
    for size, url in sorted(cands, key=lambda c: -c[0]):
        if url not in seen:
            seen.add(url)
            out.append(url)
    return out


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    best, best_url = None, None
    for url in collect_candidates():
        try:
            data = get(url)
            img = Image.open(io.BytesIO(data))
            img.load()
        except Exception as e:
            print(f"skip {url}: {e}")
            continue
        size = min(img.size)
        print(f"ok   {url}: {img.size[0]}x{img.size[1]}")
        if best is None or size > min(best.size):
            best, best_url = img, url
    if best is None:
        print("No usable icon found")
        return 1
    best.convert("RGBA").save(sys.argv[1], "PNG")
    print(f"Chosen: {best_url} ({best.size[0]}x{best.size[1]})")
    if min(best.size) < 192:
        print("::warning::Site icon is small; put a 512x512 icon.png in the repo root for a sharper icon")
    return 0


if __name__ == "__main__":
    sys.exit(main())
