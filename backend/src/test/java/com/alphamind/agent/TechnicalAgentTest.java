package com.alphamind.agent;

import com.alphamind.model.dto.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TechnicalAgentTest {
    private final TechnicalAgent agent = new TechnicalAgent();
    @AfterEach void clear() { agent.clearContext(); }
    private TechnicalIndicatorsDTO analyze(List<double[]> bars) {
        agent.setContext("marketData", MarketDataDTO.builder().currentPrice(bars.isEmpty() ? 100.0 : bars.get(bars.size()-1)[1]).klines(bars).build());
        return agent.analyze(AnalysisReportDTO.builder().stockCode("TEST").build()).getTechnicalIndicators();
    }
    private List<double[]> flat(int n) {
        List<double[]> bars = new ArrayList<>();
        for (int i=0; i<n; i++) bars.add(new double[]{100,100,100,100});
        return bars;
    }
    @Test void flatPricesProduceNeutralRsiMacdAndScore() {
        var actual = analyze(flat(34));
        assertEquals(50, actual.getTechnicalScore());
        assertEquals(50, actual.getRsi().getRsi12());
        assertEquals(0, actual.getMacd().getHistogram());
        assertEquals(100, actual.getBollinger().getMiddle());
        assertEquals(34, actual.getSampleSize());
        assertEquals(actual.getTechnicalScore(), actual.getScoreContributions().values().stream().mapToInt(Integer::intValue).sum());
    }
    @Test void linearPricesHaveAnalyticEmaDifferenceAndNoWarmupZeroPollution() {
        List<double[]> bars = new ArrayList<>();
        for (int i=0;i<34;i++) bars.add(new double[]{100+i,100+i,99+i,101+i});
        var actual = analyze(bars);
        assertEquals(7.0, actual.getMacd().getDif());
        assertEquals(7.0, actual.getMacd().getDea());
        assertEquals(0.0, actual.getMacd().getHistogram());
        assertEquals(100.0, actual.getRsi().getRsi24());
    }
    @Test void shortSeriesNeverFabricatesSyntheticBars() {
        assertThrows(RuntimeException.class, () -> analyze(flat(33)));
        assertThrows(RuntimeException.class, () -> analyze(List.of()));
    }
    @Test void invalidBarStopsScoring() {
        var bars = flat(34); bars.set(7, new double[]{100,100,101,99});
        assertThrows(RuntimeException.class, () -> analyze(bars));
        bars.set(7, new double[]{100,Double.NaN,99,101});
        assertThrows(RuntimeException.class, () -> analyze(bars));
    }
}
