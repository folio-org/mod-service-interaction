databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "2023-08-16-0940-001") {
    addColumn (tableName: "dashboard" ) {
      column(name: "dshb_display_data", type: "text")
    }
  }
}
