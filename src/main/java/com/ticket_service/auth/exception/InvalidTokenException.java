package com.ticket_service.auth.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public static InvalidTokenException expired() {
        return new InvalidTokenException("토큰이 만료되었습니다");
    }

    public static InvalidTokenException invalid() {
        return new InvalidTokenException("유효하지 않은 토큰입니다");
    }

    public static InvalidTokenException mismatchedConcert(Long expected, Long actual) {
        return new InvalidTokenException(
                String.format("토큰의 콘서트 ID가 일치하지 않습니다. expected=%d, actual=%d", expected, actual)
        );
    }
}
