package com.pricemanagement.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRealtimeServiceTests {

    @Test
    void subscribeSendsConnectedOnlyToNewEmitter() {
        NotificationRealtimeService service = new NotificationRealtimeService();
        RecordingSseEmitter existingEmitter = new RecordingSseEmitter(false);
        emitterMap(service).put(1L, new CopyOnWriteArrayList<>(List.of(existingEmitter)));

        service.subscribe(1L);

        assertThat(existingEmitter.sendCount()).isZero();
        assertThat(emitterMap(service).get(1L)).hasSize(2);
    }

    @Test
    void publishRemovesOnlyFailedEmitter() {
        NotificationRealtimeService service = new NotificationRealtimeService();
        RecordingSseEmitter healthyEmitter = new RecordingSseEmitter(false);
        RecordingSseEmitter disconnectedEmitter = new RecordingSseEmitter(true);
        emitterMap(service).put(1L, new CopyOnWriteArrayList<>(List.of(healthyEmitter, disconnectedEmitter)));

        service.publishUnreadChanged(1L, 3L);

        assertThat(healthyEmitter.sendCount()).isEqualTo(1);
        assertThat(disconnectedEmitter.sendCount()).isEqualTo(1);
        assertThat(emitterMap(service).get(1L)).containsExactly(healthyEmitter);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterMap(NotificationRealtimeService service) {
        return (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(service, "emitters");
    }

    private static class RecordingSseEmitter extends SseEmitter {

        private final boolean fail;
        private int sendCount;

        private RecordingSseEmitter(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            if (fail) {
                throw new IOException("client disconnected");
            }
        }

        private int sendCount() {
            return sendCount;
        }
    }
}
