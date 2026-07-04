# notifications-ep

Nexus endpoint for the **notifications** domain. Other domains (e.g. claims) call it across the
Nexus boundary to notify recipients instead of reaching into notification internals.

Backing service: `NotificationService`, hosted on task queue `notifications-task-queue`.

## Operations

### `sendNotification`

Resolves the recipient's preferred channels and dispatches the notification on each channel in
parallel, returning the per-channel delivery outcome.

- **Request:** `NotificationRequest`
- **Response:** `NotificationResult`
