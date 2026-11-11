"""Static harness for the shell — no Keycloak, no BFF, no database.

Serves the real shell files from src/main/resources/static, stubs the BFF
answers (/bff/me, /bff/mfes) and mounts the TEST-only demo micro frontend at
/mfe/demo/ with its API at /api/demo/. Used for headless screenshots of the
shell, the design guide and the mount contract:

    python3 -I src/test/e2e/serve.py 8796
    firefox --headless --screenshot out.png --window-size=1440,900 http://localhost:8796/demo

Projects mode: pass the projects service's static MFE directory as the second argument
to mount the REAL projects micro frontend at /mfe/projects/, with its API answered from
projects_fixture.py:

    python3 -I src/test/e2e/serve.py 8796 ../homelabs-projects/app/src/main/resources/static/mfe

Every HTML answer carries one image that loads after HOLD_SECONDS, which holds
the page's load event until the shell's module imports and fetches are done
(the headless screenshot is taken at the load event).
"""
import json
import pathlib
import sys
import time
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import projects_fixture  # noqa: E402
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = pathlib.Path(__file__).resolve().parents[2]
STATIC = ROOT / "main" / "resources" / "static"
DEMO = pathlib.Path(__file__).resolve().parent / "demo"
HOLD_SECONDS = 2.0

ME = {"sub": "sub-demo", "name": "di.mueller", "mfes": ["demo", "projects"]}
MFES = [
    {"id": "demo", "element": "hl-demo", "entry": "/mfe/demo/demo.js",
     "nav": {"label": {"de": "Demo", "en": "Demo"}, "icon": "list", "order": 20}},
]
ITEMS = [
    {"ref": "HA-1", "title": "BFF-Grundgerüst", "status": "done", "owner": "di.mueller", "due": "11.11."},
    {"ref": "HA-2", "title": "Shell + Design-System", "status": "in_progress", "owner": "di.mueller", "due": "14.11."},
    {"ref": "HA-3", "title": "Registry, Gate und Proxy", "status": "todo", "owner": None, "due": "20.11."},
    {"ref": "HA-4", "title": "Pipeline + Deploy", "status": "blocked", "owner": None, "due": None},
]
PROJECTS_MFE = None
PROJECTS_ENTRY = {"id": "projects", "element": "hl-projects", "entry": "/mfe/projects/projects.js",
                  "nav": {"label": {"de": "Projekte", "en": "Projects"}, "icon": "folder", "order": 10}}
HOLD_TAG = b'<img src="/__hold" alt="" hidden>'
# The app's policy (SecurityConfiguration.CONTENT_SECURITY_POLICY) — the screenshots
# prove the shell, the guide and the demo MFE run under it.
CSP = ("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
       "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://auth.homelabs.de")


class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def end_headers(self):
        self.send_header("Content-Security-Policy", CSP)
        super().end_headers()

    def send_json(self, value):
        body = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_file(self, path, content_type):
        body = path.read_bytes()
        if content_type == "text/html":
            body = body.replace(b"</body>", HOLD_TAG + b"</body>")
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/__hold":
            time.sleep(HOLD_SECONDS)
            self.send_response(200)
            self.send_header("Content-Type", "image/svg+xml")
            self.end_headers()
            self.wfile.write(b'<svg xmlns="http://www.w3.org/2000/svg"/>')
        elif path == "/bff/me":
            self.send_json(ME)
        elif path == "/bff/mfes":
            self.send_json(([PROJECTS_ENTRY] if PROJECTS_MFE else []) + MFES)
        elif PROJECTS_MFE and path.startswith("/api/projects/"):
            parts = urlsplit(self.path)
            body = projects_fixture.answer(parts.path[len("/api/projects"):], parse_qs(parts.query))
            if body is None:
                self.send_response(404)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"error":"not found"}')
            else:
                self.send_json(body)
        elif PROJECTS_MFE and path.startswith("/mfe/projects/"):
            file = (PROJECTS_MFE / path[len("/mfe/projects/"):]).resolve()
            if PROJECTS_MFE not in file.parents or not file.is_file():
                self.send_error(404)
                return
            self.send_file(file, "text/javascript")
        elif path == "/api/demo/items":
            self.send_json(ITEMS)
        elif path == "/mfe/demo/demo.js":
            self.send_file(DEMO / "demo.js", "text/javascript")
        elif path.startswith("/shell/"):
            file = (STATIC / path.lstrip("/")).resolve()
            if STATIC not in file.parents or not file.is_file():
                self.send_error(404)
                return
            types = {".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".json": "application/json"}
            self.send_file(file, types.get(file.suffix, "application/octet-stream"))
        else:
            self.send_file(STATIC / "index.html", "text/html")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8796
    if len(sys.argv) > 2:
        PROJECTS_MFE = pathlib.Path(sys.argv[2]).resolve()
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
