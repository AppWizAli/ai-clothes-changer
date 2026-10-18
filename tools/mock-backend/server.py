#!/usr/bin/env python3
"""
Mock try-on backend for testing the Android app's pipeline. NOT an AI service.

It implements docs/BACKEND_API.md so the app's upload, polling, cancel, retry, idempotency and error
handling can be exercised end to end without paid credentials. The "result" is a watermarked composite
of the two uploaded images, so it can never be mistaken for a real generation.

Usage:  python3 server.py [--port 8099] [--mode ok|fail|rate-limit|server-error|junk|slow]
Requires Pillow for the composite (pip install pillow); without it a plain copy of the person image is returned.
"""
import argparse
import io
import json
import threading
import time
import uuid
from email.parser import BytesParser
from email.policy import HTTP
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover
    Image = None

JOBS = {}          # remote id -> job dict
BY_KEY = {}        # idempotency key -> remote id (proves retries never create duplicates)
LOCK = threading.Lock()
ARGS = None
STATS = {"submits": 0, "dedupes": 0}


def parse_multipart(content_type, body):
    msg = BytesParser(policy=HTTP).parsebytes(b"Content-Type: " + content_type.encode() + b"\r\n\r\n" + body)
    parts = {}
    for part in msg.iter_parts():
        parts[part.get_param("name", header="content-disposition")] = part.get_payload(decode=True)
    return parts


def make_result(person, garment, category):
    if Image is None:
        return person
    base = Image.open(io.BytesIO(person)).convert("RGB")
    # Deliberately a different size than the input so the app's aspect-ratio handling is exercised.
    base = base.resize((base.width, int(base.height * 1.0)))
    tint = Image.new("RGB", base.size, (255, 0, 122))
    base = Image.blend(base, tint, 0.18)
    g = Image.open(io.BytesIO(garment)).convert("RGB")
    g.thumbnail((base.width // 3, base.height // 3))
    base.paste(g, (base.width - g.width - 16, base.height - g.height - 16))
    d = ImageDraw.Draw(base)
    d.rectangle((0, 0, base.width, 44), fill=(0, 0, 0))
    d.text((12, 14), "MOCK BACKEND - NOT AN AI RESULT (%s)" % category, fill=(255, 255, 255))
    out = io.BytesIO()
    base.save(out, "JPEG", quality=88)
    return out.getvalue()


def view(job):
    elapsed = time.time() - job["created"]
    if job["cancelled"]:
        return {"id": job["id"], "status": "canceled"}
    if ARGS.mode == "fail" and elapsed > 2:
        return {"id": job["id"], "status": "failed", "error": {"message": "Mock: no person detected in the photo"}}
    total = 14 if ARGS.mode == "slow" else 7
    if elapsed < 2:
        return {"id": job["id"], "status": "queued"}
    if elapsed < total:
        return {"id": job["id"], "status": "processing", "progress": int((elapsed - 2) / (total - 2) * 100)}
    return {"id": job["id"], "status": "succeeded", "resultUrl": "/v1/files/%s.jpg" % job["id"]}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("%s %s" % (self.command, self.path), "-", fmt % args)

    def send_json(self, code, obj, headers=None):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path != "/v1/tryon/jobs":
            return self.send_json(404, {"error": {"message": "not found"}})
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        key = self.headers.get("Idempotency-Key")
        if not key:
            return self.send_json(400, {"error": {"message": "Idempotency-Key header is required"}})
        if ARGS.mode == "rate-limit":
            return self.send_json(429, {"error": {"message": "slow down"}}, {"Retry-After": "5"})
        if ARGS.mode == "server-error":
            return self.send_json(503, {"error": {"message": "mock outage"}})
        with LOCK:
            if key in BY_KEY:  # a retried submission must not start (and bill) a second generation
                STATS["dedupes"] += 1
                print("  idempotent replay for key", key)
                return self.send_json(200, view(JOBS[BY_KEY[key]]))
            parts = parse_multipart(self.headers["Content-Type"], body)
            if not parts.get("person") or not parts.get("garment"):
                return self.send_json(422, {"error": {"message": "person and garment images are required"}})
            job = {"id": uuid.uuid4().hex[:12], "created": time.time(), "cancelled": False,
                   "category": (parts.get("category") or b"").decode(), "person": parts["person"], "garment": parts["garment"]}
            JOBS[job["id"]] = job
            BY_KEY[key] = job["id"]
            STATS["submits"] += 1
        print("  accepted job", job["id"], "person=%dB garment=%dB category=%s" % (len(job["person"]), len(job["garment"]), job["category"]))
        self.send_json(202, view(job))

    def do_GET(self):
        if self.path == "/__stats":
            return self.send_json(200, STATS)
        if self.path.startswith("/v1/tryon/jobs/"):
            job = JOBS.get(self.path.rsplit("/", 1)[1])
            return self.send_json(200, view(job)) if job else self.send_json(404, {"error": {"message": "unknown job"}})
        if self.path.startswith("/v1/files/"):
            job = JOBS.get(self.path.rsplit("/", 1)[1].removesuffix(".jpg"))
            if not job:
                return self.send_json(404, {"error": {"message": "unknown file"}})
            data = b"<html>this is not an image</html>" if ARGS.mode == "junk" else make_result(job["person"], job["garment"], job["category"])
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_json(404, {"error": {"message": "not found"}})

    def do_DELETE(self):
        job = JOBS.get(self.path.rsplit("/", 1)[1])
        if not job:
            return self.send_json(404, {"error": {"message": "unknown job"}})
        job["cancelled"] = True
        print("  cancelled job", job["id"])
        self.send_response(204)
        self.end_headers()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8099)
    parser.add_argument("--mode", default="ok", choices=["ok", "fail", "rate-limit", "server-error", "junk", "slow"])
    ARGS = parser.parse_args()
    print("mock backend on :%d mode=%s (Pillow %s)" % (ARGS.port, ARGS.mode, "yes" if Image else "NO"))
    ThreadingHTTPServer(("0.0.0.0", ARGS.port), Handler).serve_forever()
