package com.sahyatri.auth.sms;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sahyatri.common.config.AuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingSmsSenderTests {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingSmsSender.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void devLogsTheCode() {
        new LoggingSmsSender(auth(false)).sendOtp("+919876543210", "482913");
        assertThat(appender.list).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains("482913"));
    }

    @Test
    void secureDeploymentNeverLogsTheCodeOrPhone() {
        new LoggingSmsSender(auth(true)).sendOtp("+919876543210", "482913");
        assertThat(appender.list).singleElement().satisfies(e -> assertThat(e.getFormattedMessage())
                .doesNotContain("482913").doesNotContain("9876543210"));
    }

    static AuthProperties auth(boolean secure) {
        return new AuthProperties("a-random-test-secret-that-is-long-enough-0123", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofSeconds(30), secure, "");
    }
}
