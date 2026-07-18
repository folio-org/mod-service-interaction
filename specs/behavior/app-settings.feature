# App settings parity — web-toolkit AppSetting CRUD.

Feature: Application settings
  As a FOLIO administrator
  I want to manage module settings
  So that tenant behavior can be configured

  Scenario: List settings
    Given settings exist across several sections
    When the settings collection is fetched with stats=true
    Then the response is an envelope with results and totalRecords, each setting rendering section, key, settingType, vocab, defValue, and value

  Scenario: Create a setting
    Given a payload with section "numberGenerators", key "displayWarnings", and a value
    When it is POSTed to the appSettings endpoint
    Then the setting is created and returned

  Scenario: Fetch a setting
    Given a setting exists
    When it is fetched by id
    Then its render is returned, and an unknown id returns 404

  Scenario: Update a setting value
    Given a setting exists
    When its value is changed via PUT
    Then the response carries the new value

  Scenario: Delete a setting
    Given a setting exists
    When it is DELETEd by id
    Then it no longer appears in the settings list
