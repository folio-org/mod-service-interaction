package org.olf.numgen;

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.refdata.CategoryId

class NumberGeneratorSequence implements MultiTenant<NumberGeneratorSequence> {

  String id
  NumberGenerator owner
  String code
  String name
  String prefix
  String postfix
  String format
  Long nextValue
  String preChecksumTemplate
  String outputTemplate
  String description
  Boolean enabled = Boolean.TRUE

  // Between them, these will determine the maximum number a sequence should be allowed to reach,
  // and a threshold beyond which a warning will be in the API response.
  Long maximumNumber
  Long maximumNumberThreshold

  //@Defaults(['None', 'EAN13', 'ModulusTenCheckDigit', 'ISBN10CheckDigit'])
  // Defaults are now configured in the housekeeping service
  @CategoryId(defaultInternal=true)
  RefdataValue checkDigitAlgo

  @CategoryId(defaultInternal=true)
  @Defaults(['Below threshold', 'Over threshold', 'At maximum'])
  RefdataValue maximumCheck

  static constraints = {
                    prefix(nullable: true)
                   postfix(nullable: true)
                 nextValue(nullable: true)
                    format(nullable: true)
            checkDigitAlgo(nullable: true)
            outputTemplate(nullable: true)
       preChecksumTemplate(nullable: true)
               description(nullable: true)
                   enabled(nullable: true)
             maximumNumber(nullable: true)
    maximumNumberThreshold(nullable: true)
              maximumCheck(nullable: true)
  }

  def beforeValidate() {
    handleMaximumCheck();
  }

  private void handleMaximumCheck() {
    if (
      maximumNumber != null &&
      nextValue > maximumNumber
    ) {
      maximumCheck = RefdataValue.get(lookup_max_check_id('at_maximum'))
    } else if (
      maximumNumber != null &&
      maximumNumberThreshold != null &&
      nextValue > maximumNumberThreshold
    ) {
      maximumCheck = RefdataValue.get(lookup_max_check_id('over_threshold'))
    } else if (
      maximumNumber != null &&
      maximumNumberThreshold != null
    ) {
      maximumCheck = RefdataValue.get(lookup_max_check_id('below_threshold'))
    } else {
      maximumCheck = null
    }
  }

  // This might not be ideal but it's the minimal impact way to do this
  private String lookup_max_check_id(String value) {
    RefdataValue.executeQuery("""
      SELECT id FROM RefdataValue AS rdv
      WHERE
        rdv.owner.desc = 'NumberGeneratorSequence.MaximumCheck' AND
        rdv.value = :value
    """.toString(), [value: value])[0]
  }


  static mapping = {
                        id column: 'ngs_id', generator: 'uuid2', length:36
                   version column: 'ngs_version'
                     owner column: 'ngs_owner'
                      code column: 'ngs_code'
                      name column: 'ngs_name'
                    prefix column: 'ngs_prefix'
                   postfix column: 'ngs_postfix'
                 nextValue column: 'ngs_next_value'
                    format column: 'ngs_format'
            checkDigitAlgo column: 'ngs_check_digit_algorithm_fk'
            outputTemplate column: 'ngs_output_template'
       preChecksumTemplate column: 'ngs_pre_checksum_template'
               description column: 'ngs_description'
                   enabled column: 'ngs_enabled'
             maximumNumber column: 'ngs_maximum_number'
    maximumNumberThreshold column: 'ngs_maximum_number_threshold'
              maximumCheck column: 'ngs_maximum_check_fk'
  }


  public String toString() {
    return "NumberGeneratorSequence(${owner?.code}.${code} ${prefix} ${nextValue} (Max:${maximumNumber}, Threshold:${maximumNumberThreshold}, Check:${maximumCheck?.value}) ${postfix} ${format} ${checkDigitAlgo?.value},${outputTemplate})".toString();
  }
}
