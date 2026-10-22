import json, os, ssl, sys, zipfile, urllib.request

ROOT = r"D:\work\java\yijia-ai-chat-assistant\.resume-tmp"
os.makedirs(ROOT, exist_ok=True)

ctx = ssl.create_default_context()


def get(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "python-urllib/3"})
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as r:
        return r.read()


meta = json.loads(get("https://pypi.org/pypi/pypdf/json").decode("utf-8"))
urls = [u for u in meta["urls"] if u["packagetype"] == "bdist_wheel"]
if not urls:
    sys.exit("no wheel found")
wheel = urls[0]
print("wheel:", wheel["filename"])
blob = get(wheel["url"], timeout=180)
print("downloaded bytes:", len(blob))

whl_path = os.path.join(ROOT, wheel["filename"])
with open(whl_path, "wb") as f:
    f.write(blob)

lib = os.path.join(ROOT, "lib")
with zipfile.ZipFile(whl_path) as zf:
    zf.extractall(lib)
print("extracted to:", lib)

sys.path.insert(0, lib)
import pypdf
print("pypdf version:", pypdf.__version__)
