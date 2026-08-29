package com.alphamind.market.service;

/** Raised when a provider chain cannot assemble a complete real-data snapshot. */
public class MarketDataUnavailableException extends RuntimeException {
    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
