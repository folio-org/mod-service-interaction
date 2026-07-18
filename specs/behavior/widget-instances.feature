# Widget instance parity — dashboard-derived authorization, weight
# auto-assignment, and the instance wire shape.

Feature: Widget instances
  As a dashboard user
  I want to place and arrange widgets on my dashboards
  So that each dashboard shows what I need

  Scenario: Create an instance requires an editable dashboard
    Given a payload whose owner id names a missing dashboard
    When it is POSTed to the instances endpoint
    Then the response is 404, a view-only caller on an existing dashboard receives 403, and an edit-level caller receives the created instance rendering definition with name and version

  Scenario: Fetch an instance with view access
    Given an instance on a dashboard the caller can view
    When it is fetched by id
    Then the instance render is returned, while a caller without view access receives 403

  Scenario: Update requires edit access
    Given an instance on a dashboard the caller can only view
    When a PUT is attempted
    Then the response is 403, while an edit-level caller's PUT returns the updated instance

  Scenario: Delete requires edit access
    Given an instance on a dashboard the caller can only view
    When a DELETE is attempted
    Then the response is 403, while an edit-level caller's DELETE removes the instance

  Scenario: Full instance index is admin only
    Given instances across several users' dashboards
    When a caller without the admin override authority fetches the full instance index
    Then the response is 403, while a caller holding okapi.servint.dashboards.admin.allops receives every instance

  Scenario: Null weight is auto-assigned
    Given an empty dashboard the caller can edit
    When an instance is POSTed without a weight
    Then it is assigned weight 0, a second weightless instance is assigned the maximum existing weight plus one, and an instance POSTed with an explicit weight keeps it

  Scenario: List the widgets of a dashboard
    Given a dashboard with several instances and a caller with view access
    When the dashboard widgets collection is fetched
    Then all its instances are returned, each rendering definition with name and version
