# Default-dashboard auto-provisioning and creator grants — legacy
# DashboardService semantics preserved exactly.

Feature: Dashboard provisioning
  As a FOLIO user
  I want a dashboard ready on first use
  So that I never start from an empty surface

  Scenario: First my-dashboards call provisions a default dashboard
    Given a user with zero dashboards
    When they fetch my-dashboards
    Then a dashboard named "My dashboard" is created with a manage access object, defaultUserDashboard true, weight 0, and an empty DashboardDisplayData, and appears in the returned list

  Scenario: Provisioning is idempotent
    Given a user whose default dashboard was already provisioned
    When they fetch my-dashboards again
    Then the existing access objects are returned and no new dashboard is created

  Scenario: Creating a dashboard grants the creator manage access
    Given a user with zero dashboards
    When they POST their first dashboard
    Then they receive a manage access object with weight 0 and defaultUserDashboard true, and an empty DashboardDisplayData is created alongside
    When the same user POSTs a second dashboard
    Then its access object has weight 1 and defaultUserDashboard false, because the default is granted only when the creator previously had zero dashboards

  Scenario: My dashboards expands access and dashboard
    Given a user with provisioned dashboards
    When they fetch my-dashboards
    Then each access object carries the access refdata value expanded and the dashboard expanded
