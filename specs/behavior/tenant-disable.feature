# Disable under `_tenant` 2.0 (ADR-012, deviation D-26): Okapi delivers a
# POST /_/tenant job with module_from present, blank module_to, and
# purge=false. The module answers 204 as a deliberate near-no-op — no
# Liquibase, no seeding, data intact, tenant caches evicted.

Feature: Tenant disable
  As a FOLIO platform operator
  I want a disable to leave tenant data untouched
  So that a later re-enable finds everything intact

  Scenario: Disable answers 204 and keeps the data
    Given an enabled tenant with business data
    When Okapi POSTs a tenant body with module_from present, blank module_to, and purge set to false
    Then the call answers 204 and the tenant schema and all its rows remain intact

  Scenario: Disable runs no migration and no seeding
    Given an enabled tenant whose changelog row count is known and a seeded reference value has been deleted
    When Okapi POSTs the disable-shaped tenant body
    Then no Liquibase changeset executes, the deleted seeded value stays deleted, and the tenant's in-memory caches are evicted
