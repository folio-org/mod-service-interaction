package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Regression for review finding F-08 (R8b): the signing-key cache carries
 * expiry metadata that is honored on every read, and a tenant purge evicts
 * the tenant's cached keys so a re-enabled tenant never signs with a key
 * whose db_key_pair row was dropped with the schema. kid semantics
 * (deviation D-21): the JWT kid is the signing key-record id (kp_id) —
 * stable while a key pair signs, different after rotation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttestationKeyLifecycleIT {

  private static final String TENANT = "keylife";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
  private static final String USER_ID = "0a9f0f4a-6f3f-49f7-9a3d-6f70d20e4c11";

  private static String firstKidBeforePurge;

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private final ObjectMapper json = new ObjectMapper();

  // ---------------------------------------------------------------- helpers

  private void postTenant(String body) throws Exception {
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130")
            .content(body))
        .andExpect(status().isNoContent());
  }

  private void enableTenant() throws Exception {
    postTenant("{\"module_to\": \"mod-service-interaction-5.0.0\"}");
  }

  private SignedJWT requestToken() throws Exception {
    var result = mockMvc.perform(get("/servint/attestation/token")
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-user-id", USER_ID))
        .andExpect(status().isOk()).andReturn();
    var body = json.readTree(result.getResponse().getContentAsString());
    return SignedJWT.parse(body.path("token").asText());
  }

  /** db_key_pair timestamps are naive UTC wall-time (legacy convention). */
  private static Timestamp utcNaive(Instant instant) {
    return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
  }

  private String insertKeyPair(KeyPair keyPair, Instant availableFrom, Instant expiresAt) {
    var id = UUID.randomUUID().toString();
    jdbcTemplate.update("insert into " + SCHEMA + ".db_key_pair "
            + "(kp_id, kp_version, kp_available_from, kp_expires_at, kp_usage, kp_alg, "
            + "kp_public_key, kp_private_key) values (?, 0, ?, ?, 'extApp', 'RSA', ?, ?)",
        id, utcNaive(availableFrom), utcNaive(expiresAt),
        Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
        Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
    return id;
  }

  private static KeyPair rsaKeyPair() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private RSAPublicKey publicKeyOf(String base64X509) throws Exception {
    return (RSAPublicKey) KeyFactory.getInstance("RSA")
        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64X509)));
  }

  private static void waitUntilAfter(Instant instant) throws InterruptedException {
    while (!Instant.now().isAfter(instant.plusMillis(250))) {
      Thread.sleep(100);
    }
  }

  // ------------------------------------------------------------------ tests

  @Test
  @Order(1)
  void cachedKeyExpiryIsHonoredOnEveryRead() throws Exception {
    enableTenant();
    var now = Instant.now();
    var shortLived = rsaKeyPair();
    var successor = rsaKeyPair();
    var shortLivedExpiry = now.plusSeconds(5);
    var shortLivedId = insertKeyPair(shortLived, now.minus(10, ChronoUnit.DAYS), shortLivedExpiry);
    var successorId = insertKeyPair(successor, now.minus(1, ChronoUnit.DAYS),
        now.plus(730, ChronoUnit.DAYS));

    // Earliest-valid key signs and is cached with its expiry metadata.
    var first = requestToken();
    assertThat(first.getHeader().getKeyID()).isEqualTo(shortLivedId);
    assertThat(first.verify(new RSASSAVerifier((RSAPublicKey) shortLived.getPublic()))).isTrue();

    // Cache hit while still valid: same key, same kid.
    assertThat(requestToken().getHeader().getKeyID()).isEqualTo(shortLivedId);

    // Once kp_expires_at passes, the cached entry is invalid ON READ: the
    // service reloads and signs with the successor key. Before the fix the
    // expired cached KeyPair kept signing indefinitely.
    waitUntilAfter(shortLivedExpiry);
    var afterExpiry = requestToken();
    assertThat(afterExpiry.getHeader().getKeyID()).isEqualTo(successorId);
    assertThat(afterExpiry.verify(new RSASSAVerifier((RSAPublicKey) successor.getPublic()))).isTrue();
  }

  @Test
  @Order(2)
  void tokenBeforePurgeEstablishesCachedKey() throws Exception {
    var jwt = requestToken();
    firstKidBeforePurge = jwt.getHeader().getKeyID();
    // The signing key is a live db_key_pair row of this tenant generation.
    assertThat(jdbcTemplate.queryForList("select kp_id from " + SCHEMA + ".db_key_pair",
        String.class)).contains(firstKidBeforePurge);
  }

  @Test
  @Order(3)
  void purgeEvictsCachedKeysSoReenableRotates() throws Exception {
    postTenant("{\"module_from\": \"mod-service-interaction-5.0.0\", \"purge\": true}");
    enableTenant();

    // Before the fix the purged tenant's cache entry survived: this token
    // was signed with the dropped key (stale kid, no new db_key_pair row).
    var jwt = requestToken();
    var kid = jwt.getHeader().getKeyID();
    assertThat(kid).isNotEqualTo(firstKidBeforePurge);

    var rows = jdbcTemplate.queryForList("select * from " + SCHEMA + ".db_key_pair");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("kp_id")).isEqualTo(kid);
    assertThat(jwt.verify(
        new RSASSAVerifier(publicKeyOf((String) rows.get(0).get("kp_public_key"))))).isTrue();
  }
}
