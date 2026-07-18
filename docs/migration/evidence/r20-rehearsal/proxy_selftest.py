#!/usr/bin/env python3
"""Regression self-test for proxy.py's credential-channel redaction (M9 R33).

Proves mechanically, against a live proxy instance (subprocess, ephemeral
ports, throwaway sinks), the two properties review No.4 F-40 demanded:

1. NOTHING the proxy persists — JSONL log, capture body/header files,
   capture-failed markers — contains any probe credential in cleartext:
   not Authorization / X-Okapi-Token / Cookie / Proxy-Authorization /
   *-authorization header values, and not credential-shaped query parameter
   values (access_token, token, jwt, api_key, apikey, password, secret,
   authorization; case-insensitive, multi-value).
2. Forwarding is untouched: the upstream receives every credential verbatim
   (headers and raw query string), and the client receives the upstream's
   response unchanged.

Probe set: single-value, repeated-parameter, mixed-case names, header+query
combinations, and a /_/tenant POST so the capture-file sink is exercised.
Redacted values must keep the correlation form [REDACTED sha256:<16hex>]
with the correct digest so records stay correlatable.

Usage: python3 proxy_selftest.py   (stdlib only; exit 0 = PASS)
"""
import hashlib
import http.client
import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
PROXY = os.path.join(HERE, "proxy.py")

SECRETS = {
    "header-authorization": "Bearer SELFTEST-AUTH-a1",
    "header-okapi": "SELFTEST-OKAPI-b2",
    "header-cookie": "sid=SELFTEST-COOKIE-c3",
    "header-proxy-auth": "Basic SELFTEST-PROXYAUTH-d4",
    "header-custom-auth": "SELFTEST-CUSTOMAUTH-e5",
    "query-access-token": "SELFTEST-ACCESSTOKEN-f6",
    "query-jwt": "SELFTEST-JWT-g7",
    "query-token-1": "SELFTEST-TOKEN1-h8",
    "query-token-2": "SELFTEST-TOKEN2-i9",
    "query-api-key": "SELFTEST-APIKEY-j0",
    "query-password": "SELFTEST-PASSWORD-k1",
}
KEEP_VERBATIM = "keepme-not-a-credential"

failures = []


def check(label, ok, detail=""):
    print(f"{'PASS' if ok else 'FAIL'}  {label}" + (f"  [{detail}]" if detail and not ok else ""))
    if not ok:
        failures.append(label)


def corr(value):
    return "[REDACTED sha256:" + hashlib.sha256(value.encode()).hexdigest()[:16] + "]"


