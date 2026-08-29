package com.alphamind.market.provider;

import java.util.List;
import java.util.Optional;

/**
 * A market-data source that can contribute one part of a complete snapshot.
 * <p>
 * Providers deliberately do not synthesize data: an empty result means that the
 * upstream source did not return a usable response. The chain decides whether
 * another provider or a previously cached snapshot should be used.
 */
public interface MarketDataProvider {

    String sourceName();

    default Optional<MarketQuote> fetchQuote(String stockCode, String stockNameHint) {
        return Optional.empty();
    }

    default Optional<List<DailyKline>> fetchDailyKlines(String stockCode, int limit) {
        return Optional.empty();
    }
}
