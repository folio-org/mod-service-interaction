package org.folio.servint.service.numgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Characterization of the maximum-check classifier extracted from
 * {@code deriveMaximumCheck}: the legacy save-time rule mapping a sequence's
 * (maximumNumber, threshold, nextValue) to a MaximumCheck refdata value — or
 * null when no maximum is configured. Pins every branch so the extraction is
 * provably behavior-preserving.
 */
class MaximumCheckValueTest {

  @Test
  void nullWhenNoMaximumConfigured() {
    assertNull(NumberGeneratorService.maximumCheckValue(null, null, 5L));
  }

  @Test
  void nullWhenMaxAbsentEvenWithThreshold() {
    // threshold alone (no maximumNumber) never classifies — legacy requires max
    assertNull(NumberGeneratorService.maximumCheckValue(null, 10L, 5L));
  }

  @Test
  void atMaximumWhenNextExceedsMax() {
    assertEquals("at_maximum", NumberGeneratorService.maximumCheckValue(10L, 8L, 11L));
  }

  @Test
  void atMaximumEvenWithoutThreshold() {
    // next > max wins before any threshold consideration
    assertEquals("at_maximum", NumberGeneratorService.maximumCheckValue(10L, null, 11L));
  }

  @Test
  void overThresholdWhenNextExceedsThresholdButNotMax() {
    assertEquals("over_threshold", NumberGeneratorService.maximumCheckValue(10L, 5L, 6L));
  }

  @Test
  void belowThresholdWhenNextWithinThreshold() {
    assertEquals("below_threshold", NumberGeneratorService.maximumCheckValue(10L, 5L, 4L));
  }

  @Test
  void belowThresholdWhenNextNullButMaxAndThresholdPresent() {
    // next == null cannot exceed max or threshold, so it falls to below_threshold
    assertEquals("below_threshold", NumberGeneratorService.maximumCheckValue(10L, 5L, null));
  }

  @Test
  void nullWhenMaxPresentButThresholdAbsentAndNextWithinMax() {
    // max set, threshold null, next <= max: no over/below classification, null
    assertNull(NumberGeneratorService.maximumCheckValue(10L, null, 5L));
  }
}
