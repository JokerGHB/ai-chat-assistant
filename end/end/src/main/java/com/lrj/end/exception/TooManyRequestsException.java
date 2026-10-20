package com.lrj.end.exception;

/**
 * 请求频率/次数超限（如超出每日对话次数上限）→ HTTP 429。
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
