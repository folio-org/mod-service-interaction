package org.folio.servint.controller;

import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.servint.domain.dto.AttestationTokenDto;
import org.folio.servint.rest.resource.AttestationApi;
import org.folio.servint.service.attestation.AttestationService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the legacy AttestedAssertionController. The subject is the
 * calling user's FOLIO user id, resolved the way grails-okapi built its
 * principal: the x-okapi-user-id header, else the user_id claim of the
 * x-okapi-token (parsed without signature verification), else "UNKNOWN".
 */
@RestController
@RequiredArgsConstructor
@Log4j2
public class AttestationController implements AttestationApi {

  private static final String USAGE_EXT_APP = "extApp";

  private final AttestationService attestationService;
  private final FolioExecutionContext folioExecutionContext;

  @Override
  public ResponseEntity<AttestationTokenDto> getAttestationToken() {
    var token = attestationService.generateAssertion(
        resolveSubject(), folioExecutionContext.getTenantId(), USAGE_EXT_APP);
    var result = new AttestationTokenDto();
    result.setToken(token);
    result.setStatus("OK");
    return ResponseEntity.ok(result);
  }

  private String resolveSubject() {
    if (folioExecutionContext.getUserId() != null) {
      return folioExecutionContext.getUserId().toString();
    }
    var okapiToken = folioExecutionContext.getToken();
    if (okapiToken != null && !okapiToken.isEmpty()) {
      try {
        var userId = SignedJWT.parse(okapiToken).getJWTClaimsSet().getClaim("user_id");
        if (userId != null) {
          return userId.toString();
        }
      } catch (Exception e) {
        log.warn("Could not extract user_id from x-okapi-token", e);
      }
    }
    return "UNKNOWN";
  }
}
