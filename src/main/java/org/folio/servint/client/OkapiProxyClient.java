package org.folio.servint.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Okapi proxy discovery (ADR-008): which interfaces a tenant has and which
 * modules provide one. Relative URLs are resolved against the caller's
 * Okapi URL by folio-spring's EnrichUrlAndHeadersInterceptor.
 */
@HttpExchange("_/proxy/tenants/{tenant}")
public interface OkapiProxyClient {

  @GetExchange("/interfaces")
  List<InterfaceRef> getInterfaces(@PathVariable("tenant") String tenant);

  @GetExchange("/modules")
  List<ModuleRef> getModules(@PathVariable("tenant") String tenant,
                             @RequestParam("provide") String provide);

  @JsonIgnoreProperties(ignoreUnknown = true)
  record InterfaceRef(String id, String version) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ModuleRef(String id) {
  }
}
