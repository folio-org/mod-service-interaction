# Access-control parity scenarios — the view < edit < manage hierarchy,
# the my-access probe, and the admin override authority.

Feature: Dashboard access control
  As a dashboard owner
  I want access levels enforced per dashboard
  So that only the right users can see or change it

  Scenario: A manage holder passes view and edit checks
    Given a user holding manage access on a dashboard
    When they fetch the dashboard and then update it via PUT
    Then both operations succeed because manage implies edit and view

  Scenario: A view-only user cannot update the dashboard
    Given a user holding only view access
    When they attempt a PUT on the dashboard
    Then the response is 403 and the dashboard is unchanged

  Scenario: An edit user cannot delete the dashboard
    Given a user holding edit access
    When they attempt a DELETE on the dashboard
    Then the response is 403 because delete requires manage

  Scenario: A user with no access receives 403
    Given a dashboard the caller has no access object for
    When they attempt to fetch it
    Then the response is 403

  Scenario: My access reports the caller's level
    Given a user holding edit access on a dashboard
    When they fetch my-access for it
    Then the response body is exactly an access field naming "edit"

  Scenario: Admin override bypasses access checks
    Given a caller holding the authority okapi.servint.dashboards.admin.allops with no access objects
    When they fetch the index of all dashboards
    Then every dashboard is returned despite the missing access objects
