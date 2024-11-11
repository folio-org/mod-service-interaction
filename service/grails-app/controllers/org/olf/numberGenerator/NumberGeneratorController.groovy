package org.olf.numberGenerator

import grails.rest.*
import grails.converters.*
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.numgen.NumberGenerator
import org.olf.numgen.NumberGeneratorSequence
import java.text.DecimalFormat 

import org.apache.commons.validator.routines.checkdigit.*;

@Slf4j
@CurrentTenant
class NumberGeneratorController extends OkapiTenantAwareController<NumberGeneratorController> {

  private static final String default_template = '''${prefix?prefix+'-':''}${generated_number}${postfix?'-'+postfix:''}${checksum?'-'+checksum:''}'''

  NumberGeneratorController() {
    super(NumberGenerator)
  }

  public getNextNumber(String generator, String sequence) {
    log.debug("NumberGeneratorController::getNextNumber(${generator},${sequence})");
    Map result = [
      generator: generator,
      sequence: sequence,
      status: 'OK'
    ]

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

      //log.debug("Got seq : ${ngs}");

      Long next_seqno = null;

      if ( ngs != null  ) {
        // Checksum algorithms explode if given 0 as a value
        if ( ( ngs.nextValue == null ) || ( ngs.nextValue == 1 ) ) {
          next_seqno = 1
          ngs.nextValue = 2
        } else {
          // Set next_seqno to _current_ nextValue, and increment nextValue on the sequence
          // Commenting only because the syntactic sugar here tripped me up for a minute ;)
          next_seqno=ngs.nextValue++
        }

        switch (next_seqno) {
          case null:
            result.status = 'ERROR'
            result.errorCode = 'NoNextValue'
            result.message = 'Unable to determine next value in the sequence'
            // Undo any changes made to ngs
            status.setRollbackOnly()
            break;
          case {
            ngs.maximumNumber != null &&
            it > ngs.maximumNumber
          }:
            result.status = 'ERROR'
            result.errorCode = 'MaxReached'
            result.message = 'Number generator sequence has reached its maximum number'
            // Undo any changes made to ngs
            status.setRollbackOnly()
            break;
          case {
            ngs.maximumNumber != null &&
            ngs.maximumNumberThreshold != null &&
            it >= ngs.maximumNumberThreshold
          }:
            if (ngs.maximumNumber != null && next_seqno == ngs.maximumNumber) {
              // Special case for if this is the generation which will hit the maximum
              result.warningCode = 'HitMaximum'
              result.warning = 'Number generator sequence has hit its maximum number'
            } else {
              result.warningCode = 'OverThreshold'
              result.warning = 'Number generator sequence is approaching its maximum number'
            }
            result.status = 'WARNING'
            // Don't break out, because we still want to generate the number, just with a warning
          default:
            // Run the generator
            DecimalFormat df = ngs.format ? new DecimalFormat(ngs.format) : null;
            String generated_number = df ? df.format(next_seqno) : next_seqno.toString();
            String checksum_input_template = applyPreChecksumTemplate ( ngs, generated_number );
            String checksum = null;
            if (ngs.checkDigitAlgo && ngs.checkDigitAlgo.value != 'none') {
              checksum = generateCheckSum(ngs.checkDigitAlgo.value, checksum_input_template)
            }
      
            // If we don't override the template generate strings of the format
            // prefix-number-postfix-checksum
            Map template_parameters = [
                            prefix: ngs.prefix, // Deprecated
                  generated_number: generated_number,
              checksum_input_template: checksum_input_template,
                           postfix: ngs.postfix, // Deprecated
                          checksum: checksum
            ]
            def engine = new groovy.text.SimpleTemplateEngine()
            // If the seq specifies a template, use it here, otherwise just use the default
            def number_template = engine.createTemplate(ngs.outputTemplate?:default_template).make(template_parameters)
            result.nextValue = number_template.toString();
            ngs.save(flush:true, failOnError:true);
            break;
        }
      } else {
        result.status = 'ERROR'
        result.errorCode = 'NoSequence'
        result.message = "Unable to locate or create NumberGeneratorSequence for ${generator}.${sequence}".toString()
        // Undo any changes made to ngs
        status.setRollbackOnly()
      }
    }

    render result as JSON;
  }

	private String applyPreChecksumTemplate(NumberGeneratorSequence ngs, String generated_number) {
		String result = generated_number;
    if ( ngs.preChecksumTemplate != null ) {
      def engine = new groovy.text.SimpleTemplateEngine()
      Map template_parameters = [
				'generated_number':generated_number
      ]
      def pre_checksum_template = engine.createTemplate(ngs.preChecksumTemplate).make(template_parameters)
      generated_number = pre_checksum_template.toString();
		}
		return generated_number;
	}

  private String invertChecksum(String checksum, int base = 10) {
    return (base - Integer.parseInt(checksum)).toString();
  }

  // See https://www.activebarcode.com/codes/checkdigit/modulo47.html
  // See also: https://commons.apache.org/proper/commons-validator/apidocs/org/apache/commons/validator/routines/checkdigit/package-summary.html
  // and N.B. commons already has implementations for all the ones we need
  // Remember - RefdataValue normalizes values - so EAN13 becomes ean13 here

  // Returns List<String>, with checksum first and "inverse" checksum second
  private String generateCheckSum(String algorithm, String value_to_check) {
    log.debug("generateCheckSum(${algorithm},${value_to_check})");
    String cs = null;
    switch(algorithm) {
      case 'ean13':
        cs = new EAN13CheckDigit().calculate(value_to_check);
        break;
      case 'isbn10checkdigit':
        cs = new ISBN10CheckDigit().calculate(value_to_check);
        break;
      case 'issncheckdigit':
        cs = new ISSNCheckDigit().calculate(value_to_check);
        break;
      case 'luhncheckdigit':
        cs = new LuhnCheckDigit().calculate(value_to_check);
        break;
      case '1793_ltr_mod10_r':
        // We need to invert again here for type "R" because the default for ModulusTen is the inversion
        cs = invertChecksum(new ModulusTenCheckDigit(new int[] { 1, 7, 9, 3 }, false).calculate(value_to_check));
        break;
      case '12_ltr_mod10_r':
        // We need to invert again here for type "R" because the default for ModulusTen is the inversion
        cs = invertChecksum(new ModulusTenCheckDigit(new int[] { 1, 2 }, false).calculate(value_to_check));
        break;
      default:
				throw new RuntimeException("Unknown check digit algorithm ${algorithm}");
        break;
    }

    return cs;
  }

	private int[] toIntArray(String value) {
    // Loop through each character in the string
		int[] result = new int[value.length()];
    for (int i = 0; i < value.length(); i++) {
      result[i] = Character.getNumericValue(value.charAt(i));
    }
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
