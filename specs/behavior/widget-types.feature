# Widget type catalog parity — plain render and (name, typeVersion)
# uniqueness.

Feature: Widget types
  As a dashboard user
  I want to see the widget types the module knows
  So that definitions can be validated against their schemas

  Scenario: List widget types plainly
    Given widget types exist in the tenant
    When the types collection is fetched
    Then each type renders id, name, typeVersion, and its schema as a JSON string

  Scenario: Types are unique per name and version
    Given the type "SimpleSearch" exists at typeVersion "1.0"
    When the same name and typeVersion are imported again and separately a "SimpleSearch" typeVersion "1.1" is added
    Then the list still holds a single "1.0" row while the "1.1" row coexists beside it
