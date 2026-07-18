package org.folio.servint.controller;

import java.util.List;
import java.util.Map;
import org.folio.servint.web.InvalidKiwtPropertyException;
import org.folio.servint.web.InvalidPurgeFlagException;
import org.folio.servint.web.LegacyValidationException;
import org.folio.servint.web.MissingPurgeFlagException;
import org.folio.servint.web.UnknownRefdataValueException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ServintExceptionHandlers {

  /** Legacy Grails rejects invalid saves with 422 and an errors array. */
  @ExceptionHandler(LegacyValidationException.class)
  public ResponseEntity<Map<String, Object>> handleSequenceValidation(LegacyValidationException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(errors(e.getCode(), e.getMessage()));
  }

  /** Legacy kiwt maps a filter naming an unknown property to 400 (REQ-022 AC4). */
  @ExceptionHandler(InvalidKiwtPropertyException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidProperty(InvalidKiwtPropertyException e) {
    return ResponseEntity.badRequest().body(errors("invalid.property", e.getMessage()));
  }

  /**
   * Malformed JSON request body (R9 case 1, dossier D-22): stable 400 with the
   * errors envelope. The message is fixed — Jackson's parse detail (body
   * excerpts, byte offsets) never reaches the wire.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleMalformedBody(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest()
        .body(errors("malformed.json", "JSON request body could not be parsed"));
  }

  /**
   * DB integrity conflicts surfacing from user input — unique violations
   * (e.g. duplicate number-generator code), FK conflicts (R9 case 4, dossier
   * D-24): sanitized 409 with the errors envelope. The fixed message keeps
   * SQL state, constraint names and driver detail off the wire.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleIntegrityConflict(DataIntegrityViolationException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errors("integrity.violation", "Request conflicts with existing data"));
  }

  /**
   * A blank-module_to tenant body omitted the purge flag (REQ-020 AC6,
   * review-3 F-32): 400 with the errors envelope — destroying a tenant
   * requires explicit purge=true, never a bound default.
   */
  @ExceptionHandler(MissingPurgeFlagException.class)
  public ResponseEntity<Map<String, Object>> handleMissingPurgeFlag(MissingPurgeFlagException e) {
    return ResponseEntity.badRequest().body(errors("purge.not.explicit", e.getMessage()));
  }

  /**
   * A tenant body's purge member is not exactly one top-level Boolean —
   * a coercible scalar or duplicate members (REQ-020 AC6, review-4 F-37):
   * 400 before binding, so the rejection is provably side-effect-free.
   */
  @ExceptionHandler(InvalidPurgeFlagException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidPurgeFlag(InvalidPurgeFlagException e) {
    return ResponseEntity.badRequest().body(errors("purge.not.explicit", e.getMessage()));
  }

  /**
   * A body names a refdata value (id or value) that does not exist (R9 case 5,
   * dossier D-25): stable 400 with the errors envelope.
   */
  @ExceptionHandler(UnknownRefdataValueException.class)
  public ResponseEntity<Map<String, Object>> handleUnknownRefdata(UnknownRefdataValueException e) {
    return ResponseEntity.badRequest().body(errors("unknown.refdata", e.getMessage()));
  }

  /** Legacy Grails error envelope: an errors array of {code, message} entries. */
  private static Map<String, Object> errors(String code, String message) {
    return Map.of("errors", List.of(Map.of("code", code, "message", message)));
  }

  /**
   * Method-level container validation (e.g. List&lt;@Valid Dto&gt; request
   * bodies) surfaces as ConstraintViolationException, not
   * MethodArgumentNotValidException; the legacy module answers 400 there.
   */
  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  public ResponseEntity<Void> handleConstraintViolation() {
    return ResponseEntity.badRequest().build();
  }
}