class Upstream(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    seen = []  # [(method, raw_path, [(header, value)...], body_bytes)]

    def log_message(self, fmt, *args):
        pass

    def _serve(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        body = self.rfile.read(length) if length else b""
        Upstream.seen.append((self.command, self.path, list(self.headers.items()), body))
        payload = b'{"upstream":"ok"}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    do_GET = _serve
    do_POST = _serve


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_listening(port, timeout=10):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError(f"proxy never listened on :{port}")


def request(port, method, path, headers, body=None):
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
    try:
        conn.putrequest(method, path, skip_host=True, skip_accept_encoding=True)
        conn.putheader("Host", "selftest")
        for k, v in headers:
            conn.putheader(k, v)
        data = body.encode() if body else b""
        if data:
            conn.putheader("Content-Length", str(len(data)))
        conn.endheaders()
        if data:
            conn.send(data)
        resp = conn.getresponse()
        return resp.status, resp.read()
    finally:
        conn.close()


def main():
    up_port, proxy_port = free_port(), free_port()
    upstream = ThreadingHTTPServer(("127.0.0.1", up_port), Upstream)
    threading.Thread(target=upstream.serve_forever, daemon=True).start()

    with tempfile.TemporaryDirectory(prefix="proxy-selftest-") as tmp:
        log_path = os.path.join(tmp, "wire.jsonl")
        cap_dir = os.path.join(tmp, "captures")
        proc = subprocess.Popen(
            [sys.executable, PROXY, str(proxy_port), "127.0.0.1", str(up_port),
             log_path, cap_dir, "selftest"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            wait_listening(proxy_port)

            # p1: the F-40 reproduction — credential headers + credential query params
            status, body = request(
                proxy_port, "GET",
                "/probe?access_token=" + SECRETS["query-access-token"]
                + "&jwt=" + SECRETS["query-jwt"] + "&plain=" + KEEP_VERBATIM,
                [("Authorization", SECRETS["header-authorization"]),
                 ("X-Okapi-Token", SECRETS["header-okapi"]),
                 ("Cookie", SECRETS["header-cookie"]),
                 ("Proxy-Authorization", SECRETS["header-proxy-auth"])])
            check("p1 relayed 200 + upstream body", status == 200 and body == b'{"upstream":"ok"}')

            # p2: mixed-case names, *-authorization family, repeated token param
            request(proxy_port, "GET",
                    "/probe?Access_Token=" + SECRETS["query-access-token"]
                    + "&API_KEY=" + SECRETS["query-api-key"]
                    + "&token=" + SECRETS["query-token-1"]
                    + "&token=" + SECRETS["query-token-2"],
                    [("PROXY-AUTHORIZATION", SECRETS["header-proxy-auth"]),
                     ("X-Custom-Authorization", SECRETS["header-custom-auth"])])

            # p3: /_/tenant POST (capture-file sink) with credential headers
            request(proxy_port, "POST", "/_/tenant",
                    [("Authorization", SECRETS["header-authorization"]),
                     ("Content-Type", "application/json")],
                    body='{"module_to":"selftest-1.0.0"}')

            # p4: credential params on a mutating route with a query string
            request(proxy_port, "POST", "/other?password=" + SECRETS["query-password"]
                    + "&secret=" + SECRETS["query-password"],
                    [("Cookie", SECRETS["header-cookie"])],
                    body='{"probe":4}')

            time.sleep(0.5)  # let the last persistence writes land
        finally:
            proc.terminate()
            proc.wait(timeout=10)
            upstream.shutdown()

        # --- property 1: zero cleartext credentials anywhere under the sinks ---
        persisted = {}
        for root, _dirs, files in os.walk(tmp):
            for name in files:
                path = os.path.join(root, name)
                with open(path, "rb") as f:
                    persisted[os.path.relpath(path, tmp)] = f.read()
        check("persistence produced artifacts", len(persisted) >= 2, str(list(persisted)))
        blob = b"\n".join(persisted.values())
        for label, secret in SECRETS.items():
            check(f"no cleartext {label}", secret.encode() not in blob)

        # --- redaction keeps the correlation form and non-credentials verbatim ---
        records = [json.loads(line) for line in
                   persisted.get("wire.jsonl", b"").decode().splitlines()]
        check("all probes logged", len(records) == 4, str(len(records)))
        p1 = records[0]
        check("p1 path access_token correlation hash",
              "access_token=" + corr(SECRETS["query-access-token"]) in p1["path"], p1["path"])
        check("p1 path jwt correlation hash",
              "jwt=" + corr(SECRETS["query-jwt"]) in p1["path"], p1["path"])
        check("p1 non-credential param verbatim", "plain=" + KEEP_VERBATIM in p1["path"])
        headers1 = {k.lower(): v for k, v in p1["request_headers"]}
        for header, secret_key in [("authorization", "header-authorization"),
                                   ("x-okapi-token", "header-okapi"),
                                   ("cookie", "header-cookie"),
                                   ("proxy-authorization", "header-proxy-auth")]:
            check(f"p1 {header} correlation hash", headers1.get(header) == corr(SECRETS[secret_key]),
                  str(headers1.get(header)))
        p2 = records[1]
        check("p2 mixed-case Access_Token redacted",
              "Access_Token=" + corr(SECRETS["query-access-token"]) in p2["path"], p2["path"])
        check("p2 mixed-case API_KEY redacted",
              "API_KEY=" + corr(SECRETS["query-api-key"]) in p2["path"], p2["path"])
        check("p2 both repeated token values redacted",
              "token=" + corr(SECRETS["query-token-1"]) in p2["path"]
              and "token=" + corr(SECRETS["query-token-2"]) in p2["path"], p2["path"])
        headers2 = {k.lower(): v for k, v in p2["request_headers"]}
        check("p2 custom *-authorization redacted",
              headers2.get("x-custom-authorization") == corr(SECRETS["header-custom-auth"]),
              str(headers2.get("x-custom-authorization")))
        cap_headers = persisted.get(os.path.join("captures", "01-selftest.headers"), b"").decode()
        check("tenant capture header file redacts Authorization",
              corr(SECRETS["header-authorization"]) in cap_headers, cap_headers)

        # --- property 2: the upstream saw every credential verbatim ---
        check("upstream saw 4 requests", len(Upstream.seen) == 4, str(len(Upstream.seen)))
        up1_method, up1_path, up1_headers, _ = Upstream.seen[0]
        up1_map = {k.lower(): v for k, v in up1_headers}
        check("upstream p1 query verbatim",
              "access_token=" + SECRETS["query-access-token"] in up1_path
              and "jwt=" + SECRETS["query-jwt"] in up1_path, up1_path)
        for header, secret_key in [("authorization", "header-authorization"),
                                   ("x-okapi-token", "header-okapi"),
                                   ("cookie", "header-cookie"),
                                   ("proxy-authorization", "header-proxy-auth")]:
            check(f"upstream p1 {header} verbatim", up1_map.get(header) == SECRETS[secret_key],
                  str(up1_map.get(header)))
        _, _, _, up3_body = Upstream.seen[2]
        check("upstream p3 body verbatim", up3_body == b'{"module_to":"selftest-1.0.0"}')

    print()
    if failures:
        print(f"SELF-TEST FAILED: {len(failures)} check(s): {failures}")
        return 1
    print("SELF-TEST PASSED: every credential channel redacted, forwarding intact")
    return 0


if __name__ == "__main__":
    sys.exit(main())
