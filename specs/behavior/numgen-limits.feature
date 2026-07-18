# Maximum guard-rail parity scenarios (dossier §5.5 step 4 / §6 fixtures).

Feature: Sequence maximum limits
  As a FOLIO administrator
  I want sequences to warn as they approach their maximum and refuse beyond it
  So that number ranges never silently overflow

  Scenario: Exceeding the maximum is refused
    Given a sequence whose next raw value exceeds maximumNumber
    When getNextNumber is called
    Then the envelope is ERROR with errorCode "MaxReached" and no nextValue
    And the transaction rolls back so the sequence value is not consumed

  Scenario: Hitting the maximum still generates with a warning
    Given a sequence whose next raw value equals maximumNumber
    When getNextNumber is called
    Then the number is generated and the envelope is WARNING with warningCode "HitMaximum"

  Scenario: Threshold equal to maximum warns HitMaximum at the maximum
    Given a sequence with maximumNumberThreshold equal to maximumNumber at its maximum
    When getNextNumber is called
    Then the envelope is WARNING with warningCode "HitMaximum"

  Scenario: Passing the threshold warns OverThreshold
    Given a sequence whose next raw value is at or above maximumNumberThreshold but below maximumNumber
    When getNextNumber is called
    Then the number is generated and the envelope is WARNING with warningCode "OverThreshold"

  Scenario: Far below the maximum generates normally
    Given a sequence whose next raw value is below the threshold
    When getNextNumber is called
    Then the envelope status is "OK"

  Scenario: Unresolvable next value is an error
    Given a sequence whose next raw value cannot be determined
    When getNextNumber is called
    Then the envelope is ERROR with errorCode "NoNextValue" and the transaction rolls back

  Scenario: Saving a sequence derives its maximum check state
    Given a sequence with maximumNumber and maximumNumberThreshold set
    When the sequence is saved with a nextValue beyond, between, or below the bounds
    Then its maximumCheck refdata value becomes at_maximum, over_threshold, or below_threshold respectively, and null when maximumNumber is unset
