# payment-ep

Nexus endpoint for the **payment** domain. Other domains (e.g. claims) call it across the Nexus
boundary to disburse customer payments instead of owning payment logic themselves.

Backing service: `PaymentService`, hosted on task queue `payment-task-queue`.

## Operations

### `processPayment`

Disburses a payment to a policyholder, backed by a workflow that drives the (mocked, flaky)
payment gateway to success through Temporal's retries, and returns the settlement outcome.

- **Request:** `PaymentRequest`
- **Response:** `PaymentResult`
