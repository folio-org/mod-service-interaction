package org.folio.servint.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.folio.servint.domain.entity.NumberGeneratorSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NumberGeneratorSequenceRepository
    extends JpaRepository<NumberGeneratorSequence, String>, JpaSpecificationExecutor<NumberGeneratorSequence> {

  /** Legacy getNextNumber criteria: owner.code + code with a pessimistic row lock. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from NumberGeneratorSequence s where s.owner.code = :generator and s.code = :sequence")
  Optional<NumberGeneratorSequence> lockByGeneratorCodeAndCode(
      @Param("generator") String generator, @Param("sequence") String sequence);

  /** Legacy resetYearSequences criteria: all reset-enabled sequences, locked. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from NumberGeneratorSequence s where s.resetOnYearChange = true")
  List<NumberGeneratorSequence> lockAllResetOnYearChange();

  Optional<NumberGeneratorSequence> findByOwnerCodeAndCode(String ownerCode, String code);

  /**
   * Race-free default-sequence creation for concurrent first use (review
   * F-05). Column values mirror the legacy initialiseDefaultSequence; the
   * SELECT resolves the owner FK in the same statement, and ON CONFLICT on
   * NumberGeneratorSequenceUniqueCode (ngs_owner, ngs_code) lets concurrent
   * initialisers converge on a single surviving row. outputTemplate is bound
   * as a parameter because its ${...} token would otherwise be parsed as a
   * value expression inside the query string.
   */
  @Modifying
  @Query(value = "insert into number_generator_sequence "
      + "(ngs_id, ngs_version, ngs_owner, ngs_code, ngs_name, ngs_format, ngs_next_value, "
      + "ngs_output_template, ngs_enabled, ngs_reset_on_year_change, ngs_check_digit_algorithm_fk) "
      + "select :id, 0, ng.ng_id, :sequence, :sequence, '000000000', 1, "
      + ":outputTemplate, true, false, :checkDigitAlgoId "
      + "from number_generator ng where ng.ng_code = :generator "
      + "on conflict (ngs_owner, ngs_code) do nothing", nativeQuery = true)
  int insertDefaultIfAbsent(@Param("id") String id, @Param("generator") String generator,
      @Param("sequence") String sequence, @Param("outputTemplate") String outputTemplate,
      @Param("checkDigitAlgoId") String checkDigitAlgoId);
}
