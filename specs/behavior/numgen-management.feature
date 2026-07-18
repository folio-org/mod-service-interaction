# CRUD parity scenarios for generators and sequences — cover every
# management operation of the servint number generator surface.

Feature: Number generator management
  As a FOLIO administrator
  I want to manage number generators and their sequences
  So that other apps can draw well-formed numbers

  Scenario: Create a generator with embedded sequences
    Given a generator payload with code "orders" embedding two sequences
    When it is POSTed to /servint/numberGenerators
    Then the response returns the created generator with sequences fully expanded

  Scenario: List generators with the stats envelope
    Given several generators exist
    When the collection is fetched with stats=true and a filters expression
    Then the response is an envelope with results and totalRecords honouring the filter

  Scenario: Fetch a single generator
    Given a generator exists
    When it is fetched by id
    Then the wire shape expands its sequences, and an unknown id returns 404

  Scenario: Update a generator
    Given a generator exists
    When its name is changed via PUT
    Then the response carries the updated generator

  Scenario: Delete a generator cascades to its sequences
    Given a generator with sequences exists
    When it is DELETEd by id
    Then the generator and all its sequences are gone

  Scenario: Create a sequence directly
    Given an existing generator
    When a sequence payload naming it as owner is POSTed to /servint/numberGeneratorSequences
    Then the sequence is created under that generator

  Scenario: List sequences with the owner snippet
    Given sequences exist under several generators
    When the sequence collection is fetched
    Then each sequence expands checkDigitAlgo and maximumCheck and carries an owner snippet with id, name, and code

  Scenario: Fetch a single sequence
    Given a sequence exists
    When it is fetched by id
    Then the wire shape includes the owner snippet, and an unknown id returns 404

  Scenario: Update a sequence
    Given a sequence exists
    When its nextValue is changed via PUT
    Then the response carries the updated sequence

  Scenario: Delete a sequence
    Given a sequence exists
    When it is DELETEd by id
    Then it no longer appears under its generator
