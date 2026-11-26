"""A copy of the FishOnMC wiki for FOE+'s in-game wiki, so it reads everything from the mod and never goes online.

python3 tools/wiki_snapshot.py [resources dir]   (default: src/main/resources)

Starts at the wiki's start page and follows every link to another wiki page. For each page it keeps only its
article (the part the wiki panel shows) and its title; every picture the pages use is downloaded once, made no bigger
than 512 pixels (as the panel shows them) and saved as a PNG. Writes assets/foeplus/wiki/:
  pages/<id>.html   one page's article
  media/<name>.png  one picture (.jpg for big screenshots with nothing see-through)
  index.json        {"pages": {id: {"title", "text"}}, "media": {key: file}}  (text: the words, for searching)
Run it again before a release to bring the copy up to date.
"""
import hashlib, io, json, os, re, shutil, sys, time, urllib.parse, urllib.request

from bs4 import BeautifulSoup
from PIL import Image

BASE = "https://wiki.fishonmc.net"
UA = "FOE+ (Minecraft mod) wiki reader"
OUT = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources", "assets", "foeplus", "wiki")


def get(url, tries=3):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return 404, b""
            time.sleep(1 + i)
        except Exception:
            time.sleep(1 + i)
    return 0, b""


def page_id(href):
    """The wiki page a link goes to ("doku.php?id=pets#x" -> "pets"), or None for anything else."""
    if not href:
        return None
    u = urllib.parse.urlparse(urllib.parse.urljoin(BASE + "/", href))
    if u.netloc and u.netloc != "wiki.fishonmc.net":
        return None
    q = urllib.parse.parse_qs(u.query)
    if "do" in q or u.path.startswith("/lib/") or u.path.startswith("/_media") or u.path.startswith("/_detail"):
        return None
    if "id" in q:
        pid = q["id"][0]
    elif u.path.startswith("/doku.php/"):
        pid = u.path[len("/doku.php/"):]
    elif u.path in ("", "/", "/doku.php"):
        pid = "start"
    else:
        return None
    pid = urllib.parse.unquote(pid).strip().lower()
    return pid or None


def media_key(src):
    """What a picture is known by: its media name for the wiki's own ("fish:bass.png"), else its full address."""
    u = urllib.parse.urlparse(urllib.parse.urljoin(BASE + "/", src))
    if u.netloc in ("", "wiki.fishonmc.net"):
        q = urllib.parse.parse_qs(u.query)
        if "media" in q:
            return "media:" + q["media"][0].lower()
        for pre in ("/_media/", "/_detail/"):
            if u.path.startswith(pre):
                return "media:" + urllib.parse.unquote(u.path[len(pre):]).replace("/", ":").lower()
        return "url:" + urllib.parse.urljoin(BASE + "/", src)
    return "url:" + urllib.parse.urlunparse(u)


def media_url(key):
    if key.startswith("media:"):
        return BASE + "/lib/exe/fetch.php?media=" + urllib.parse.quote(key[6:], safe=":")
    return key[4:]


def same(img, path):
    """Whether the saved picture has exactly the pixels of img."""
    return list(Image.open(path).convert("RGBA").getdata()) == list(img.getdata())


def file_name(pid):
    return re.sub(r"[^a-z0-9_.-]", "_", pid.replace(":", "__"))


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, "pages"))
    os.makedirs(os.path.join(OUT, "media"))
    todo, seen, pages, media_keys = ["start"], {"start"}, {}, set()
    while todo:
        pid = todo.pop(0)
        status, body = get(BASE + "/doku.php?id=" + urllib.parse.quote(pid, safe=":"))
        if status != 200:
            print("skip", pid, status)
            continue
        soup = BeautifulSoup(body, "html.parser")
        art = soup.find(id="main-content") or soup.find(id="dokuwiki__content") or soup.find("article")
        if art is None or "This topic does not exist yet" in art.get_text():
            print("empty", pid)
            continue
        for a in art.find_all("a", href=True):
            nxt = page_id(a["href"])
            if nxt and nxt not in seen:
                seen.add(nxt)
                todo.append(nxt)
            if "/lib/exe/fetch.php" in a["href"] or "/lib/exe/detail.php" in a["href"] or "/_media/" in a["href"] or "/_detail/" in a["href"]:
                media_keys.add(media_key(a["href"]))
        for img in art.find_all("img", src=True):
            media_keys.add(media_key(img["src"]))
        h1 = art.find("h1")
        h = h1 or art.find("h2")
        # no heading: its name from its id ("location:lake_biwa" -> "Lake Biwa")
        title = h.get_text(" ", strip=True) if h else pid.split(":")[-1].replace("_", " ").title()
        text = re.sub(r"\s+", " ", art.get_text(" ", strip=True))
        with open(os.path.join(OUT, "pages", file_name(pid) + ".html"), "w", encoding="utf-8") as f:
            f.write(str(art))
        pages[pid] = {"title": title, "text": text[:20000]}
        print("page", pid, len(str(art)))
    media = {}
    for key in sorted(media_keys):
        status, data = get(media_url(key))
        if status != 200 or not data:
            print("no picture", key, status)
            continue
        try:
            img = Image.open(io.BytesIO(data))
            img.seek(0)
            img = img.convert("RGBA")
        except Exception as e:
            print("not a picture", key, e)
            continue
        img.thumbnail((512, 512), Image.LANCZOS)
        name = hashlib.sha1(key.encode()).hexdigest()[:16]
        colours = img.getcolors(256)
        if colours is not None:
            # few colours (most pictures: items, icons): exactly as they are, in a small palette PNG
            name += ".png"
            img.quantize(colors=len(colours), method=Image.FASTOCTREE, dither=Image.NONE).save(os.path.join(OUT, "media", name), optimize=True)
            if not same(img, os.path.join(OUT, "media", name)):
                img.save(os.path.join(OUT, "media", name), optimize=True)
        elif img.getchannel("A").getextrema()[0] == 255:
            # a photo-like screenshot with nothing see-through: a JPEG, a fraction of the size
            name += ".jpg"
            img.convert("RGB").save(os.path.join(OUT, "media", name), quality=85, optimize=True)
        else:
            name += ".png"
            img.save(os.path.join(OUT, "media", name), optimize=True)
        media[key] = name
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"made": time.strftime("%Y-%m-%d"), "pages": pages, "media": media}, f, ensure_ascii=False, separators=(",", ":"))
    print(len(pages), "pages,", len(media), "pictures")


if __name__ == "__main__":
    main()
