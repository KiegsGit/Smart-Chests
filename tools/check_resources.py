#!/usr/bin/env python3
"""Static resource check: every model/texture/blockstate/item/loot/recipe/lang reference must resolve. Exit 1 on error."""
import json, os, re, sys
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "smartchests"
A, D = os.path.join(ROOT, "assets", NS), os.path.join(ROOT, "data", NS)
errors = []

def need(path, why):
    if not os.path.exists(path):
        errors.append(f"missing {os.path.relpath(path, ROOT)} ({why})")

def ref(r):
    ns, _, p = r.partition(":") if ":" in r else ("minecraft", "", r)
    return ns, p

def load(path):
    try:
        return json.load(open(path))
    except Exception as e:
        errors.append(f"bad JSON {os.path.relpath(path, ROOT)}: {e}")
        return {}

name = "smart_storage_terminal"
lang = load(os.path.join(A, "lang", "en_us.json"))
for key in (f"block.{NS}.{name}", f"item.{NS}.{name}", f"container.{NS}.terminal"):
    if key not in lang:
        errors.append(f"lang key missing: {key}")
for f in (os.path.join(A, "blockstates", name + ".json"), os.path.join(A, "items", name + ".json"),
          os.path.join(A, "models", "block", name + ".json"), os.path.join(D, "recipe", name + ".json"),
          os.path.join(D, "loot_table", "blocks", name + ".json"), os.path.join(D, "advancement", "recipes", name + ".json"),
          os.path.join(A, "icon.png")):
    need(f, "required file")
    if f.endswith(".json") and os.path.exists(f):
        load(f)
for root, _, files in os.walk(A):
    for f in files:
        if not f.endswith(".json") or "lang" in root:
            continue
        data = load(os.path.join(root, f))
        for tex in (data.get("textures") or {}).values():
            if tex.startswith("#"):
                continue
            ns, p = ref(tex)
            if ns == NS:
                need(os.path.join(A, "textures", p + ".png"), f"texture used by {f}")
        m = data.get("model")
        if isinstance(m, dict) and "model" in m:
            ns, p = ref(m["model"])
            if ns == NS:
                need(os.path.join(A, "models", p + ".json"), f"model used by {f}")
        for v in (data.get("variants") or {}).values():
            ns, p = ref(v["model"])
            if ns == NS:
                need(os.path.join(A, "models", p + ".json"), f"model used by {f}")
mod = load(os.path.join(ROOT, "fabric.mod.json"))
if mod:
    need(os.path.join(ROOT, mod["icon"]), "fabric.mod.json icon")
if errors:
    print("\n".join(errors)); sys.exit(1)
print("resource check OK")
