# Bulk reordering of the caller's own dashboards (PUT my-dashboards) —
# weight updates, the single-default invariant, and whole-request rejection.

Feature: Dashboard user ordering
  As a FOLIO user
  I want to reorder my dashboards and pick a default
  So that my most-used dashboard opens first

  Scenario: Reorder dashboards by weight
    Given a user with three dashboards weighted 0, 1, and 2
    When they PUT their access objects with the weights rearranged
    Then each access object carries its new userDashboardWeight and unchanged items are not re-saved

  Scenario: Electing a new default clears the previous one
    Given a user whose first dashboard is the default
    When they PUT an item marking the second dashboard as default
    Then the second access object has defaultUserDashboard true and the first has it cleared, leaving a single default

  Scenario: Item missing id or user id rejects the whole request
    Given a bulk payload where one item lacks an id
    When it is PUT to my-dashboards
    Then the response is 400 and no item was applied

  Scenario: Foreign user id rejects the whole request
    Given a bulk payload where one item names another user
    When it is PUT to my-dashboards
    Then the response is 403 and no item was applied

  Scenario: Attempted creation through ordering is ignored
    Given a bulk payload containing an item with an id matching no existing access object
    When it is PUT to my-dashboards
    Then the item is ignored and the response carries the caller's refreshed my-dashboards list
