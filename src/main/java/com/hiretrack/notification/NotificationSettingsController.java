package com.hiretrack.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hiretrack.notification.dto.NotificationSettingsRequest;
import com.hiretrack.notification.dto.NotificationSettingsResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users/me/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;

    public NotificationSettingsController(NotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public NotificationSettingsResponse get(@AuthenticationPrincipal Long userId) {
        return settingsService.get(userId);
    }

    @PutMapping
    public NotificationSettingsResponse update(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody NotificationSettingsRequest request) {
        return settingsService.update(userId, request);
    }
}
