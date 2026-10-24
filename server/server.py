#!/usr/bin/env python3
"""
Petit serveur "Pâtée du chat".

- Garde une copie des repas et programmes envoyés par le téléphone (fichier JSON).
- Sert une page web pour consulter / encoder les repas depuis un PC.
- Aucune dépendance : Python 3.8+ suffit.

Réglages (variables d'environnement, tous facultatifs) :
  PATE_PORT   port d'écoute            (défaut 8765)
  PATE_HOST   adresse d'écoute         (défaut 0.0.0.0)
  PATE_TOKEN  code secret à exiger     (défaut : aucun)
  PATE_DATA   fichier de données       (défaut ./data/state.json)
"""
import hmac
import json
import os
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = Path(os.environ.get("PATE_DATA", HERE / "data" / "state.json"))
TOKEN = os.environ.get("PATE_TOKEN", "")
PORT = int(os.environ.get("PATE_PORT", "8765"))
HOST = os.environ.get("PATE_HOST", "0.0.0.0")
INDEX = HERE / "static" / "index.html"
MAX_BODY = 5_000_000

LOCK = threading.Lock()


def load_state():
    try:
        with open(DATA, "r", encoding="utf-8") as f:
            s = json.load(f)
        return {
            "plans": list(s.get("plans", [])),
            "entries": list(s.get("entries", [])),
            "cats": list(s.get("cats", [])),
        }
    except (FileNotFoundError, ValueError):
        return {"plans": [], "entries": [], "cats": []}


def save_state(state):
    DATA.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=str(DATA.parent), suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(state, f, ensure_ascii=False)
        os.replace(tmp, DATA)
    except Exception:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def valid_item(x):
    return (
        isinstance(x, dict)
        and isinstance(x.get("id"), str)
        and x["id"] != ""
        and isinstance(x.get("updatedAt"), (int, float))
        and not isinstance(x.get("updatedAt"), bool)
    )


def merge_lists(current, incoming):
    """Fusionne par id ; l'élément avec le updatedAt le plus récent gagne."""
    by_id = {x["id"]: x for x in current if valid_item(x)}
    for x in incoming:
        if not valid_item(x):
            continue
        old = by_id.get(x["id"])
        if old is None or x["updatedAt"] > old["updatedAt"]:
            by_id[x["id"]] = x
    return list(by_id.values())


class Handler(BaseHTTPRequestHandler):
    server_version = "PateServer/1.0"

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)

    # --- utilitaires ---
    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        if isinstance(body, (dict, list)):
            body = json.dumps(body, ensure_ascii=False).encode("utf-8")
        elif isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self):
        if not TOKEN:
            return True
        given = self.headers.get("X-Token", "")
        return hmac.compare_digest(given.encode("utf-8"), TOKEN.encode("utf-8"))

    # --- routes ---
    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/index.html"):
            try:
                self._send(200, INDEX.read_bytes(), "text/html; charset=utf-8")
            except FileNotFoundError:
                self._send(500, {"error": "static/index.html introuvable"})
        elif path == "/api/health":
            self._send(200, {"ok": True, "tokenRequired": bool(TOKEN)})
        elif path == "/api/state":
            if not self._authorized():
                return self._send(401, {"error": "unauthorized"})
            with LOCK:
                self._send(200, load_state())
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        if path != "/api/sync":
            return self._send(404, {"error": "not found"})
        if not self._authorized():
            return self._send(401, {"error": "unauthorized"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self._send(400, {"error": "bad length"})
        if length <= 0 or length > MAX_BODY:
            return self._send(400, {"error": "bad body size"})
        try:
            incoming = json.loads(self.rfile.read(length).decode("utf-8"))
            plans_in = incoming.get("plans", [])
            entries_in = incoming.get("entries", [])
            cats_in = incoming.get("cats", [])
            if not (isinstance(plans_in, list) and isinstance(entries_in, list) and isinstance(cats_in, list)):
                raise ValueError("plans/entries/cats must be lists")
        except (ValueError, AttributeError):
            return self._send(400, {"error": "bad json"})

        with LOCK:
            state = load_state()
            state["plans"] = merge_lists(state["plans"], plans_in)
            state["entries"] = merge_lists(state["entries"], entries_in)
            state["cats"] = merge_lists(state["cats"], cats_in)
            save_state(state)
            self._send(200, state)


def main():
    DATA.parent.mkdir(parents=True, exist_ok=True)
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Serveur pâtée prêt sur http://{HOST}:{PORT}  (données : {DATA})", flush=True)
    if not TOKEN:
        print("Attention : aucun PATE_TOKEN défini, l'API est ouverte à tout le réseau local.", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
