#!/usr/bin/env python3
"""M9 R31 Phase 1 — supplementary batch: operator tokens inside values.

Pins how far legacy's greedy value_exp reaches over comparison-operator
tokens WITHOUT any structural (&&/||/parens) or escaped token present —
the exact shapes the port-side absorption generalization must decide
(collapse vs drop). Directed rows: alpha==, alpha=~, alpha<, alpha==beta,
alpha=~beta, alpha<beta, alpha>5.
"""
import importlib.util
import json
import pathlib

spec = importlib.util.spec_from_file_location(
    "oracle", pathlib.Path(__file__).parent / "oracle.py")
oracle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oracle)

call, probe, enc, GEN, R31 = oracle.call, oracle.probe, oracle.enc, oracle.GEN, oracle.R31

ROWS = ["alpha==", "alpha=~", "alpha<", "alpha==beta", "alpha=~beta",
        "alpha<beta", "alpha>5"]


def main():
    log = open(R31 / "probes-log.txt", "a")
    log.write("\n== Phase S: operator tokens inside values (no structural/escaped tokens) ==\n")
    for code in ROWS:
        status, text = call("POST", GEN, {"code": code, "name": code})
        log.write(f"CREATE {json.dumps(code)} -> {status}\n")
    status, text = call("GET", f"{GEN}?perPage=100")
    log.write(f"BASELINE unfiltered perPage=100 -> HTTP {status} "
              f"rows={len(json.loads(text))}\n")

    p = "perPage=100&"
    b = lambda *a: probe(R31, log, *a)
    b("s1", "eq-trailing-eq", p + enc("code==alpha=="), "code==alpha==")
    b("s2", "eq-trailing-cont", p + enc("code==alpha=~"), "code==alpha=~")
    b("s3", "eq-trailing-lt", p + enc("code==alpha<"), "code==alpha<")
    b("s4", "eq-mid-eq", p + enc("code==alpha==beta"), "code==alpha==beta")
    b("s5", "eq-mid-cont", p + enc("code==alpha=~beta"), "code==alpha=~beta")
    b("s6", "eq-mid-lt", p + enc("code==alpha<beta"), "code==alpha<beta")
    b("s7", "cont-trailing-eq", p + enc("code=~alpha=="), "code=~alpha==")
    b("s8", "eq-gt-number", p + enc("code==alpha>5"), "code==alpha>5")
    log.close()
    print("supplementary complete")


if __name__ == "__main__":
    main()
