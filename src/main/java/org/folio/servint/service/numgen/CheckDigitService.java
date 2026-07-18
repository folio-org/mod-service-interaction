package org.folio.servint.service.numgen;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.EAN13CheckDigit;
import org.apache.commons.validator.routines.checkdigit.ISBN10CheckDigit;
import org.apache.commons.validator.routines.checkdigit.ISSNCheckDigit;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.apache.commons.validator.routines.checkdigit.ModulusTenCheckDigit;
import org.springframework.stereotype.Service;

/**
 * Verbatim port of the legacy check-digit dispatch (commons-validator, the
 * same library the Grails module used). Algorithm keys are normalized
 * refdata values. Unknown algorithm throws — the legacy behavior (HTTP 500).
 */
@Service
public class CheckDigitService {

  public String calculate(String algorithm, String valueToCheck) {
    try {
      return switch (algorithm) {
        case "ean13" -> new EAN13CheckDigit().calculate(valueToCheck);
        case "isbn10checkdigit" -> new ISBN10CheckDigit().calculate(valueToCheck);
        case "issncheckdigit" -> new ISSNCheckDigit().calculate(valueToCheck);
        case "luhncheckdigit" -> new LuhnCheckDigit().calculate(valueToCheck);
        // "R" variants: commons ModulusTen returns the inverted digit by default,
        // so invert again — same as legacy.
        case "1793_ltr_mod10_r" ->
            invert(new ModulusTenCheckDigit(new int[] {1, 7, 9, 3}, false).calculate(valueToCheck));
        case "12_ltr_mod10_r" ->
            invert(new ModulusTenCheckDigit(new int[] {1, 2}, false).calculate(valueToCheck));
        default -> throw new IllegalStateException("Unknown check digit algorithm " + algorithm);
      };
    } catch (CheckDigitException e) {
      throw new IllegalStateException("Check digit calculation failed for algorithm " + algorithm, e);
    }
  }

  private String invert(String checksum) {
    return String.valueOf((10 - Integer.parseInt(checksum)) % 10);
  }
}
