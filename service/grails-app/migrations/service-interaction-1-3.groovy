databaseChangeLog = {
  // Create dashboard access table
  changeSet(author: "efreestone (manual)", id: "2022-08-10-1238-001") {
    createTable(tableName: "dashboard_access") {
      column(name: "da_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "da_version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "da_dashboard_fk", type: "VARCHAR(36)")
      column(name: "da_user_fk", type: "VARCHAR(36)")
      column(name: "da_access_fk", type: "VARCHAR(36)")

      column(name: "da_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")
      column(name: "da_user_dashboard_weight", type: "int8")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2022-08-11-1021-001") {
    addForeignKeyConstraint(
      baseTableName: "dashboard_access",
      baseColumnNames: "da_dashboard_fk",
      constraintName: "dashboard_access_dashboard_FK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedTableName: "dashboard",
      referencedColumnNames: "dshb_id"
    )
  }

  changeSet(author: "efreestone (manual)", id: "2022-08-11-1021-002") {
    addForeignKeyConstraint(
      baseTableName: "dashboard_access",
      baseColumnNames: "da_user_fk",
      constraintName: "dashboard_access_user_FK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedTableName: "external_user",
      referencedColumnNames: "eu_id"
    )
  }

  changeSet(author: "efreestone (manual)", id: "2022-08-11-1021-003") {
    addForeignKeyConstraint(
      baseTableName: "dashboard_access",
      baseColumnNames: "da_access_fk",
      constraintName: "dashboard_access_access_FK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedTableName: "refdata_value",
      referencedColumnNames: "rdv_id"
    )
  }

   // Add refdata category for 'DashboardAccess.Access' (if it does not exist)
  changeSet(author: "ethanfreestone (manual)", id: "2022-08-11-1021-004") {
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_category (rdc_id, rdc_version, internal, rdc_description) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, true as internal, 'DashboardAccess.Access' as description WHERE NOT EXISTS (SELECT rdc_description FROM ${database.defaultSchemaName}.refdata_category WHERE (rdc_description)=('DashboardAccess.Access') LIMIT 1);".toString())
      }
    }
  }

  // Ensure 'manage' is a refdata value for rdc 'DashboardAccess.Access'
  // (We can leave the other ones to the @Default annotation because we don't need them in the migration)
  changeSet(author: "ethanfreestone (manual)", id:"2022-08-11-1021-005") {
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_value (rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'manage' as value, (SELECT rdc_id FROM  ${database.defaultSchemaName}.refdata_category WHERE rdc_description='DashboardAccess.Access') as owner, 'Manage' as label WHERE NOT EXISTS (SELECT rdv_id FROM ${database.defaultSchemaName}.refdata_value INNER JOIN ${database.defaultSchemaName}.refdata_category ON refdata_value.rdv_owner = refdata_category.rdc_id WHERE rdc_description='DashboardAccess.Access' AND rdv_value='manage' LIMIT 1);".toString())
      }
    }
  }

  // Transfer all existing dashboards over to this model
  changeSet(author: "efreestone (manual)", id: "2022-08-11-1021-006") {
     grailsChange {
      change {
        // Grab the rdv_id for the "Manage" access level
        Map manage_rdv = sql.firstRow("""
          SELECT rdv_id FROM
            ${database.defaultSchemaName}.refdata_value INNER JOIN
            ${database.defaultSchemaName}.refdata_category
            ON refdata_value.rdv_owner = refdata_category.rdc_id
            WHERE rdc_description='DashboardAccess.Access' AND rdv_value='manage'
        """.toString())
        String manage_id = manage_rdv['rdv_id'];
        
        // For each dashboard in the system, create a dashboard access object and rename (Should all be called DEFAULT, rename to DEFAULT_userID)
        sql.eachRow("SELECT dshb.dshb_id, dshb.dshb_owner_fk FROM ${database.defaultSchemaName}.dashboard as dshb".toString()) { def dashMap ->
          sql.execute("""
            INSERT INTO ${database.defaultSchemaName}.dashboard_access
            (da_id, da_version, da_dashboard_fk, da_user_fk, da_access_fk, da_user_dashboard_weight)
              SELECT md5(random()::text || clock_timestamp()::text) as da_id,
              0 as da_version,
              :dashboard as da_dashboard_fk,
              :user as da_user_fk,
              :manage as da_access_fk,
              0 as da_user_dashboard_weight
          """.toString(), [dashboard: dashMap['dshb_id'], user: dashMap['dshb_owner_fk'], manage: manage_id])

          // Rename dashboards "my dashboard" from "DEFAULT"
          sql.execute("""
            UPDATE ${database.defaultSchemaName}.dashboard
            SET dshb_name = 'My dashboard'
            WHERE dshb_id = :dashId
          """.toString(), [dashId: dashMap['dshb_id']])
        }
      }
    }
  }

  // Remove now unnecessary owner column from dashboard
  changeSet(author: "sosguthorpe (generated)", id: "2022-08-11-1021-007") {
    dropColumn(
      tableName: "dashboard",
      columnName: "dshb_owner_fk"
    )
  }
}
