databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "2021-02-01-1350-001") {
      createTable(tableName: "external_user") {
          column(name: "eu_id", type: "VARCHAR(36)") {
              constraints(nullable: "false")
          }

          column(name: "eu_version", type: "BIGINT") {
              constraints(nullable: "false")
          }
      }
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-01-1350-002") {
    addPrimaryKey(columnNames: "eu_id", constraintName: "externalUserPK", tableName: "external_user")
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
      referencedColumnNames: "eu_id", referencedTableName: "external_user")
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-02-1611-001") {
    createTable(tableName: "widget_type") {
      column(name: "wtype_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "wtype_version", type: "BIGINT") {
        constraints(nullable: "false")
      }
      
      column(name: "wtype_type_version", type: "VARCHAR(36)")
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
      column(name: "wdef_definition_version", type: "VARCHAR(36)")
      column(name: "wdef_name", type: "VARCHAR(255)")
      column(name: "wdef_definition", type: "text")
      column(name: "wdef_type_name", type: "VARCHAR(255)")
      column(name: "wdef_type_version", type: "VARCHAR(36)")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-02-1611-004") {
    addPrimaryKey(columnNames: "wdef_id", constraintName: "widgetDefinitionPK", tableName: "widget_definition")
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-08-1131-001") {
    createTable(tableName: "widget_instance") {
      column(name: "wins_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "wins_version", type: "BIGINT") {
        constraints(nullable: "false")
      }
      column(name: "wins_name", type: "VARCHAR(255)")
      column(name: "wins_definition_name", type: "VARCHAR(255)")
      column(name: "wins_definition_version", type: "VARCHAR(36)")
      column(name: "wins_owner_fk", type: "VARCHAR(36)")
      column(name: "wins_configuration", type: "text")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-08-1131-002") {
    addPrimaryKey(columnNames: "wins_id", constraintName: "widgetInstancePK", tableName: "widget_instance")
  }

  changeSet(author: "efreestone (manual)", id: "2021-02-08-1131-004") {
    addForeignKeyConstraint(baseColumnNames: "wins_owner_fk",
      baseTableName: "widget_instance",
      constraintName: "widget_instance_owner_fk",
      deferrable: "false", initiallyDeferred: "false",
      referencedColumnNames: "dshb_id", referencedTableName: "dashboard"
    )
  }

  changeSet(author: "efreestone (manual)", id: "2021-03-08-1243-001") {
    addColumn (tableName: "widget_instance" ) {
      column(name: "wins_weight", type: "int8")
    }
  }

  // Add uniqueness constraint to name+version of widget definitions and types
  changeset(author: "efreestone (manual)", id: "2021-04-14-1228-001") {
    addUniqueConstraint(columnNames: "wdef_name, wdef_definition_version", constraintName: "widget_definition_name_version_unique", tableName: "widget_definition")
    addUniqueConstraint(columnNames: "wtype_name, wtype_type_version", constraintName: "widget_type_name_version_unique", tableName: "widget_type")
  }
}
