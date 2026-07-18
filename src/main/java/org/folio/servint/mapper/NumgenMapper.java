package org.folio.servint.mapper;

import org.folio.servint.domain.dto.NumberGeneratorDto;
import org.folio.servint.domain.dto.NumberGeneratorSequenceDto;
import org.folio.servint.domain.dto.NumberGeneratorSequenceDtoOwner;
import org.folio.servint.domain.dto.RefdataValueDto;
import org.folio.servint.domain.entity.NumberGenerator;
import org.folio.servint.domain.entity.NumberGeneratorSequence;
import org.folio.servint.domain.entity.RefdataValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Wire rendering per the legacy gson views: generators expand sequences
 * (without owner snippets); standalone sequence renders add the owner
 * {id, name, code} snippet; refdata FKs expand to {id, value, label,
 * owner{id, desc, internal}} — the owning category is omitted only when a
 * value renders inside that category's own values array (M4-verified).
 */
@Mapper(componentModel = "spring")
public interface NumgenMapper {

  @Named("sequenceNoOwner")
  @Mapping(target = "owner", ignore = true)
  NumberGeneratorSequenceDto toDto(NumberGeneratorSequence entity);

  RefdataValueDto toDto(RefdataValue entity);

  @Named("valueNoOwner")
  @Mapping(target = "owner", ignore = true)
  RefdataValueDto toDtoNoOwner(RefdataValue entity);

  @Mapping(target = "sequences", qualifiedByName = "sequenceNoOwner")
  NumberGeneratorDto toDto(NumberGenerator entity);

  default NumberGeneratorSequenceDto toDtoWithOwner(NumberGeneratorSequence entity) {
    var dto = toDto(entity);
    var owner = entity.getOwner();
    if (owner != null) {
      var snippet = new NumberGeneratorSequenceDtoOwner();
      snippet.setId(owner.getId());
      snippet.setName(owner.getName());
      snippet.setCode(owner.getCode());
      dto.setOwner(snippet);
    }
    return dto;
  }
}
