databaseChangeLog = {
  // These were the last few needed manually from web-toolkit before we started to import changelog files directly from WT
  changeSet(author: "efreestone (manual)", id: "2023-02-16-1519-001") {
    addColumn (tableName: "custom_property_definition" ) {
      column(name: "pd_ctx", type: "VARCHAR(255)")
    }
  }
  
  changeSet(author: "efreestone (manual)", id: "2023-02-16-1519-002") {
    addColumn(tableName: "custom_property_definition") {
      column(name: "pd_retired", type: "BOOLEAN")
    }
    addNotNullConstraint (tableName: "custom_property_definition", columnName: "pd_retired", defaultNullValue: 'FALSE')
  }

  changeSet(author: "efreestone (manual)", id: "2023-02-16-1519-003") {
    createIndex(indexName: "td_retired_idx", tableName: "custom_property_definition") {
      column(name: "pd_retired")
    }
  }

  // Change name of ILL patron request generator
  changeSet(author: "efreestone (manual)", id: "2023-02-16-1448-001") {
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.number_generator
          SET ng_name = 'ILL: Patron request number'
          WHERE ng_code = 'patronRequest'
        """.toString())
      }
    }
  }

  // Change name of open access generator
  changeSet(author: "efreestone (manual)", id: "2023-02-16-1448-002") {
    grailsChange {
      change {
        // Change name of ILL patron request generator
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.number_generator
          SET ng_name = 'Open access: Publication request number'
          WHERE ng_code = 'openAccess'
        """.toString())
      }
    }
  }

  // Change name/code of patron generator
  changeSet(author: "efreestone (manual)", id: "2023-02-16-1448-003") {
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.number_generator
          SET ng_name = 'Users: Patron barcode'
          WHERE ng_code = 'Patron'
        """.toString())

        sql.execute("""
          UPDATE ${database.defaultSchemaName}.number_generator
          SET ng_code = 'users_patronBarcode'
          WHERE ng_name = 'Users: Patron barcode'
        """.toString())
      }
    }
  }

  // Delete old organizations generator (New one should be created through housekeeping)
  changeSet(author: "efreestone (manual)", id: "2023-02-16-1448-004") {
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.number_generator
          SET ng_name = 'Organizations: Vendor code'
          WHERE ng_code = 'Vendor'
        """.toString())

        sql.execute("""
          UPDATE ${database.defaultSchemaName}.number_generator
          SET ng_code = 'organizations_vendorCode'
          WHERE ng_name = 'Organizations: Vendor code'
        """.toString())
      }
    }
  }
}
