# R26 drift probe — tenant-surface runtime contract gate (F-33)

Run 2026-07-22 against `TenantContractCompletenessIT` (remediation-plan-3
§R26). The tenant surface (`specs/api/servint-tenant.yaml`) is the one API
spec outside the six `openapi-generator` executions — `ServintTenantController`
hand-implements folio-spring's `TenantApi` (ADR-012), so spec↔controller drift
there is not compile-enforced. The IT closes that gap at runtime: it compares
the served `/_/tenant*` surface (Spring's `requestMappingHandlerMapping`)
against the spec's declared operations in both directions, and pins the
declared response statuses to the lifecycle contract `TenantEnableIT`
exercises over the wire. The spec path is overridable via
`-Dservint.tenant.spec` so a mutated copy can prove the gate bites without
touching the governed spec.

| Run | Spec | Expect | Got |
|---|---|---|---|
| head-pass | `specs/api/servint-tenant.yaml` (HEAD) | 2/2 pass | `Tests run: 2, Failures: 0` BUILD SUCCESS (head-pass.out) |
| probe-extra-op | `servint-tenant-extra-op.yaml` — declares `PUT /_/tenant` the controller does not serve | surface test FAILS | `servedTenantSurfaceMatchesTheSpec` fails: served set lacks `PUT /_/tenant`; BUILD FAILURE exit 1 (probe-extra-op.out) |
| probe-status | `servint-tenant-status.yaml` — POST `"400"` response flipped to `"422"` | status test FAILS | `declaredStatusesStayPinnedToTheLifecycleContract` fails: declared `[204, 422]` vs pinned `[204, 400]`; BUILD FAILURE exit 1 (probe-status.out) |

The two mutated fixtures are committed alongside verbatim. Command shape per
probe:

```
mvn -B verify -Dit.test=TenantContractCompletenessIT \
    -Dservint.tenant.spec=<mutated-copy>.yaml
```
