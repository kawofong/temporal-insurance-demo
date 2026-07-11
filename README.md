# Temporal Insurance Demo

A Unified Insurance Platform, powered by Temporal.

## Prerequisites

- [Mise](https://mise.jdx.dev/)
- [Temporal CLI](https://docs.temporal.io/cli)
  - With [Temporal Cloud extension](https://docs.temporal.io/cli/setup-cli#install-the-temporal-cloud-extension)

## Setup

Install the toolchain (Java, Gradle, Node, ...) and the portal dependencies:

```bash
mise install
mise run portal:install
```

## Run the demo

The demo runs as four long-running processes. Start each in its own terminal, in order:

```bash
# 1. Temporal dev server (UI at http://localhost:8233)
mise run temporal:dev

# 2. Worker — executes the policy and demo workflows
mise run temporal:worker

# 3. HTTP API (Swagger UI at http://localhost:8080/swagger-ui.html)
mise run api

# 4. Policyholder portal (http://localhost:5173)
mise run portal:dev
```

Once all four are running, seed the sample policies:

```bash
curl -X POST http://localhost:8080/api/v1/demo/setup
```

This sets up the demo environment. The call is idempotent, so it is safe to re-run.
You can also trigger it from the Swagger UI at http://localhost:8080/swagger-ui.html.

Then open the portal at http://localhost:5173 to walk through the demo.

## Running against Temporal Cloud

The demo can run against a Temporal Cloud namespace instead of the local dev server. The
worker and API connect to Cloud via an API key over HTTPS; the portal and API still run
locally.

Configure your credentials (kept out of git):

```bash
cp .env.cloud.example .env.cloud   # then edit .env.cloud with your endpoint, namespace, and API key
```

> **API key permissions:** provisioning creates Nexus endpoints, which a **Namespace-scoped
> service account API key cannot do** (insufficient permission). Use an account-level API key
> from a user or an account-scoped service account with a role that can manage Nexus endpoints.
> See [Generate an API key](https://docs.temporal.io/cloud/api-keys#generate-an-api-key).

Provision the Cloud namespace. Idempotent, so it is safe to re-run:

```bash
mise run temporal:cloud:setup
```

Start everything against Cloud from VS Code with the **Start Cloud** task
(Terminal → Run Task → Start Cloud), or from the CLI by setting `MISE_ENV=cloud`:

```bash
MISE_ENV=cloud mise run temporal:worker   # worker against Cloud
MISE_ENV=cloud mise run api               # API against Cloud
mise run portal:dev                       # portal (unchanged; proxies /api to the local API)
MISE_ENV=cloud mise run demo:setup        # seed demo data (skips Nexus creation on Cloud)
```

`MISE_ENV=cloud` loads `mise.cloud.toml`, which activates the Spring `cloud` profile and reads
`.env.cloud`. `.env.cloud` is gitignored, so real credentials are never committed.

## Other tasks

List all available tasks:

```bash
mise tasks
```

Run the Java test suite:

```bash
mise run test
```
