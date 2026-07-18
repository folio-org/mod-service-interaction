package org.folio.servint.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.servint.domain.dto.NextNumberResultDto;
import org.folio.servint.domain.dto.NumberGeneratorDto;
import org.folio.servint.domain.dto.NumberGeneratorSequenceDto;
import org.folio.servint.domain.dto.YearResetResultDto;
import org.folio.servint.domain.entity.NumberGenerator;
import org.folio.servint.domain.entity.NumberGeneratorSequence;
import org.folio.servint.mapper.NumgenMapper;
import org.folio.servint.repository.NumberGeneratorRepository;
import org.folio.servint.repository.NumberGeneratorSequenceRepository;
import org.folio.servint.rest.resource.NumberGeneratorsApi;
import org.folio.servint.service.numgen.NumberGeneratorService;
import org.folio.servint.service.refdata.RefdataService;
import org.folio.servint.web.KiwtListing;
import org.folio.servint.web.UnknownRefdataValueException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the servint number-generator surface (specs/api/servint-number-generators.yaml).
 *
 * <p>Transactions are per-method, not class-level (F-17): getNextNumber and
 * resetYearSequences delegate to a service that opens its own REQUIRES_NEW
 * transaction, and an outer controller transaction would pin a second pooled
 * connection per request — deadlocking the Hikari pool at pool-size concurrency.
 */
@RestController
@RequiredArgsConstructor
public class NumberGeneratorsController implements NumberGeneratorsApi {

  private final NumberGeneratorRepository generators;
  private final NumberGeneratorSequenceRepository sequences;
  private final NumberGeneratorService numberGeneratorService;
  private final RefdataService refdata;
  private final NumgenMapper mapper;
  private final KiwtListing listing;

  // --- generators -------------------------------------------------------

  @Override
  @Transactional
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<NumberGeneratorDto>> listNumberGenerators(List<String> filters,
      List<String> match, String term, List<String> sort, Integer perPage, Integer max, Integer page,
      Integer offset, Boolean stats) {
    return (ResponseEntity) listing.list(generators, filters, match, term, sort, perPage, max, page,
        offset, stats, mapper::toDto);
  }

  @Override
  @Transactional
  public ResponseEntity<NumberGeneratorDto> createNumberGenerator(NumberGeneratorDto body) {
    var entity = new NumberGenerator();
    bindGenerator(entity, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(generators.saveAndFlush(entity)));
  }

