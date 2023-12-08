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
  String outputTemplate
  String description
  Boolean enabled = Boolean.TRUE

  // Between them, these will determine the maximum number a sequence should be allowed to reach,
  // and a threshold beyond which a warning will be in the API response.
  Long maximumNumber
  Long maximumNumberThreshold

  @CategoryId(defaultInternal=true)
  @Defaults(['None', 'EAN13', 'Modulo10', 'Modulo11', 'Modulo16', 'Modulo43', 'Modulo47'])
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
               description(nullable: true)
                   enabled(nullable: true)
             maximumNumber(nullable: true)
    maximumNumberThreshold(nullable: true)
              maximumCheck(nullable: true)
  }

  def beforeValidate() {
    handleMaximumCheck();
  }

  public void handleMaximumCheck() {
    if (
      this.maximumNumber != null &&
      this.nextValue > maximumNumber
    ) {
      this.maximumCheck = RefdataValue.lookupOrCreate('NumberGeneratorSequence.MaximumCheck', 'At maximum')
    } else if (
      this.maximumNumber != null &&
      this.maximumNumberThreshold != null &&
      this.nextValue > maximumNumberThreshold
    ) {
      this.maximumCheck = RefdataValue.lookupOrCreate('NumberGeneratorSequence.MaximumCheck', 'Over threshold')
    } else if (
      this.maximumNumber != null &&
      this.maximumNumberThreshold != null
    ) {
      this.maximumCheck = RefdataValue.lookupOrCreate('NumberGeneratorSequence.MaximumCheck', 'Below threshold')
    } else {
      this.maximumCheck = null
    }
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
               description column: 'ngs_description'
                   enabled column: 'ngs_enabled'
             maximumNumber column: 'ngs_maximum_number'
    maximumNumberThreshold column: 'ngs_maximum_number_threshold'
              maximumCheck column: 'ngs_maximum_check_fk'
  }


  public String toString() {
    return "NumberGeneratorSequence(${owner?.code}.${code} ${prefix} ${nextValue} ${postfix} ${format} ${checkDigitAlgo?.value},${outputTemplate})".toString();
  }
}
