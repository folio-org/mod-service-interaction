# Parity scenarios for getNextNumber — fixtures lifted verbatim from the
# legacy NumberGeneratorSpec so the Java rewrite can prove byte-identical
# output. <year> denotes the current UTC calendar year at run time.

Feature: Number generation
  As a FOLIO module or client app
  I want to draw the next number from a configured sequence
  So that barcodes, request numbers, and codes are unique and well-formed

  Scenario: First number from an auto-created sequence
    Given no number generator with code "Wibble" exists
    When getNextNumber is called with generator "Wibble" and sequence "dibble"
    Then the generator and sequence are auto-created with library defaults
    And the envelope status is "OK" with nextValue "000000001"
    And a second call returns nextValue "000000002"

  Scenario: Sequential numbers with prefix and default output template
    Given the "patron" sequence with prefix "user" and format "000000000"
    When getNextNumber is called three times
    Then the rendered values are "user-000000001", "user-000000002", "user-000000003"

  Scenario: Prefix and postfix with grouped format
    Given the "staff" sequence with prefix "staff", postfix "test", and format "000,000,000"
    When getNextNumber is called
    Then the rendered value is "staff-000,000,001-test"

  Scenario: Unformatted sequence renders the raw value
    Given the "noformat" sequence with prefix "nf" and no format
    When getNextNumber is called
    Then the rendered value is "nf-1"

  Scenario: High initial value is formatted
    Given the "highinit" sequence with prefix "hi", format "000000000", and nextValue 100000
    When getNextNumber is called
    Then the rendered value is "hi-000100000"

  Scenario: Custom template slices the generated number with substring
    Given a sequence whose output template slices generated_number with substring(0,4) and substring(4,9)
    When getNextNumber is called
    Then the rendered value is "0700-0000-7-00001-post"

  Scenario: Pre-checksum template feeds the checksum input
    Given a sequence with preChecksumTemplate "100${generated_number}001" and output template "0700-${checksum_input_template}-${checksum}-post"
    When getNextNumber is called
    Then the rendered value is "0700-100000000001001-3-post"

  Scenario: EAN13 check digit
    Given an EAN13 sequence with prefix "069", postfix "1", and format "000000000"
    When getNextNumber is called
    Then the rendered value is "069-000000001-1-7"

  Scenario: Luhn check digit
    Given a Luhn sequence with nextValue 117707, preChecksumTemplate "22356${generated_number}", and format "00000000"
    When getNextNumber is called
    Then the rendered value is "22356001177070"

  Scenario: ISBN-10 check digit
    Given an ISBN-10 sequence with nextValue 100000 and format "000000000"
    When getNextNumber is called
    Then the rendered value is "000100000-4"

  Scenario: ISSN check digit including the X digit
    Given an ISSN sequence with nextValue 1050124 and format "0000000"
    When getNextNumber is called
    Then the rendered value is "1050-124X"

  Scenario: Inverted modulus-10 with weights 1-7-9-3
    Given a 1793_ltr_mod10_r sequence with nextValue 771962, format "00000000", and template "${generated_number}${checksum}077"
    When getNextNumber is called
    Then the rendered value is "007719628077"

  Scenario: Inverted modulus-10 with weights 1-2
    Given a 12_ltr_mod10_r sequence with nextValue 7298, preChecksumTemplate "05${generated_number}01", and format "0000000"
    When getNextNumber is called
    Then the rendered value is "050007298013"

  Scenario: Unknown check digit algorithm fails the request
    Given a sequence whose checkDigitAlgo value matches no known algorithm
    When getNextNumber is called
    Then the request fails with a server error

  Scenario: Concurrent generation never duplicates numbers
    Given a sequence under concurrent getNextNumber load
    When multiple requests race on the same sequence
    Then each request receives a distinct number and the sequence advances once per request

  Scenario: Successful generation stamps the last used year
    Given a sequence that has never been used
    When getNextNumber is called
    Then the sequence's lastUsedYear equals the current UTC year
