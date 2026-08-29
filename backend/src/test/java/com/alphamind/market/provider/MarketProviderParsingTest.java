package com.alphamind.market.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MarketProviderParsingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesSinaGbkCompatiblePayloadWithVolumeInShares() {
        String fields = "贵州茅台,1680.00,1670.00,1685.50,1690.00,1665.00,1680.00,1685.50,12345,987654321.00,";
        Optional<MarketQuote> result = SinaQuoteMarketDataProvider.parseQuote(
                "600519", "var hq_str_sh600519=\"" + fields + "\";");

        assertTrue(result.isPresent());
        MarketQuote quote = result.orElseThrow();
        assertEquals("贵州茅台", quote.stockName());
        assertEquals(1685.50, quote.current());
        assertEquals(12_345L, quote.volume());
        assertEquals(987654321.00, quote.amount());
    }

    @Test
    void rejectsEmptyOrSuspendedSinaPayload() {
        assertTrue(SinaQuoteMarketDataProvider.parseQuote("600519", "var hq_str_sh600519=\"\";").isEmpty());
        assertTrue(SinaQuoteMarketDataProvider.parseQuote("600519",
                "var hq_str_sh600519=\"贵州茅台,1,1,0,1,1,1,1,1,1\";").isEmpty());
    }

    @Test
    void parsesEastmoneyKlinesSortsAndLimitsBars() {
        String response = "{\"data\":{\"klines\":["
                + "\"2026-08-28,10,10.5,10.8,9.8,100,1000,1,0,0,1.2\","
                + "\"2026-08-27,9,9.5,9.8,8.9,90,900,1,0,0,1.1\","
                + "\"2026-08-26,8,8.5,8.8,7.9,80,800,1,0,0,1.0\"]}}";

        var result = EastmoneyKlineMarketDataProvider.parseKlines(response, 2, mapper);
        assertTrue(result.isPresent());
        assertEquals(2, result.orElseThrow().size());
        assertEquals(LocalDate.of(2026, 8, 27), result.orElseThrow().get(0).tradeDate());
        assertEquals(10.5, result.orElseThrow().get(1).close());
        assertEquals(10_000L, result.orElseThrow().get(1).volume());
    }

    @Test
    void malformedEastmoneyBarsAreSkippedWithoutFabricatingValues() {
        String response = "{\"data\":{\"klines\":[\"bad\",\"2026-08-28,10,10.5,10.8,9.8,100,1000,1,0,0,1.2\"]}}";
        var result = EastmoneyKlineMarketDataProvider.parseKlines(response, 60, mapper);
        assertTrue(result.isPresent());
        assertEquals(1, result.orElseThrow().size());
    }
}
