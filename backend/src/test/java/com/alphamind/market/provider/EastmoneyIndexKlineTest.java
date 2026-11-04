package com.alphamind.market.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EastmoneyIndexKlineTest {
    @Test
    void benchmarkUsesExplicitShanghaiSecIdNotStockMapping() {
        RestOperations rest = mock(RestOperations.class);
        when(rest.getForObject(anyString(), eq(String.class))).thenReturn("""
                {"data":{"klines":["2026-11-02,3900.0,3910.5,3920.0,3890.0,100,1000,0,0,0,0"]}}""");
        var provider = new EastmoneyKlineMarketDataProvider(rest, new ObjectMapper());

        var bars = provider.fetchBenchmarkKlines(30).orElseThrow();

        verify(rest).getForObject(contains("secid=" + EastmoneyKlineMarketDataProvider.CSI300_SECID + "&"), eq(String.class));
        assertEquals("1.000300", EastmoneyKlineMarketDataProvider.CSI300_SECID);
        assertEquals(3910.5, bars.get(0).close());
    }
}
