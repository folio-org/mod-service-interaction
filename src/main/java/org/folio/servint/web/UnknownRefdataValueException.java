package org.folio.servint.web;

/**
 * A request body referenced a refdata value (by id or value) that does not
 * exist. Mapped to 400 with the errors envelope (code {@code unknown.refdata})
 * by {@code ServintExceptionHandlers} — dossier D-25: legacy web-toolkit
 * binding silently dropped the unknown reference instead.
 */
public class UnknownRefdataValueException extends RuntimeException {

  public UnknownRefdataValueException(String message) {
    super(message);
  }
}
