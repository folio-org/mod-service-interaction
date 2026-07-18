# Okapi tenant lifecycle (`_tenant` 2.0, ADR-012) — schema-per-tenant
# provisioning, upgrade, purge, and the operation-status routes.

Feature: Tenant lifecycle
  As a FOLIO platform operator
  I want the module to honour the tenant interface
  So that tenants can be enabled, upgraded, and purged safely

  Scenario: Enable creates the tenant schema
    Given a tenant not yet enabled for the module
    When Okapi POSTs the tenant body with module_to
    Then the call answers 204, the tenant schema is created under the legacy naming convention and the module changelog is applied, and a repeat POST upgrades the same schema without error

  Scenario: Reference parameter triggers seeding
    Given a tenant body carrying the parameter loadReference with value "true"
    When Okapi POSTs it
    Then the reference-data seeding runs after provisioning

  Scenario: Purge drops the tenant schema
    Given an enabled tenant with data
    When Okapi POSTs a tenant body with blank module_to and purge set to true
    Then the call answers 204 and the tenant schema and all its data are gone

  Scenario: Omitted purge flag is rejected instead of purging
    Given an enabled tenant with data
    When a hand-crafted tenant body with blank module_to and no purge flag is POSTed
    Then the call answers 400 with the errors envelope and the tenant schema and all its data are untouched

  Scenario: Non-Boolean or duplicate purge discriminators are rejected
    Given an enabled tenant with data
    When a hand-crafted tenant body carries a purge member that is a coercible non-Boolean scalar, a member duplicated in either order, or a structured value
    Then each call answers 400 with the errors envelope before any binding, regardless of module_to, and the tenant schema and all its data are untouched

  Scenario: Operation status routes answer as declared
    Given any tenant operation identifier
    When a GET and a DELETE are sent to the tenant operation route
    Then the GET answers 200 with body "true" and the DELETE answers 204
