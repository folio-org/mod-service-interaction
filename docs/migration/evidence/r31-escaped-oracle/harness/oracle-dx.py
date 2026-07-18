#!/usr/bin/env python3
"""M9 R31/R32 Phase 1 — dx follow-up: directed disambiguation probes.

Round 2 of the oracle after reading the main matrix + legacy parse trees:
pins (a) the boundary of the bare-`%` matches-nothing rule in the `=~`/`!~`
paths, (b) whether quote characters are literal value text (rows created
WITH quotes in code), (c) `\\`-family lexing, (d) empty-RHS contains, and
(e) match-path wildcard edge cases. Appends to both bundles' probes-log.txt.
"""
import importlib.util
import pathlib

spec = importlib.util.spec_from_file_location(
    "oracle", pathlib.Path(__file__).parent / "oracle.py")
oracle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oracle)

call, probe, enc, GEN = oracle.call, oracle.probe, oracle.enc, oracle.GEN
R31, R32 = oracle.R31, oracle.R32

DX_CODES = ['"alpha&&beta"', '"ab%cd"', "a%b", "a\\b", "a\\\\b"]


def main():
    log31 = open(R31 / "probes-log.txt", "a")
    log32 = open(R32 / "probes-log.txt", "a")

    log31.write("\n== Phase D (dx): directed disambiguation ==\n")
    import json
    for code in DX_CODES:
        status, text = call("POST", GEN, {"code": code, "name": code})
        log31.write(f"CREATE {json.dumps(code)} -> {status}\n")
        if status not in (200, 201):
            log31.write(f"  BODY: {text}\n")
    status, text = call("GET", f"{GEN}?perPage=100")
    (R31 / "fixture" / "baseline-post-dx.json").write_text(text)
    log31.write(f"BASELINE unfiltered perPage=100 -> HTTP {status} "
                f"rows={len(json.loads(text))}\n")

    p = "perPage=100&"

    # R31 dx: quotes-as-literal + backslash family
    b = lambda *a: probe(R31, log31, *a)
    b("dq1", "quoted-raw-and-rowed", p + enc('code=="alpha&&beta"'),
      'code=="alpha&&beta" (row with literal quotes now exists)')
    b("dq2", "quoted-esc-and-rowed", p + enc('code=="alpha\\&&beta"'),
      'code=="alpha\\&&beta" (does a quoted escape match a quoted-raw row?)')
    b("db1", "esc-nonspecial", p + enc("code==a\\b"),
      "code==a\\b (backslash before non-special char; rows a\\b and a\\\\b exist)")
    b("db2", "esc-backslash", p + enc("code==a\\\\b"),
      "code==a\\\\b (double backslash)")
    log31.close()

    # R32 dx: bare-% boundary + quoted-% + empty RHS + match edges
    w = lambda *a: probe(R32, log32, *a)
    log32.write("\n== Phase D (dx): bare-% boundary + quotes ==\n")
    w("d1", "contains-trailing-pct", p + enc("code=~ab%"), "code=~ab%")
    w("d2", "contains-leading-pct", p + enc("code=~%cd"), "code=~%cd")
    w("d3", "contains-only-pct", p + enc("code=~%"), "code=~%")
    w("d4", "contains-mid-pct", p + enc("code=~a%b"),
      "code=~a%b (row a%b exists)")
    w("d5", "ncontains-trailing-pct", p + enc("code!~ab%"), "code!~ab%")
    w("d6", "contains-mixed-esc-bare", p + enc("code=~ab\\%c%"),
      "code=~ab\\%c% (escaped %, then bare %)")
    w("d7", "eq-mid-pct", p + enc("code==a%b"), "code==a%b (row exists)")
    w("d8", "quoted-eq-pct-rowed", p + enc('code=="ab%cd"'),
      'code=="ab%cd" (row with literal quotes now exists)')
    w("d9", "quoted-contains-pct-rowed", p + enc('code=~"ab%cd"'),
      'code=~"ab%cd" (quoted value containing bare %)')
    w("d10", "contains-esc-mid", p + enc("code=~a\\%b"),
      "code=~a\\%b (escaped % as literal; row a%b exists)")
    w("d11", "contains-empty-rhs", p + enc("code=~"),
      "code=~ (empty RHS on contains)")
    w("d12", "match-only-pct",
      p + "match=code&term=%25", "match=code&term=%")
    w("d13", "match-empty-term", p + "match=code&term=",
      "match=code&term= (empty term)")
    w("d14", "match-esc-underscore",
      p + "match=code&term=ab%5C_cd", "match=code&term=ab\\_cd")
    log32.close()
    print("dx complete")


if __name__ == "__main__":
    main()
