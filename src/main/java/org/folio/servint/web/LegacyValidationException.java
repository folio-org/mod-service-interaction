package org.folio.servint.web;

import lombok.Getter;

/** Save-time sequence validation failure; carries the legacy error code. */
@Getter
public class LegacyValidationException extends RuntimeException {

  private final String code;

  public LegacyValidationException(String code, String message) {
    super(message);
    this.code = code;
  }
}
