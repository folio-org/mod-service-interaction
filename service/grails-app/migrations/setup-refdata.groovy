databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "202012101256-001") {
    createTable(tableName: "refdata_category") {
      column(name: "rdc_id", type: "VARCHAR(36)") {
          constraints(nullable: "false")
      }

      column(name: "rdc_version", type: "BIGINT") {
          constraints(nullable: "false")
      }

      column(name: "rdc_description", type: "VARCHAR(255)") {
          constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "efreestone (manual)", id: "202012101256-002") {
    createTable(tableName: "refdata_value") {
      column(name: "rdv_id", type: "VARCHAR(36)") {
          constraints(nullable: "false")
      }

      column(name: "rdv_version", type: "BIGINT") {
          constraints(nullable: "false")
      }

      column(name: "rdv_value", type: "VARCHAR(255)") {
          constraints(nullable: "false")
      }

      column(name: "rdv_owner", type: "VARCHAR(36)") {
          constraints(nullable: "false")
      }

      column(name: "rdv_label", type: "VARCHAR(255)") {
          constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "efreestone (manual)", id: "202012101256-003") {
    addPrimaryKey(columnNames: "rdc_id", constraintName: "refdata_categoryPK", tableName: "refdata_category")
  }

  changeSet(author: "efreestone (manual)", id: "202012101256-004") {
    addPrimaryKey(columnNames: "rdv_id", constraintName: "refdata_valuePK", tableName: "refdata_value")
  }

  changeSet(author: "efreestone (manual)", id: "202012101256-005") {
    createIndex(indexName: "rdv_entry_idx", tableName: "refdata_value") {
      column(name: "rdv_value")

      column(name: "rdv_owner")
    }
  }

  changeSet(author: "efreestone (manual)", id: "202012101256-006") {
    addForeignKeyConstraint(baseColumnNames: "rdv_owner", baseTableName: "refdata_value", constraintName: "FKh4fon2a7k4y8b2sicjm0i6oy8", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdc_id", referencedTableName: "refdata_category")
  }

  changeSet(author: "efreestone (manual)", id: "202012101256-007") {
    addColumn(tableName: "refdata_category") {
      column(name: "internal", type: "boolean")
    }
    addNotNullConstraint (tableName: "refdata_category", columnName: "internal", defaultNullValue: false)
  }

  changeSet(author: "ianibbo (manual)", id: "202106021259-001") {
    addUniqueConstraint(columnNames: "rdv_owner,rdv_value", constraintName: "RDV_UNIQUE_VALUE", tableName: "refdata_value")
  }

}
