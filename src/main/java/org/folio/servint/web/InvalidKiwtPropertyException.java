package org.folio.servint.web;

/**
 * A well-formed filter naming an unknown property answers 400 with an
 * invalid-property message (legacy SimpleLookupServiceException
 * INVALID_PROPERTY; REQ-022 AC4).
 */
public class InvalidKiwtPropertyException extends RuntimeException {

  public InvalidKiwtPropertyException(String path) {
    super("Invalid property: " + path);
  }
}
