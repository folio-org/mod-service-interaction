#!/usr/bin/env python3
"""R-VER gate 2 — re-run of the R31/R32 oracle matrices, both sides.

Same probe set as ../r31-escaped-oracle/harness/oracle.py (Phase B escaped
tokens + Phase C wildcards), parametrized so one driver runs against either
side without touching the committed Phase-1 bundles:

    BASE=http://localhost:8080 TENANT=r31o CREATE_FIXTURE=0 OUT=legacy python3 rerun-oracle.py
    BASE=http://localhost:8081 TENANT=rvo  CREATE_FIXTURE=1 OUT=port   python3 rerun-oracle.py

CREATE_FIXTURE=1 first enables the tenant (port _tenant 2.0 shape) and
creates the directed fixture rows through the module's own REST API; the
legacy side reuses the standing r31o tenant whose fixture rows are the
Phase-1 originals. Output: probes-log.txt + per-probe bodies under OUT/.
"""
import json
import os
import pathlib
import urllib.parse
import urllib.request

BASE = os.environ["BASE"]
TENANT = os.environ["TENANT"]
OUT = pathlib.Path(__file__).resolve().parent / os.environ["OUT"]
CREATE = os.environ.get("CREATE_FIXTURE") == "1"
GEN = "/servint/numberGenerators"

FIXTURE_CODES = [
    "alpha",
    "alpha&&prefix==", "alpha\\&&prefix==",
    "alpha||prefix==", "alpha\\||prefix==",
    "alpha&prefix==", "alpha|prefix==",
    "alpha!x", "alpha\\!x",
    "alpha(x", "alpha\\(x",
    "alpha)x", "alpha\\)x",
    "a&b", "a|b",
    "alpha&&beta||gamma", "alpha\\&&beta\\||gamma", "alpha&beta|gamma",
    "alpha&&beta", "alpha\\&&beta", "alpha&beta",
    "alpha||beta", "alpha\\||beta", "alpha|beta",
    "alpha&&x", "alpha\\&&x", "alpha&x",
    "alpha\\&&x&&code==alpha",
    "ab_cd", "abXcd", "ab%cd", "abcd", "abXYcd", "ab\\%cd",
]


