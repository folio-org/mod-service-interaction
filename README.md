# mod-service-interaction

Copyright (C) 2018-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Introduction - developers looking to enhance the resources that mod-service-interaction provides

Mod-Service-Interaction is a FOLIO Backend Module for cross-app connectivity.

Mod-Service-Interaction is currently just storage for the dashboard, but since it handles things such as making multi-interface okapi calls and consolidating the returned values, this module represents an opportunity for other such operations, particularly in an ERM context, to have a home in future.

Developers looking to access the services exposed by mod-service-interaction can find more information in the following documentation:
[See the documentation](https://wiki.folio.org/display/ERM/Dashboard+Documentation)

## ModuleDescriptor
https://github.com/folio-org/mod-service-interaction/blob/master/service/src/main/okapi/ModuleDescriptor-template.json

# For module developers looking to extend or modify the resources presented by this module

This is the main starter repository for the Grails-based OLF - ERM backend modules.

- [Getting started](service/docs/getting-started.md "Getting started")

## Additional information

### Issue tracker

See project [ERM](https://issues.folio.org/projects/ERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)


## Running using grails run-app with the vagrant-db profile

    grails -Dgrails.env=vagrant-db run-app


## Initial Setup

Most developers will run some variant of the following commands the first time through

### In window #1

Start the vagrant image up from the project root

    vagrant destroy
    vagrant up

Sometimes okapi does not start cleanly in the vagrant image - you can check this with

    vagrant ssh

then once logged in

    docker ps

should list running images - if no processes are listed, you will need to restart okapi (In the vagrant image) with

    sudo su - root
    service okapi stop
    service okapi start

Finish the part off with

    tail -f /var/log/folio/okapi/okapi.log

### In window #2

Build and run mod-mod-service-int stand alone

    cd service
    grails war
    ../scripts/run_external_reg.sh

### In window #3

Register the module

  cd scripts
  ./register_and_enable.sh


### In window #4

Run up a stripes platform containing [ui-dashboard](https://github.com/folio-org/ui-dashboard)

---
**NOTE**

platform-erm does not yet contain ui-dashboard, it will soon.

---

This section is run in a local setup, not from any particular checked out project, YMMV

    cd ../platform/stripes/platform-erm
    stripes serve ./stripes.config.js --has-all-perms



You should get back

Waiting for webpack to build...
Listening at http://localhost:3000

and then be able to access the app

  

