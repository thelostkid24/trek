package com.sahyatri.common.mail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.config.AuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingMailTransportTests {

    private static final String LINK = "https://example.com/reset-password?token=secret-token";

    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingMailTransport.class);
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
    void devLogsTheWholeMessage() {
        transport(false).send("asha@example.com", "Reset your password", "Open " + LINK);
        assertThat(appender.list).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains(LINK, "asha@example.com"));
    }

    @Test
    void secureDeploymentLogsOnlyTheSubject() {
        transport(true).send("asha@example.com", "Reset your password", "Open " + LINK);
        assertThat(appender.list).singleElement().satisfies(e -> assertThat(e.getFormattedMessage())
                .contains("Reset your password").doesNotContain("secret-token").doesNotContain("asha@example.com"));
    }

    private static LoggingMailTransport transport(boolean secure) {
        return new LoggingMailTransport(
                new AppProperties("http://localhost:8081", "http://localhost:5173", null,
                        new AppProperties.Mail("log", "Test <no-reply@example.com>")),
                new AuthProperties("a-random-test-secret-that-is-long-enough-0123", Duration.ofMinutes(15),
                        Duration.ofDays(30), Duration.ofSeconds(30), secure, ""));
    }
}
