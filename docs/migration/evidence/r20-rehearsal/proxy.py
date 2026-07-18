#!/usr/bin/env python3
"""Logging reverse proxy (stdlib only) for the tenant-lifecycle rehearsals.

Usage: proxy.py <listen_port> <upstream_host> <upstream_port> <log_jsonl> <capture_dir> <side>

Forwards every method/path/header/body verbatim to the upstream and relays the
response unchanged (chunked bodies are de-chunked and re-sent with
Content-Length; forwarded header VALUES and body BYTES are untouched). Appends
one JSONL record per request to <log_jsonl>. For every request whose path is
exactly /_/tenant, additionally writes the raw request body and headers to
numbered files in <capture_dir>: NN-<side>.body.json / NN-<side>.headers .

Evidence hygiene (review No.3 F-34, remediation R27; review No.4 F-40,
remediation R33):

- PERSISTED artifacts redact the values of Authorization / X-Okapi-Token /
  Cookie / Proxy-Authorization request headers — and of ANY header whose
  name ends in "-authorization" — the header NAME is kept and the value
  becomes "[REDACTED sha256:<first-16-hex-of-value>]", so identical
  credentials stay correlatable across records without being recoverable.
  Forwarding is unaffected — the upstream always receives the original
  values.
- PERSISTED paths redact credential-shaped query parameter values
  (access_token, token, jwt, api_key, apikey, password, secret,
  authorization — case-insensitive) with the same correlation-hash form,
  before the path reaches any sink (JSONL records, capture header files,
  capture-failed markers). The path forwarded upstream is untouched.
  proxy_selftest.py, committed beside this file, proves both properties
  mechanically.
- Capture files are written atomically (tmp + os.replace); a reader never
  observes a half-written capture.
- Evidence sinks are preflighted at startup (fail fast before any traffic).
- A persistence failure AFTER the upstream has executed the request never
  changes what the client sees: the true upstream response is still relayed,
  and the failure is recorded loudly as a capture-failed marker (stderr plus
  a best-effort <log_jsonl>.capture-failed sidecar) so the evidence gap is
  explicit instead of masquerading as a client error inviting a retry.
"""
import base64
import hashlib
import http.client
import json
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LISTEN_PORT = int(sys.argv[1])
UP_HOST = sys.argv[2]
UP_PORT = int(sys.argv[3])
LOG_PATH = sys.argv[4]
CAP_DIR = sys.argv[5]
SIDE = sys.argv[6]

_lock = threading.Lock()
_cap_seq = 0

HOP_BY_HOP = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
              "te", "trailers", "transfer-encoding", "upgrade"}

SENSITIVE_HEADERS = {"authorization", "x-okapi-token", "cookie", "proxy-authorization"}

SENSITIVE_PARAMS = {"access_token", "token", "jwt", "api_key", "apikey",
                    "password", "secret", "authorization"}


def _correlation_hash(value):
    digest = hashlib.sha256(value.encode("utf-8", "replace")).hexdigest()[:16]
    return f"[REDACTED sha256:{digest}]"


def _redacted(name, value):
    lname = name.lower()
    if lname in SENSITIVE_HEADERS or lname.endswith("-authorization"):
        return _correlation_hash(value)
    return value


def _redacted_path(path):
    """Redact credential-shaped query parameter values for persistence.

    Works on the RAW query string (no decode/re-encode round trip) so every
    non-credential byte persists verbatim; matching is on the parameter name,
    case-insensitive, and each occurrence of a repeated parameter is redacted
    independently. The path forwarded upstream is never this one.
    """
    if "?" not in path:
        return path
    base, query = path.split("?", 1)
    pairs = []
    for pair in query.split("&"):
        name, sep, value = pair.partition("=")
        if sep and value and name.lower() in SENSITIVE_PARAMS:
            value = _correlation_hash(value)
        pairs.append(name + sep + value)
    return base + "?" + "&".join(pairs)


def _write_atomic(path, data):
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, path)


def _read_body(handler):
    te = handler.headers.get("Transfer-Encoding", "")
    if "chunked" in te.lower():
        chunks = []
        while True:
            size_line = handler.rfile.readline().strip()
            size = int(size_line.split(b";")[0], 16)
            if size == 0:
                handler.rfile.readline()  # trailing CRLF
                break
            chunks.append(handler.rfile.read(size))
            handler.rfile.readline()  # chunk CRLF
        return b"".join(chunks)
    length = int(handler.headers.get("Content-Length", 0) or 0)
    return handler.rfile.read(length) if length else b""


class ProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # silence default stderr access log
        pass

    def _handle(self):
        body = _read_body(self)
        req_headers = list(self.headers.items())

        conn = http.client.HTTPConnection(UP_HOST, UP_PORT, timeout=290)
        try:
            conn.putrequest(self.command, self.path, skip_host=True, skip_accept_encoding=True)
            sent_cl = False
            for k, v in req_headers:
                lk = k.lower()
                if lk == "transfer-encoding":
                    continue  # body was de-chunked; send Content-Length instead
                if lk == "content-length":
                    conn.putheader("Content-Length", str(len(body)))
                    sent_cl = True
                    continue
                conn.putheader(k, v)
            if body and not sent_cl:
                conn.putheader("Content-Length", str(len(body)))
            conn.endheaders()
            if body:
                conn.send(body)
            resp = conn.getresponse()
            resp_body = resp.read()
            resp_status = resp.status
            resp_reason = resp.reason
            resp_headers = resp.getheaders()
        finally:
            conn.close()

        # persist evidence — but the upstream mutation already happened, so a
        # persistence failure must never surface to the client (F-34): mark it
        # loudly and fall through to the relay below.
        try:
            self._persist_evidence(body, req_headers, resp_status)
        except Exception as exc:
            marker = (f"capture-failed {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())} "
                      f"{self.command} {_redacted_path(self.path)} upstream={resp_status}: {exc!r}\n")
            sys.stderr.write(marker)
            sys.stderr.flush()
            try:
                with open(LOG_PATH + ".capture-failed", "a") as f:
                    f.write(marker)
            except OSError:
                pass  # evidence sink is gone entirely; the stderr marker stands

        # relay response verbatim (minus hop-by-hop framing headers)
        self.send_response_only(resp_status, resp_reason)
        for k, v in resp_headers:
            if k.lower() in HOP_BY_HOP or k.lower() == "content-length":
                continue
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(resp_body)))
        self.end_headers()
        if resp_body:
            self.wfile.write(resp_body)

    def _persist_evidence(self, body, req_headers, resp_status):
        global _cap_seq
        ts = time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime()) + "Z"
        persisted_headers = [[k, _redacted(k, v)] for k, v in req_headers]
        persisted_path = _redacted_path(self.path)
        try:
            body_rec = {"request_body_utf8": body.decode("utf-8")}
        except UnicodeDecodeError:
            body_rec = {"request_body_b64": base64.b64encode(body).decode()}
        rec = {"ts": ts, "side": SIDE, "method": self.command, "path": persisted_path,
               "request_headers": persisted_headers, **body_rec, "response_status": resp_status}
        with _lock:
            with open(LOG_PATH, "a") as f:
                f.write(json.dumps(rec) + "\n")
            if self.path == "/_/tenant":
                _cap_seq += 1
                stem = f"{CAP_DIR}/{_cap_seq:02d}-{SIDE}"
                _write_atomic(stem + ".body.json", body)
                header_lines = [f"{ts} {self.command} {persisted_path} -> upstream {UP_HOST}:{UP_PORT}"
                                f" (response {resp_status})"]
                header_lines += [f"{k}: {v}" for k, v in persisted_headers]
                _write_atomic(stem + ".headers", ("\n".join(header_lines) + "\n").encode())

    def __getattr__(self, name):
        if name.startswith("do_"):
            return self._handle
        raise AttributeError(name)


if __name__ == "__main__":
    # preflight both evidence sinks — fail fast BEFORE any traffic is proxied
    os.makedirs(CAP_DIR, exist_ok=True)
    with open(LOG_PATH, "a"):
        pass
    _write_atomic(os.path.join(CAP_DIR, ".write-probe"), b"ok")
    os.remove(os.path.join(CAP_DIR, ".write-probe"))

    server = ThreadingHTTPServer(("127.0.0.1", LISTEN_PORT), ProxyHandler)
    print(f"proxy :{LISTEN_PORT} -> {UP_HOST}:{UP_PORT} side={SIDE}", flush=True)
    server.serve_forever()
