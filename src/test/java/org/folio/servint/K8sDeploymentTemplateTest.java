package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Review-4 F-42 gate: the sample k8s manifests must route to the port the
 * application actually serves. The template is not compiled or booted, so
 * port drift between it and application.yml is invisible to every other
 * test — this one parses both sides and fails the build instead of a
 * reviewer. It also pins the DP-3(a) enforcement artifact: the
 * NetworkPolicy restricting module-port ingress to Okapi that the
 * attestation trust-boundary ADR cites.
 */
class K8sDeploymentTemplateTest {

  private static final Path TEMPLATE =
      Path.of("scripts/k8s_deployment_template.yaml").toAbsolutePath();
  private static final Path APPLICATION_YML =
      Path.of("src/main/resources/application.yml").toAbsolutePath();

  private static int serverPort;
  private static List<Map<String, Object>> documents;
  private static Map<String, Object> deployment;
  private static Map<String, Object> service;

  @BeforeAll
  static void parseBothSides() throws IOException {
    Map<String, Object> application = new Yaml()
        .load(Files.readString(APPLICATION_YML, StandardCharsets.UTF_8));
    serverPort = (int) path(application, "server", "port");

    documents = new ArrayList<>();
    new Yaml().loadAll(Files.readString(TEMPLATE, StandardCharsets.UTF_8))
        .forEach(doc -> documents.add(asMap(doc)));
    deployment = byKind("Deployment");
    service = byKind("Service");
  }

  @Test
  void containerPortMatchesConfiguredServerPort() {
    assertThat(path(container(), "ports"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .extracting(port -> asMap(port).get("containerPort"))
        .as("Deployment containerPort")
        .containsExactly(serverPort);
  }

  @Test
  void servicePortAndExplicitTargetPortMatchConfiguredServerPort() {
    List<?> ports = (List<?>) path(service, "spec", "ports");
    assertThat(ports).hasSize(1);
    Map<String, Object> port = asMap(ports.get(0));
    assertThat(port.get("port")).as("Service port").isEqualTo(serverPort);
    assertThat(port.get("targetPort")).as("Service targetPort (explicit)").isEqualTo(serverPort);
  }

  @Test
  void livenessAndReadinessProbeAdminHealthOnServerPort() {
    for (String probe : List.of("livenessProbe", "readinessProbe")) {
      Map<String, Object> httpGet = asMap(path(container(), probe, "httpGet"));
      assertThat(httpGet.get("path")).as("%s path", probe).isEqualTo("/admin/health");
      assertThat(httpGet.get("port")).as("%s port", probe).isEqualTo(serverPort);
    }
  }

  @Test
  void networkPolicyRestrictsModulePortIngress() {
    Map<String, Object> networkPolicy = byKind("NetworkPolicy");
    List<?> ingress = (List<?>) path(networkPolicy, "spec", "ingress");
    assertThat(ingress).as("single Okapi-only ingress rule").hasSize(1);
    Map<String, Object> rule = asMap(ingress.get(0));
    assertThat((List<?>) rule.get("from")).as("ingress restricted to a pod selector").isNotEmpty();
    assertThat((List<?>) rule.get("ports"))
        .extracting(port -> asMap(port).get("port"))
        .as("NetworkPolicy guards the module port")
        .containsExactly(serverPort);
    assertThat(path(networkPolicy, "spec", "podSelector", "matchLabels"))
        .as("policy selects the module pods")
        .isEqualTo(Map.of("app", "$MOD_DEPLOY_AS"));
  }

  private static Map<String, Object> container() {
    List<?> containers = (List<?>) path(deployment, "spec", "template", "spec", "containers");
    assertThat(containers).hasSize(1);
    return asMap(containers.get(0));
  }

  private static Map<String, Object> byKind(String kind) {
    return documents.stream()
        .filter(doc -> kind.equals(doc.get("kind")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("template has no " + kind + " document"));
  }

  private static Object path(Map<String, Object> root, String... keys) {
    Object current = root;
    for (String key : keys) {
      current = asMap(current).get(key);
      assertThat(current).as("template path %s", String.join(".", keys)).isNotNull();
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }
}
