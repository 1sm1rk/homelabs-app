"""Fixture answers of the projects service's API, for the harness's projects mode.

Shapes follow the service's wire records (snake_case); the values are invented. Only
GET routes the projects micro frontend reads are answered.
"""
import re
from datetime import datetime, timedelta, timezone

NOW = datetime.now(timezone.utc)


def ago(**delta):
    return (NOW - timedelta(**delta)).isoformat().replace("+00:00", "Z")


ME = "di.mueller"

PROJECTS = [
    {"id": "p-ha", "reference": "HA", "name": "homelabs-app", "status": "active", "owner_name": ME,
     "updated_at": ago(minutes=12), "excerpt": "Micro-Frontend-App", "labels": ["frontend", "mfe"]},
    {"id": "p-hp", "reference": "HP", "name": "homelabs-projects", "status": "active", "owner_name": ME,
     "updated_at": ago(hours=3), "excerpt": "PM-Backend", "labels": ["backend"]},
    {"id": "p-hd", "reference": "HD", "name": "homelabs-dashboard", "status": "deactivated", "owner_name": ME,
     "updated_at": ago(days=8), "excerpt": "Web-Frontend", "labels": ["frontend"]},
]

PROJECT = {
    "id": "p-ha", "short_code": "HA", "name": "homelabs-app",
    "description_html": "<p>Micro-Frontend-App unter <code>app.homelabs.de</code>: ein Token-Handler-BFF und eine "
                        "Shell in plain JS. Die Dienste liefern ihre MFEs selbst aus.</p>"
                        "<ul><li>Stufe 1: Rolle pro MFE</li><li>Stufe 2: bleibt im Backend</li></ul>",
    "description_text": "", "status": "active", "owner_sub": "sub-dm", "owner_name": ME,
    "created_at": ago(hours=6), "updated_at": ago(minutes=12),
    "labels": [{"id": "l1", "name": "frontend", "color": None}, {"id": "l2", "name": "mfe", "color": None}],
}


def item(ref, title, status, order, assignee=None, due=None, wp=None):
    return {"id": f"id-{ref}", "reference": ref, "title": title, "status": status, "sort_order": order,
            "assignee_sub": "sub-dm" if assignee else None, "assignee_name": assignee, "due_date": due,
            "excerpt": "", "work_package_id": f"id-{wp}" if wp else None, "work_package_reference": wp}


WORK_PACKAGES = [
    item("HA-1", "Repo-Bootstrap + BFF-Grundgerüst", "done", 1, ME),
    item("HA-2", "Shell + Design-System", "done", 2, ME),
    item("HA-3", "MFE-Registry, Gate und Proxy", "done", 3, ME),
    item("HA-4", "Pipeline + Deploy", "todo", 4, None, "2026-11-20"),
]
TASKS = [
    item("HA-5", "Realm-Client app anlegen", "done", 1, ME, None, "HA-1"),
    item("HA-6", "Session-Persistenz beweisen", "done", 2, ME, None, "HA-1"),
    item("HA-7", "Design-Guide unter /design", "done", 1, ME, None, "HA-2"),
    item("HA-8", "CSP im Harness prüfen", "in_progress", 1, ME, "2026-11-12", "HA-3"),
    item("HA-9", "Caddy-Host app.homelabs.de", "blocked", 1, None, "2026-11-18", "HA-4"),
    item("HA-10", "Lose Notiz ohne Paket", "todo", 1),
]

COMMENTS = [
    {"id": "c1", "entity_type": "project", "entity_id": "p-ha", "author_sub": "sub-dm", "author_name": ME,
     "content": "Design sieht gut aus, weiter gehts", "created_at": ago(minutes=40)},
    {"id": "c2", "entity_type": "project", "entity_id": "p-ha", "author_sub": "sub-dm", "author_name": ME,
     "content": "Ruhig und dicht, bitte — die Grundstruktur bleibt.", "created_at": ago(hours=5)},
]

ACTIVITIES = [
    {"id": "a1", "entity_type": "work_package", "entity_id": "id-HA-3", "actor_sub": "sub-dm", "actor_name": ME,
     "source": "claude-code", "event_type": "status_changed", "detail": "in_progress → done", "created_at": ago(minutes=12)},
    {"id": "a2", "entity_type": "project", "entity_id": "p-ha", "actor_sub": "sub-dm", "actor_name": ME,
     "source": None, "event_type": "created", "detail": None, "created_at": ago(hours=6)},
]

ATTACHMENTS = [
    {"id": "f1", "entity_type": "project", "entity_id": "p-ha", "filename": "hd-review.png", "mime_type": "image/png",
     "uploaded_by_sub": "sub-dm", "uploaded_by_name": ME, "created_at": ago(hours=4)},
]


def full(summary, kind):
    record = dict(summary)
    record.update({"project_id": "p-ha", "project_short_code": "HA",
                   "description_html": "<p>Volle Beschreibung von <strong>" + summary["reference"] + "</strong>.</p>",
                   "description_text": "", "created_at": ago(hours=6), "updated_at": ago(minutes=30),
                   "labels": [{"id": "l3", "name": "dogfood", "color": None}]})
    for key in ("excerpt", "sort_order"):
        record.pop(key, None)
    if kind == "work_package":
        record.pop("work_package_id")
        record.pop("work_package_reference")
    return record


def answer(path, query):
    """The fixture body for a GET below /api of the projects service, or None for 404."""
    if path == "/projects":
        status = query.get("status")
        q = (query.get("q") or "").lower()
        return [p for p in PROJECTS if (not status or p["status"] in status)
                and (not q or q in p["name"].lower() or q in p["reference"].lower())]
    if path == "/projects/HA":
        return PROJECT
    if path == "/work-packages":
        return WORK_PACKAGES if query.get("project") == ["HA"] else []
    if path == "/tasks":
        return TASKS if query.get("project") == ["HA"] else []
    if path == "/comments":
        return COMMENTS if query.get("parent") == ["project:HA"] else COMMENTS[:1]
    if path == "/activities":
        return ACTIVITIES
    if path == "/attachments":
        return ATTACHMENTS
    match = re.fullmatch(r"/(work-packages|tasks)/(HA-\d+)", path)
    if match:
        kind, ref = match.groups()
        pool = WORK_PACKAGES if kind == "work-packages" else TASKS
        found = next((i for i in pool if i["reference"] == ref), None)
        return full(found, "work_package" if kind == "work-packages" else "task") if found else None
    return None
