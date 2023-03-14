## 2.3.0 In progress

## 2.2.1 2023-02-20
  * ERM-2433 Bumped dependencies of postgres, opencsv, web-toolkit and grails-okapi
    * Addec migrations to handle updates to grails-okapi and web-toolkit
  * Added migrations for better number generator generator names/codes (Code changes only for those not in use in production already)
  * Number generator names bootstrapped into the system were too generic, changes to bootstrapped data and migrations for existing data made
  * Fix for initialiseDefaultSequence, wasn't saving thanks to non-nullable name field

## 2.1.0 2023-01-10
  * Fix for CheckDigitAlgo

## 2.0.0 2022-10-25
  * ERM-2312 Managed Dashboards: backend model
    * Changed domain model to allow for multiple dashboards per user and multiple users per dashboard
    * Changed endpoints to reflect this (Breaking change)
  * Number generator
    * Refactors
    * Added configuration for user sequences
    * NextValue defaults to 1
    * Added `enabled` and `description` fields
    * Added `name` field

## 1.1.0 2022-06-29
  * ERM-2134 Service Interaction - mod-service-interaction lacks memory limit in launch descriptor
  * ERM-2071 mod-service-interaction Grails wrapper SAXParseException
  * Number generator
    * Added Number Generator domain classes
    * Number Generator endpoints and services

## 1.0.0 2021-06-15
* ERM-1740: Method to refresh WidgetTypes from scratch
* ERM-1738: Support health check endpoint for mod-service-interaction
* ERM-1696: Support match type search in SimpleSearch Widgets
* ERM-1685: Support "Link" result values
* ERM-1651/ERM-1652/ERM-1653: Support "Array" display values
* ERM-1650: Add unique indexes for refdata tables
* ERM-1643: Manage tenant widget definitions for different applications
* ERM-1580: Added weight to WidgetInstances on dashboard
* ERM-1579: WidgetDef tweaks, added UUID valueType
* ERM-1562: Formatting of bootstrapped data, added licenses definition and "Enum" valueType
* ERM-1546: Added some more structure to widgetType
* ERM-1529: Initial setup, added domain classes, controllers and initial widget data