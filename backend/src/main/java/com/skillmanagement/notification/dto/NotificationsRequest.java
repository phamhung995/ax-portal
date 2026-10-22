package com.skillmanagement.notification.dto;

public record NotificationsRequest(
        boolean emailNotify,
        boolean inappNotify
) {
}
