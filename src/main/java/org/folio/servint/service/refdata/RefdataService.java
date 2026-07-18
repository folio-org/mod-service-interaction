package org.folio.servint.service.refdata;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.servint.domain.entity.RefdataCategory;
import org.folio.servint.domain.entity.RefdataValue;
import org.folio.servint.repository.RefdataCategoryRepository;
import org.folio.servint.repository.RefdataValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bespoke replacement (ADR-007) for the web-toolkit refdata lookupOrCreate
 * pattern the legacy module used for controlled vocabularies.
 */
@Service
@RequiredArgsConstructor
public class RefdataService {

  private final RefdataCategoryRepository categories;
  private final RefdataValueRepository values;
  private final EntityManager entityManager;

  /** Legacy RefdataValue.normValue: trim, lowercase, whitespace to underscore. */
  public static String normValue(String label) {
    return label.trim().toLowerCase().replaceAll("\\s+", "_");
  }

  @Transactional
  public RefdataValue lookupOrCreate(String categoryDesc, String label, String explicitValue) {
    var value = explicitValue != null ? explicitValue : normValue(label);
    var existing = values.findByOwnerDescAndValue(categoryDesc, value);
    if (existing.isPresent()) {
      return existing.get();
    }
    // Lazy creation can race (concurrent first use of a fresh sequence,
    // F-05, creates the check-digit vocabulary on demand) and the category
    // has no uniqueness constraint, so racers would each create their own
    // copy. Serialize creators per schema+category and re-read what the
    // winner committed before creating anything.
    lockCategoryCreation(categoryDesc);
    return values.findByOwnerDescAndValue(categoryDesc, value)
        .orElseGet(() -> create(categoryDesc, label, value));
  }

  private RefdataValue create(String categoryDesc, String label, String value) {
    var category = categories.findByDesc(categoryDesc).orElseGet(() -> {
      var c = new RefdataCategory();
      c.setDesc(categoryDesc);
      c.setInternal(true);
      return categories.save(c);
    });
    var v = new RefdataValue();
    v.setOwner(category);
    v.setValue(value);
    v.setLabel(label);
    return values.save(v);
  }

  /** Transaction-scoped advisory lock; released automatically on commit/rollback. */
  private void lockCategoryCreation(String categoryDesc) {
    entityManager
        .createNativeQuery(
            "select cast(pg_advisory_xact_lock(hashtext(current_schema() || ?1)) as text)")
        .setParameter(1, categoryDesc)
        .getSingleResult();
  }

  public Optional<RefdataValue> find(String categoryDesc, String value) {
    return values.findByOwnerDescAndValue(categoryDesc, value);
  }

  public Optional<RefdataValue> findById(String id) {
    return values.findById(id);
  }
}
