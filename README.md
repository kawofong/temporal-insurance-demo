# Temporal Insurance Demo

A Unified Insurance Platform, powered by Temporal.

## Prerequisites

- [Mise](https://mise.jdx.dev/) installed and available on `PATH`
- [Temporal CLI](https://docs.temporal.io/cli) installed and available on `PATH` for local dev server runs

## Setup

```bash
mise install
mise run portal:install
```

## Temporal Java application

The Spring Boot Temporal application follows a domain/worker split inspired by the Temporal Jumpstart Java sample:

- `src/main/java/com/example/insurance/domains/policy` contains the `policy` domain workflow contracts and implementations.
- `src/main/java/com/example/insurance/workers` contains worker-oriented configuration and shared task queue names.

The default configuration connects to a local Temporal dev server at `localhost:7233` in the `default` namespace. Override with `TEMPORAL_TARGET` and `TEMPORAL_NAMESPACE` when needed.

Run unit tests with Temporal's in-memory test environment:

```bash
mise run temporal:test
```

Run the Temporal worker against a local Temporal dev server:

```bash
mise run temporal:dev-server
mise run temporal:worker
```

## Usage

List available tasks:

```bash
mise tasks
```
