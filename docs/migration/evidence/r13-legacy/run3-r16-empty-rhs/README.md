# run3-r16-empty-rhs — legacy empty-RHS operator matrix (R16 extension)

Directed probes against the SAME running R13 rig (jar sha, boot recipe, and
tenant `r13b` as in ../README.md), 2026-07-21, answering the question R13
left open: does legacy null-coalesce an empty right side (the old REQ-022
AC1 claim: `a==` IS NULL / `a!=` IS NOT NULL) or drop the clause?

Dataset (all-rows.json): 11 sequences — prefix set on exactly 1 row
(`plain`, prefix "p"), NULL on 10. Discriminating outcome per filter:
IS NULL semantics would answer 10 rows for `prefix==` and 1 row for
`prefix!=`; clause-drop answers all 11 for both.

Observed (x2, observations.tsv and observations-run2.tsv identical):
every variant — `prefix==`, `prefix!=`, `prefix=`, `prefix<>`, `code=i=`,
`code=~`, `code!~` — answers **200 with all 11 rows**: the clause is
DROPPED for every operator. The old null-coalescing claim is false for
equality AND inequality alike; legacy's grammar simply fails to match a
comparison without a right side and web-toolkit discards the filter.
Raw bodies + headers per probe retained alongside.

Downstream: REQ-022 AC1 correction + port parser change (empty-RHS
comparison -> clause dropped) in the R16 SDD session; KiwtListingGrammarIT
pins the port side. `is null` / `is not null` / `is set` / `is empty`
predicates (unchanged) remain the way to express nullness.
