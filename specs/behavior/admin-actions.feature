# Admin maintenance action parity — widget type import and display-data
# repair, each answering a constant OK envelope.

Feature: Admin actions
  As a FOLIO administrator
  I want maintenance actions for widget types and display data
  So that tenants can be repaired and seeded on demand

  Scenario: Import widget types from sample data
    Given the classpath sample data holds widget types and one (name, typeVersion) pair already exists in the tenant
    When the triggerTypeImport action is executed
    Then the missing types are imported, the existing pair is skipped, and the response is status "OK"

  Scenario: Clean import replaces all types
    Given the tenant holds widget types no longer present in the sample data
    When the triggerTypeImportClean action is executed
    Then every widget type is deleted first and the sample data is imported afresh

  Scenario: Ensure display data repairs dashboards
    Given one dashboard lacking a DashboardDisplayData row and one that has one
    When the ensureDisplayData action is executed
    Then an empty display-data row is created for the lacking dashboard and the existing row is untouched
