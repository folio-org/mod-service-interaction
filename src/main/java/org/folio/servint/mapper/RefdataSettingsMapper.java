package org.folio.servint.mapper;

import org.folio.servint.domain.dto.AppSettingDto;
import org.folio.servint.domain.dto.RefdataCategoryDto;
import org.folio.servint.domain.entity.AppSetting;
import org.folio.servint.domain.entity.RefdataCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Wire rendering per the legacy web-toolkit gson views: categories expand
 * their values as {id, value, label} WITHOUT the owner back-reference
 * (owner renders only in standalone value contexts — M4-verified); settings
 * render every column directly (settingType is a plain string).
 */
@Mapper(componentModel = "spring", uses = NumgenMapper.class)
public interface RefdataSettingsMapper {

  @Mapping(target = "values", qualifiedByName = "valueNoOwner")
  RefdataCategoryDto toDto(RefdataCategory entity);

  AppSettingDto toDto(AppSetting entity);
}
