package com.meuquarto.shared.storage;

/**
 * The storage server cannot be reached or refused the call. Carries no detail from the server on
 * purpose: callers turn it into one of our own error codes.
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(Throwable cause) {
        super("object storage unavailable", cause);
    }
}
