package org.folio.servint.service.numgen;

import static org.folio.servint.service.numgen.NumberGeneratorService.CAT_CHECK_DIGIT;
import static org.folio.servint.service.numgen.NumberGeneratorService.CAT_MAX_CHECK;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.servint.domain.entity.NumberGenerator;
import org.folio.servint.domain.entity.NumberGeneratorSequence;
import org.folio.servint.repository.NumberGeneratorRepository;
import org.folio.servint.repository.NumberGeneratorSequenceRepository;
import org.folio.servint.service.refdata.RefdataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent tenant seeding (REQ-019). Two legacy buckets with different
 * triggers: MaximumCheck replicates the web-toolkit @Defaults baseline that
 * grails-okapi materialized on every enable, while the check-digit vocabulary
 * and the default number generators port the HousekeepingService
 * okapi:dataload:reference subscriber and run only on loadReference=true.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NumgenSeedService {

  private final RefdataService refdata;
  private final NumberGeneratorRepository generators;
  private final NumberGeneratorSequenceRepository sequences;

  private record SeqSeed(String name, String code, String format, String algoValue, String outputTemplate) { }

  private record GenSeed(String name, String code, List<SeqSeed> sequences) { }

  private static final List<GenSeed> DEFAULT_GENERATORS = List.of(
      new GenSeed("Open access: Publication request number", "openAccess", List.of(
          new SeqSeed("Request sequence", "requestSequence", "000000000", "none", "oa-${generated_number}"))),
      new GenSeed("ILL: Patron request number", "patronRequest", List.of(
          new SeqSeed("Request sequence", "requestSequence", "000000000", "ean13", "ill-${generated_number}-${checksum}"))),
      new GenSeed("Users: Patron barcode", "users_patronBarcode", List.of(
          new SeqSeed("Patron", "patron", "000000000", "ean13", "P${generated_number}-${checksum}"),
          new SeqSeed("Staff", "staff", "000000000", "ean13", "S${generated_number}-${checksum}"))),
      new GenSeed("Organizations: Vendor code", "organizations_vendorCode", List.of(
          new SeqSeed("Vendor", "vendor", "000", "none", "K${generated_number}"))),
      new GenSeed("Inventory: Accession number", "inventory_accessionNumber", List.of(
          new SeqSeed("Accession number", "accessionNumber", "00000", "none", "31A-2023-${generated_number}"))),
      new GenSeed("Inventory: Call number", "inventory_callNumber", List.of(
          new SeqSeed("Call number", "callNumber", "00000", "none", "B 2023 / ${generated_number}"))),
      new GenSeed("Inventory: Item barcode", "inventory_itemBarcode", List.of(
          new SeqSeed("Item barcode", "itemBarcode", "0000000000", "none", "${generated_number}"))),
      new GenSeed("Serials management: Pattern number", "serialsManagement_patternNumber", List.of(
          new SeqSeed("Pattern number", "patternNumber", "000000000", "none", "pattern-${generated_number}"))));

  @Transactional
  public void seedMaximumCheck() {
    refdata.lookupOrCreate(CAT_MAX_CHECK, "Below threshold", null);
    refdata.lookupOrCreate(CAT_MAX_CHECK, "Over threshold", null);
    refdata.lookupOrCreate(CAT_MAX_CHECK, "At maximum", null);
  }

  @Transactional
  public void seedCheckDigitAlgo() {
    refdata.lookupOrCreate(CAT_CHECK_DIGIT, "None", "none");
    refdata.lookupOrCreate(CAT_CHECK_DIGIT, "31-RTL-mod10-I (EAN)", "ean13");
    refdata.lookupOrCreate(CAT_CHECK_DIGIT, "1793-LTR-mod10-R", "1793_ltr_mod10_r");
    refdata.lookupOrCreate(CAT_CHECK_DIGIT, "12-LTR-mod10-R", "12_ltr_mod10_r");
    refdata.lookupOrCreate(CAT_CHECK_DIGIT, "2345678910-RTL-mod11-I-X (ISBN10)", "isbn10checkdigit");
    refdata.lookupOrCreate(CAT_CHECK_DIGIT, "8765432-LTR-mod11-I-X (ISSN)", "issncheckdigit");
    refdata.lookupOrCreate(CAT_CHECK_DIGIT, "21-RTL-mod10-I (Luhn)", "luhncheckdigit");
  }

  @Transactional
  public void seedDefaultGenerators() {
    for (var genSeed : DEFAULT_GENERATORS) {
      var generator = generators.findByCode(genSeed.code()).orElseGet(() -> {
        var g = new NumberGenerator();
        g.setCode(genSeed.code());
        g.setName(genSeed.name());
        return generators.saveAndFlush(g);
      });
      for (var seqSeed : genSeed.sequences()) {
        if (sequences.findByOwnerCodeAndCode(genSeed.code(), seqSeed.code()).isEmpty()) {
          var s = new NumberGeneratorSequence();
          s.setOwner(generator);
          s.setName(seqSeed.name());
          s.setCode(seqSeed.code());
          s.setFormat(seqSeed.format());
          s.setNextValue(1L);
          s.setOutputTemplate(seqSeed.outputTemplate());
          s.setCheckDigitAlgo(refdata.find(CAT_CHECK_DIGIT, seqSeed.algoValue()).orElse(null));
          sequences.save(s);
        }
      }
    }
    log.info("Default number generators seeded");
  }
}
