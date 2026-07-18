# CRUD parity scenarios for dashboards — wire shape (widget summaries),
# cascade delete, and the admin-only index.

Feature: Dashboard management
  As a FOLIO user
  I want to manage my dashboards
  So that I can arrange widgets from many apps in one place

  Scenario: Create a dashboard
    Given a payload with a name and description
    When it is POSTed to the dashboard endpoint
    Then the response returns the created dashboard with an empty widgets summary array

  Scenario: Fetch a dashboard with widget summaries
    Given a dashboard with widget instances exists and the caller has view access
    When it is fetched by id
    Then the render carries scalar fields plus widgets as summaries with id, weight, and name only

  Scenario: Update a dashboard
    Given a dashboard exists and the caller has edit access
    When its name is changed via PUT
    Then the response carries the updated render, and an unknown id returns 404

  Scenario: Delete a dashboard removes access and display data
    Given a dashboard with access objects and display data exists and the caller has manage access
    When it is DELETEd by id
    Then the dashboard, every DashboardAccess row, and its DashboardDisplayData are gone

  Scenario: Admin index requires the override authority
    Given dashboards belonging to several users exist
    When a caller without the admin override authority fetches the full dashboard index
    Then the response is 403, while a caller holding okapi.servint.dashboards.admin.allops receives every dashboard
