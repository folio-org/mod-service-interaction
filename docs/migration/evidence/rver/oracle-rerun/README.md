# R-VER gate 2 — R31/R32 oracle matrices re-run, both sides

Re-run of the Phase-1 legacy-oracle matrices (`../r31-escaped-oracle/`,
`../r32-wildcard-oracle/`) at the M9 closing HEAD, this time against **both**
modules, proving the R31/R32 parser fixes hold over the wire. Run date:
2026-07-23.

## Rig

| Side | Target | Tenant | Fixture |
|---|---|---|---|
| legacy | standing rig jar `abb37211…9b4c57` (JDK 17), :8080 | `r31o` | the Phase-1 fixture as left by Round 1 — `oracle.py` FIXTURE_CODES **plus** the s-matrix/dx driver rows and planted discriminators (64 rows) |
| port | container `mod-service-interaction:rver` (Id `a358168c…`), :8081, same `testing_pg` database | `rvo` (fresh DDL, loadReference) | `oracle.py` FIXTURE_CODES created through the port's own REST API, plus the three planted discriminators `ab$2cd` / `"ab%cd"` / `"alpha&&beta"` (45 rows) |

`rerun-oracle.py` is the parametrized driver (same probe set as the committed
`oracle.py` Phases B+C; env-selected side; never touches the Phase-1 bundles).

## Verdict (`compare.txt`)

41 probes. After stripping rows unique to one side's fixture census (the
legacy tenant carries Round-1 s-matrix rows the port tenant never had — a
fixture difference, not parser behavior), **39/41 probes EQUAL**, including:

- the F-38 escaped-token matrix (`e*`, `q1`, `x*`, `l*`, `m*`) — raw-literal
  escape semantics, operator-spelling absorption, structural-token voiding;
- the F-39 wildcard matrix (`w*`, `t1`–`t5`) — live `%`/`_`, escaped
  literals, `=i=` unwrapped ilike, match/term passthrough;
- **`w1` positive proof over the wire**: `code=~ab%cd` matches the planted
  `ab$2cd` row on BOTH sides — the legacy `[^\\])% → $1 + literal "$2"`
  broken transform is reproduced bug-for-bug by the port (bind `%ab$2cd%`).

The 2 remaining divergences are registered deviations:

| Probe | Legacy | Port | Deviation |
|---|---|---|---|
| `t6` (`match=code&term=ab%cd&term=abcd`) | 500 | 200, 0 rows (comma-joined single term) | **D-33** |
| `r1` (raw `filters=code%3D~ab%cd` — unencoded `%`) | 200, 0 rows (Tomcat 9 lenient decode) | 400 container-level (Tomcat 10.1 strict URI validation) | **D-34** (registered this round; also pinned by harness probe `r32-raw-unencoded-pct`) |
