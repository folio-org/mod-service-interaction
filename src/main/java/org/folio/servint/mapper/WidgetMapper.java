package org.folio.servint.mapper;

import java.util.Map;
import org.folio.servint.domain.dto.WidgetDefinitionDto;
import org.folio.servint.domain.dto.WidgetDefinitionDtoType;
import org.folio.servint.domain.dto.WidgetInstanceDto;
import org.folio.servint.domain.dto.WidgetInstanceDtoDefinition;
import org.folio.servint.domain.dto.WidgetInstanceDtoOwner;
import org.folio.servint.domain.dto.WidgetTypeDto;
import org.folio.servint.domain.entity.WidgetDefinition;
import org.folio.servint.domain.entity.WidgetInstance;
import org.folio.servint.domain.entity.WidgetType;
import org.mapstruct.Mapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wire rendering per the legacy gson views: instances compose definition
 * {name, version} (never the flat pair), definitions render the PARSED
 * definition JSON with version/type composed and never the entity id
 * (legacy gson renders with includes:[]), types render plainly with the
 * schema string.
 */
@Mapper(componentModel = "spring")
public abstract class WidgetMapper {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  public WidgetInstanceDto toDto(WidgetInstance entity) {
    if (entity == null) {
      return null;
    }
    var dto = new WidgetInstanceDto();
    dto.setId(entity.getId());
    dto.setName(entity.getName());
    dto.setWeight(entity.getWeight());
    dto.setConfiguration(entity.getConfiguration());
    var definition = new WidgetInstanceDtoDefinition();
    definition.setName(entity.getDefinitionName());
    definition.setVersion(entity.getDefinitionVersion());
    dto.setDefinition(definition);
    if (entity.getOwner() != null) {
      var owner = new WidgetInstanceDtoOwner();
      owner.setId(entity.getOwner().getId());
      dto.setOwner(owner);
    }
    return dto;
  }

  public abstract WidgetTypeDto toDto(WidgetType entity);

  public WidgetDefinitionDto toDto(WidgetDefinition entity) {
    if (entity == null) {
      return null;
    }
    var dto = new WidgetDefinitionDto();
    dto.setName(entity.getName());
    dto.setVersion(entity.getDefinitionVersion());
    var type = new WidgetDefinitionDtoType();
    type.setName(entity.getTypeName());
    type.setVersion(entity.getTypeVersion());
    dto.setType(type);
    dto.setDefinition(entity.getDefinition() == null
        ? null : jsonMapper.readValue(entity.getDefinition(), Map.class));
    return dto;
  }

  /** A federated definition harvested as raw JSON from another module. */
  @SuppressWarnings("unchecked")
  public WidgetDefinitionDto toDto(Map<String, Object> harvested) {
    if (harvested == null) {
      return null;
    }
    var dto = new WidgetDefinitionDto();
    dto.setName((String) harvested.get("name"));
    dto.setVersion((String) harvested.get("version"));
    var typeMap = (Map<String, Object>) harvested.get("type");
    if (typeMap != null) {
      var type = new WidgetDefinitionDtoType();
      type.setName((String) typeMap.get("name"));
      type.setVersion((String) typeMap.get("version"));
      dto.setType(type);
    }
    dto.setDefinition(harvested.get("definition"));
    return dto;
  }
}
