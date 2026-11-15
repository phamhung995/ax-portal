package com.furelise.skillmanagement.notification.dto;

public record NotificationsRequest(
        boolean emailNotify,
        boolean inappNotify
) {
}
