# Display-data (layout) parity scenarios — plain render, view to read,
# edit to write.

Feature: Dashboard display data
  As a FOLIO user
  I want my dashboard layout persisted
  So that the arrangement survives between sessions

  Scenario: Fetch display data with view access
    Given a dashboard with display data and a caller holding view access
    When the display data is fetched
    Then the response is the plain record with id, dashId, and layoutData

  Scenario: Update layout data with edit access
    Given a caller holding edit access on the dashboard
    When they PUT a payload with new layoutData
    Then the record is updated and the response carries the new layoutData

  Scenario: View-only user cannot update display data
    Given a caller holding only view access
    When they attempt a PUT on the display data
    Then the response is 403 and the layout is unchanged
