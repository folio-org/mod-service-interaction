## 4.1.0 In progress

## 4.0.2 2024-04-17
  * ERM-3190 DB Connections are not being released
  * SI-52 Review outdated/vulnerable dependencies in mod-service-interaction
  * Fixed typo in application-vagrant-db

## 4.0.1 2024-03-25
  * Fix missing spring dependencies from built jar

## 4.0.0 2024-03-22
  * ERM-3111 Upgrade Grails from 5 to 6
  * SI-22 Expanded owner on sequences

## 3.0.3 2024-02-07
  * SI-45 Dashboards not displaying after upgrade to Poppy
    * New endpoint: `/servint/admin/ensureDisplayData` to add (empty) display data for all dashboards which are missing it.

## 3.0.2 2023-11-23
  * ERM-3112 org.json:json:20201115 DoS/OOM

## 3.0.1 2023-11-01
  * ERM-3061: On setting document filter spaces are stripped from filter value
    * Bumped kint-web-toolkit version  to 8.1.4

## 3.0.0 2023-10-06
  * ERM-2966 Error when match and filter contain the same path root
    * update to 'com.k_int.grails:web-toolkit-ce:8.1.0'
  * ERM-2940 spring-webmvc 5.3.25 security bypass vulnerability
  * ERM-2642 Hibernate JPA Criteria SQL Injection (CVE-2020-25638)
  * ERM-2641 Upgrade to Grails 5 (including Hibernate 5.6.x) for Poppy
  * ERM-1795 Dashboard: apply a common set of drag and drop styles and behaviours
    * ERM-2971 Back end changes to support storing location/dimensions of widgets
  * SI-11 Hibernate 5.6.x for Poppy
  * Number generators
    * Inventory
      * Accession number
      * Call number
      * Item barcode
    * Serials management
      * Pattern number

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
