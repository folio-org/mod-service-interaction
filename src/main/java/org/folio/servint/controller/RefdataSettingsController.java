package org.folio.servint.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.servint.domain.dto.AppSettingDto;
import org.folio.servint.domain.dto.RefdataCategoryDto;
import org.folio.servint.domain.dto.RefdataValueDto;
import org.folio.servint.domain.entity.AppSetting;
import org.folio.servint.domain.entity.RefdataCategory;
import org.folio.servint.domain.entity.RefdataValue;
import org.folio.servint.mapper.NumgenMapper;
import org.folio.servint.mapper.RefdataSettingsMapper;
import org.folio.servint.repository.AppSettingRepository;
import org.folio.servint.repository.RefdataCategoryRepository;
import org.folio.servint.repository.RefdataValueRepository;
import org.folio.servint.rest.resource.RefdataSettingsApi;
import org.folio.servint.service.refdata.RefdataService;
import org.folio.servint.web.KiwtListing;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the servint refdata + settings surface
 * (specs/api/servint-refdata-settings.yaml). The domain/property lookup
 * resolves the category the way the legacy web-toolkit registered it:
 * case-insensitive persistent-entity simple name plus the capitalized
 * property, joined with a dot.
 */
@RestController
@RequiredArgsConstructor
@Transactional
public class RefdataSettingsController implements RefdataSettingsApi {

  /** Legacy persistent-entity simple names the lookup can resolve. */
  private static final Map<String, String> KNOWN_DOMAINS = List.of(
          "Dashboard", "DashboardAccess", "DashboardDisplayData", "ExternalUser",
          "WidgetType", "WidgetDefinition", "WidgetInstance", "DBKeyPair",
          "NumberGenerator", "NumberGeneratorSequence",
          "AppSetting", "RefdataCategory", "RefdataValue").stream()
      .collect(Collectors.toMap(name -> name.toLowerCase(Locale.ROOT), Function.identity()));

  private final RefdataCategoryRepository categories;
  private final RefdataValueRepository values;
  private final AppSettingRepository settings;
  private final RefdataService refdataService;
  private final RefdataSettingsMapper mapper;
  private final NumgenMapper refdataValueMapper;
  private final KiwtListing listing;

  // --- refdata categories ----------------------------------------------

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<RefdataCategoryDto>> listRefdataCategories(List<String> filters,
      List<String> match, String term, List<String> sort, Integer perPage, Integer max, Integer page,
      Integer offset, Boolean stats) {
    return (ResponseEntity) listing.list(categories, filters, match, term, sort, perPage, max, page,
        offset, stats, mapper::toDto);
  }

  @Override
  public ResponseEntity<RefdataCategoryDto> createRefdataCategory(RefdataCategoryDto body) {
    requireOnCreate(body.getDesc(), "desc");
    var entity = new RefdataCategory();
    bindCategory(entity, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(categories.saveAndFlush(entity)));
  }

