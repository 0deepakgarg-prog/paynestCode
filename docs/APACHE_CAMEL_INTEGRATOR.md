# PayNest Apache Camel Integrator

## Goal

The integrator is a single standalone project under `paynest-integrator/`. It stays separate from the PayNest wallet service, but all integration code, Camel flows, mappings, templates, and Java processors live inside the integrator project.

This avoids a scattered runtime model while still keeping the wallet jar clean.

## Project Layout

```text
paynest-integrator/
  pom.xml
  src/main/java/com/paynest/integrator/
    PayNestIntegratorApplication.java
    processor/
      *.java                         # Camel Processor beans
  src/main/resources/
    application.yml                  # integrator runtime config
    camel/
      routes/
        *.yaml                       # Camel routes
        *.xml
      templates/
        *.yaml                       # Camel route templates
        *.xml
      mappings/
        *.json                       # mapping definitions used by processors/routes
```

## Runtime Model

The PayNest wallet service and the integrator run as two independent JVMs:

```text
paynest-wallet-1.0.0.jar       # wallet APIs and core business service
paynest-integrator-1.0.0.jar   # Camel integration runtime and flows
```

Starting the wallet does not start the integrator. Starting the integrator does not embed the wallet.

The integrator should call the wallet through stable boundaries such as HTTP APIs, Kafka topics, files, or other explicit integration contracts.

The integrator currently exposes a Camel HTTP route:

```text
POST /bill/enquiry
```

The wallet bill enquiry APIs call this route synchronously.

Wallet-facing APIs:

```text
POST /api/v1/bill/subscriber/enquiry
POST /api/v1/bill/agent/enquiry
```

Request:

```json
{
  "biller_code": "ELECTRICITY",
  "partner_data": {
    "customer_ref": "123456789"
  }
}
```

The wallet validates the Bearer token, reads `tenant_id` from the token tenant claim, and sends this payload to Camel:

```json
{
  "biller_code": "ELECTRICITY",
  "tenant_id": "tenant-a",
  "partner_data": {
    "customer_ref": "123456789"
  }
}
```

## Routes And Templates

Routes are loaded from the integrator jar classpath:

```yaml
camel:
  main:
    routes-include-pattern: classpath:camel/routes/*.yaml,classpath:camel/routes/*.xml,classpath:camel/templates/*.yaml,classpath:camel/templates/*.xml
```

For Camel 4.10 YAML route templates, define template inputs under `parameters`:

```yaml
- routeTemplate:
    id: partner-http-post-template
    parameters:
      - name: routeId
      - name: inputUri
      - name: targetUrl
```

## Java Processors

Add complex Java logic directly inside the integrator project:

```java
@Component("normalizePaymentProcessor")
public class NormalizePaymentProcessor implements Processor {
    @Override
    public void process(Exchange exchange) {
        // custom mapping, validation, enrichment, signing, etc.
    }
}
```

Reference it from a route:

```yaml
- process:
    ref: normalizePaymentProcessor
```

Because processors are part of the integrator jar, changing processor code requires rebuilding and redeploying `paynest-integrator`.

## External Configuration

Flow definitions are inside the integrator project, but environment-specific values can still be externalized on Linux:

```text
/opt/paynest/integrator/config/application.yml
/opt/paynest/integrator/config/integrator.properties
```

The integrator imports these files if present:

```yaml
spring:
  config:
    import: optional:file:./config/application.yml,optional:file:./config/integrator.properties

camel:
  main:
    file-configurations: file:./config/*.properties
```

Routes can use placeholders such as:

```yaml
to:
  uri: "{{paynest.base-url}}/api/internal/payments"
```

## Build

```powershell
mvn -f paynest-integrator/pom.xml clean package
```

Artifact:

```text
paynest-integrator/target/paynest-integrator-1.0.0.jar
```

## Linux Deployment

Recommended layout:

```text
/opt/paynest/
  wallet/
    paynest-wallet-1.0.0.jar
  integrator/
    paynest-integrator-1.0.0.jar
    config/
      application.yml
      integrator.properties
```

Manual run:

```bash
cd /opt/paynest/integrator
java -Dloader.main=com.paynest.integrator.PayNestIntegratorApplication -jar paynest-integrator-1.0.0.jar
```

Use the provided systemd unit:

```text
scripts/systemd/paynest-integrator.service
```
