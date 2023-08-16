databaseChangeLog = {
  include file: 'initial-customisations.groovy'
  include file: 'setup-refdata.groovy'
  include file: 'setup-custom-properties.groovy'
  include file: 'setup-app-settings.groovy'
  include file: 'create-mod-service-interaction.groovy'
  include file: 'initial-model.groovy'
  include file: 'number-generator-model.groovy'
  include file: 'service-interaction-1-3.groovy'
  include file: 'wtk/multi-value-custprops.feat.groovy'
  include file: 'wtk/hidden-appsetting.feat.groovy'
  include file: 'service-interaction-2-2.groovy'
  include file: 'service-interaction-3-0.groovy'
}
