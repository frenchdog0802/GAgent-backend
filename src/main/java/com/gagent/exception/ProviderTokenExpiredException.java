package com.gagent.exception;

import lombok.Getter;

@Getter
public class ProviderTokenExpiredException extends RuntimeException {

    private final String provider;

    public ProviderTokenExpiredException(String provider, String message) {
        super(message);
        this.provider = provider;
    }
}
