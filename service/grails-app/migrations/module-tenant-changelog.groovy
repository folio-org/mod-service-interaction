databaseChangeLog = {
  include file: 'initial-customisations.groovy'
  include file: 'setup-refdata.groovy'
  include file: 'setup-custom-properties.groovy'
  include file: 'setup-app-settings.groovy'
  include file: 'create-mod-service-interaction.groovy'
  include file: 'initial-model.groovy'
}
