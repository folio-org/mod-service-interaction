package org.folio.servint.controller;

import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.folio.servint.service.ServintTenantService;
import org.folio.servint.web.MissingPurgeFlagException;
import org.folio.spring.controller.TenantController;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController("folioTenantController")
public class ServintTenantController extends TenantController {

  private final ServintTenantService tenantService;

  public ServintTenantController(ServintTenantService tenantService) {
    super(tenantService);
    this.tenantService = tenantService;
  }

  /**
   * Okapi signals a disable as POST /_/tenant with module_from present, a
   * blank module_to, and an explicit purge:false (ADR-012, D-26). The
   * framework routing would treat that shape as an upgrade and re-run
   * Liquibase plus the seeding hooks; a disable must leave the tenant
   * untouched, so it answers 204 here with only the tenant's caches evicted.
   * A blank-module_to body whose purge flag is omitted or null is rejected
   * 400 (REQ-020 AC6, F-32): destroying a tenant requires explicit
   * purge=true, so the module never substitutes a destructive default —
   * TenantPurgeFlagAdvice restores the wire-level null the DTO default would
   * otherwise mask. Okapi always sends the flag on the 2.0 interface. The
   * gate must precede delegation: the framework's isDisableJob unboxes the
   * flag when module_to is blank.
   */
  @Override
  public ResponseEntity<Void> postTenant(@Valid TenantAttributes tenantAttributes) {
    if (isFlaglessBlankModuleTo(tenantAttributes)) {
      throw new MissingPurgeFlagException();
    }
    if (isOkapiDisableShape(tenantAttributes)) {
      tenantService.disableTenant();
      return ResponseEntity.noContent().build();
    }
    return super.postTenant(tenantAttributes);
  }

  /**
   * Every tenant job completes synchronously inside POST /_/tenant, so any
   * operation is complete by the time it can be asked about (ADR-012). The
   * framework default would answer 500 (not implemented), contradicting the
   * declared `_tenant` 2.0 contract.
   */
  @Override
  public ResponseEntity<String> getTenant(String operationId) {
    return ResponseEntity.ok("true");
  }

  private static boolean isFlaglessBlankModuleTo(TenantAttributes attributes) {
    return StringUtils.isBlank(attributes.getModuleTo()) && attributes.getPurge() == null;
  }

  private static boolean isOkapiDisableShape(TenantAttributes attributes) {
    return StringUtils.isNotBlank(attributes.getModuleFrom())
        && StringUtils.isBlank(attributes.getModuleTo())
        && Boolean.FALSE.equals(attributes.getPurge());
  }
}
