#!/usr/bin/env python3
"""M9 R31/R32 Phase 1 — mech phase: SQL-bind capture round.

Re-runs the discriminating probes against the SAME legacy jar restarted
with Hibernate SQL/bind trace logging (-Dlogging.level.org.hibernate.SQL=
DEBUG, org.hibernate.type=TRACE) and pairs every probe with the ILIKE/eq
bind value legacy actually sent to PostgreSQL. Includes the positive-proof
row `ab$2cd` (created this phase): `code=~ab%cd` MATCHES it, proving the
contains-path transform emits the literal characters `$2`.
"""
import importlib.util
import json
import pathlib
import re
import sys

spec = importlib.util.spec_from_file_location(
    "oracle", pathlib.Path(__file__).parent / "oracle.py")
oracle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oracle)

call, probe, enc, GEN = oracle.call, oracle.probe, oracle.enc, oracle.GEN
R31, R32 = oracle.R31, oracle.R32

LOG = pathlib.Path(sys.argv[1])  # legacy sqltrace log


def bind_after(offset):
    text = LOG.read_text(errors="replace")[offset:]
    binds = re.findall(
        r"ng_code (?:ilike|=|<>|!=)[^\n]*\n(?:.*BasicBinder[^\n]*- \[([^\n]*)\]\n?)",
        text)
    # fall back: any BasicBinder line following an ng_code criterion line
    lines, out = text.splitlines(), []
    for i, ln in enumerate(lines):
        if "ng_code" in ln and ("ilike" in ln or "=?" in ln.replace(" ", "")):
            for follow in lines[i + 1:i + 4]:
                m = re.search(r"BasicBinder.*- \[(.*)\]$", follow)
                if m:
                    out.append(m.group(1))
                    break
    return out


def main():
    log32 = open(R32 / "probes-log.txt", "a")
    log31 = open(R31 / "probes-log.txt", "a")
    log32.write("\n== Phase M (mech): SQL-bind capture (legacy restarted with "
                "Hibernate SQL/bind trace; same jar, db, tenant) ==\n")
    status, _ = call("POST", GEN, {"code": "ab$2cd", "name": "ab$2cd"})
    log32.write(f'CREATE "ab$2cd" (positive-proof row) -> {status}\n')

    p = "perPage=100&"
    cases32 = [
        ("mp1", "bindproof-contains-pct", p + enc("code=~ab%cd"),
         "code=~ab%cd — positive $2 proof (row ab$2cd exists)"),
        ("mp2", "bind-contains-underscore", p + enc("code=~ab_cd"), "code=~ab_cd"),
        ("mp3", "bind-contains-esc-pct", p + enc("code=~ab\\%cd"), "code=~ab\\%cd"),
        ("mp4", "bind-double-leading-pct", p + enc("code=~%%cd"), "code=~%%cd"),
        ("mp5", "bind-double-mid-pct", p + enc("code=~a%%b"), "code=~a%%b"),
        ("mp6", "bind-ncontains-pct", p + enc("code!~ab%cd"), "code!~ab%cd"),
        ("mp7", "bind-eq-pct", p + enc("code==ab%cd"), "code==ab%cd"),
        ("mp8", "bind-match-pct", p + "match=code&term=ab%25cd",
         "match=code&term=ab%cd"),
        ("mb1", "bind-contains-esc-nonspecial", p + enc("code=~a\\b"),
         "code=~a\\b (backslash before non-special in contains path)"),
        ("mb2", "bind-contains-esc-backslash", p + enc("code=~a\\\\b"),
         "code=~a\\\\b (double backslash in contains path)"),
        ("mb3", "bind-match-esc-nonspecial", p + "match=code&term=a%5Cb",
         "match=code&term=a\\b"),
    ]
    for pid, slug, query, note in cases32:
        offset = len(LOG.read_text(errors="replace"))
        probe(R32, log32, pid, slug, query, note)
        import time
        time.sleep(0.4)
        for bv in bind_after(offset):
            log32.write(f"   sql-bind: [{bv}]\n")

    log31.write("\n== Phase M (mech): SQL-bind capture for the == path ==\n")
    cases31 = [
        ("mb4", "bind-eq-esc-and", p + enc("code==alpha\\&&prefix=="),
         "code==alpha\\&&prefix== (F-38 row 1 with bind)"),
        ("mb5", "bind-eq-esc-nonspecial", p + enc("code==a\\b"), "code==a\\b"),
    ]
    for pid, slug, query, note in cases31:
        offset = len(LOG.read_text(errors="replace"))
        probe(R31, log31, pid, slug, query, note)
        import time
        time.sleep(0.4)
        for bv in bind_after(offset):
            log31.write(f"   sql-bind: [{bv}]\n")

    log31.close()
    log32.close()
    print("mech complete")


if __name__ == "__main__":
    main()
