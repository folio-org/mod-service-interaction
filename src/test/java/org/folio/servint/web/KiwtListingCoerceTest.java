package org.folio.servint.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Characterization of {@link KiwtListing#coerce}: pins the exact per-type
 * coercion behavior (trim rules, drop-on-failure, unsupported-type drop) so the
 * complexity-reducing refactor is provably behavior-preserving. Every branch of
 * the legacy value-coercion contract (REQ-022 AC4) is exercised directly.
 */
class KiwtListingCoerceTest {

  @Nested
  class Strings {
    @Test
    void returnsRawStringWithoutTrimming() {
      // legacy: strings pass through verbatim, whitespace and all
      assertSame(" abc ", KiwtListing.coerce(String.class, " abc "));
    }
  }

  @Nested
  class Booleans {
    @Test
    void trimsThenValueOfForBoxed() {
      assertEquals(Boolean.TRUE, KiwtListing.coerce(Boolean.class, "  true "));
    }

    @Test
    void trimsThenValueOfForPrimitive() {
      assertEquals(Boolean.TRUE, KiwtListing.coerce(boolean.class, "true"));
    }

    @Test
    void nonBooleanTextBecomesFalseNeverDrops() {
      // Boolean.valueOf never throws — "notabool" is simply false (legacy quirk)
      assertEquals(Boolean.FALSE, KiwtListing.coerce(Boolean.class, "notabool"));
    }
  }

  @Nested
  class Longs {
    @Test
    void trimsThenParses() {
      assertEquals(42L, KiwtListing.coerce(Long.class, " 42 "));
    }

    @Test
    void primitiveVariantParses() {
      assertEquals(42L, KiwtListing.coerce(long.class, "42"));
    }

    @Test
    void unparseableDropsClause() {
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(Long.class, "x"));
    }
  }

  @Nested
  class Integers {
    @Test
    void trimsThenParses() {
      assertEquals(7, KiwtListing.coerce(Integer.class, " 7 "));
    }

    @Test
    void primitiveVariantParses() {
      assertEquals(7, KiwtListing.coerce(int.class, "7"));
    }

    @Test
    void unparseableDropsClause() {
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(Integer.class, "x"));
    }
  }

  @Nested
  class Uuids {
    @Test
    void parsesValidUuid() {
      var uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
      assertEquals(uuid, KiwtListing.coerce(UUID.class, " 11111111-1111-1111-1111-111111111111 "));
    }

    @Test
    void invalidUuidDropsClause() {
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(UUID.class, "not-a-uuid"));
    }
  }

  @Nested
  class Temporals {
    @Test
    void parsesInstant() {
      assertEquals(Instant.parse("2020-01-01T00:00:00Z"),
          KiwtListing.coerce(Instant.class, " 2020-01-01T00:00:00Z "));
    }

    @Test
    void invalidInstantDropsClause() {
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(Instant.class, "nope"));
    }

    @Test
    void parsesLocalDate() {
      assertEquals(LocalDate.parse("2020-01-01"),
          KiwtListing.coerce(LocalDate.class, " 2020-01-01 "));
    }

    @Test
    void invalidLocalDateDropsClause() {
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(LocalDate.class, "nope"));
    }

    @Test
    void parsesLocalDateTime() {
      assertEquals(LocalDateTime.parse("2020-01-01T00:00:00"),
          KiwtListing.coerce(LocalDateTime.class, " 2020-01-01T00:00:00 "));
    }

    @Test
    void invalidLocalDateTimeDropsClause() {
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(LocalDateTime.class, "nope"));
    }
  }

  @Nested
  class UnsupportedTypes {
    @Test
    void unsupportedTargetTypeDropsClause() {
      // e.g. association equality or an unmapped scalar — never coerced
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(Double.class, "1.0"));
    }

    @Test
    void nullTypeDropsClauseNeverThrowsNpe() {
      // defensive: an unresolved type must drop the clause, not blow up
      assertThrows(KiwtListing.DroppedClauseException.class,
          () -> KiwtListing.coerce(null, "1.0"));
    }
  }
}