def call(method, path_and_query, body=None):
    req = urllib.request.Request(
        BASE + path_and_query,
        method=method,
        headers={
            "X-Okapi-Tenant": TENANT,
            "x-okapi-url": "http://localhost:9130",
            "Content-Type": "application/json",
        },
        data=json.dumps(body).encode() if body is not None else None,
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def probe(log, pid, slug, query, decoded_note):
    status, text = call("GET", f"{GEN}?{query}")
    try:
        parsed = json.loads(text)
        codes = sorted(g["code"] for g in parsed) if isinstance(parsed, list) else None
        rows = len(parsed) if isinstance(parsed, list) else "-"
    except (json.JSONDecodeError, TypeError, KeyError):
        codes, rows = None, "-"
    (OUT / f"{pid}-{slug}.json").write_text(text)
    log.write(f"{pid} GET {GEN}?{query}\n")
    log.write(f"   decoded: {decoded_note}\n")
    log.write(f"   -> HTTP {status} rows={rows}"
              + (f" codes={json.dumps(codes)}" if codes is not None else "")
              + "\n")


def enc(filter_expr):
    return "filters=" + urllib.parse.quote(filter_expr, safe="")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    log = open(OUT / "probes-log.txt", "w")
    log.write(f"== R-VER oracle re-run: side={os.environ['OUT']} base={BASE} tenant={TENANT} ==\n")

    if CREATE:
        status, text = call("POST", "/_/tenant", {
            "module_to": "mod-service-interaction-5.0.0",
            "parameters": [{"key": "loadReference", "value": "true"}],
        })
        log.write(f"TENANT ENABLE -> {status}\n")
        assert status in (200, 201, 204), text
        for code in FIXTURE_CODES:
            status, text = call("POST", GEN, {"code": code, "name": code})
            log.write(f"CREATE {json.dumps(code)} -> {status}\n")
            if status not in (200, 201):
                log.write(f"  BODY: {text}\n")

    status, text = call("GET", f"{GEN}?perPage=100")
    n = len(json.loads(text))
    log.write(f"BASELINE unfiltered perPage=100 -> HTTP {status} rows={n}\n\n")

    p = "perPage=100&"
    b = lambda *a: probe(log, *a)
    log.write("== Phase B: R31 escaped-token matrix ==\n")
    b("c1", "unfiltered", "perPage=100", "(unfiltered baseline)")
    b("c2", "eq-alpha", p + enc("code==alpha"), "code==alpha")
    b("c3", "absorb-and", p + enc("code==alpha&&prefix=="), "code==alpha&&prefix==")
    b("c4", "absorb-or", p + enc("code==alpha||prefix=="), "code==alpha||prefix==")
    b("c5", "and-control", p + enc("code==alpha&&code==alpha"), "code==alpha&&code==alpha")
    b("e1", "esc-and-empty-rhs", p + enc("code==alpha\\&&prefix=="), "code==alpha\\&&prefix==")
    b("e2", "esc-or-empty-rhs", p + enc("code==alpha\\||prefix=="), "code==alpha\\||prefix==")
    b("e3", "esc-and-nonempty", p + enc("code==alpha\\&&beta"), "code==alpha\\&&beta")
    b("e4", "esc-or-nonempty", p + enc("code==alpha\\||beta"), "code==alpha\\||beta")
    b("e5", "multi-escape", p + enc("code==alpha\\&&beta\\||gamma"), "code==alpha\\&&beta\\||gamma")
    b("q1", "quoted-esc-and", p + enc('code=="alpha\\&&beta"'), 'code=="alpha\\&&beta"')
    b("q2", "quoted-raw-and", p + enc('code=="alpha&&beta"'), 'code=="alpha&&beta"')
    b("x1", "esc-bang", p + enc("code==alpha\\!x"), "code==alpha\\!x")
    b("x2", "esc-lparen", p + enc("code==alpha\\(x"), "code==alpha\\(x")
    b("x3", "esc-rparen", p + enc("code==alpha\\)x"), "code==alpha\\)x")
    b("x4", "raw-bang", p + enc("code==alpha!x"), "code==alpha!x (unescaped)")
    b("x5", "raw-lparen", p + enc("code==alpha(x"), "code==alpha(x (unescaped)")
    b("x6", "raw-rparen", p + enc("code==alpha)x"), "code==alpha)x (unescaped)")
    b("l1", "lone-amp", p + enc("code==a&b"), "code==a&b (lone &)")
    b("l2", "lone-pipe", p + enc("code==a|b"), "code==a|b (lone |)")
    b("m1", "esc-plus-real-and", p + enc("code==alpha\\&&x&&code==alpha"), "code==alpha\\&&x&&code==alpha")
    b("m2", "esc-plus-real-or", p + enc("code==alpha\\&&x||code==alpha"), "code==alpha\\&&x||code==alpha")

    log.write("== Phase C: R32 wildcard matrix ==\n")
    b("w1", "contains-pct", p + enc("code=~ab%cd"), "code=~ab%cd")
    b("w2", "contains-underscore", p + enc("code=~ab_cd"), "code=~ab_cd")
    b("w3", "contains-plain", p + enc("code=~abcd"), "code=~abcd")
    b("w4", "contains-esc-pct", p + enc("code=~ab\\%cd"), "code=~ab\\%cd")
    b("w5", "contains-esc-underscore", p + enc("code=~ab\\_cd"), "code=~ab\\_cd")
    b("w6", "contains-suffix", p + enc("code=~cd"), "code=~cd")
    b("w7", "ncontains-pct", p + enc("code!~ab%cd"), "code!~ab%cd")
    b("w8", "quoted-eq-pct", p + enc('code=="ab%cd"'), 'code=="ab%cd"')
    b("w9", "eq-pct", p + enc("code==ab%cd"), "code==ab%cd")
    b("w10", "eq-underscore", p + enc("code==ab_cd"), "code==ab_cd")
    b("w11", "quoted-contains-pct", p + enc('code=~"ab%cd"'), 'code=~"ab%cd"')
    b("w12", "neq-pct", p + enc("code!=ab%cd"), "code!=ab%cd")
    b("t1", "match-pct", p + "match=code&term=" + urllib.parse.quote("ab%cd", safe=""), "match=code&term=ab%cd")
    b("t2", "match-underscore", p + "match=code&term=" + urllib.parse.quote("ab_cd", safe=""), "match=code&term=ab_cd")
    b("t3", "match-plain", p + "match=code&term=abcd", "match=code&term=abcd")
    b("t4", "match-esc-pct", p + "match=code&term=" + urllib.parse.quote("ab\\%cd", safe=""), "match=code&term=ab\\%cd")
    b("t5", "match-multiprop", p + "match=code&match=name&term=" + urllib.parse.quote("ab%cd", safe=""), "match=code&match=name&term=ab%cd")
    b("t6", "match-multiterm", p + "match=code&term=" + urllib.parse.quote("ab%cd", safe="") + "&term=abcd", "match=code&term=ab%cd&term=abcd (D-33)")
    b("r1", "raw-unencoded-pct", p + "filters=code%3D~ab%cd", "RAW query 'filters=code=~ab%cd' (naive client)")
    log.close()
    print(f"oracle re-run complete: {os.environ['OUT']}")


if __name__ == "__main__":
    main()