  @Override
  public ResponseEntity<RefdataCategoryDto> getRefdataCategory(String id) {
    return categories.findById(id)
        .map(c -> ResponseEntity.ok(mapper.toDto(c)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<RefdataCategoryDto> updateRefdataCategory(String id, RefdataCategoryDto body) {
    return categories.findById(id)
        .map(c -> {
          bindCategory(c, body);
          return ResponseEntity.ok(mapper.toDto(categories.saveAndFlush(c)));
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<Void> deleteRefdataCategory(String id) {
    return categories.findById(id)
        .map(c -> {
          categories.delete(c);
          return ResponseEntity.noContent().<Void>build();
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<RefdataValueDto>> lookupRefdataValues(String domain, String property,
      List<String> match, String term, List<String> sort, Integer perPage, Integer max, Integer page,
      Integer offset, Boolean stats) {
    var entityName = KNOWN_DOMAINS.get(domain.toLowerCase(Locale.ROOT));
    if (entityName == null) {
      return ResponseEntity.notFound().build();
    }
    var category = entityName + "." + capitalize(property);
    return (ResponseEntity) listing.list(values, List.of("owner.desc==" + category),
        match, term, sort, perPage, max, page, offset, stats, refdataValueMapper::toDto);
  }

  // --- app settings -----------------------------------------------------

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<AppSettingDto>> listAppSettings(List<String> filters,
      List<String> match, String term, List<String> sort, Integer perPage, Integer max, Integer page,
      Integer offset, Boolean stats) {
    return (ResponseEntity) listing.list(settings, filters, match, term, sort, perPage, max, page,
        offset, stats, mapper::toDto);
  }

  @Override
  public ResponseEntity<AppSettingDto> createAppSetting(AppSettingDto body) {
    requireOnCreate(body.getKey(), "key");
    var entity = new AppSetting();
    bindSetting(entity, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(settings.saveAndFlush(entity)));
  }

  @Override
  public ResponseEntity<AppSettingDto> getAppSetting(String id) {
    return settings.findById(id)
        .map(s -> ResponseEntity.ok(mapper.toDto(s)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<AppSettingDto> updateAppSetting(String id, AppSettingDto body) {
    return settings.findById(id)
        .map(s -> {
          bindSetting(s, body);
          return ResponseEntity.ok(mapper.toDto(settings.saveAndFlush(s)));
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<Void> deleteAppSetting(String id) {
    return settings.findById(id)
        .map(s -> {
          settings.delete(s);
          return ResponseEntity.noContent().<Void>build();
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // --- binding ----------------------------------------------------------

  private void bindCategory(RefdataCategory entity, RefdataCategoryDto dto) {
    if (dto.getDesc() != null) {
      entity.setDesc(dto.getDesc());
    }
    if (dto.getInternal() != null) {
      entity.setInternal(dto.getInternal());
    }
    if (dto.getValues() != null) {
      mergeValues(entity, dto.getValues());
    }
  }

  /**
   * Legacy GORM all-delete-orphan collection binding: entries with a known
   * id update in place, entries without one are created (value defaulting
   * to the normalized label), entries missing from the payload are removed.
   */
  private void mergeValues(RefdataCategory entity, List<RefdataValueDto> submitted) {
    var byId = entity.getValues().stream()
        .collect(Collectors.toMap(RefdataValue::getId, Function.identity()));
    var merged = submitted.stream().map(dto -> {
      var existing = dto.getId() == null ? null : byId.get(dto.getId());
      if (existing != null) {
        if (dto.getLabel() != null) {
          existing.setLabel(dto.getLabel());
        }
        if (dto.getValue() != null) {
          existing.setValue(dto.getValue());
        }
        return existing;
      }
      var created = new RefdataValue();
      created.setOwner(entity);
      created.setLabel(dto.getLabel());
      created.setValue(dto.getValue() != null ? dto.getValue() : RefdataService.normValue(dto.getLabel()));
      return created;
    }).toList();
    entity.getValues().clear();
    entity.getValues().addAll(merged);
  }

  private void bindSetting(AppSetting entity, AppSettingDto dto) {
    if (dto.getSection() != null) {
      entity.setSection(dto.getSection());
    }
    if (dto.getKey() != null) {
      entity.setKey(dto.getKey());
    }
    if (dto.getSettingType() != null) {
      entity.setSettingType(dto.getSettingType());
    }
    if (dto.getVocab() != null) {
      entity.setVocab(dto.getVocab());
    }
    if (dto.getDefValue() != null) {
      entity.setDefValue(dto.getDefValue());
    }
    if (dto.getValue() != null) {
      entity.setValue(dto.getValue());
    }
    if (dto.getHidden() != null) {
      entity.setHidden(dto.getHidden());
    }
  }

  /** Legacy GORM validation answers 422 with an errors envelope on create. */
  private static void requireOnCreate(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new org.folio.servint.web.LegacyValidationException(
          "nullable", "Property [" + field + "] cannot be null");
    }
  }

  private static String capitalize(String property) {
    return property.isEmpty() ? property
        : Character.toUpperCase(property.charAt(0)) + property.substring(1);
  }
}
