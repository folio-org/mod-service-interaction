databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "2023-12-08-1010-001") {
    addColumn (tableName: "number_generator_sequence" ) {
      column(name: "ngs_maximum_number", type: "bigint")
    }

    addColumn (tableName: "number_generator_sequence" ) {
      column(name: "ngs_maximum_number_threshold", type: "bigint")
    }

    addColumn (tableName: "number_generator_sequence" ) {
      column(name: "ngs_maximum_check_fk", type: "varchar(36)")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2023-12-08-1010-002") {
    addForeignKeyConstraint(
      baseColumnNames: "ngs_maximum_check_fk",
      baseTableName: "number_generator_sequence",
      constraintName: "ngs_ngs_maximum_check_FK_CONSTRAINT",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "rdv_id",
      referencedTableName: "refdata_value"
    )
  }
}
