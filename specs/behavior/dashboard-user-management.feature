# Granting, changing, and revoking other users' access
# (POST dashboard users) — per-item semantics with silent-ignore rules.

Feature: Dashboard user management
  As a dashboard manager
  I want to control who can use my dashboard
  So that I can share it at the right level

  Scenario: Managing users requires manage access
    Given a user holding only edit access on a dashboard
    When they POST a users payload for it
    Then the response is 403, while a manage holder's request succeeds and returns the refreshed users list

  Scenario: Grant access to a new user
    Given a manager and a target user with zero dashboards
    When the manager POSTs an item without id naming the target with view access
    Then an access object is created with userDashboardWeight 0 and defaultUserDashboard true, because the target's dashboard count was zero
    When the manager grants access to another user who owns two dashboards
    Then that access object is created with userDashboardWeight 2 and defaultUserDashboard false

  Scenario: Duplicate or self grants are ignored
    Given a target user who already has access on the dashboard
    When the manager POSTs a creation item for that user and another for the manager themselves
    Then both items are ignored and the existing access objects are unchanged

  Scenario: Delete a user access object
    Given a target user with access on the dashboard
    When the manager POSTs the item with its id and _delete true
    Then the access object is removed from the dashboard

  Scenario: Edit changes only the access level
    Given a target user's access item resubmitted with a higher level and a changed weight
    When the manager POSTs it
    Then only the access level changes and the item is re-saved just when the level differed

  Scenario: List dashboard users without expanding the dashboard
    Given a dashboard with several access objects and a caller with view access
    When the users list is fetched
    Then each item expands the access refdata value and renders the dashboard as an id-only reference
