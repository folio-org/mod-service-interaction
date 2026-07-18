# Refdata parity — category CRUD and the domain/property value lookup.

Feature: Reference data management
  As a FOLIO administrator
  I want to manage controlled vocabularies
  So that modules share consistent values

  Scenario: List categories with their values
    Given refdata categories exist
    When the category collection is fetched
    Then each category renders id, desc, internal, and its values expanded

  Scenario: Create a category
    Given a payload with desc "Wibble.Wobble" and two values
    When it is POSTed to the refdata endpoint
    Then the category is created with its values

  Scenario: Fetch a single category
    Given a category exists
    When it is fetched by id
    Then its render includes the expanded values, and an unknown id returns 404

  Scenario: Update a category
    Given a category exists
    When a value is added via PUT
    Then the updated category carries the new value

  Scenario: Delete a category
    Given a category exists
    When it is DELETEd by id
    Then it no longer appears in the category list

  Scenario: Look up values by domain and property
    Given the category "NumberGeneratorSequence.CheckDigitAlgo" is registered
    When the lookup for domain "NumberGeneratorSequence" and property "CheckDigitAlgo" is fetched with a term filter
    Then the category's matching values are returned as a kiwt list honouring term, match, sort, stats, and the perPage cap of 100

  Scenario: Unknown domain and property returns 404
    Given no category is registered for domain "No" and property "Such"
    When the lookup is fetched
    Then the response is 404
