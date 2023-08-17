databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "2023-08-16-0940-001") {
    addColumn (tableName: "dashboard" ) {
      column(name: "dshb_display_data", type: "text")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2023-08-17-1207-001") {
    createTable (tableName: "dashboard_display_data" ) {
      column(name: "ddd_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "ddd_version", type: "BIGINT") {
        constraints(nullable: "false")
      }
      column(name: "ddd_dash_id", type: "VARCHAR(36)")
      column(name: "ddd_layout_data", type: "text")
    }
  }

  changeSet(author: "efreestone (manual)", id: "2023-08-17-1207-002") {
    addUniqueConstraint(
      columnNames: "ddd_dash_id",
      constraintName: "dashboard_display_data_dash_id_unique",
      tableName: "dashboard_display_data"
    )
  }
}
