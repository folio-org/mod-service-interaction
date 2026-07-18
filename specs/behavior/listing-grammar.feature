# The web-toolkit listing grammar matrix (REQ-022) — operator, compound,
# paging, tolerance, and text-match semantics exercised against the number
# generator listings, where the review's probes ran (F-04/F-14).

Feature: Listing grammar
  As an API consumer
  I want the port's collection GETs to evaluate the legacy kiwt grammar
  So that existing filter, paging, and search requests keep working

  Scenario: Comparison and containment operators filter the listings
    Given number generator sequences with nextValue 2, 6, 9, and 2147483649
    When GET /servint/numberGeneratorSequences carries filters nextValue>5
    Then exactly the sequences with values 6, 9, and 2147483649 are returned
    And a comparison with an empty right side is dropped so the listing is unfiltered, == compares the raw value literally, =i= and =~ share the measured legacy ilike pipeline where a non-leading % becomes the literal characters $2 while a leading % and every _ stay live wildcards, and is-null and is-not-null predicates work on dotted association paths

  Scenario: Compound expressions and ranges combine filters
    Given number generators named alpha, beta, and gamma
    When GET /servint/numberGenerators carries a parenthesized filter combining && and || with a negation and a middle-subject range
    Then the result matches the boolean evaluation of the expression and repeated filters parameters are combined with AND
    And a same-level chain of three or more || terms evaluates as a flat N-ary disjunction

  Scenario: Empty right sides inside compounds absorb greedily as in legacy
    Given number generators named alpha and gamma
    When GET /servint/numberGenerators carries filters code==alpha&&prefix== and then filters (code==alpha||prefix==)
    Then each answers 200 with only the rows whose code literally equals the remainder after the first operator, so both listings are empty
    And an empty right side followed by more text absorbs that text, so prefix==&&code==alpha compares prefix against the literal remainder and answers 400 invalid-property where prefix is not a property of the listed entity
    And a negation containing an empty right side drops the whole parameter and answers 200, where legacy answered 500

  Scenario: Paging clamps silently and honours the legacy aliases
    Given more than ten number generators exist
    When GET /servint/numberGenerators is called with perPage absent, perPage=0, perPage=101, max=5, and page=2 in turn
    Then absent and 0 both yield the default page size 10, 101 answers 200 with page size clamped to 100, max acts as the perPage alias, and page is 1-based taking precedence over offset

  Scenario: Stats envelope carries the legacy fields
    Given any number generator listing
    When GET /servint/numberGenerators is called with stats=true
    Then the body holds exactly results, pageSize, page, totalPages, meta, totalRecords, and total, where total equals totalRecords and meta is empty

  Scenario: Bad sort and malformed filters are tolerated
    Given number generators exist
    When GET /servint/numberGenerators is called with an unknown sort property, then with a syntactically malformed filters clause, then with a well-formed filter naming an unknown property
    Then the unknown sort is skipped with a 200, the malformed clause is dropped with a 200, and the unknown property answers 400 with an invalid-property message
    And a value that cannot be coerced to a numeric property drops the clause with a 200 and a boolean property compared to garbage coerces to false with a 200, where legacy answered 500
    And unparseable stats, perPage, max, page, and offset values behave as if the parameter were absent, answering 200 with default semantics

  Scenario: Term matching combines quoted phrases across match properties
    Given number generators whose names and codes contain multi-word phrases
    When GET /servint/numberGenerators is called with a quoted two-word term and match properties name and code
    Then the quoted phrase is kept intact, each term must match within a property, the per-property clauses combine with OR, and the text block combines with AND against the filters
    And % and _ inside a term pass through as live wildcards while an escaped \% matches a literal percent

  Scenario: Escaped tokens are raw literal value text and operator spellings absorb
    Given number generator rows whose codes literally contain backslashes, structural tokens, and operator spellings
    When GET /servint/numberGenerators carries filters code==alpha\&&prefix== and then filters code==alpha==beta
    Then each answers 200 with only the row whose code literally equals the raw remainder after the first operator, backslash retained
    And a real unescaped && or || after an escaped token still splits the compound so absorption never crosses it
    And code==alpha<beta answers 400 invalid-property naming beta while an unescaped ! ( or ) inside a value voids the whole filter and answers 200 unfiltered
