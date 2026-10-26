package com.hiretrack.notification.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SMTP 설정(spring.mail.host)이 없을 때 쓰는 개발용 메일러. 실제로 보내지 않고 로그로 남긴다. */
public class LoggingReminderMailer implements ReminderMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderMailer.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[mail-not-configured] to={} subject={}\n{}", to, subject, body);
    }
}
