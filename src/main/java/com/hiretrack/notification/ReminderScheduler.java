package com.hiretrack.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 매시 정각(서울 기준) 실행. 사용자마다 알림 받을 시각(send_hour)이 달라서 하루 한 번이 아니라 매시 돌고,
 * 그 시각을 고른 사용자에게만 보낸다(기본 09시).
 * <p>
 * 서버가 여러 대면 각 서버에서 동시에 돌 수 있다. notification_logs 의 유니크 제약으로 중복 발송은 막히지만,
 * 다중 서버로 갈 때는 ShedLock 으로 한 서버만 실행하게 하는 편이 깔끔하다(README 참고).
 */
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final DeadlineReminderService reminderService;

    public ReminderScheduler(DeadlineReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void sendDeadlineReminders() {
        int sent = reminderService.sendDueReminders();
        log.info("deadline-reminders sent={}", sent);
    }
}
