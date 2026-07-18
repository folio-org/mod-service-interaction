package org.folio.servint.service;

import lombok.extern.log4j.Log4j2;
import org.folio.servint.service.attestation.KeyPairService;
import org.folio.servint.service.dashboard.DashboardSeedService;
import org.folio.servint.service.numgen.NumgenSeedService;
import org.folio.servint.service.widget.WidgetDefinitionService;
import org.folio.servint.service.widget.WidgetTypeService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tenant lifecycle per ADR-006: the Liquibase changelog adopts existing legacy
 * schemas (ADR-005). Seeding follows the legacy two-bucket triggers (REQ-019):
 * afterTenantUpdate seeds only the unconditional @Defaults baseline
 * (DashboardAccess.Access + NumberGeneratorSequence.MaximumCheck, as
 * grails-okapi's setDefaultsForTenant did on every enable); the check-digit
 * vocabulary and default generators load via loadReferenceData, widget types
 * via loadSampleData — both invoked by the framework TenantController only
 * when the matching tenant parameter is the literal "true", within the same
 * request-scoped FolioExecutionContext as afterTenantUpdate.
 *
 * <p>Module-level per-tenant caches are evicted here (F-07/F-08): the
 * widget-definition harvest on every enable/upgrade (an upgrade can change
 * the provider set) and on purge; the attestation signing keys on purge
 * (the schema drop deletes the backing db_key_pair rows).
 */
@Log4j2
@Service
public class ServintTenantService extends TenantService {

  private final NumgenSeedService numgenSeedService;
  private final DashboardSeedService dashboardSeedService;
  private final WidgetTypeService widgetTypeService;
  private final WidgetDefinitionService widgetDefinitionService;
  private final KeyPairService keyPairService;

  public ServintTenantService(JdbcTemplate jdbcTemplate,
                              FolioExecutionContext context,
                              FolioSpringLiquibase folioSpringLiquibase,
                              NumgenSeedService numgenSeedService,
                              DashboardSeedService dashboardSeedService,
                              WidgetTypeService widgetTypeService,
                              WidgetDefinitionService widgetDefinitionService,
                              KeyPairService keyPairService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.numgenSeedService = numgenSeedService;
    this.dashboardSeedService = dashboardSeedService;
    this.widgetTypeService = widgetTypeService;
    this.widgetDefinitionService = widgetDefinitionService;
    this.keyPairService = keyPairService;
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    log.info("Tenant [{}] enabled/upgraded; seeding @Defaults baseline", context.getTenantId());
    widgetDefinitionService.evictTenant(context.getTenantId());
    dashboardSeedService.seed();
    numgenSeedService.seedMaximumCheck();
  }

  @Override
  protected void afterTenantDeletion(TenantAttributes tenantAttributes) {
    var tenantId = context.getTenantId();
    log.info("Tenant [{}] purged; evicting module-level caches", tenantId);
    widgetDefinitionService.evictTenant(tenantId);
    keyPairService.evictTenant(tenantId);
  }

  /**
   * `_tenant` 2.0 disable (ADR-012, D-26): the tenant must stay untouched —
   * no Liquibase, no seeding — so this never enters createOrUpdateTenant and
   * only evicts the module-level caches (the same set afterTenantDeletion
   * evicts), letting a later re-enable start cold.
   */
  public void disableTenant() {
    var tenantId = context.getTenantId();
    log.info("Tenant [{}] disabled; schema untouched, evicting module-level caches", tenantId);
    widgetDefinitionService.evictTenant(tenantId);
    keyPairService.evictTenant(tenantId);
  }

  @Override
  public void loadReferenceData() {
    log.info("Tenant [{}]: loading reference data", context.getTenantId());
    numgenSeedService.seedCheckDigitAlgo();
    numgenSeedService.seedDefaultGenerators();
  }

  @Override
  public void loadSampleData() {
    log.info("Tenant [{}]: loading sample data", context.getTenantId());
    widgetTypeService.triggerTypeImport(false);
  }
}
