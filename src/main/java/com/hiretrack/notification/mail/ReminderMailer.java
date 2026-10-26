package com.hiretrack.notification.mail;

public interface ReminderMailer {

    /** 발송에 실패하면 RuntimeException 을 던진다(호출 쪽에서 발송 기록을 롤백). */
    void send(String to, String subject, String body);
}
