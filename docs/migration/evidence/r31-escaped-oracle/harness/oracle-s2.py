#!/usr/bin/env python3
"""M9 R31 Phase 1 — supplementary batch 2: absorbability per operator spelling.

Phase S proved `==`/`=~` absorb into values while `<`/`>` shapes drop or
error. This batch pins the remaining spellings (`=`, `!=`, `!~`, `=i=`,
`<=`) and a mid-value quote, so the port's absorption fallback can gate on
exactly the spellings legacy's value_exp accepts.
"""
import importlib.util
import json
import pathlib

spec = importlib.util.spec_from_file_location(
    "oracle", pathlib.Path(__file__).parent / "oracle.py")
oracle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oracle)

call, probe, enc, GEN, R31 = oracle.call, oracle.probe, oracle.enc, oracle.GEN, oracle.R31

ROWS = ["alpha=beta", "alpha!=beta", "alpha!~beta", "alpha=i=beta",
        "alpha<=beta", 'alpha"beta']


def main():
    log = open(R31 / "probes-log.txt", "a")
    log.write("\n== Phase S2: absorbability per operator spelling ==\n")
    for code in ROWS:
        status, text = call("POST", GEN, {"code": code, "name": code})
        log.write(f"CREATE {json.dumps(code)} -> {status}\n")
    status, text = call("GET", f"{GEN}?perPage=100")
    log.write(f"BASELINE unfiltered perPage=100 -> HTTP {status} "
              f"rows={len(json.loads(text))}\n")

    p = "perPage=100&"
    b = lambda *a: probe(R31, log, *a)
    b("s9", "eq-mid-single-eq", p + enc("code==alpha=beta"), "code==alpha=beta")
    b("s10", "eq-mid-neq", p + enc("code==alpha!=beta"), "code==alpha!=beta")
    b("s11", "eq-mid-ncont", p + enc("code==alpha!~beta"), "code==alpha!~beta")
    b("s12", "eq-mid-cieq", p + enc("code==alpha=i=beta"), "code==alpha=i=beta")
    b("s13", "eq-mid-le", p + enc("code==alpha<=beta"), "code==alpha<=beta")
    b("s14", "eq-mid-quote", p + enc('code==alpha"beta'), 'code==alpha"beta')
    log.close()
    print("s2 complete")


if __name__ == "__main__":
    main()
