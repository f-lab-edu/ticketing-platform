package com.ticket_service.common.redis;

public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String key) {
      super("Failed to acquire lock. key=" + key);
    }
}
