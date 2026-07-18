package org.folio.servint.service.widget;

import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.servint.domain.entity.WidgetType;
import org.folio.servint.repository.WidgetTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Port of the legacy WidgetTypeService plus the type-import admin action
 * from UtilityService: types load from classpath sample_data/widgetTypes/*,
 * skipping existing (name, typeVersion) pairs; the clean variant deletes
 * every type first.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class WidgetTypeService {

  private final WidgetTypeRepository widgetTypes;
  private final VersionService versions;
  private final JsonSchemaService jsonSchemas;
  private final JsonMapper jsonMapper;

  public WidgetType latestCompatibleType(String name, String version) {
    return widgetTypes.findByName(name).stream()
        .filter(wt -> versions.compatibleVersion(wt.getTypeVersion(), version))
        .max(Comparator.comparingInt(this::minorOf))
        .orElse(null);
  }

  private int minorOf(WidgetType type) {
    var matcher = versions.versionMatcher(type.getTypeVersion());
    return matcher.matches() ? Integer.parseInt(matcher.group("MINOR")) : -1;
  }

  @Transactional
  public void triggerTypeImport(boolean cleanSlate) {
    log.info("Importing widget types (cleanSlate={})", cleanSlate);
    if (cleanSlate) {
      widgetTypes.deleteAll();
      widgetTypes.flush();
    }
    for (var wt : jsonSchemas.getJsonFilesFromClassPath("sample_data/widgetTypes/*")) {
      var name = (String) wt.get("name");
      var version = (String) wt.get("version");
      if (widgetTypes.findByNameAndTypeVersion(name, version).isEmpty()) {
        var widgetType = new WidgetType();
        widgetType.setName(name);
        widgetType.setTypeVersion(version);
        widgetType.setSchema(jsonMapper.writeValueAsString(wt.get("schema")));
        widgetTypes.saveAndFlush(widgetType);
      }
    }
  }
}
