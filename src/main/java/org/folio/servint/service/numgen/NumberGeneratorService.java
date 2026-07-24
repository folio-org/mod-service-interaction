package org.folio.servint.service.numgen;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.servint.domain.dto.NextNumberResultDto;
import org.folio.servint.web.LegacyValidationException;
import org.folio.servint.domain.dto.YearResetResultDto;
import org.folio.servint.domain.entity.NumberGeneratorSequence;
import org.folio.servint.repository.NumberGeneratorRepository;
import org.folio.servint.repository.NumberGeneratorSequenceRepository;
import org.folio.servint.service.refdata.RefdataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Port of the legacy NumberGeneratorController generation core. The guard
 * order, rollback points, template parameters, and message texts are the
 * wire contract (numgen-generation/limits/year-reset requirements) — change
 * nothing without a spec change.
 */
@Log4j2
@Service
public class NumberGeneratorService {

  public static final String CAT_CHECK_DIGIT = "NumberGeneratorSequence.CheckDigitAlgo";
  public static final String CAT_MAX_CHECK = "NumberGeneratorSequence.MaximumCheck";

  private static final String DEFAULT_TEMPLATE =
      "${prefix?prefix+'-':''}${generated_number}${postfix?'-'+postfix:''}${checksum?'-'+checksum:''}";

  private final NumberGeneratorRepository generators;
  private final NumberGeneratorSequenceRepository sequences;
  private final CheckDigitService checkDigits;
  private final NumberTemplateService templates;
  private final RefdataService refdata;
  private final TransactionTemplate tx;

