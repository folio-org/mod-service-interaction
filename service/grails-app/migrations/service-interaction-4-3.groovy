databaseChangeLog = {
  changeSet(author: "ian (manual)", id: "2025-11-15-0903-001") {
      createTable(tableName: "key_pair") {
          column(name: "kp_id", type: "VARCHAR(36)") {
              constraints(nullable: "false")
          }

          column(name: "kp_version", type: "BIGINT") {
              constraints(nullable: "false")
          }

          column(name: "kp_created_at", type: "TIMESTAMP WITHOUT TIME ZONE")
          column(name: "kp_expires_at", type: "TIMESTAMP WITHOUT TIME ZONE")
          column(name: "kp_usage", type: "VARCHAR(32)")
          column(name: "kp_alg", type: "VARCHAR(32)")
          column(name: "kp_public_key", type: "text")
          column(name: "kp_private_key", type: "text")
      }
  }
}
