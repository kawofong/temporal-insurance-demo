// The delivery channels the notifications domain can dispatch on.
// A recipient's preference selects which of these actually fire for a given notification.
package com.ziggy.insurance.domains.notifications.models;

public enum NotificationChannel {
    EMAIL,   // e-mail to the recipient
    APP,     // in-app / push notification
    SMS      // text message
}
