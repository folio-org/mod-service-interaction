# Local widget definition catalog parity — wire shape and the
# permission-free `dashboard` interface endpoint.

Feature: Widget definitions
  As a dashboard user
  I want to browse the widget definitions this module hosts
  So that I can place well-formed widgets

  Scenario: List local widget definitions
    Given widget definitions exist in the tenant
    When the local definitions collection is fetched
    Then each definition renders as name, version, type with name and version, and a definition object, with no entity id

  Scenario: Fetch a single widget definition
    Given a widget definition exists
    When it is fetched by id
    Then the definition wire shape is returned, and an unknown id returns 404

  Scenario: Serve definitions permission-free on the dashboard interface
    Given the module implements the dashboard 1.0 Okapi interface
    When GET dashboard definitions is called without any servint permission
    Then the same local-definition render is returned so other modules can harvest it

  Scenario: Definition wire shape renames and parses fields
    Given a definition persisted with definitionVersion "2.1", typeName "SimpleSearch", typeVersion "1.0", and a JSON-string body
    When it is rendered on the wire
    Then version is "2.1", type carries name "SimpleSearch" and version "1.0", and definition is the parsed JSON object rather than a string
