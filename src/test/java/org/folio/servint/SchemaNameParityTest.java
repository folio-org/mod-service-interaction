package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.spring.config.FolioSpringConfiguration;
import org.junit.jupiter.api.Test;

/**
 * ADR-006 gate: the schema name folio-spring resolves must be byte-identical
 * to what the legacy com.k_int OkapiTenantResolver produced
 * (tenant lowercased + "_" + appName with dashes as underscores), or adopted
 * legacy data would be invisible to the Java module.
 */
class SchemaNameParityTest {

  private static final String APP_NAME = "mod-service-interaction";

  private final org.folio.spring.FolioModuleMetadata metadata =
      new FolioSpringConfiguration().folioModuleMetadata(APP_NAME);

  @Test
  void resolvesLegacySchemaNameForPlainTenant() {
    assertThat(metadata.getDBSchemaName("diku")).isEqualTo("diku_mod_service_interaction");
  }

  @Test
  void lowercasesTenantIdLikeLegacyResolver() {
    assertThat(metadata.getDBSchemaName("TestTenant")).isEqualTo("testtenant_mod_service_interaction");
  }

  @Test
  void matchesLegacyResolverConventionForAnyTenant() {
    var tenant = "m2parity";
    var legacyConvention = tenant.toLowerCase() + "_" + APP_NAME.replace('-', '_');
    assertThat(metadata.getDBSchemaName(tenant)).isEqualTo(legacyConvention);
  }
}
