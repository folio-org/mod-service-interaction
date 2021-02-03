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

  changeSet(author: "efreestone (manual)", id: "2021-02-02-1611-001") {
    createTable(tableName: "widget_type") {
      column(name: "wtype_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "wtype_version", type: "BIGINT") {
        constraints(nullable: "false")
      }
      
      column(name: "wtype_widget_version", type: "VARCHAR(36)")
      column(name: "wtype_name", type: "VARCHAR(255)")
      column(name: "wtype_schema", type: "text")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-02-1611-002") {
    addPrimaryKey(columnNames: "wtype_id", constraintName: "widgetTypePK", tableName: "widget_type")
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-02-1611-003") {
    createTable(tableName: "widget_definition") {
      column(name: "wdef_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "wdef_version", type: "BIGINT") {
        constraints(nullable: "false")
      }
      column(name: "wdef_name", type: "VARCHAR(255)")
      column(name: "wdef_definition", type: "text")
      
      column(name: "wdef_type_fk", type: "VARCHAR(36)")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-02-1611-004") {
    addPrimaryKey(columnNames: "wdef_id", constraintName: "widgetDefinitionPK", tableName: "widget_definition")
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-02-1611-005") {
    addForeignKeyConstraint(baseColumnNames: "wdef_type_fk",
      baseTableName: "widget_definition",
      constraintName: "widget_definition_type_fk",
      deferrable: "false", initiallyDeferred: "false",
      referencedColumnNames: "wtype_id", referencedTableName: "widget_type")
  }
   
}
