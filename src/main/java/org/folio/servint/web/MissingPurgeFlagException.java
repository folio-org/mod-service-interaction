package org.folio.servint.web;

/**
 * A POST /_/tenant body with blank module_to omitted the purge flag (or sent
 * it null). Destroying a tenant requires explicit purge=true and a disable
 * explicit purge=false, so the module refuses to guess (REQ-020 AC6, F-32).
 */
public class MissingPurgeFlagException extends RuntimeException {

  public MissingPurgeFlagException() {
    super("Blank module_to requires an explicit purge flag");
  }
}
