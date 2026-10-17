package com.alphamind.investment;

import com.alphamind.model.dto.MarketDataDTO;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalTimingCalculatorTest {
    @Test void wilderRsiMatchesPublishedWorkedExample() {
        List<Double> closes = List.of(44.34,44.09,44.15,43.61,44.33,44.83,45.10,45.42,45.84,46.08,45.89,46.03,45.61,46.28,46.28);
        assertEquals(70.4641, PersonalTimingCalculator.rsi(closes,14), 0.0001);
    }
    @Test void flatRisingAndFallingSeriesHaveDefinedLimits() {
        List<Double> flat = new ArrayList<>(), up = new ArrayList<>(), down = new ArrayList<>();
        for (int i=0;i<20;i++) { flat.add(100.0); up.add(100.0+i); down.add(100.0-i); }
        assertEquals(50,PersonalTimingCalculator.rsi(flat,14));
        assertEquals(100,PersonalTimingCalculator.rsi(up,14));
        assertEquals(0,PersonalTimingCalculator.rsi(down,14));
        assertNull(PersonalTimingCalculator.rsi(flat.subList(0,14),14));
    }
    @Test void atrAccountsForPriceGapAndUses20PeriodWilderSmoothing() {
        List<double[]> bars = new ArrayList<>();
        for(int i=0;i<20;i++) bars.add(new double[]{100,100,99,101});
        bars.add(new double[]{105,105,104,106});
        var timing = PersonalTimingCalculator.calculate(MarketDataDTO.builder().currentPrice(105.0).klines(bars).build(),LocalDate.now());
        assertEquals(6,timing.trueRange()); assertEquals(2.2,timing.atr20());
        assertNull(timing.weeklyRsi14());
    }
    @Test void insufficientOrInvalidBarsRemainUnknown() {
        var result = PersonalTimingCalculator.calculate(MarketDataDTO.builder().klines(List.of(new double[]{1,1,2,1})).build(),LocalDate.now());
        assertNull(result.dailyRsi14()); assertNull(result.atr20());
    }
}