  public NumberGeneratorService(NumberGeneratorRepository generators,
                                NumberGeneratorSequenceRepository sequences,
                                CheckDigitService checkDigits,
                                NumberTemplateService templates,
                                RefdataService refdata,
                                PlatformTransactionManager transactionManager) {
    this.generators = generators;
    this.sequences = sequences;
    this.checkDigits = checkDigits;
    this.templates = templates;
    this.refdata = refdata;
    this.tx = new TransactionTemplate(transactionManager);
    // Own transaction like the legacy withTransaction block: the error paths
    // mark rollback-only without poisoning a caller's transaction.
    this.tx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public NextNumberResultDto getNextNumber(String generator, String sequence) {
    var result = new NextNumberResultDto();
    result.setGenerator(generator);
    result.setSequence(sequence);
    result.setStatus(NextNumberResultDto.StatusEnum.OK);

    try {
      runGeneration(generator, sequence, result);
    } catch (org.springframework.transaction.UnexpectedRollbackException e) {
      // The error paths mark rollback-only and still answer 200 with the ERROR
      // envelope (legacy withTransaction semantics); the rollback is intended.
    }
    return result;
  }

  private void runGeneration(String generator, String sequence, NextNumberResultDto result) {
    tx.executeWithoutResult(status -> {
      var ngs = lockOrInitialiseSequence(generator, sequence);

      if (ngs == null) {
        result.setStatus(NextNumberResultDto.StatusEnum.ERROR);
        result.setErrorCode(NextNumberResultDto.ErrorCodeEnum.NO_SEQUENCE);
        result.setMessage("Unable to locate or create NumberGeneratorSequence for " + generator + "." + sequence);
        status.setRollbackOnly();
        return;
      }

      if (ngs.isYearResetPending()) {
        ngs.setNextValue(1L);
      }

      // Checksum algorithms explode if given 0 as a value.
      Long nextSeqno;
      if (ngs.getNextValue() == null || ngs.getNextValue() == 1L) {
        nextSeqno = 1L;
        ngs.setNextValue(2L);
      } else {
        nextSeqno = ngs.getNextValue();
        ngs.setNextValue(nextSeqno + 1);
      }

      if (hasExceededMaximum(ngs, nextSeqno)) {
        result.setStatus(NextNumberResultDto.StatusEnum.ERROR);
        result.setErrorCode(NextNumberResultDto.ErrorCodeEnum.MAX_REACHED);
        result.setMessage("Number generator sequence has reached its maximum number");
        status.setRollbackOnly();
      } else if (isAtMaximum(ngs, nextSeqno)) {
        result.setStatus(NextNumberResultDto.StatusEnum.WARNING);
        result.setWarningCode(NextNumberResultDto.WarningCodeEnum.HIT_MAXIMUM);
        result.setWarning("Number generator sequence has hit its maximum number of "
            + ngs.getMaximumNumber() + " and cannot be used again");
        generateAndSetNextValue(result, ngs, nextSeqno);
      } else if (isOverThresholdButBelowMaximum(ngs, nextSeqno)) {
        result.setStatus(NextNumberResultDto.StatusEnum.WARNING);
        result.setWarningCode(NextNumberResultDto.WarningCodeEnum.OVER_THRESHOLD);
        result.setWarning("Number generator sequence is approaching its maximum number");
        generateAndSetNextValue(result, ngs, nextSeqno);
      } else {
        generateAndSetNextValue(result, ngs, nextSeqno);
      }
    });
  }

  public YearResetResultDto resetYearSequences() {
    var currentYear = NumberGeneratorSequence.currentYear();
    var reset = tx.execute(status -> {
      var count = 0;
      for (var s : sequences.lockAllResetOnYearChange()) {
        if (s.isYearResetPending(currentYear)) {
          s.setNextValue(1L);
          s.setLastUsedYear(currentYear);
          sequences.save(s);
          count++;
        }
      }
      return count;
    });
    log.info("resetYearSequences: reset {} sequence(s) to 1 for {}", reset, currentYear);
    var dto = new YearResetResultDto();
    dto.setCurrentYear(currentYear);
    dto.setSequencesReset(reset);
    return dto;
  }

  /**
   * Save-time rules ported from the legacy domain class: the year-reset flag
   * requires the ${current_year} token, and maximumCheck is derived from
   * nextValue against maximumNumber/maximumNumberThreshold.
   */
  public void prepareForSave(NumberGeneratorSequence s) {
    if (Boolean.TRUE.equals(s.getResetOnYearChange())
        && (s.getOutputTemplate() == null || !s.getOutputTemplate().contains("${current_year}"))) {
      throw new LegacyValidationException("resetOnYearChange.tokenMissing",
          "resetOnYearChange requires the ${current_year} token in outputTemplate");
    }
    deriveMaximumCheck(s);
  }

  private void deriveMaximumCheck(NumberGeneratorSequence s) {
    var value = maximumCheckValue(s.getMaximumNumber(), s.getMaximumNumberThreshold(), s.getNextValue());
    s.setMaximumCheck(value == null ? null : refdata.find(CAT_MAX_CHECK, value).orElse(null));
  }

  /**
   * Legacy save-time classification of a sequence against its maximum: the
   * MaximumCheck refdata value, or null when no maximum is configured.
   * {@code next > max} wins outright; otherwise a configured threshold splits
   * over/below; a null next is treated as below both.
   */
  static String maximumCheckValue(Long max, Long threshold, Long next) {
    if (max == null) {
      return null;
    }
    if (next != null && next > max) {
      return "at_maximum";
    }
    if (threshold == null) {
      return null;
    }
    return next != null && next > threshold ? "over_threshold" : "below_threshold";
  }

  /**
   * First-use path (review F-05): the legacy find-then-insert initialisation
   * raced under concurrent first use — both requests missed the locked read,
   * both inserted, and one surfaced a uniqueness violation. INSERT .. ON
   * CONFLICT DO NOTHING makes initialisation race-free and the pessimistic
   * re-read serialises the racers on the surviving row. The single retry
   * covers a racing initialiser whose transaction rolled back after we
   * skipped the insert (its row vanished before our re-read).
   */
  private NumberGeneratorSequence lockOrInitialiseSequence(String generator, String sequence) {
    var ngs = sequences.lockByGeneratorCodeAndCode(generator, sequence);
    for (int attempt = 0; attempt < 2 && ngs.isEmpty(); attempt++) {
      insertDefaultSequenceIfAbsent(generator, sequence);
      ngs = sequences.lockByGeneratorCodeAndCode(generator, sequence);
    }
    return ngs.orElse(null);
  }

  private void insertDefaultSequenceIfAbsent(String generator, String sequence) {
    var noneAlgo = refdata.lookupOrCreate(CAT_CHECK_DIGIT, "None", "none");
    generators.insertIfAbsent(UUID.randomUUID().toString(), generator);
    sequences.insertDefaultIfAbsent(UUID.randomUUID().toString(), generator, sequence,
        "${generated_number}", noneAlgo.getId());
  }

  private boolean hasExceededMaximum(NumberGeneratorSequence ngs, Long value) {
    return ngs.getMaximumNumber() != null && value > ngs.getMaximumNumber();
  }

  private boolean isAtMaximum(NumberGeneratorSequence ngs, Long value) {
    return ngs.getMaximumNumber() != null && value.equals(ngs.getMaximumNumber());
  }

  private boolean isOverThresholdButBelowMaximum(NumberGeneratorSequence ngs, Long value) {
    return ngs.getMaximumNumber() != null
        && ngs.getMaximumNumberThreshold() != null
        && value >= ngs.getMaximumNumberThreshold()
        && value < ngs.getMaximumNumber();
  }

  private void generateAndSetNextValue(NextNumberResultDto result, NumberGeneratorSequence ngs, Long nextSeqno) {
    var currentYear = NumberGeneratorSequence.currentYear();
    var df = ngs.getFormat() != null ? new DecimalFormat(ngs.getFormat()) : null;
    var generatedNumber = df != null ? df.format(nextSeqno) : nextSeqno.toString();

    var checksumInput = generatedNumber;
    if (ngs.getPreChecksumTemplate() != null) {
      Map<String, Object> preParams = new HashMap<>();
      preParams.put("generated_number", generatedNumber);
      checksumInput = templates.render(ngs.getPreChecksumTemplate(), preParams);
    }

    String checksum = null;
    var algo = ngs.getCheckDigitAlgo();
    if (algo != null && !"none".equals(algo.getValue())) {
      checksum = checkDigits.calculate(algo.getValue(), checksumInput);
    }

    Map<String, Object> params = new HashMap<>();
    params.put("prefix", ngs.getPrefix());
    params.put("generated_number", generatedNumber);
    params.put("checksum_input_template", checksumInput);
    params.put("postfix", ngs.getPostfix());
    params.put("checksum", checksum);
    params.put("current_year", currentYear);

    var template = ngs.getOutputTemplate() != null ? ngs.getOutputTemplate() : DEFAULT_TEMPLATE;
    result.setNextValue(templates.render(template, params));
    ngs.setLastUsedYear(currentYear);
    sequences.saveAndFlush(ngs);
  }
}
