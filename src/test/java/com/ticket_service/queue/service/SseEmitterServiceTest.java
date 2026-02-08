package com.ticket_service.queue.service;

import com.ticket_service.queue.service.dto.QueueEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterServiceTest {

    private SseEmitterService sseEmitterService;

    private static final Long CONCERT_ID = 1L;
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        sseEmitterService = new SseEmitterService();
    }

    @Nested
    @DisplayName("createEmitter 메서드")
    class CreateEmitterTest {

        @DisplayName("SSE 연결 생성 성공")
        @Test
        void createEmitter_success() {
            // when
            SseEmitter emitter = sseEmitterService.createEmitter(CONCERT_ID, USER_ID);

            // then
            assertThat(emitter).isNotNull();
        }

        @DisplayName("동일 사용자 재연결 시 기존 연결 종료 후 새 연결 생성")
        @Test
        void createEmitter_replaces_existing() {
            // given
            SseEmitter first = sseEmitterService.createEmitter(CONCERT_ID, USER_ID);

            // when
            SseEmitter second = sseEmitterService.createEmitter(CONCERT_ID, USER_ID);

            // then
            assertThat(second).isNotSameAs(first);
        }
    }

    @Nested
    @DisplayName("completeEmitter 메서드")
    class CompleteEmitterTest {

        @DisplayName("SSE 연결 종료 성공")
        @Test
        void completeEmitter_success() {
            // given
            sseEmitterService.createEmitter(CONCERT_ID, USER_ID);

            // when - 예외 없이 종료
            sseEmitterService.completeEmitter(CONCERT_ID, USER_ID);
        }

        @DisplayName("존재하지 않는 연결 종료 시 예외 없음")
        @Test
        void completeEmitter_nonexistent_no_error() {
            // when - 예외 없이 종료
            sseEmitterService.completeEmitter(CONCERT_ID, "nonexistent-user");
        }
    }

    @Nested
    @DisplayName("sendEvent 메서드")
    class SendEventTest {

        @DisplayName("존재하지 않는 연결에 이벤트 전송 시 예외 없음")
        @Test
        void sendEvent_nonexistent_emitter_no_error() {
            // when - 예외 없이 무시
            sseEmitterService.sendEvent(CONCERT_ID, "nonexistent-user", QueueEventType.ENTER, "data");
        }
    }
}
