#!/usr/bin/env python3
"""M9 R31/R32 Phase 1 — legacy oracle harness (shared rig session).

Runs against the BOOTED legacy Grails module (:8080, tenant r31o).
Phase A creates the directed fixture (every plausible literal
interpretation of every probe exists as a generator row, so the matched
codes disambiguate the parse). Phase B runs the R31 escaped-token matrix,
Phase C the R32 wildcard matrix. Every request, exact wire query string,
status, row count and matched codes are appended to the bundle's
probes-log.txt; every response body is saved verbatim as <id>-<slug>.json.
"""
import json
import pathlib
import sys
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"
TENANT = "r31o"
GEN = "/servint/numberGenerators"
R31 = pathlib.Path(__file__).resolve().parents[1]
R32 = R31.parent / "r32-wildcard-oracle"

FIXTURE_CODES = [
    # R31 candidates — escaped/structural-token literals
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
    # R32 candidates — wildcard discriminators
    "ab_cd", "abXcd", "ab%cd", "abcd", "abXYcd", "ab\\%cd",
]


def call(method, path_and_query, body=None):
    req = urllib.request.Request(
        BASE + path_and_query,
        method=method,
        headers={"X-Okapi-Tenant": TENANT, "Content-Type": "application/json"},
        data=json.dumps(body).encode() if body is not None else None,
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def create_fixture(log):
    created = []
    for code in FIXTURE_CODES:
        status, text = call("POST", GEN, {"code": code, "name": code})
        log.write(f"CREATE {json.dumps(code)} -> {status}\n")
        created.append((code, status))
        if status not in (200, 201):
            log.write(f"  BODY: {text}\n")
    return created


def probe(bundle, log, pid, slug, query, decoded_note):
    """query is the EXACT raw query string appended to the URL (already
    encoded exactly as intended — encoding is part of the probe design)."""
    status, text = call("GET", f"{GEN}?{query}")
    try:
        parsed = json.loads(text)
        codes = sorted(g["code"] for g in parsed) if isinstance(parsed, list) else None
        rows = len(parsed) if isinstance(parsed, list) else "-"
    except (json.JSONDecodeError, TypeError, KeyError):
        codes, rows = None, "-"
    (bundle / f"{pid}-{slug}.json").write_text(text)
    log.write(f"{pid} GET {GEN}?{query}\n")
    log.write(f"   decoded: {decoded_note}\n")
    log.write(f"   -> HTTP {status} rows={rows}"
              + (f" codes={json.dumps(codes)}" if codes is not None else "")
              + "\n")
    return status, rows, codes


def enc(filter_expr):
    """Canonical encoding: filters=<expr> with the expr percent-encoded the
    way a correct client encodes it (space-free, reserved chars encoded)."""
    return "filters=" + urllib.parse.quote(filter_expr, safe="")


def main():
    R31.mkdir(parents=True, exist_ok=True)
    R32.mkdir(parents=True, exist_ok=True)
    (R31 / "fixture").mkdir(exist_ok=True)

    log31 = open(R31 / "probes-log.txt", "w")
    log32 = open(R32 / "probes-log.txt", "w")

    # ---- Phase A: shared fixture ----
    log31.write("== Phase A: directed fixture (shared with r32-wildcard-oracle) ==\n")
    create_fixture(log31)
    status, text = call("GET", f"{GEN}?perPage=100")
    (R31 / "fixture" / "baseline-post-create.json").write_text(text)
    n = len(json.loads(text))
    log31.write(f"BASELINE unfiltered perPage=100 -> HTTP {status} rows={n}\n\n")
    log32.write("== Fixture: shared session — see ../r31-escaped-oracle/"
                "fixture/ and its probes-log.txt Phase A ==\n\n")

    p = "perPage=100&"

    # ---- Phase B: R31 escaped-token matrix ----
    log31.write("== Phase B: R31 escaped-token matrix ==\n")
    b = lambda *a: probe(R31, log31, *a)
    b("c1", "unfiltered", "perPage=100", "(unfiltered baseline)")
    b("c2", "eq-alpha", p + enc("code==alpha"), "code==alpha")
    b("c3", "absorb-and", p + enc("code==alpha&&prefix=="), "code==alpha&&prefix== (R22 dx2 re-confirm)")
    b("c4", "absorb-or", p + enc("code==alpha||prefix=="), "code==alpha||prefix== (R22 dx4 re-confirm)")
    b("c5", "and-control", p + enc("code==alpha&&code==alpha"), "code==alpha&&code==alpha (R22 c3)")
    b("e1", "esc-and-empty-rhs", p + enc("code==alpha\\&&prefix=="), "code==alpha\\&&prefix== (F-38 row 1)")
    b("e2", "esc-or-empty-rhs", p + enc("code==alpha\\||prefix=="), "code==alpha\\||prefix== (F-38 row 2)")
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
    log31.close()

    # ---- Phase C: R32 wildcard matrix ----
    log32.write("== R32 wildcard matrix ==\n")
    w = lambda *a: probe(R32, log32, *a)
    w("c1", "unfiltered", "perPage=100", "(unfiltered baseline)")
    w("w1", "contains-pct", p + enc("code=~ab%cd"), "code=~ab%cd (F-39 row 1; %25-encoded on wire)")
    w("w2", "contains-underscore", p + enc("code=~ab_cd"), "code=~ab_cd")
    w("w3", "contains-plain", p + enc("code=~abcd"), "code=~abcd")
    w("w4", "contains-esc-pct", p + enc("code=~ab\\%cd"), "code=~ab\\%cd")
    w("w5", "contains-esc-underscore", p + enc("code=~ab\\_cd"), "code=~ab\\_cd")
    w("w6", "contains-suffix", p + enc("code=~cd"), "code=~cd (plain contains control)")
    w("w7", "ncontains-pct", p + enc("code!~ab%cd"), "code!~ab%cd")
    w("w8", "quoted-eq-pct", p + enc('code=="ab%cd"'), 'code=="ab%cd"')
    w("w9", "eq-pct", p + enc("code==ab%cd"), "code==ab%cd (F-39 == control)")
    w("w10", "eq-underscore", p + enc("code==ab_cd"), "code==ab_cd")
    w("w11", "quoted-contains-pct", p + enc('code=~"ab%cd"'), 'code=~"ab%cd"')
    w("w12", "neq-pct", p + enc("code!=ab%cd"), "code!=ab%cd")
    w("t1", "match-pct", p + "match=code&term=" + urllib.parse.quote("ab%cd", safe=""), "match=code&term=ab%cd (F-39 row 2)")
    w("t2", "match-underscore", p + "match=code&term=" + urllib.parse.quote("ab_cd", safe=""), "match=code&term=ab_cd")
    w("t3", "match-plain", p + "match=code&term=abcd", "match=code&term=abcd")
    w("t4", "match-esc-pct", p + "match=code&term=" + urllib.parse.quote("ab\\%cd", safe=""), "match=code&term=ab\\%cd")
    w("t5", "match-multiprop", p + "match=code&match=name&term=" + urllib.parse.quote("ab%cd", safe=""), "match=code&match=name&term=ab%cd")
    w("t6", "match-multiterm", p + "match=code&term=" + urllib.parse.quote("ab%cd", safe="") + "&term=abcd", "match=code&term=ab%cd&term=abcd")
    # encoding forensics: what a client that does NOT encode '%' actually sends
    w("r1", "raw-unencoded-pct", p + "filters=code%3D~ab%cd", "RAW query 'filters=code=~ab%cd' — '%cd' is a valid percent-escape (0xCD) and decodes to a non-ASCII byte; pins what a naive client gets")
    log32.close()
    print("oracle complete")


if __name__ == "__main__":
    main()
