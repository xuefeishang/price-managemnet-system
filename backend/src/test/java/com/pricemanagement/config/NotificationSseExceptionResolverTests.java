package com.pricemanagement.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationSseExceptionResolverTests {

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        mockMvc = MockMvcBuilders.standaloneSetup(new SseFailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    void disconnectedSseClientUsesLifecycleHandlerInsteadOfGeneric500Handler() throws Exception {
        mockMvc.perform(get("/test/notifications/events"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertThat(appender.list)
                .extracting(ILoggingEvent::getLevel)
                .doesNotContain(Level.WARN, Level.ERROR);
        assertThat(appender.list)
                .noneSatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("Unexpected error occurred"));
    }

    @RestController
    private static class SseFailureController {

        @GetMapping(value = "/test/notifications/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        void events() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("client disconnected");
        }
    }
}
