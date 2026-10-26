package com.hiretrack.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.hiretrack.notification.mail.LoggingReminderMailer;
import com.hiretrack.notification.mail.ReminderMailer;
import com.hiretrack.notification.mail.SmtpReminderMailer;

@Configuration
public class NotificationConfig {

    /**
     * spring.mail.host 가 실제 값으로 설정돼 있으면 SMTP 로, 없거나 빈 값이면 로그로 "발송"한다.
     * (docker compose 가 미설정 변수를 빈 문자열로 넘기면 JavaMailSender 빈은 만들어지지만 쓸 수 없다)
     */
    @Bean
    public ReminderMailer reminderMailer(ObjectProvider<JavaMailSender> mailSender, NotificationProperties properties,
                                         Environment environment) {
        JavaMailSender sender = mailSender.getIfAvailable();
        boolean configured = StringUtils.hasText(environment.getProperty("spring.mail.host"));
        return sender != null && configured ? new SmtpReminderMailer(sender, properties.mailFrom()) : new LoggingReminderMailer();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "notification.scheduler-enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {

        @Bean
        ReminderScheduler reminderScheduler(DeadlineReminderService reminderService) {
            return new ReminderScheduler(reminderService);
        }
    }
}
