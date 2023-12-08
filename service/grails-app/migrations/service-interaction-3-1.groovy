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


  changeSet(author: "efreestone (manual)", id: "2023-12-08-1010-003") {
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.refdata_category SET internal = true
            WHERE rdc_description IN ('NumberGeneratorSequence.CheckDigitAlgo')
        """.toString())
      }
    }
  }

  // We need to ensure check digit algo is an internal refdata
  // See PR https://github.com/folio-org/mod-agreements/pull/704 for example of this in ERM
  changeSet(author: "efreestone (manual)", id: "2023-12-08-1010-004") {
		grailsChange {
			change {
				sql.eachRow("""
					SELECT DISTINCT ngs_check_digit_algorithm
          FROM ${database.defaultSchemaName}.number_generator_sequence
					WHERE NOT EXISTS (
						SELECT rdv_id FROM ${database.defaultSchemaName}.refdata_value
            WHERE rdv_id = ngs_check_digit_algorithm
					)""".toString()
				) { def row ->
          println("LOGDEBUG ROW: ${row}")
					sql.execute("""
						INSERT INTO ${database.defaultSchemaName}.refdata_value
						(rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label) VALUES
						('${row.ngs_check_digit_algorithm}',
						0,
						'missing_check_digit_algo_${row.ngs_check_digit_algorithm}',
						(
							SELECT rdc_id FROM  ${database.defaultSchemaName}.refdata_category
							WHERE rdc_description='NumberGeneratorSequence.CheckDigitAlgo'
						),
						'Missing check digit algorithm ${row.ngs_check_digit_algorithm}'
					)""".toString())
				}
			}
		}
	}

  changeSet(author: "efreestone (manual)", id: "2023-12-08-1010-005") {
    renameColumn(
      tableName: "number_generator_sequence",
      oldColumnName: "ngs_check_digit_algorithm",
      newColumnName: "ngs_check_digit_algorithm_fk"
    )
  }

  changeSet(author: "efreestone (manual)", id: "2023-12-08-1010-006") {
    addForeignKeyConstraint(
      baseColumnNames: "ngs_check_digit_algorithm_fk",
      baseTableName: "number_generator_sequence",
      constraintName: "ngs_check_digit_algo_FK_CONSTRAINT",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "rdv_id",
      referencedTableName: "refdata_value"
    )
  }
}
