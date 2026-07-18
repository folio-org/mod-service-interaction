package org.folio.servint.service.numgen;

import groovy.lang.GroovyShell;
import groovy.text.SimpleTemplateEngine;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Service;

/**
 * Renders number templates with the same engine and sandbox the legacy module
 * used (Groovy SimpleTemplateEngine + SecureASTCustomizer). Templates are
 * tenant data — existing templates with expressions like
 * ${generated_number.substring(0,4)} must keep evaluating identically.
 */
@Service
public class NumberTemplateService {

  public String render(String template, Map<String, Object> parameters) {
    var customizer = new SecureASTCustomizer();
    customizer.setClosuresAllowed(true);
    customizer.setAllowedImports(
        List.of("org.springframework.beans.factory.annotation.Autowired", "java.lang.Object"));
    customizer.setAllowedReceiversClasses(
        List.of(Math.class, Integer.class, Double.class, String.class, groovy.lang.GString.class, Object.class));
    var config = new CompilerConfiguration();
    config.addCompilationCustomizers(customizer);
    var engine = new SimpleTemplateEngine(new GroovyShell(config));
    try {
      return engine.createTemplate(template).make(new HashMap<>(parameters)).toString();
    } catch (ClassNotFoundException | IOException e) {
      throw new IllegalStateException("Failed to render number template", e);
    }
  }
}
