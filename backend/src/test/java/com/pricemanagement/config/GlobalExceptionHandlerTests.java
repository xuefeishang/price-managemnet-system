package com.pricemanagement.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pricemanagement.dto.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    void asyncTimeoutIsTreatedAsNormalLifecycle() {
        handler.handleAsyncRequestLifecycleException(
                new AsyncRequestTimeoutException(),
                new MockHttpServletRequest("GET", "/api/notifications/events"));

        assertThat(appender.list)
                .extracting(ILoggingEvent::getLevel)
                .doesNotContain(Level.WARN, Level.ERROR);
        assertThat(appender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                    assertThat(event.getFormattedMessage()).contains("异步请求已结束或客户端已断开");
                });
    }

    @Test
    void asyncDisconnectedClientIsTreatedAsNormalLifecycle() {
        handler.handleAsyncRequestLifecycleException(
                new AsyncRequestNotUsableException("client disconnected"),
                new MockHttpServletRequest("GET", "/api/notifications/events"));

        assertThat(appender.list)
                .extracting(ILoggingEvent::getLevel)
                .doesNotContain(Level.WARN, Level.ERROR);
        assertThat(appender.list)
                .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    void genericExceptionStillReturnsServerError() {
        Result<Void> result = handler.handleGenericException(
                new IllegalStateException("boom"),
                new MockHttpServletRequest("GET", "/api/products"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(appender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("Unexpected error occurred");
                });
    }
}
