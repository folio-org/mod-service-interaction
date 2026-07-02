databaseChangeLog = {
  changeSet(author: "laemmer (manual)", id: "2026-06-22-0940-001") {
    addColumn(tableName: "number_generator_sequence") {
      column(name: "ngs_reset_on_year_change", type: "BOOLEAN", defaultValueBoolean: false)
      column(name: "ngs_last_used_year",       type: "VARCHAR(4)")
    }
  }
}
