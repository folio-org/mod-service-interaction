package org.folio.servint.web;

/**
 * A POST /_/tenant body carried a purge member that is not exactly one
 * top-level JSON Boolean — a coercible scalar ("true", 1) or duplicate
 * members that binding would collapse last-one-wins. Destroying a tenant
 * requires an unambiguous explicit purge=true, so these shapes are rejected
 * before binding (REQ-020 AC6, review-4 F-37).
 */
public class InvalidPurgeFlagException extends RuntimeException {

  public InvalidPurgeFlagException() {
    super("The purge flag must be exactly one Boolean member");
  }
}
