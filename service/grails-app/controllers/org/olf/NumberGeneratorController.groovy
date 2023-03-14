package org.olf

import grails.rest.*
import grails.converters.*
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.numgen.NumberGenerator
import org.olf.numgen.NumberGeneratorSequence
import java.text.DecimalFormat 

import org.apache.commons.validator.routines.checkdigit.EAN13CheckDigit;

@Slf4j
@CurrentTenant
class NumberGeneratorController extends OkapiTenantAwareController<NumberGeneratorController> {

  private static final String default_template = '''${prefix?prefix+'-':''}${generated_number}${postfix?'-'+postfix:''}${checksum?'-'+checksum:''}'''

  NumberGeneratorController() {
    super(NumberGenerator)
  }

  public getNextNumber(String generator, String sequence) {
    log.debug("NumberGeneratorController::getNextNumber(${generator},${sequence})");
    Map result = [:]
    NumberGenerator.withTransaction { status ->
      NumberGeneratorSequence ngs = NumberGeneratorSequence.createCriteria().get { 
        owner {
          eq('code', generator)
        }
        eq('code', sequence) 
        lock true
      }

      if ( ngs == null ) {
        ngs = initialiseDefaultSequence(generator,sequence);
      }

      log.debug("Got seq : ${ngs}");

      Long next_seqno = null;

      if ( ngs != null  ) {
        // Checksum algorithms explode if given 0 as a value
        if ( ( ngs.nextValue == null ) || ( ngs.nextValue == 1 ) ) {
          next_seqno=1
          ngs.nextValue = 2
        }
        else {
          next_seqno=ngs.nextValue++
        }

        if ( next_seqno != null ) {

          DecimalFormat df = ngs.format ? new DecimalFormat(ngs.format) : null;
          String generated_number = df ? df.format(next_seqno) : next_seqno.toString()
          String checksum = ngs.checkDigitAlgo ? generateCheckSum(ngs.checkDigitAlgo.value, generated_number) : null;

          // If we don't override the template generate strings of the format
          // prefix-number-postfix-checksum
          Map template_parameters = [
                      'prefix': ngs.prefix,
            'generated_number': generated_number,
                     'postfix': ngs.postfix,
                    'checksum': checksum
          ]
          def engine = new groovy.text.SimpleTemplateEngine()
          // If the seq specifies a template, use it here, otherwise just use the default
          def number_template = engine.createTemplate(ngs.outputTemplate?:default_template).make(template_parameters)
          result.nextValue = number_template.toString();
        }

        ngs.save(flush:true, failOnError:true);
      }
      else {
        result.status = 'ERROR'
        result.message = "unable to locate NumberGeneratorSequence for ${generator}.${sequence}".toString()
      }
    }

    render result as JSON;
  }

  // See https://www.activebarcode.com/codes/checkdigit/modulo47.html
  // Remember - RefdataValue normalizes values - so EAN13 becomes ean13 here
  private String generateCheckSum(String algorithm, String value_to_check) {
    log.debug("generateCheckSum(${algorithm},${value_to_check})");
    String result = null;
    switch(algorithm) {
      case 'ean13':
        result=new EAN13CheckDigit().calculate(value_to_check)
        break;
      default:
        break;
    }
    return result;
  }

  private NumberGeneratorSequence initialiseDefaultSequence(String generator, String sequence) {
    NumberGeneratorSequence result = null;
    NumberGenerator ng = NumberGenerator.findByCode(generator) ?: new NumberGenerator(code:generator, name:generator).save(flush:true, failOnError:true)
    result = new NumberGeneratorSequence(owner: ng,
                                         name: sequence, // Set up default name
                                         code: sequence,
                                         prefix: null,
                                         postfix: null,
                                         format: '000000000',  // Default to a 9 digit 0 padded number
                                         nextValue: 1,
                                         outputTemplate:null).save(flush:true, failOnError:true);
    return result;
  }
}
