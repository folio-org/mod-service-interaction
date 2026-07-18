# R20 rehearsal transcript — every Okapi API call, in order

Run 2026-07-21 against Okapi 7.0.6 (`okapi-r20`, dev mode, host network, :9130).
All commands executed from the repo root. Responses are verbatim (`-o` body +
`-w` status recorded together).

Pre-step (not an Okapi call): port container `msi-port-r20` started per
okapi-identification.txt; `/admin/health` polled until `{"status":"UP"}` (200);
both logging proxies smoke-tested with a GET before Okapi was wired to them
(first entries in proxy-logs/*.jsonl).

## 1. Register legacy ModuleDescriptor

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:9130/_/proxy/modules \
  -H 'Content-Type: application/json' \
  -d @service/build/resources/main/okapi/ModuleDescriptor.json
```

Response:

```
HTTP 201
```

Response body: the registered MD echoed back (201; omitted here — identical to the checked-in descriptor, sha256 in descriptors.txt).

## 2. Register port ModuleDescriptor

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:9130/_/proxy/modules \
  -H 'Content-Type: application/json' \
  -d @target/ModuleDescriptor.json
```

Response:

```
HTTP 201
```

Response body: the registered MD echoed back (201; omitted — sha256 in descriptors.txt).

## 3. Discovery entry, legacy

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:9130/_/discovery/modules \
  -H 'Content-Type: application/json' \
  -d '{"srvcId":"mod-service-interaction-4.4.0-SNAPSHOT","instId":"r20-legacy","url":"http://localhost:18080"}'
```

Response:

```
{
  "instId" : "r20-legacy",
  "srvcId" : "mod-service-interaction-4.4.0-SNAPSHOT",
  "url" : "http://localhost:18080"
}
HTTP 201
```

## 4. Discovery entry, port

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:9130/_/discovery/modules \
  -H 'Content-Type: application/json' \
  -d '{"srvcId":"mod-service-interaction-5.0.0-SNAPSHOT","instId":"r20-port","url":"http://localhost:18081"}'
```

Response:

```
{
  "instId" : "r20-port",
  "srvcId" : "mod-service-interaction-5.0.0-SNAPSHOT",
  "url" : "http://localhost:18081"
}
HTTP 201
```

## 5. Create tenant r20r

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:9130/_/proxy/tenants \
  -H 'Content-Type: application/json' -d '{"id":"r20r"}'
```

Response:

```
{
  "id" : "r20r"
}
HTTP 201
```

## 6. Enable internal okapi module (satisfies `requires okapi 1.9`)

```bash
curl -s -w '\nHTTP %{http_code}\n' --max-time 60 -X POST http://localhost:9130/_/proxy/tenants/r20r/install \
  -H 'Content-Type: application/json' -d '[{"id":"okapi","action":"enable"}]'
```

Response:

```
[ {
  "id" : "okapi-7.0.6",
  "action" : "enable"
} ]
HTTP 200
```

## 7. Step (a) INSTALL LEGACY (tenantParameters=loadReference=true,loadSample=true)

```bash
curl -s -w '\nHTTP %{http_code}\n' --max-time 300 -X POST \
  'http://localhost:9130/_/proxy/tenants/r20r/install?tenantParameters=loadReference%3Dtrue%2CloadSample%3Dtrue' \
  -H 'Content-Type: application/json' \
  -d '[{"id":"mod-service-interaction-4.4.0-SNAPSHOT","action":"enable"}]'
```

Response:

```
[ {
  "id" : "mod-service-interaction-4.4.0-SNAPSHOT",
  "action" : "enable"
} ]
HTTP 200
```

Okapi delivered POST /_/tenant to the LEGACY (:18080) — tenant-calls/01-legacy-install.* (module answered 201). psql: psql/a-post-install-legacy.txt

## 8. Step (b) UPGRADE TO PORT (same tenantParameters)

```bash
curl -s -w '\nHTTP %{http_code}\n' --max-time 300 -X POST \
  'http://localhost:9130/_/proxy/tenants/r20r/install?tenantParameters=loadReference%3Dtrue%2CloadSample%3Dtrue' \
  -H 'Content-Type: application/json' \
  -d '[{"id":"mod-service-interaction-5.0.0-SNAPSHOT","action":"enable"}]'
```

Response:

```
[ {
  "id" : "mod-service-interaction-5.0.0-SNAPSHOT",
  "from" : "mod-service-interaction-4.4.0-SNAPSHOT",
  "action" : "enable"
} ]
HTTP 200
```

Okapi delivered POST /_/tenant to the PORT (:18081) — tenant-calls/02-port-upgrade.* (module answered 204). psql: psql/b-post-upgrade-port.txt

## 9. Step (c) TIMERS

```bash
curl -s -w '\nHTTP %{http_code}\n' http://localhost:9130/_/proxy/tenants/r20r/timers
```

Response:

```
[ {
  "id" : "mod-service-interaction_0",
  "routingEntry" : {
    "methods" : [ "POST" ],
    "pathPattern" : "/servint/numberGenerators/resetYearSequences",
    "unit" : "hour",
    "delay" : "24",
    "permissionsRequired" : [ ]
  },
  "modified" : false
} ]
HTTP 200
```

Response body also saved verbatim as timers.json.

## 10. Step (d) SMOKE THROUGH OKAPI

```bash
curl -s -w '\nHTTP %{http_code}\n' --max-time 60 http://localhost:9130/servint/numberGenerators \
  -H 'X-Okapi-Tenant: r20r'
```

Response:

```
[{"code":"serialsManagement_patternNumber","name":"Serials management: Pattern number","id":"7889044c-5f6c-4af7-b241-c5a2a0f0f6db","sequences":[{"code":"patternNumber","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa10001c","label":"None","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"none"},"enabled":true,"format":"000000000","id":"443b148a-6666-4b3d-bbe0-b7f38c70949f","name":"Pattern number","nextValue":1,"outputTemplate":"pattern-${generated_number}","resetOnYearChange":false}]},{"code":"organizations_vendorCode","name":"Organizations: Vendor code","id":"91fe1c7d-d126-488f-8ea9-f7f5e5b8f834","sequences":[{"code":"vendor","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa10001c","label":"None","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"none"},"enabled":true,"format":"000","id":"d79eeffa-98f7-493a-86a7-4003d7617c0f","name":"Vendor","nextValue":1,"outputTemplate":"K${generated_number}","resetOnYearChange":false}]},{"code":"users_patronBarcode","name":"Users: Patron barcode","id":"a2793938-6cf5-44a4-8248-ed178417bb6c","sequences":[{"code":"patron","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa15001d","label":"31-RTL-mod10-I (EAN)","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"ean13"},"enabled":true,"format":"000000000","id":"2a2eee74-d8f7-4a60-99d1-1c0042f14fe3","name":"Patron","nextValue":1,"outputTemplate":"P${generated_number}-${checksum}","resetOnYearChange":false},{"code":"staff","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa15001d","label":"31-RTL-mod10-I (EAN)","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"ean13"},"enabled":true,"format":"000000000","id":"1ef2559f-ec6a-403e-8e32-ebbaf31d18a2","name":"Staff","nextValue":1,"outputTemplate":"S${generated_number}-${checksum}","resetOnYearChange":false}]},{"code":"inventory_itemBarcode","name":"Inventory: Item barcode","id":"a8bb0fd0-e7f6-4eb9-ba7d-3ec76e03b89f","sequences":[{"code":"itemBarcode","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa10001c","label":"None","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"none"},"enabled":true,"format":"0000000000","id":"a70fb06a-96d5-4d9d-8f81-8dd4e4d127e5","name":"Item barcode","nextValue":1,"outputTemplate":"${generated_number}","resetOnYearChange":false}]},{"code":"inventory_callNumber","name":"Inventory: Call number","id":"b5845460-cb4f-4c08-a651-9723ef58ddc9","sequences":[{"code":"callNumber","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa10001c","label":"None","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"none"},"enabled":true,"format":"00000","id":"a2c3cada-01d6-43f5-b1a5-8cab2f308011","name":"Call number","nextValue":1,"outputTemplate":"B 2023 / ${generated_number}","resetOnYearChange":false}]},{"code":"openAccess","name":"Open access: Publication request number","id":"d6027b45-a4df-47d4-9f8e-9ec697a8d3a4","sequences":[{"code":"requestSequence","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa10001c","label":"None","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"none"},"enabled":true,"format":"000000000","id":"c8972ddf-4aaf-46bd-b4ed-706f7b62758b","name":"Request sequence","nextValue":1,"outputTemplate":"oa-${generated_number}","resetOnYearChange":false}]},{"code":"inventory_accessionNumber","name":"Inventory: Accession number","id":"ddbaa24d-47a6-4b08-afbd-8f8cd567ba53","sequences":[{"code":"accessionNumber","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa10001c","label":"None","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"none"},"enabled":true,"format":"00000","id":"63efb810-21db-4c1c-aac9-64236bcc4b38","name":"Accession number","nextValue":1,"outputTemplate":"31A-2023-${generated_number}","resetOnYearChange":false}]},{"code":"patronRequest","name":"ILL: Patron request number","id":"f63d3edb-146c-4db2-8c28-0064be401ffc","sequences":[{"code":"requestSequence","checkDigitAlgo":{"id":"ff8081819f839030019f84abfa15001d","label":"31-RTL-mod10-I (EAN)","owner":{"desc":"NumberGeneratorSequence.CheckDigitAlgo","id":"ff8081819f839030019f84abfa0c001b","internal":true},"value":"ean13"},"enabled":true,"format":"000000000","id":"94da9ef8-449d-43e9-a9d1-150adf5b07ac","name":"Request sequence","nextValue":1,"outputTemplate":"ill-${generated_number}-${checksum}","resetOnYearChange":false}]}]
HTTP 200
```

## 11. Step (e) DISABLE (no tenantParameters)

```bash
curl -s -w '\nHTTP %{http_code}\n' --max-time 300 -X POST http://localhost:9130/_/proxy/tenants/r20r/install \
  -H 'Content-Type: application/json' \
  -d '[{"id":"mod-service-interaction-5.0.0-SNAPSHOT","action":"disable"}]'
```

Response:

```
[ {
  "id" : "mod-service-interaction-5.0.0-SNAPSHOT",
  "action" : "disable"
} ]
HTTP 200
```

Okapi delivered POST /_/tenant to the PORT — tenant-calls/03-port-disable.* (module answered 204). psql: psql/e-post-disable.txt

## 12. Step (f) RE-ENABLE (no tenantParameters)

```bash
curl -s -w '\nHTTP %{http_code}\n' --max-time 300 -X POST http://localhost:9130/_/proxy/tenants/r20r/install \
  -H 'Content-Type: application/json' \
  -d '[{"id":"mod-service-interaction-5.0.0-SNAPSHOT","action":"enable"}]'
```

Response:

```
[ {
  "id" : "mod-service-interaction-5.0.0-SNAPSHOT",
  "action" : "enable"
} ]
HTTP 200
```

Okapi delivered POST /_/tenant to the PORT — tenant-calls/04-port-reenable.* (module answered 204). psql: psql/f-post-reenable.txt

## 13. Step (g) PURGE

```bash
curl -s -w '\nHTTP %{http_code}\n' --max-time 300 -X POST \
  'http://localhost:9130/_/proxy/tenants/r20r/install?purge=true' \
  -H 'Content-Type: application/json' \
  -d '[{"id":"mod-service-interaction-5.0.0-SNAPSHOT","action":"disable"}]'
```

Response:

```
[ {
  "id" : "mod-service-interaction-5.0.0-SNAPSHOT",
  "action" : "disable"
} ]
HTTP 200
```

Okapi delivered POST /_/tenant to the PORT — tenant-calls/05-port-purge.* (module answered 204). psql: psql/g-post-purge.txt

