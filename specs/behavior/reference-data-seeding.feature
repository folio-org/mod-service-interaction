# Tenant reference-data seeding parity — per-trigger coverage of the
# legacy two-bucket semantics: the unconditional @Defaults baseline,
# the loadReference-gated vocabulary and generators, and the
# loadSample-gated widget types.

Feature: Reference data seeding
  As a FOLIO platform operator
  I want tenants seeded with the module's reference data
  So that dashboards and generators work out of the box

  Scenario: Enable without parameters seeds only the baseline vocabularies
    Given a tenant provisioning body that carries no tenant parameters
    When POST /_/tenant completes the enable
    Then the DashboardAccess.Access category exists as internal with the values manage, edit, and view labelled Manage, Edit, and View, and NumberGeneratorSequence.MaximumCheck holds below_threshold, over_threshold, and at_maximum
    And the enable has seeded no other categories, no number generators, and no widget types

  Scenario: Check digit algorithms are seeded on reference load
    Given a tenant enabled with the loadReference parameter set to true
    When the reference load completes
    Then NumberGeneratorSequence.CheckDigitAlgo holds none, ean13, 1793_ltr_mod10_r, 12_ltr_mod10_r, isbn10checkdigit, issncheckdigit, and luhncheckdigit with their exact legacy labels

  Scenario: Default generators are seeded on reference load
    Given a tenant enabled with the loadReference parameter set to true
    When the reference load completes
    Then the eight default generators from openAccess to serialsManagement_patternNumber exist with their legacy sequences, formats, templates, check-digit algorithms, and nextValue 1

  Scenario: Widget types are imported on sample load
    Given a tenant enabled with the loadSample parameter set to true
    When the sample load completes
    Then the widget types from the bundled sample data exist and a tenant enabled without loadSample has none

  Scenario: Reference loads are idempotent
    Given a tenant already seeded whose patron sequence has advanced to nextValue 500
    When the reference load runs again
    Then no duplicate categories, values, generators, or sequences appear and the advanced nextValue remains 500
