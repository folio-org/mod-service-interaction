# ${current_year} token + year-based reset parity scenarios (SI-151).
# <year> denotes the current UTC calendar year at run time.

Feature: Year-based sequence reset
  As a FOLIO administrator
  I want year-scoped sequences to restart each calendar year
  So that year-prefixed numbers (accession numbers, call numbers) stay correct

  Scenario: Year token renders the current year
    Given a sequence with output template "${current_year}-ABC ${generated_number}" and format "000"
    When getNextNumber is called
    Then the rendered value is "<year>-ABC 001"
    And a template "01A-${current_year}-${generated_number}" with format "0000" renders "01A-<year>-0001"

  Scenario: Stale year with reset enabled restarts at one
    Given a resetOnYearChange sequence whose lastUsedYear is a past year and nextValue is 150
    When getNextNumber is called twice
    Then the rendered values are "<year>-001" then "<year>-002"

  Scenario: Stale year with reset disabled continues counting
    Given a sequence with resetOnYearChange disabled, a stale lastUsedYear, and nextValue 150
    When getNextNumber is called twice
    Then the rendered values are "<year>-150" then "<year>-151"

  Scenario: Reset-enabled sequence requires the year token in its template
    Given a sequence payload with resetOnYearChange true and an output template lacking ${current_year}
    When it is saved
    Then the save is rejected with validation error "resetOnYearChange.tokenMissing"

  Scenario: Timer sweep resets only stale year-scoped sequences
    Given a stale resetOnYearChange sequence and a control sequence without reset
    When POST /servint/numberGenerators/resetYearSequences runs
    Then only the stale sequence is reset to nextValue 1 with lastUsedYear set to the current year
    And the response reports currentYear and sequencesReset
    And running the sweep again resets nothing
