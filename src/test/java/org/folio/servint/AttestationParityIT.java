package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Behavior-parity suite for RFC 8693 attestation (dossier §5.7): token
 * envelope, claim set, subject resolution (x-okapi-user-id header, else the
 * token's user_id claim, else "UNKNOWN"), earliest-valid-first key
 * selection, and on-demand RSA-2048 key creation valid 730 days.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttestationParityIT {

  private static final String TENANT = "attparity";
  private static final String TENANT_B = "attparityb";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
  private static final String SCHEMA_B = TENANT_B + "_mod_service_interaction";
  private static final String USER_ID = "3b0d31de-2f11-4e5a-8b7a-9c5c1e60be1a";
  private static boolean initialized;

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

  @BeforeEach
  void enableTenants() throws Exception {
    if (initialized) {
      return;
    }
    for (var tenant : new String[] {TENANT, TENANT_B}) {
      mockMvc.perform(post("/_/tenant")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-okapi-tenant", tenant)
              .header("x-okapi-url", "http://localhost:9130")
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\"}"))
          .andExpect(status().is2xxSuccessful());
    }
    initialized = true;
  }

  // ---------------------------------------------------------------- helpers

  private SignedJWT requestToken(String tenant, Map<String, String> extraHeaders) throws Exception {
    var request = get("/servint/attestation/token").header("x-okapi-tenant", tenant);
    for (var header : extraHeaders.entrySet()) {
      request = request.header(header.getKey(), header.getValue());
    }
    var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    JsonNode body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("status").asText()).isEqualTo("OK");
    assertThat(body.properties()).hasSize(2);
    return SignedJWT.parse(body.path("token").asText());
  }

  /**
   * db_key_pair timestamps are naive UTC wall-time (legacy TIMESTAMP
   * WITHOUT TIME ZONE); JDBC would otherwise apply the JVM zone.
   */
  private static Timestamp utcNaive(Instant instant) {
    return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
  }

  private static Instant utcInstant(Object naiveTimestamp) {
    return ((Timestamp) naiveTimestamp).toLocalDateTime().toInstant(ZoneOffset.UTC);
  }

  private RSAPublicKey publicKeyOf(String base64X509) throws Exception {
    return (RSAPublicKey) KeyFactory.getInstance("RSA")
        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64X509)));
  }

  private String insertKeyPair(String schema, KeyPair keyPair, Instant availableFrom) {
    var id = UUID.randomUUID().toString();
    jdbcTemplate.update("insert into " + schema + ".db_key_pair "
            + "(kp_id, kp_version, kp_available_from, kp_expires_at, kp_usage, kp_alg, "
            + "kp_public_key, kp_private_key) values (?, 0, ?, ?, 'extApp', 'RSA', ?, ?)",
        id, utcNaive(availableFrom),
        utcNaive(availableFrom.plus(730, ChronoUnit.DAYS)),
        Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
        Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
    return id;
  }

  // ------------------------------------------------------------------ tests

  @Test
  @Order(1)
  void tokenCarriesTheAttestationClaimSet() throws Exception {
    var jwt = requestToken(TENANT, Map.of("x-okapi-user-id", USER_ID));

    var header = jwt.getHeader();
    assertThat(header.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
    assertThat(header.getType()).isEqualTo(JOSEObjectType.JWT);
    // kid is the signing key-pair record id (deviation D-21), not the
    // legacy usage/audience string.
    assertThat(UUID.fromString(header.getKeyID())).isNotNull();

    var claims = jwt.getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo("FOLIO::mod-service-interaction");
    assertThat(claims.getSubject()).isEqualTo(USER_ID);
    assertThat(claims.getAudience()).containsExactly("extApp");
    assertThat(claims.getStringClaim("tenant")).isEqualTo(TENANT);
    assertThat(UUID.fromString(claims.getJWTID())).isNotNull();
    var lifetime = claims.getExpirationTime().getTime() - claims.getIssueTime().getTime();
    assertThat(lifetime).isEqualTo(300_000L);
    assertThat(claims.getIssueTime().toInstant())
        .isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
  }

  @Test
  @Order(2)
  void missingKeyPairIsCreatedOnDemandAndReusedFromCache() throws Exception {
    var rows = jdbcTemplate.queryForList("select * from " + SCHEMA + ".db_key_pair");
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.get("kp_usage")).isEqualTo("extApp");
    assertThat(row.get("kp_alg")).isEqualTo("RSA");
    var availableFrom = utcInstant(row.get("kp_available_from"));
    var expiresAt = utcInstant(row.get("kp_expires_at"));
    assertThat(availableFrom).isCloseTo(Instant.now(), within(60, ChronoUnit.SECONDS));
    assertThat(expiresAt).isEqualTo(availableFrom.plus(730, ChronoUnit.DAYS));

    var storedPublicKey = publicKeyOf((String) row.get("kp_public_key"));
    var first = requestToken(TENANT, Map.of("x-okapi-user-id", USER_ID));
    assertThat(first.verify(new RSASSAVerifier(storedPublicKey))).isTrue();
    assertThat(first.getHeader().getKeyID()).isEqualTo(row.get("kp_id"));

    // A second call signs with the cached key and creates no further row.
    var second = requestToken(TENANT, Map.of("x-okapi-user-id", USER_ID));
    assertThat(second.verify(new RSASSAVerifier(storedPublicKey))).isTrue();
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from " + SCHEMA + ".db_key_pair", Long.class)).isEqualTo(1);
  }

  @Test
  @Order(3)
  void earliestValidKeySignsTheToken() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var earliest = generator.generateKeyPair();
    var newer = generator.generateKeyPair();
    var future = generator.generateKeyPair();
    var now = Instant.now();
    var earliestId = insertKeyPair(SCHEMA_B, earliest, now.minus(10, ChronoUnit.DAYS));
    insertKeyPair(SCHEMA_B, newer, now.minus(1, ChronoUnit.DAYS));
    insertKeyPair(SCHEMA_B, future, now.plus(30, ChronoUnit.DAYS));

    var jwt = requestToken(TENANT_B, Map.of("x-okapi-user-id", USER_ID));
    assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) earliest.getPublic()))).isTrue();
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(earliestId);
    assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) newer.getPublic()))).isFalse();
    assertThat(jwt.getJWTClaimsSet().getStringClaim("tenant")).isEqualTo(TENANT_B);
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from " + SCHEMA_B + ".db_key_pair", Long.class)).isEqualTo(3);
  }

  @Test
  @Order(4)
  void subjectFallsBackToTokenClaimThenUnknown() throws Exception {
    var tokenUserId = UUID.randomUUID().toString();
    var okapiToken = new SignedJWT(
        new JWSHeader(JWSAlgorithm.HS256),
        new JWTClaimsSet.Builder().subject("someone").claim("user_id", tokenUserId).build());
    okapiToken.sign(new MACSigner(new byte[32]));

    var fromToken = requestToken(TENANT, Map.of("x-okapi-token", okapiToken.serialize()));
    assertThat(fromToken.getJWTClaimsSet().getSubject()).isEqualTo(tokenUserId);

    var anonymous = requestToken(TENANT, Map.of());
    assertThat(anonymous.getJWTClaimsSet().getSubject()).isEqualTo("UNKNOWN");
  }
}
