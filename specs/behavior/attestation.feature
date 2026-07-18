# RFC 8693 attestation parity — token envelope, claim set, key selection,
# and on-demand key creation (legacy AttestationController + KeyPairService).

Feature: Attested assertion tokens
  As an external service
  I want a signed assertion vouching for the calling user and tenant
  So that modules can federate trust

  Scenario: Issue a signed attestation token
    Given a caller holding the extsvc permission
    When the attestation token endpoint is called
    Then the response is a token field carrying a compact RS256 JWS beside status "OK"

  Scenario: The token carries the attestation claim set
    Given a caller on tenant "diku" whose x-okapi-user-id header carries a FOLIO user id, with "UNKNOWN" standing in only when neither that header nor a user_id claim in the x-okapi-token identifies the caller
    When a token is issued
    Then its claims are iss "FOLIO::mod-service-interaction", sub equal to the caller's FOLIO user id, aud "extApp", exp 300 seconds after iat, a random UUID jti, and tenant "diku", while the JWS header carries kid equal to the signing key record's id and typ JWT

  Scenario: The earliest valid key signs the token
    Given stored key pairs for the usage with availableFrom yesterday, today, and next month, all unexpired
    When a token is issued
    Then it is signed with the pair available since yesterday, because selection orders valid keys by availableFrom ascending and takes the first, letting future keys be pre-published

  Scenario: A missing key pair is created on demand
    Given no stored key pair for the usage
    When a token is issued
    Then an RSA-2048 pair is created with availableFrom now and expiresAt 730 days later, persisted Base64-encoded as X509 public and PKCS8 private keys, and used to sign the token
