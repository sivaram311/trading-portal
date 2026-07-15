package com.delena.tradingportal.web;

/** Application-level HTTP error signals mapped to responses by {@link GlobalExceptionHandler}. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static class NotFoundException extends RuntimeException {
        private final String code;

        public NotFoundException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static class ConflictException extends RuntimeException {
        private final String code;

        public ConflictException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
