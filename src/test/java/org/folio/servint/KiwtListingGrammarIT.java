package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * Grammar/paging/tolerance parity corpus for the kiwt listing engine
 * (REQ-022 AC1-AC5), transcribing the review probe matrix (F-04/F-14) plus
 * the legacy SimpleLookupWtk operator table against the numgen sequence
 * listing. Fixture nextValue values 2, 6, 9, 2147483649 pin the headline
 * probe: filters=nextValue&gt;5 must answer exactly three rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KiwtListingGrammarIT {

  private static final String TENANT = "kiwtgrammar";
  private static final String SEQUENCES = "/servint/numberGeneratorSequences";
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
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\","
                  + " \"parameters\": [{\"key\": \"loadReference\", \"value\": \"true\"}]}"))
          .andExpect(status().is2xxSuccessful());
      tenantInitialized = true;
    }
  }

  // ---------------------------------------------------------------- helpers

  private String createGenerator(String code, String name) throws Exception {
    var body = json.createObjectNode().put("code", code).put("name", name);
    MvcResult result = mockMvc.perform(post("/servint/numberGenerators")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content(body.toString()))
        .andExpect(status().isCreated())
        .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private ObjectNode seq(String generatorId, String code, String name, long nextValue) {
    var node = json.createObjectNode();
    node.putObject("owner").put("id", generatorId);
    node.put("code", code).put("name", name).put("nextValue", nextValue);
    return node;
  }

  private void createSequence(ObjectNode body) throws Exception {
    mockMvc.perform(post(SEQUENCES)
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content(body.toString()))
        .andExpect(status().isCreated());
  }

  /** GET the sequence listing with the given repeatable param pairs, expecting 200. */
  private JsonNode list(String... paramPairs) throws Exception {
    var request = get(SEQUENCES).header("x-okapi-tenant", TENANT);
    for (int i = 0; i < paramPairs.length; i += 2) {
      request = request.param(paramPairs[i], paramPairs[i + 1]);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private List<String> codes(JsonNode array) {
    var out = new ArrayList<String>();
    array.forEach(row -> out.add(row.path("code").asText()));
    return out;
  }

  private void assertCodes(JsonNode array, String... expected) {
    assertThat(codes(array)).containsExactlyInAnyOrder(expected);
  }

  // ---------------------------------------------------------------- fixture

  @Test
  @Order(1)
  void fixture() throws Exception {
    var kg1 = createGenerator("kg1", "Kiwt Grammar One");
    var kg2 = createGenerator("kg2", "Other Gen");
    createSequence(seq(kg1, "alpha", "Alpha One", 2).put("prefix", "p"));
    var beta = seq(kg1, "beta", "Beta Two", 6);
    beta.putObject("checkDigitAlgo").put("value", "ean13");
    createSequence(beta);
    createSequence(seq(kg1, "gamma", "gamma three", 9));
    createSequence(seq(kg1, "delta", "Delta Big", 2147483649L));
    createSequence(seq(kg2, "onefive", "Alphabet Soup", 4));

    assertCodes(list("filters", "owner.code==kg1"), "alpha", "beta", "gamma", "delta");
  }

  // ----------------------------------------------------- AC1: operators

  @Test
  @Order(2)
  void comparisonOperators() throws Exception {
    // the headline review probe: legacy answers exactly the 3 rows above 5
    assertCodes(list("filters", "nextValue>5"), "beta", "gamma", "delta");
    assertCodes(list("filters", "nextValue>=6"), "beta", "gamma", "delta");
    assertCodes(list("filters", "owner.code==kg1", "filters", "nextValue<6"), "alpha");
    assertCodes(list("filters", "owner.code==kg1", "filters", "nextValue<=6"), "alpha", "beta");
    // value above Integer.MAX_VALUE proves Long coercion (F-03)
    assertCodes(list("filters", "nextValue>2147483648"), "delta");
    // value-first subject position: legacy inverts the operator
    assertCodes(list("filters", "5<nextValue"), "beta", "gamma", "delta");
  }

  @Test
  @Order(3)
  void equalityAndEmptyRightSide() throws Exception {
    assertCodes(list("filters", "code==alpha"), "alpha");
    assertCodes(list("filters", "code=alpha"), "alpha"); // single = is equality too
    assertCodes(list("filters", "owner.code==kg1", "filters", "code!=alpha"),
        "beta", "gamma", "delta");
    assertCodes(list("filters", "owner.code==kg1", "filters", "code<>alpha"),
        "beta", "gamma", "delta");
    // empty right side drops the clause (legacy-identical; nullness needs the
    // `is null` predicates — covered in the specials test)
    assertCodes(list("filters", "owner.code==kg1", "filters", "prefix=="),
        "alpha", "beta", "gamma", "delta");
    assertCodes(list("filters", "owner.code==kg1", "filters", "prefix!="),
        "alpha", "beta", "gamma", "delta");
  }

  @Test
  @Order(4)
  void caseInsensitiveEqualityAndContainment() throws Exception {
    assertCodes(list("filters", "name=i=ALPHA ONE"), "alpha");
    assertCodes(list("filters", "owner.code==kg1", "filters", "name=~eta"), "beta");
    assertCodes(list("filters", "owner.code==kg1", "filters", "name!~eta"),
        "alpha", "gamma", "delta");
  }

  @Test
  @Order(5)
  void nullnessSpecialsOnNullableAssociation() throws Exception {
    assertCodes(list("filters", "owner.code==kg1", "filters", "checkDigitAlgo is null"),
        "alpha", "gamma", "delta");
    assertCodes(list("filters", "owner.code==kg1", "filters", "checkDigitAlgo is not null"), "beta");
    assertCodes(list("filters", "owner.code==kg1", "filters", "checkDigitAlgo is set"), "beta");
    assertCodes(list("filters", "owner.code==kg1", "filters", "checkDigitAlgo is not set"),
        "alpha", "gamma", "delta");
  }

  // ----------------------------------------------- AC2: compounds & ranges

  @Test
  @Order(6)
  void compounds() throws Exception {
    assertCodes(list("filters", "code==alpha&&nextValue==2"), "alpha");
    assertThat(list("filters", "code==alpha&&nextValue==6")).isEmpty();
    assertCodes(list("filters", "owner.code==kg1", "filters", "code==alpha||code==beta"),
        "alpha", "beta");
    // three-term || chain is a FLAT disjunction (the legacy top-two pairing
    // quirk — a AND (b OR c) — is deliberately not replicated)
    assertCodes(list("filters", "owner.code==kg1",
            "filters", "code==alpha||code==beta||code==gamma"),
        "alpha", "beta", "gamma");
    assertCodes(list("filters", "owner.code==kg1", "filters", "!(code==alpha)"),
        "beta", "gamma", "delta");
    assertCodes(list("filters", "(code==alpha||code==beta)&&nextValue<5"), "alpha");
  }

  @Test
  @Order(7)
  void rangesAndRepeatedFilters() throws Exception {
    // middle-subject range
    assertCodes(list("filters", "owner.code==kg1", "filters", "3<nextValue<10"), "beta", "gamma");
    assertCodes(list("filters", "owner.code==kg1", "filters", "3<=nextValue<=9"), "beta", "gamma");
    // each repeated filters parameter is AND-ed
    assertCodes(list("filters", "nextValue>5", "filters", "nextValue<10"), "beta", "gamma");
  }

  // --------------------------------------------------------- AC3: paging

  @Test
  @Order(8)
  void pagingDefaultsAliasesAndClamp() throws Exception {
    // perPage=0 behaves as unset: default page size 10 (legacy Groovy falsy-0)
    assertThat(list("stats", "true", "perPage", "0").path("pageSize").asInt()).isEqualTo(10);
    // perPage above 100 answers 200, silently clamped (legacy Math.min; never 400)
    assertThat(list("stats", "true", "perPage", "101").path("pageSize").asInt()).isEqualTo(100);
    // max is a perPage alias; perPage wins when both are sent
    assertThat(list("stats", "true", "max", "5").path("pageSize").asInt()).isEqualTo(5);
    assertThat(list("stats", "true", "perPage", "7", "max", "3").path("pageSize").asInt())
        .isEqualTo(7);
  }

  @Test
  @Order(9)
  void pageParameterAndOffsetDerivation() throws Exception {
    var byPage = list("filters", "owner.code==kg1", "sort", "code;asc",
        "perPage", "2", "page", "2", "stats", "true");
    assertThat(codes(byPage.path("results"))).containsExactly("delta", "gamma");
    assertThat(byPage.path("page").asInt()).isEqualTo(2);

    var byOffset = list("filters", "owner.code==kg1", "sort", "code;asc",
        "perPage", "2", "offset", "2", "stats", "true");
    assertThat(codes(byOffset.path("results"))).containsExactly("delta", "gamma");
    assertThat(byOffset.path("page").asInt()).isEqualTo(2);

    // page wins over offset when both are sent
    var pageWins = list("filters", "owner.code==kg1", "sort", "code;asc",
        "perPage", "2", "page", "1", "offset", "2", "stats", "true");
    assertThat(codes(pageWins.path("results"))).containsExactly("alpha", "beta");

    var descending = list("filters", "owner.code==kg1", "sort", "nextValue;desc");
    assertThat(codes(descending)).containsExactly("delta", "gamma", "beta", "alpha");
  }

  @Test
  @Order(10)
  void statsEnvelopeShape() throws Exception {
    var envelope = list("filters", "owner.code==kg1", "stats", "true");
    var fields = new ArrayList<String>();
    envelope.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactly(
        "results", "pageSize", "page", "totalPages", "meta", "totalRecords", "total");
    assertThat(envelope.path("meta").isObject()).isTrue();
    assertThat(envelope.path("meta").isEmpty()).isTrue();
    assertThat(envelope.path("totalRecords").asLong()).isEqualTo(4);
    assertThat(envelope.path("total").asLong()).isEqualTo(envelope.path("totalRecords").asLong());
    assertThat(envelope.path("pageSize").asInt()).isEqualTo(10);
    assertThat(envelope.path("page").asInt()).isEqualTo(1);
    assertThat(envelope.path("totalPages").asInt()).isEqualTo(1);
  }

  // ------------------------------------------------------ AC4: tolerance

  @Test
  @Order(11)
  void toleranceForBadInput() throws Exception {
    // unknown sort property: skipped, 200
    assertCodes(list("filters", "owner.code==kg1", "sort", "notAProp;asc"),
        "alpha", "beta", "gamma", "delta");
    // syntactically malformed filter clause: dropped, 200
    assertCodes(list("filters", "owner.code==kg1", "filters", "garbage!!!"),
        "alpha", "beta", "gamma", "delta");
    // unknown match property: skipped, 200
    assertCodes(list("filters", "owner.code==kg1", "match", "notAProp", "term", "x"),
        "alpha", "beta", "gamma", "delta");
    // well-formed filter naming an unknown property: 400 invalid.property
    MvcResult result = mockMvc.perform(get(SEQUENCES)
            .param("filters", "notAProp==x")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isBadRequest())
        .andReturn();
    var error = json.readTree(result.getResponse().getContentAsString()).path("errors").get(0);
    assertThat(error.path("code").asText()).isEqualTo("invalid.property");
    assertThat(error.path("message").asText()).isEqualTo("Invalid property: notAProp");
  }

  // ------------------------------------------------------ AC5: match/term

  @Test
  @Order(12)
  void termMatching() throws Exception {
    // case-insensitive containment on a single property
    assertCodes(list("filters", "owner.code==kg1", "match", "name", "term", "alpha"), "alpha");
    // several terms AND within a property ("Alphabet Soup" has alpha but not one)
    assertCodes(list("match", "name", "term", "alpha one"), "alpha");
    // OR across match properties; the alpha row matches name AND code yet
    // appears once (distinct results)
    assertCodes(list("match", "name", "match", "code", "term", "alpha"), "alpha", "onefive");
    // a double-quoted phrase stays intact across match properties
    assertCodes(list("match", "name", "match", "code", "term", "\"Alpha One\""), "alpha");
    assertThat(list("match", "name", "match", "code", "term", "\"One Alpha\"")).isEmpty();
    // the text block ANDs with the filter block
    assertThat(list("match", "name", "term", "alpha", "filters", "nextValue>5")).isEmpty();
  }

  // --------------------------------------- R16: F-21 directed parser rows

  @Test
  @Order(13)
  void f21DirectedRowsBehaveAsGoverned() throws Exception {
    // Boolean.valueOf coerces garbage to false; no disabled rows exist -> empty.
    // Legacy 500s (ConversionFailedException) — registered deviation D-28.
    assertThat(list("filters", "enabled==notabool")).isEmpty();
    // The escaped pair is RAW literal value text, backslash retained —
    // alph\a matches only a row whose code is literally alph\a, so nothing
    // here (legacy-identical; the former unescaping deviation D-29 is
    // retired by the r31 oracle: legacy never unescapes).
    assertThat(list("filters", "code==alph\\a")).isEmpty();
    // Unbalanced parenthesis: ParseException -> whole filter dropped, the
    // listing is unfiltered (all five fixture rows appear alongside the
    // loadReference-seeded sequences). Legacy 500s — registered deviation D-30.
    assertThat(codes(list("filters", "(code==alpha", "perPage", "100")))
        .contains("alpha", "beta", "gamma", "delta", "onefive");
    // Uncoercible numeric value: clause dropped, listing unfiltered.
    // Legacy 500s — registered deviation D-30.
    assertThat(codes(list("filters", "nextValue>notanumber", "perPage", "100")))
        .contains("alpha", "beta", "gamma", "delta", "onefive");
  }

  // ------------------------------- R22: F-26 compound empty-RHS rows (AC6)

  @Test
  @Order(14)
  void emptyRightSidesInsideCompoundsAbsorbGreedily() throws Exception {
    // Trailing empty leaf: the parameter collapses into ONE comparison of the
    // first subject/operator against the literal remainder (legacy greedy
    // value consumption, D-32; oracle p1/p2/d2) — nothing matches normally...
    assertThat(list("filters", "code==alpha&&prefix==")).isEmpty();
    assertThat(list("filters", "(code==alpha||prefix==)")).isEmpty();
    assertThat(list("filters", "code==alpha||prefix==")).isEmpty();
    // ...an empty leaf followed by text absorbs that text into its value
    // (oracle sd1/p9): prefix IS a sequence property, so 200 with no match
    assertThat(list("filters", "prefix==&&code==alpha")).isEmpty();
    assertThat(list("filters", "code==alpha&&prefix==&&code==alpha")).isEmpty();
    // ...and each shape matches a row whose value IS the absorbed literal
    // (the oracle's discriminator rows, reproduced here)
    var kg3 = createGenerator("kg3", "Absorption Gen");
    createSequence(seq(kg3, "alpha&&prefix==", "Absorb And", 1));
    createSequence(seq(kg3, "alpha||prefix==", "Absorb Or", 1));
    createSequence(seq(kg3, "absorbpfx", "Absorb Prefix", 1).put("prefix", "&&code==alpha"));
    assertCodes(list("filters", "code==alpha&&prefix=="), "alpha&&prefix==");
    assertCodes(list("filters", "(code==alpha||prefix==)"), "alpha||prefix==");
    assertCodes(list("filters", "prefix==&&code==alpha"), "absorbpfx");
    // An absorbed subject that is not a property answers 400 invalid-property
    // (oracle d1: legacy 400 where the leading subject is unknown)
    MvcResult bad = mockMvc.perform(get(SEQUENCES)
            .param("filters", "notAProp==&&code==alpha")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isBadRequest())
        .andReturn();
    var error = json.readTree(bad.getResponse().getContentAsString()).path("errors").get(0);
    assertThat(error.path("code").asText()).isEqualTo("invalid.property");
    assertThat(error.path("message").asText()).isEqualTo("Invalid property: notAProp");
  }

  @Test
  @Order(15)
  void emptyRightSideDropsBeforeValidationAndJoins() throws Exception {
    var unfiltered = codes(list("perPage", "100"));
    // Top-level empty RHS on an unknown property: dropped, NOT 400 — the drop
    // precedes property validation (AC1; oracle p6)
    assertThat(codes(list("filters", "notAProp==", "perPage", "100"))).isEqualTo(unfiltered);
    // Dotted association path with an empty RHS: dropped with NO join residue —
    // the null-checkDigitAlgo rows stay in the listing (AC1; oracle p4; the
    // review's failing scratch IT, now committed)
    assertThat(codes(list("filters", "checkDigitAlgo.value==", "perPage", "100")))
        .isEqualTo(unfiltered);
    // Negation containing an empty RHS drops the whole parameter and answers
    // 200 — legacy answered 500 (D-32; oracle p8)
    assertThat(codes(list("filters", "!(prefix==)", "perPage", "100"))).isEqualTo(unfiltered);
    // Uncoercible value inside a compound drops the whole parameter — legacy
    // answered 500 alone or in compound (AC4; oracle x1-x3)
    assertThat(codes(list("filters", "code==alpha&&nextValue==abc", "perPage", "100")))
        .isEqualTo(unfiltered);
  }

  // ------------------- M9 R31: escaped-token / operator-absorption matrix
  //   (oracle docs/migration/evidence/r31-escaped-oracle — F-38)

  @Test
  @Order(16)
  void escapedTokensAreRawLiteralValueText() throws Exception {
    var kg4 = createGenerator("kg4", "Escape Gen");
    createSequence(seq(kg4, "alpha\\&&prefix==", "Esc And Empty", 1));
    createSequence(seq(kg4, "alpha\\||prefix==", "Esc Or Empty", 1));
    createSequence(seq(kg4, "alpha\\&&beta", "Esc And Mid", 1));
    createSequence(seq(kg4, "alpha\\!x", "Esc Bang", 1));
    createSequence(seq(kg4, "alpha\\&&x", "Esc Before Real Op", 1));
    createSequence(seq(kg4, "\"q&&q\"", "Quoted Literal", 1));
    createSequence(seq(kg4, "a\\b", "Backslash NonSpecial", 1));
    createSequence(seq(kg4, "a\\\\b", "Double Backslash", 1));
    createSequence(seq(kg4, "alpha\\&&prefix==&&code==alpha", "Whole Absorb Bait", 1));

    // F-38 rows: the escape steals one ampersand so no structural && forms;
    // the whole remainder is ONE raw value, backslash retained (oracle e1-e3)
    assertCodes(list("filters", "code==alpha\\&&prefix=="), "alpha\\&&prefix==");
    assertCodes(list("filters", "code==alpha\\||prefix=="), "alpha\\||prefix==");
    assertCodes(list("filters", "code==alpha\\&&beta"), "alpha\\&&beta");
    // escaped specials are raw literal text; unescaped ! voids the filter
    // (oracle x1/x4)
    assertCodes(list("filters", "code==alpha\\!x"), "alpha\\!x");
    var unfiltered = codes(list("perPage", "100"));
    assertThat(codes(list("filters", "code==alpha!x", "perPage", "100"))).isEqualTo(unfiltered);
    // a REAL structural token after an escaped one still splits (oracle m1/m2/s17):
    // the And is unsatisfiable even though the whole-absorb bait row exists
    assertThat(list("filters", "code==alpha\\&&prefix==&&code==alpha")).isEmpty();
    assertCodes(list("filters", "code==alpha\\&&x||code==alpha"), "alpha", "alpha\\&&x");
    // quotes are ordinary literal value characters (oracle q2/dq1)
    assertCodes(list("filters", "code==\"q&&q\""), "\"q&&q\"");
    // backslash before a non-special and a double backslash: raw either way
    // (oracle db1/db2)
    assertCodes(list("filters", "code==a\\b"), "a\\b");
    assertCodes(list("filters", "code==a\\\\b"), "a\\\\b");
  }

  @Test
  @Order(17)
  void operatorSpellingsAbsorbIntoValues() throws Exception {
    var kg6 = createGenerator("kg6", "Absorb Spelling Gen");
    createSequence(seq(kg6, "alpha==", "Trailing Eq", 1));
    createSequence(seq(kg6, "alpha==beta", "Mid Eq", 1));
    createSequence(seq(kg6, "alpha!=beta", "Mid Neq", 1));
    createSequence(seq(kg6, "alpha<=beta", "Mid Le", 1));

    // ==, !=, <= (and =~, !~, =i=, >=, <>) are legal INSIDE a value —
    // the leaf collapses to one raw comparison (oracle s1/s4/s10/s13)
    assertCodes(list("filters", "code==alpha=="), "alpha==");
    assertCodes(list("filters", "code==alpha==beta"), "alpha==beta");
    assertCodes(list("filters", "code==alpha!=beta"), "alpha!=beta");
    assertCodes(list("filters", "code==alpha<=beta"), "alpha<=beta");
    // a single < or > cannot live inside a value: a bare trailing one voids
    // the filter (oracle s3); an identifier tail becomes the subject and
    // answers 400 invalid-property (oracle s6/s9)
    var unfiltered = codes(list("perPage", "100"));
    assertThat(codes(list("filters", "code==alpha<", "perPage", "100"))).isEqualTo(unfiltered);
    for (var shape : List.of("code==alpha<beta", "code==alpha=beta")) {
      MvcResult bad = mockMvc.perform(get(SEQUENCES)
              .param("filters", shape)
              .header("x-okapi-tenant", TENANT))
          .andExpect(status().isBadRequest())
          .andReturn();
      var error = json.readTree(bad.getResponse().getContentAsString()).path("errors").get(0);
      assertThat(error.path("message").asText()).isEqualTo("Invalid property: beta");
    }
    // non-identifier tail after a single > drops the clause — legacy's
    // uncaught 500 (oracle s8) is not replicated, same governed family as
    // D-30/D-32
    assertThat(codes(list("filters", "code==alpha>5", "perPage", "100"))).isEqualTo(unfiltered);
  }

  // --------------------------- M9 R32: %/_ wildcard semantics per path
  //   (oracle docs/migration/evidence/r32-wildcard-oracle — F-39, DP-1(a))

  @Test
  @Order(18)
  void wildcardSemanticsPerLookupPath() throws Exception {
    var kg5 = createGenerator("kg5", "Wildcard Gen");
    for (var code : List.of("ab_cd", "abXcd", "ab%cd", "abcd", "abXYcd",
        "ab\\%cd", "ab$2cd")) {
      createSequence(seq(kg5, code, code, 1));
    }

    // =~ / !~: the broken legacy transform — a non-leading % becomes the
    // LITERAL characters $2 (oracle w1/mp1: the planted ab$2cd row matches)
    assertCodes(list("filters", "code=~ab%cd"), "ab$2cd");
    assertCodes(list("filters", "code=~ab%"), "ab$2cd");
    // _ stays a live single-char wildcard; \% and \_ are escaped literals
    // (oracle w2/w4/w5)
    assertCodes(list("filters", "code=~ab_cd"), "ab_cd", "abXcd", "ab%cd");
    assertCodes(list("filters", "code=~ab\\%cd"), "ab%cd");
    assertCodes(list("filters", "code=~ab\\_cd"), "ab_cd");
    // a LEADING % survives as a live wildcard (oracle d2)
    assertThat(codes(list("filters", "code=~%cd", "perPage", "100")))
        .contains("ab_cd", "abXcd", "ab%cd", "abcd", "abXYcd", "ab\\%cd", "ab$2cd")
        .doesNotContain("alpha");
    // !~ negates the same transformed pattern (oracle w7/mp6)
    var notContains = codes(list("filters", "code!~ab%cd", "perPage", "100"));
    assertThat(notContains).contains("ab%cd", "abcd").doesNotContain("ab$2cd");
    // == / != are raw literal comparisons (oracle w9/w12)
    assertCodes(list("filters", "code==ab%cd"), "ab%cd");
    assertCodes(list("filters", "code==ab_cd"), "ab_cd");
    // =i= is the same ilike WITHOUT the contains wrap: transform applies and
    // _ is live even in "equality" (oracle mi1-mi4)
    assertCodes(list("filters", "code=i=ab%cd"), "ab$2cd");
    assertCodes(list("filters", "code=i=ab_cd"), "ab_cd", "abXcd", "ab%cd");
    assertCodes(list("filters", "code=i=AB_CD"), "ab_cd", "abXcd", "ab%cd");
    // match/term passes wildcards through LIVE, \% escaped literal
    // (oracle t1/t2/t4/mp8)
    assertThat(codes(list("match", "code", "term", "ab%cd", "perPage", "100")))
        .contains("ab_cd", "abXcd", "ab%cd", "abcd", "abXYcd", "ab\\%cd", "ab$2cd")
        .doesNotContain("alpha");
    assertCodes(list("match", "code", "term", "ab_cd"), "ab_cd", "abXcd", "ab%cd");
    assertCodes(list("match", "code", "term", "ab\\%cd"), "ab%cd");
    // term=% alone matches every row (oracle d12)
    assertThat(codes(list("match", "code", "term", "%", "perPage", "100")))
        .contains("ab_cd", "ab%cd", "ab$2cd", "alpha", "beta");
    // repeated term parameters: legacy crashes with an uncaught 500 (oracle
    // t6); the port binds them as one comma-joined string and answers 200 —
    // registered deviation D-33
    mockMvc.perform(get(SEQUENCES)
            .param("match", "code").param("term", "ab%cd").param("term", "abcd")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk());
  }
}
