# Cross-module widget definition federation parity — harvest, cache,
# validation, conflict handling, and MAJOR.MINOR compatibility semantics.

Feature: Widget definition federation
  As a dashboard user
  I want widget definitions gathered from every implementing module
  So that one catalog spans the whole platform

  Scenario: Aggregate definitions across implementing modules
    Given two modules besides this one implement the dashboard interface at version compatible with 1.0
    When the global definitions collection is fetched
    Then the result contains definitions harvested from all three modules via their dashboard definitions endpoints

  Scenario: Cache refetches when the implementor set changes
    Given a populated federation cache
    When a module implementing the dashboard interface is added or removed and the global collection is fetched again
    Then the definitions are refetched from the new implementor set, while an unchanged set is served from the cache

  Scenario: Invalid harvested definitions are dropped
    Given an implementor serving one definition that violates the generic widget definition schema and another that violates its widget type schema, and an implementor answering with a non-list body
    When the global collection is fetched
    Then both invalid definitions and the non-list response are dropped, and each remaining definition was validated against the latest compatible widget type for its type name and version

  Scenario: Same-name conflicts keep the first module's definition
    Given two modules each serving a definition named "recentItems"
    When the global collection is fetched
    Then only the first module's "recentItems" survives and the conflict is logged as an error

  Scenario: Version filters keep the highest compatible minor
    Given definitions named "chart" at versions "1.0", "1.2", and "2.0"
    When the global collection is fetched with version "1.1"
    Then only "1.2" is returned because compatibility requires the same major and a minor of at least "1.1", and "2.0" is excluded for its different major
