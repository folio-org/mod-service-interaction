package org.folio.servint.config;

import org.folio.servint.client.DashboardDefinitionsClient;
import org.folio.servint.client.OkapiProxyClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Declarative HTTP-interface clients (ADR-008) materialized through
 * folio-spring's HttpServiceProxyFactory (folio.exchange.enabled=true).
 */
@Configuration
public class HttpClientConfiguration {

  @Bean
  public OkapiProxyClient okapiProxyClient(HttpServiceProxyFactory factory) {
    return factory.createClient(OkapiProxyClient.class);
  }

  @Bean
  public DashboardDefinitionsClient dashboardDefinitionsClient(HttpServiceProxyFactory factory) {
    return factory.createClient(DashboardDefinitionsClient.class);
  }
}
