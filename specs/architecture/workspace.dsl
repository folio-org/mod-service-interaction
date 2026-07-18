/*
 * SDD Architecture Specification — mod-service-interaction (parity target).
 * Structurizr DSL (C4). Cross-references live in the companion
 * workspace.dsl.refs.yaml sidecar.
 */

workspace "mod-service-interaction" "FOLIO cross-app connectivity module — Java Spring Boot rewrite, wire- and data-compatible with the legacy Grails module" {

    model {
        user = person "FOLIO User" "Library staff or patron using dashboards and drawing generated numbers"
        okapi = softwareSystem "Okapi / FOLIO Platform" "API gateway routing tenant-scoped requests; drives tenant lifecycle and timers; hosts sibling modules implementing the dashboard interface" "External"

        system = softwareSystem "mod-service-interaction" "Cross-app connectivity module: dashboards and widgets, number generation, attested assertions, platform services" {
            numgen = container "Number Generation" "Number generator and sequence CRUD, atomic next-number generation with templates and check digits, year reset, maximum guards" "Spring Boot service layer"
            dashboards = container "Dashboards and Widgets" "User dashboards with per-dashboard access control, widget instances, widget definitions and types, cross-module definition federation" "Spring Boot service layer"
            attestation = container "Attestation" "RFC 8693 attested assertion tokens signed RS256 with per-tenant rotating RSA key pairs" "Spring Boot service layer"
            platform = container "Platform Services" "Refdata vocabularies, application settings, tenant lifecycle with reference-data seeding, admin maintenance actions" "Spring Boot service layer"
        }

        user -> system "Uses via Okapi"
        okapi -> system "Routes requests, drives _tenant and _timer"
        dashboards -> okapi "Harvests /dashboard/definitions from every implementing module"
    }

    views {
        systemContext system "SystemContext" {
            include *
            autoLayout
        }
        container system "Containers" {
            include *
            autoLayout
        }
    }

}
