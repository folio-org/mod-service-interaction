package org.olf

import static groovyx.net.http.ContentTypes.*
import static groovyx.net.http.HttpBuilder.configure
import static org.springframework.http.HttpStatus.*

import com.k_int.okapi.OkapiHeaders
import com.k_int.okapi.OkapiTenantResolver
import geb.spock.GebSpec
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer
import groovyx.net.http.HttpBuilder
import groovyx.net.http.HttpVerb
import java.time.LocalDate
import spock.lang.Stepwise
import spock.lang.Unroll

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class NumberGeneratorSpec extends BaseSpec {
  private static String DEFAULT_TEMPLATE = '''${prefix?prefix+'-':''}${generated_number}${postfix?'-'+postfix:''}${checksum?'-'+checksum:''}'''

  void "Configure Number Generator" () {
    when: 'We post the user barcode number generator'
      log.debug("Create new number generator for user barcode")

      Map user_barcode_numgen = [
        'code': 'TSTUserBarcode',
        'name': 'TSTUser Barcode',
        'defaultSequenceCode': 'patron',
        'description' : 'User Barcode Test Cases',
        'sequences':[
          [ 'name': 'patron',    'code': 'patron',    'prefix':'user',      'postfix':null,       'format':'000000000',         'enabled':true ],
          [ 'name': 'staff',     'code': 'staff',     'prefix':'staff',     'postfix':'test',     'format':'000,000,000',       'enabled':true ],
          [ 'name': 'noformat',  'code': 'noformat',  'prefix':'nf',        'enabled':true ],
          [ 'name': 'highinit',  'code': 'highinit',  'prefix':'hi',        'format':'000000000', 'nextValue':100000,           'enabled':true ],
          [ 'name': 'mod10test', 'code': 'mod10test', 'format':'000000000', 'nextValue':100000,   'checkDigitAlgo': 'ModulusTenCheckDigit', 'enabled':true ],
          [ 'name': 'mod11test', 'code': 'mod11test', 'format':'000000000', 'nextValue':100000,   'checkDigitAlgo': 'ISBN10CheckDigit', 'enabled':true ],
          [ 'name': '069',       'code': '069',       'prefix':'069',       'postfix':'1',        'format':'000000000',         'enabled':true,  'nextValue':1,   'checkDigitAlgo':'EAN13' ],
          [ 'name': '0698',      'code': '0698',      'format':'000000000', 'nextValue':1,        'checkDigitAlgo': 'EAN13',    'enabled':true,  'outputTemplate':'0698${generated_number}${checksum}',  ],
          [ 'name': '0699',      'code': '0699',      'format':'000000000', 'nextValue':1,        'checkDigitAlgo': 'EAN13',    'enabled':true,  'outputTemplate':'0699-${generated_number}-${checksum}-post'],
          [ 'name': '0700',      'code': '0700',      'format':'000000000', 'nextValue':1,        'checkDigitAlgo': 'EAN13',    'enabled':true,  'outputTemplate':'0700-${generated_number.substring(0,4)}-${checksum}-${generated_number.substring(4,9)}-post'],
          [ 'name': 'DD',        'code': 'DD',        'prefix':'DD',        'format':'000000000', 'nextValue':1,                'enabled':true ],
          [ 'name': 'distest',   'code': 'distest',   'prefix':'DD',        'format':'000000000', 'nextValue':1,                'enabled':false, 'description': 'THis one is disabled' ],
					// All the things
          [ 'name': '0800',
            'code': '0800',
            'format':'000000000',
            'nextValue':1,
            'checkDigitAlgo': 'EAN13',
            'enabled':true,
            'outputTemplate':'0700-${generated_number}-${checksum}-post',
            'preChecksumTemplate':'100${generated_number}001'
          ],
        ]
      ]

      Map respMap = doPost("/servint/numberGenerators", user_barcode_numgen)
    then: "Response is good and we have a new ID"
      respMap.id != null

    when: 'We post the checksum testing number generator'
      log.debug("Create new number generator for checksum testing")

      Map checksum_test_numgen = [
        code:'checksumTest',
        name:'Checksum testing',
        sequences: [
          [
            name: 'Luhn test',
            code:'luhnTest',
            format:'00000000',
            nextValue: 117707,
            checkDigitAlgo:'luhncheckdigit',
            preChecksumTemplate: '22356${generated_number}',
            outputTemplate:'${checksum_calculation}${checksum}',
            note: 'Starting value for use case example is 117707'
          ],
          [
            name: 'EAN test',
            code:'eanTest',
            format:'0000000',
            nextValue: 254,
            checkDigitAlgo:'ean13',
            preChecksumTemplate: '0017${generated_number}',
            outputTemplate:'${checksum_calculation}${checksum}',
            note: 'Starting value for use case example is 254'
          ],
          [
            name: '1793 mod10 test',
            code:'1793Mod10Test',
            format:'00000000',
            nextValue: 771962,
            checkDigitAlgo:'1793_ltr_mod10_r',
            preChecksumTemplate: null, // Not required for this use case
            outputTemplate:'${generated_number}${inverted_checksum}077', // inverse_checksum required for use case
            note: 'Starting value for use case example is 771962'
          ],
          [
            name: '12 LTR mod10 test',
            code:'12ltrmod10test',
            format:'0000000',
            nextValue: 7298,
            checkDigitAlgo:'12_ltr_mod10_r',
            preChecksumTemplate: '05${generated_number}01',
            outputTemplate:'${checksum_calculation}${inverted_checksum}', // inverse_checksum required for use case
            note: 'Starting value for use case example is 7298'
          ]
        ]
      ];

      respMap = doPost("/servint/numberGenerators", checksum_test_numgen)

    then: "Response is good and we have a new ID"
      respMap.id != null
      respMap.sequences.size() == 4;
  }

  void "Get next number in user patron sequence"(gen, seq, expected_response_code, expected_result, tmpl) {
    when: 'We post to the getNextNumber action'
      Map resp = doGet("/servint/numberGenerators/getNextNumber", ['generator':gen, 'sequence':seq] )
    then: 'We get the next number'
      log.debug("NumberGenerator Test Got result ${resp} template was ${tmpl}");
      resp != null;
      resp.nextValue == expected_result
    where:
      gen | seq | expected_response_code | expected_result | tmpl
      'TSTUserBarcode' | 'patron'    | 200 | 'user-000000001'          | DEFAULT_TEMPLATE
      'TSTUserBarcode' | 'patron'    | 200 | 'user-000000002'          | DEFAULT_TEMPLATE
      'TSTUserBarcode' | 'patron'    | 200 | 'user-000000003'          | DEFAULT_TEMPLATE
      'TSTUserBarcode' | 'staff'     | 200 | 'staff-000,000,001-test'  | DEFAULT_TEMPLATE
      'TSTUserBarcode' | 'noformat'  | 200 | 'nf-1'                    | DEFAULT_TEMPLATE
      'TSTUserBarcode' | 'highinit'  | 200 | 'hi-000100000'            | DEFAULT_TEMPLATE
			// ISBN==mod11
      'TSTUserBarcode' | 'mod11test' | 200 | '000100000-4'             | DEFAULT_TEMPLATE
      'TSTUserBarcode' | '069'       | 200 | '069-000000001-1-7'       | DEFAULT_TEMPLATE
      'TSTUserBarcode' | '0698'      | 200 | '06980000000017'          | '0698${generated_number}${checksum}'
      'TSTUserBarcode' | '0699'      | 200 | '0699-000000001-7-post'   | '0699-${generated_number}-${checksum}-post'
      'TSTUserBarcode' | '0700'      | 200 | '0700-0000-7-00001-post'  | '0700-${generated_number.substring(0,4)}-${checksum}-${generated_number.substring(5,9)}-post'
      'TSTUserBarcode' | 'DD'        | 200 | 'DD-000000001'            | DEFAULT_TEMPLATE
      'TSTUserBarcode' | '0800'      | 200 | '0700-100000000001001-3-post' | '0700-${generated_number}-${checksum}-post'
  }

  void "Get Number Generator Record"() {
    when: 'we get the UserBarcode generator'
      Map resp = doGet("/servint/numberGenerators", [filters:['code==TSTUserBarcode'], stats:'true'])

    then: 'Get the record back'
      log.debug("Got resp ${resp}");
      resp != null
      resp.totalRecords == 1
  }

  void "Test the automatic creation of number generators"(gen, seq, expected_response_code, expected_result) {
    when: 'We post to the getNextNumber action'
      Map resp = doGet("/servint/numberGenerators/getNextNumber", ['generator':gen, 'sequence':seq] )

    then: 'We get the next number'
      log.debug("Got result ${resp}");
      resp != null;
      resp.nextValue == expected_result
    where:
      gen      | seq        | expected_response_code | expected_result
      'OA'     | 'default'  | 200                    | '000000001'
      'OA'     | 'default'  | 200                    | '000000002'
      'OA'     | 'notdef'   | 200                    | '000000001'
      'Wibble' | 'dibble'   | 200                    | '000000001'
  }
}

