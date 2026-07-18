package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Review finding F-05: concurrent first use of a fresh generator+sequence
 * pair raced through the lazy find-then-insert initialisation — both requests
 * missed the locked read, both inserted, and one surfaced a uniqueness
 * violation on the wire. The getNextNumber contract is an always-200
 * envelope, so every racing request must answer 200 and the surviving
 * sequence must hand out distinct, gap-free values.
 *
 * <p>Review finding F-17: the controller's class-level transaction pinned one
 * pooled connection per request while the service demanded a second
 * (REQUIRES_NEW), deadlocking the default 10-connection Hikari pool at
 * pool-size concurrency. Both tests therefore run at or beyond pool size:
 * getNextNumber must hold only the service's own transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NumberGeneratorConcurrencyIT {

  private static final String TENANT = "ngrace";
  private static final int ROUNDS = 5;
  private static final int CONCURRENT_REQUESTS = 16;
  private static final int POOL_STARVATION_REQUESTS = 20;
  private static boolean tenantInitialized;

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

  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void enableTenant() throws Exception {
    if (!tenantInitialized) {
      mockMvc.perform(post("/_/tenant")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-okapi-tenant", TENANT)
              .header("x-okapi-url", "http://localhost:9130")
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\"}"))
          .andExpect(status().is2xxSuccessful());
      tenantInitialized = true;
    }
  }

  @Test
  void concurrentFirstUseAlwaysAnswers200WithDistinctSequentialValues() throws Exception {
    var pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
    try {
      for (int round = 1; round <= ROUNDS; round++) {
        var generator = "race" + round;
        var requests = submitConcurrentGetNextNumber(pool, CONCURRENT_REQUESTS, generator);

        List<String> values = new ArrayList<>();
        for (var request : requests) {
          var result = request.get(60, TimeUnit.SECONDS);
          assertThat(result.getResponse().getStatus())
              .as("round %s: every racing request must answer 200", round)
              .isEqualTo(200);
          var envelope = json.readTree(result.getResponse().getContentAsString());
          assertThat(envelope.path("status").asText())
              .as("round %s: envelope %s", round, envelope)
              .isEqualTo("OK");
          assertThat(envelope.path("generator").asText()).isEqualTo(generator);
          assertThat(envelope.path("sequence").asText()).isEqualTo("seq");
          values.add(envelope.path("nextValue").asText());
        }

        var expected = IntStream.rangeClosed(1, CONCURRENT_REQUESTS)
            .mapToObj(n -> String.format("%09d", n))
            .toArray(String[]::new);
        assertThat(values)
            .as("round %s: one distinct sequential value per request", round)
            .containsExactlyInAnyOrder(expected);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentRequestsBeyondPoolSizeDoNotStarveTheConnectionPool() throws Exception {
    var pool = Executors.newFixedThreadPool(POOL_STARVATION_REQUESTS);
    try {
      var requests = submitConcurrentGetNextNumber(pool, POOL_STARVATION_REQUESTS, "poolstarve");

      List<String> values = new ArrayList<>();
      for (var request : requests) {
        var result = request.get(60, TimeUnit.SECONDS);
        assertThat(result.getResponse().getStatus())
            .as("every request beyond pool size must answer 200")
            .isEqualTo(200);
        var envelope = json.readTree(result.getResponse().getContentAsString());
        assertThat(envelope.path("status").asText())
            .as("envelope %s", envelope)
            .isEqualTo("OK");
        values.add(envelope.path("nextValue").asText());
      }

      var expected = IntStream.rangeClosed(1, POOL_STARVATION_REQUESTS)
          .mapToObj(n -> String.format("%09d", n))
          .toArray(String[]::new);
      assertThat(values)
          .as("one distinct gap-free value per request")
          .containsExactlyInAnyOrder(expected);
    } finally {
      pool.shutdownNow();
    }
  }

  private List<Future<MvcResult>> submitConcurrentGetNextNumber(ExecutorService pool,
      int count, String generator) {
    var barrier = new CyclicBarrier(count);
    List<Future<MvcResult>> requests = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      requests.add(pool.submit(() -> {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(get("/servint/numberGenerators/getNextNumber")
                .param("generator", generator)
                .param("sequence", "seq")
                .header("x-okapi-tenant", TENANT))
            .andReturn();
      }));
    }
    return requests;
  }
}
