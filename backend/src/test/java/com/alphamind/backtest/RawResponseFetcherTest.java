package com.alphamind.backtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RawResponseFetcherTest {
    @TempDir Path dir;

    @Test
    void parsesSavedEastmoneyResponseWithProductionParser() throws Exception {
        Files.writeString(dir.resolve("600036.json"), """
                {"rc":0,"data":{"code":"600036","klines":[
                "2021-08-24,42.77,42.95,43.60,41.76,528277,2748379136.00,4.32,0.73,0.31,0.26",
                "2021-08-25,43.33,42.18,43.46,41.96,407312,2100000000.00,3.50,-1.79,-0.77,0.20"]}}
                """);
        var fetcher = TechnicalBacktestMain.rawResponseFetcher(dir, new ObjectMapper());

        var bars = fetcher.apply("600036").orElseThrow();

        assertEquals(2, bars.size());
        assertEquals(LocalDate.of(2021, 8, 24), bars.get(0).tradeDate());
        assertEquals(42.95, bars.get(0).close());
        assertEquals(41.76, bars.get(0).low());
        assertTrue(fetcher.apply("000001").isEmpty(), "缺少文件时返回 empty");
    }
}
