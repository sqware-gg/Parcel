package com.sqware.parcel;

final class ApiException extends Exception {
    ApiException(String message) {
        super(message);
    }

    ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
