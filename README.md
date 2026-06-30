# Temporal Insurance Demo

A Unified Insurance Platform, powered by Temporal.

## Prerequisites

- [Mise](https://mise.jdx.dev/) installed and available on `PATH`
- [Temporal CLI](https://docs.temporal.io/cli) installed and available on `PATH`

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
mise run temporal:dev-server

# 2. Worker — executes the policy and demo workflows
mise run temporal:worker

# 3. HTTP API (Swagger UI at http://localhost:8080/swagger-ui.html)
mise run temporal:api

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

## Other tasks

List all available tasks:

```bash
mise tasks
```

Run the Java test suite:

```bash
mise run temporal:test
```
