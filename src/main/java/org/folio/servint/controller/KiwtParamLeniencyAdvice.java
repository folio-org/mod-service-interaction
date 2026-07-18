package org.folio.servint.controller;

import java.beans.PropertyEditorSupport;
import java.util.Locale;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Legacy-identical leniency for the kiwt listing query parameters (R9 case 2):
 * Grails read them via {@code params.boolean(..)} / {@code params.int(..)},
 * which silently yield null for unparseable input, so the legacy module
 * answered 200 with default behavior for {@code stats=notabool} or
 * {@code perPage=abc}. Without this advice Spring's strict conversion answers
 * 400 (MethodArgumentTypeMismatchException). Scoped by parameter name to the
 * kiwt params only — every other binding stays strict.
 */
@ControllerAdvice
public class KiwtParamLeniencyAdvice {

  @InitBinder("stats")
  void lenientStats(WebDataBinder binder) {
    binder.registerCustomEditor(Boolean.class, new LenientBooleanEditor());
  }

  @InitBinder({"perPage", "max", "page", "offset"})
  void lenientPaging(WebDataBinder binder) {
    binder.registerCustomEditor(Integer.class, new LenientIntegerEditor());
  }

  /** Spring's usual boolean tokens still convert; anything else becomes null. */
  private static final class LenientBooleanEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) {
      var token = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
      setValue(switch (token) {
        case "true", "on", "yes", "1" -> Boolean.TRUE;
        case "false", "off", "no", "0" -> Boolean.FALSE;
        default -> null;
      });
    }
  }

  /** Numeric text converts as before; anything else becomes null. */
  private static final class LenientIntegerEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) {
      try {
        setValue(text == null ? null : Integer.valueOf(text.trim()));
      } catch (NumberFormatException notNumeric) {
        setValue(null);
      }
    }
  }
}
