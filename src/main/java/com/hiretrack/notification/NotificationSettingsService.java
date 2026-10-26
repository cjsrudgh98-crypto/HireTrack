package com.hiretrack.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.notification.dto.NotificationSettingsRequest;
import com.hiretrack.notification.dto.NotificationSettingsResponse;
import com.hiretrack.user.User;
import com.hiretrack.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class NotificationSettingsService {

    private final NotificationSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public NotificationSettingsService(NotificationSettingsRepository settingsRepository, UserRepository userRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    public NotificationSettingsResponse get(Long userId) {
        User user = findUser(userId);
        NotificationSettings settings = settingsRepository.findById(userId)
                .orElseGet(() -> NotificationSettings.defaults(userId));
        return NotificationSettingsResponse.of(settings, user.getEmail());
    }

    @Transactional
    public NotificationSettingsResponse update(Long userId, NotificationSettingsRequest request) {
        User user = userRepository.findForUpdateById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!NotificationSettings.ALLOWED_DAYS.containsAll(request.remindDays())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.emailEnabled() && request.remindDays().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        NotificationSettings settings = settingsRepository.findById(userId).orElse(null);
        if (settings == null) {
            settings = settingsRepository.save(new NotificationSettings(userId, request.emailEnabled(),
                    request.remindDays(), request.sendHour()));
        } else {
            settings.update(request.emailEnabled(), request.remindDays(), request.sendHour());
        }
        return NotificationSettingsResponse.of(settings, user.getEmail());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
