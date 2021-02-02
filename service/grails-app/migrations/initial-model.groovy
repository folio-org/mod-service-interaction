databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "2021-02-01-1350-001") {
      createTable(tableName: "user_proxy") {
          column(name: "up_id", type: "VARCHAR(36)") {
              constraints(nullable: "false")
          }

          column(name: "up_version", type: "BIGINT") {
              constraints(nullable: "false")
          }

          column(name: "up_user_uuid", type: "VARCHAR(255)")
      }
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-01-1350-002") {
    addPrimaryKey(columnNames: "up_id", constraintName: "user_proxyPK", tableName: "user_proxy")
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-01-1350-003") {
      createTable(tableName: "dashboard") {
          column(name: "dshb_id", type: "VARCHAR(36)") {
              constraints(nullable: "false")
          }

          column(name: "dshb_version", type: "BIGINT") {
              constraints(nullable: "false")
          }

          column(name: "dshb_name", type: "VARCHAR(255)")

          column(name: "dshb_owner_fk", type: "VARCHAR(36)")
      }
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-01-1350-004") {
    addPrimaryKey(columnNames: "dshb_id", constraintName: "dashboardPK", tableName: "dashboard")
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-01-1350-005") {
    addForeignKeyConstraint(baseColumnNames: "dshb_owner_fk",
      baseTableName: "dashboard",
      constraintName: "dashboard_owner_fk",
      deferrable: "false", initiallyDeferred: "false",
      referencedColumnNames: "up_id", referencedTableName: "user_proxy")
  }
}
