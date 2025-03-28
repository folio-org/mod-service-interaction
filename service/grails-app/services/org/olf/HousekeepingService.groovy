package org.olf

import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.refdata.RefdataValue;

import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import org.olf.numgen.*;


/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
public class HousekeepingService {

  @Subscriber('okapi:dataload:reference')
  public void onLoadReference (final String tenantId, String value, final boolean existing_tenant, final boolean upgrading, final String toVersion, final String fromVersion) {
    log.debug("ServintHousekeepingService::onLoadReference(${tenantId},${value},${existing_tenant},${upgrading},${toVersion},${fromVersion})");
    final String tenant_schema_id = OkapiTenantResolver.getTenantSchemaName(tenantId)
    try {
      Tenants.withId(tenant_schema_id) {

        // Setup refdata (more granular control than @Default)
        RefdataValue.withTransaction {
          [
            [
              cat: 'NumberGeneratorSequence.CheckDigitAlgo',
              value: 'none',
              label: 'None',
              defaultInternal: true
            ],
            [
              cat: 'NumberGeneratorSequence.CheckDigitAlgo',
              value: 'ean13',
              label: '31-RTL-mod10-I (EAN)',
              defaultInternal: true
            ],
            [
              cat: 'NumberGeneratorSequence.CheckDigitAlgo',
              value: '1793_ltr_mod10_r',
              label: '1793-LTR-mod10-R',
              defaultInternal: true
            ],
            [
              cat: 'NumberGeneratorSequence.CheckDigitAlgo',
              value: '12_ltr_mod10_r',
              label: '12-LTR-mod10-R',
              defaultInternal: true
            ],
            [
              cat: 'NumberGeneratorSequence.CheckDigitAlgo',
              value: 'isbn10checkdigit',
              label: '2345678910-RTL-mod11-I-X (ISBN10)',
              defaultInternal: true
            ],
            [
              cat: 'NumberGeneratorSequence.CheckDigitAlgo',
              value: 'issncheckdigit',
              label: '8765432-LTR-mod11-I-X (ISSN)',
              defaultInternal: true
            ],
            [
              cat: 'NumberGeneratorSequence.CheckDigitAlgo',
              value: 'luhncheckdigit',
              label: '21-RTL-mod10-I (Luhn)',
              defaultInternal: true
            ]
          ].each { rdv ->
            RefdataValue.lookupOrCreate(rdv.cat, rdv.label, rdv.value, rdv.defaultInternal);
          }
        }

        // Load default sequences available for all installations
        NumberGeneratorSequence.withTransaction {
          [
            [
              /*
                -- IMPORTANT --
                this one was created before we decided to standardise,
                so only label can change, but future generators should have more specific
                names AND codes -- see vendor code below
              */
              code:'openAccess',
              name:'Open access: Publication request number',
              sequences: [
                [
                  name: 'Request sequence',
                  code:'requestSequence',
                  format:'000000000',
                  checkDigitAlgo:'None',
                  outputTemplate:'oa-${generated_number}'
                ]
              ]
            ],
            [
              /*
                -- IMPORTANT --
                this one was created before we decided to standardise,
                so only label can change, but future generators should have more specific
                names AND codes -- see vendor code below
              */
              code:'patronRequest',
              name:'ILL: Patron request number',
              sequences: [
                [
                  name: 'Request sequence',
                  code:'requestSequence',
                  format:'000000000',
                  checkDigitAlgo:'EAN13',
                  outputTemplate:'ill-${generated_number}-${checksum}'
                ]
              ]
            ],
            [
              name:'Users: Patron barcode',
              code:'users_patronBarcode',
              sequences: [
                [
                  name: 'Patron',
                  code: 'patron',
                  format: '000000000',
                  checkDigitAlgo: 'EAN13',
                  outputTemplate: 'P${generated_number}-${checksum}'
                ],
                [
                  name: 'Staff',
                  code: 'staff',
                  format: '000000000',
                  checkDigitAlgo: 'EAN13',
                  outputTemplate: 'S${generated_number}-${checksum}'
                ]
              ]
            ],
            [
              name:'Organizations: Vendor code',
              code:'organizations_vendorCode',
              sequences: [
                [
                  name: 'Vendor',
                  code: 'vendor',
                  format: '000',
                  checkDigitAlgo: 'None',
                  outputTemplate:'K${generated_number}'
                ],
              ]
            ],
            [
              name:'Inventory: Accession number',
              code:'inventory_accessionNumber',
              sequences: [
                [
                  name: 'Accession number',
                  code:'accessionNumber',
                  format:'00000',
                  checkDigitAlgo:'None',
                  outputTemplate:'31A-2023-${generated_number}'
                ],
              ]
            ],
            [
              name:'Inventory: Call number',
              code:'inventory_callNumber',
              sequences: [
                [
                  name: 'Call number',
                  code:'callNumber',
                  format:'00000',
                  checkDigitAlgo:'None',
                  outputTemplate:'B 2023 / ${generated_number}'
                ],
              ]
            ],
            [
              name:'Inventory: Item barcode',
              code:'inventory_itemBarcode',
              sequences: [
                [
                  name: 'Item barcode',
                  code:'itemBarcode',
                  format:'0000000000',
                  checkDigitAlgo:'None',
                  outputTemplate:'${generated_number}'
                ],
              ]
            ],
            [
              name:'Serials management: Pattern number',
              code:'serialsManagement_patternNumber',
              sequences: [
                [
                  name: 'Pattern number',
                  code:'patternNumber',
                  format:'000000000',
                  checkDigitAlgo:'None',
                  outputTemplate:'pattern-${generated_number}'
                ],
              ]
            ],
          ].each { ng_defn ->
            NumberGenerator ng = NumberGenerator.findByCode(ng_defn.code) ?: new NumberGenerator(code:ng_defn.code, name:ng_defn.name).save(flush:true, failOnError:true);
            ng_defn.sequences.each { seq_defn ->
              NumberGeneratorSequence ngs = NumberGeneratorSequence.findByOwnerAndCode(ng, seq_defn.code)
              if ( ngs == null ) {
                ngs = new NumberGeneratorSequence(
                        owner: ng,
                        code: seq_defn.code,
                        format: seq_defn.format,
                        name: seq_defn.name,
                        nextValue: seq_defn.nextValue ?: 1,
                        note: seq_defn.note,
                        checkDigitAlgo: seq_defn.checkDigitAlgo ? RefdataValue.lookupOrCreate('NumberGeneratorSequence.CheckDigitAlgo',seq_defn.checkDigitAlgo) : null,
                        preChecksumTemplate: seq_defn.preChecksumTemplate,
                        outputTemplate:seq_defn.outputTemplate
                      ).save(flush:true, failOnError:true)
              }
            }
          }
        }
      }
    }
    catch ( Exception e ) {
      log.error("Problem with load reference",e);
    }
  }
}
