package org.folio.servint.service.widget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Port of the legacy UtilityService version helpers. compatibleVersion is
 * the federation-wide rule: same MAJOR and incoming MINOR >= comparison
 * MINOR, on MAJOR.MINOR strings.
 */
@Log4j2
@Service
public class VersionService {

  private static final Pattern VERSION_PATTERN =
      Pattern.compile("(?<MAJOR>0|(?:[1-9]\\d*))\\.(?<MINOR>0|(?:[1-9]\\d*))");

  public Matcher versionMatcher(String version) {
    return VERSION_PATTERN.matcher(version);
  }

  public boolean compatibleVersion(String incomingVersion, String comparisonVersion) {
    var incoming = versionMatcher(incomingVersion);
    var comparison = versionMatcher(comparisonVersion);
    if (!incoming.matches() || !comparison.matches()) {
      log.warn("Semver version match error for {} and/or {}", incomingVersion, comparisonVersion);
      return false;
    }
    if (!incoming.group("MAJOR").equals(comparison.group("MAJOR"))) {
      return false;
    }
    return Integer.parseInt(incoming.group("MINOR")) >= Integer.parseInt(comparison.group("MINOR"));
  }
}