  @Override
  @Transactional
  public ResponseEntity<NumberGeneratorDto> getNumberGenerator(String id) {
    return generators.findById(id)
        .map(g -> ResponseEntity.ok(mapper.toDto(g)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @Transactional
  public ResponseEntity<NumberGeneratorDto> updateNumberGenerator(String id, NumberGeneratorDto body) {
    return generators.findById(id)
        .map(g -> {
          bindGenerator(g, body);
          return ResponseEntity.ok(mapper.toDto(generators.saveAndFlush(g)));
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @Transactional
  public ResponseEntity<Void> deleteNumberGenerator(String id) {
    return generators.findById(id)
        .map(g -> {
          generators.delete(g);
          return ResponseEntity.noContent().<Void>build();
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // --- generation + timer ----------------------------------------------
  // Deliberately NOT @Transactional: the service runs REQUIRES_NEW so the
  // number consumption commits (or rolls back to an ERROR envelope)
  // independently; an outer transaction here only holds a second connection.

  @Override
  public ResponseEntity<NextNumberResultDto> getNextNumber(String generator, String sequence) {
    return ResponseEntity.ok(numberGeneratorService.getNextNumber(generator, sequence));
  }

  @Override
  public ResponseEntity<YearResetResultDto> resetYearSequences() {
    return ResponseEntity.ok(numberGeneratorService.resetYearSequences());
  }

  // --- sequences --------------------------------------------------------

  @Override
  @Transactional
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<NumberGeneratorSequenceDto>> listNumberGeneratorSequences(List<String> filters,
      List<String> match, String term, List<String> sort, Integer perPage, Integer max, Integer page,
      Integer offset, Boolean stats) {
    return (ResponseEntity) listing.list(sequences, filters, match, term, sort, perPage, max, page,
        offset, stats, mapper::toDtoWithOwner);
  }

  @Override
  @Transactional
  public ResponseEntity<NumberGeneratorSequenceDto> createNumberGeneratorSequence(NumberGeneratorSequenceDto body) {
    var entity = new NumberGeneratorSequence();
    bindSequence(entity, body);
    numberGeneratorService.prepareForSave(entity);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toDtoWithOwner(sequences.saveAndFlush(entity)));
  }

  @Override
  @Transactional
  public ResponseEntity<NumberGeneratorSequenceDto> getNumberGeneratorSequence(String id) {
    return sequences.findById(id)
        .map(s -> ResponseEntity.ok(mapper.toDtoWithOwner(s)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @Transactional
  public ResponseEntity<NumberGeneratorSequenceDto> updateNumberGeneratorSequence(String id,
      NumberGeneratorSequenceDto body) {
    return sequences.findById(id)
        .map(s -> {
          bindSequence(s, body);
          numberGeneratorService.prepareForSave(s);
          return ResponseEntity.ok(mapper.toDtoWithOwner(sequences.saveAndFlush(s)));
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @Transactional
  public ResponseEntity<Void> deleteNumberGeneratorSequence(String id) {
    return sequences.findById(id)
        .map(s -> {
          sequences.delete(s);
          return ResponseEntity.noContent().<Void>build();
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // --- binding ----------------------------------------------------------

  private void bindGenerator(NumberGenerator entity, NumberGeneratorDto dto) {
    if (dto.getCode() != null) {
      entity.setCode(dto.getCode());
    }
    if (dto.getName() != null) {
      entity.setName(dto.getName());
    }
    if (dto.getDescription() != null) {
      entity.setDescription(dto.getDescription());
    }
    if (dto.getDefaultSequenceCode() != null) {
      entity.setDefaultSequenceCode(dto.getDefaultSequenceCode());
    }
  }

  private void bindSequence(NumberGeneratorSequence entity, NumberGeneratorSequenceDto dto) {
    if (dto.getOwner() != null && dto.getOwner().getId() != null) {
      entity.setOwner(generators.findById(dto.getOwner().getId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
              "No number generator " + dto.getOwner().getId())));
    }
    if (dto.getCode() != null) {
      entity.setCode(dto.getCode());
    }
    if (dto.getName() != null) {
      entity.setName(dto.getName());
    }
    if (dto.getPrefix() != null) {
      entity.setPrefix(dto.getPrefix());
    }
    if (dto.getPostfix() != null) {
      entity.setPostfix(dto.getPostfix());
    }
    if (dto.getFormat() != null) {
      entity.setFormat(dto.getFormat());
    }
    if (dto.getNextValue() != null) {
      entity.setNextValue(dto.getNextValue());
    }
    if (dto.getPreChecksumTemplate() != null) {
      entity.setPreChecksumTemplate(dto.getPreChecksumTemplate());
    }
    if (dto.getOutputTemplate() != null) {
      entity.setOutputTemplate(dto.getOutputTemplate());
    }
    if (dto.getDescription() != null) {
      entity.setDescription(dto.getDescription());
    }
    if (dto.getEnabled() != null) {
      entity.setEnabled(dto.getEnabled());
    }
    if (dto.getResetOnYearChange() != null) {
      entity.setResetOnYearChange(dto.getResetOnYearChange());
    }
    if (dto.getLastUsedYear() != null) {
      entity.setLastUsedYear(dto.getLastUsedYear());
    }
    if (dto.getMaximumNumber() != null) {
      entity.setMaximumNumber(dto.getMaximumNumber());
    }
    if (dto.getMaximumNumberThreshold() != null) {
      entity.setMaximumNumberThreshold(dto.getMaximumNumberThreshold());
    }
    if (dto.getCheckDigitAlgo() != null) {
      var ref = dto.getCheckDigitAlgo();
      var resolved = ref.getId() != null
          ? refdata.findById(ref.getId())
          : refdata.find(NumberGeneratorService.CAT_CHECK_DIGIT, ref.getValue());
      entity.setCheckDigitAlgo(resolved.orElseThrow(() -> new UnknownRefdataValueException(
          "Unknown check digit algorithm refdata: "
              + (ref.getId() != null ? ref.getId() : ref.getValue()))));
    }
  }
}
