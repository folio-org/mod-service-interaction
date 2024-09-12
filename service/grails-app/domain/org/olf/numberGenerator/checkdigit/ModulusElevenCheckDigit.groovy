package org.olf.numberGenerator.checkdigit;

import java.util.Arrays;

import org.apache.commons.validator.routines.checkdigit.ModulusCheckDigit;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;

import org.apache.commons.validator.GenericValidator;
import org.apache.commons.validator.routines.CodeValidator;

/* public class ModulusElevenCheckDigit {
  public ModulusElevenCheckDigit() {
  }
} */

public class ModulusElevenCheckDigit extends ModulusCheckDigit {
  private final int[] postitionWeight;
  private final boolean useRightPos;
  private final boolean sumWeightedDigits;

  public ModulusElevenCheckDigit(final int[] postitionWeight) {
    this(postitionWeight, false, false);
  }

  public ModulusElevenCheckDigit(final int[] postitionWeight, final boolean useRightPos) {
    this(postitionWeight, useRightPos, false);
  }

  public ModulusElevenCheckDigit(final int[] postitionWeight, final boolean useRightPos, final boolean sumWeightedDigits) {
    super(MODULUS_11);
    this.postitionWeight = Arrays.copyOf(postitionWeight, postitionWeight.length);
    this.useRightPos = useRightPos;
    this.sumWeightedDigits = sumWeightedDigits;
  }

  @Override
  protected String toCheckDigit(final int charValue) throws CheckDigitException {
    if (charValue == 10) {
      return "0";
    }
    return super.toCheckDigit(charValue);
  }

  @Override
  protected int toInt(final char character, final int leftPos, final int rightPos) throws CheckDigitException {
    if (useRightPos && rightPos == 1 && character == '0') {
      return 10;
    }
    
    if (!useRightPos && leftPos == 1 && character == '0') {
      return 10;
    }
    return super.toInt(character, leftPos, rightPos);
  }

  @Override
  public boolean isValid(final String code) {
    if (GenericValidator.isBlankOrNull(code)) {
      return false;
    }
    if (!Character.isDigit(code.charAt(code.length() - 1))) {
      return false;
    }
    return super.isValid(code);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "M11[postitionWeight=" + Arrays.toString(postitionWeight) + ", useRightPos="
      + useRightPos + ", sumWeightedDigits=" + sumWeightedDigits + "]";
  }

  @Override
  protected int weightedValue(final int charValue, final int leftPos, final int rightPos) {
    final int pos = useRightPos ? rightPos : leftPos;
    final int weight = postitionWeight[(pos - 1) % postitionWeight.length];
    int weightedValue = charValue * weight;
    if (sumWeightedDigits) {
      weightedValue = sumDigits(weightedValue);
    }
    return weightedValue;
  }
}