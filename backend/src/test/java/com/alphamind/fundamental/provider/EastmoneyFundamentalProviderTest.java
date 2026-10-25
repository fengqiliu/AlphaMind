package com.alphamind.fundamental.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EastmoneyFundamentalProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesLatestValuationAndComputesPercentileOverPositiveSamples() {
        StringBuilder rows = new StringBuilder();
        LocalDate day = LocalDate.of(2026, 10, 23);
        // 300 个交易日：最新 PE 20，历史 PE 为 1..300 的倒序；PB 混入一个负值样本
        for (int i = 0; i < 300; i++) {
            double pe = i == 0 ? 20 : 300 - i + 1;
            double pb = i == 5 ? -1 : 1 + i / 100.0;
            if (i > 0) rows.append(',');
            rows.append("{\"TRADE_DATE\":\"").append(day.minusDays(i)).append(" 00:00:00\",\"CLOSE_PRICE\":41.77,")
                    .append("\"TOTAL_MARKET_CAP\":1.05e12,\"PE_TTM\":").append(pe).append(",\"PB_MRQ\":").append(pb)
                    .append(",\"PCF_OCF_TTM\":1.69,\"PS_TTM\":3.05,\"PEG_CAR\":null}");
        }
        String response = "{\"result\":{\"pages\":1,\"data\":[" + rows + "]},\"success\":true,\"code\":0}";

        ValuationMetrics metrics = EastmoneyFundamentalProvider.parseValuation(response, mapper).orElseThrow();

        assertEquals(LocalDate.of(2026, 10, 23), metrics.tradeDate());
        assertEquals(20.0, metrics.peTtm());
        assertEquals(1.0, metrics.pbMrq());
        assertNull(metrics.pegCar());
        assertEquals(300, metrics.peSampleDays());
        assertEquals(299, metrics.pbSampleDays(), "负 PB 不进入分位样本");
        // PE 样本 2..300 加最新 20：≤20 的有 2..20 共 19 个 + 当前 1 个 = 20 / 300
        assertEquals(6.7, metrics.pePercentile());
        assertEquals(LocalDate.of(2026, 10, 23).minusDays(299), metrics.historyFrom());
    }

    @Test
    void percentileIsNullForLossMakingOrShortHistory() {
        List<Double> enough = new ArrayList<>(Collections.nCopies(EastmoneyFundamentalProvider.MIN_PERCENTILE_SAMPLES, 10.0));
        assertNull(EastmoneyFundamentalProvider.percentile(-5.0, enough), "亏损时 PE 分位不适用");
        assertNull(EastmoneyFundamentalProvider.percentile(10.0, enough.subList(0, 10)), "样本不足不计算分位");
        assertEquals(100.0, EastmoneyFundamentalProvider.percentile(10.0, enough));
    }

    @Test
    void parsesFinancialsFromMainIndicatorPayload() {
        String response = """
                {"result":{"pages":51,"data":[{"SECUCODE":"002475.SZ","ORG_TYPE":"通用","REPORT_DATE":"2026-06-30 00:00:00",
                "REPORT_DATE_NAME":"2026中报","NOTICE_DATE":"2026-08-25 00:00:00","EPSJB":1.0774,"EPSKCJB":0.98,
                "MGJYXJJE":-0.334349418799,"TOTALOPERATEREVE":1.3e11,"PARENTNETPROFIT":6.5e9,"KCFJCXSYJLR":5961877867.17,
                "TOTALOPERATEREVETZ":20.1,"PARENTNETPROFITTZ":7.2,"KCFJCXSYJLRTZ":6.472875651221,"ROEJQ":8.76,
                "XSMLL":11.7785360877,"ZCFZL":67.4394845184}]},"success":true,"code":0}
                """;

        FinancialMetrics metrics = EastmoneyFundamentalProvider.parseFinancials(response, mapper).orElseThrow();

        assertEquals("2026中报", metrics.reportName());
        assertEquals(LocalDate.of(2026, 6, 30), metrics.reportDate());
        assertEquals(LocalDate.of(2026, 8, 25), metrics.noticeDate());
        assertEquals(-0.334349418799, metrics.ocfPerShare());
        assertEquals(6.472875651221, metrics.deductedNetProfitYoy());
        assertEquals(8.76, metrics.roe());
    }

    @Test
    void emptyOrFailedPayloadsReturnEmpty() {
        String etfEmpty = "{\"version\":null,\"result\":null,\"success\":false,\"message\":\"返回数据为空\",\"code\":9201}";
        assertTrue(EastmoneyFundamentalProvider.parseValuation(etfEmpty, mapper).isEmpty());
        assertTrue(EastmoneyFundamentalProvider.parseFinancials(etfEmpty, mapper).isEmpty());
        assertTrue(EastmoneyFundamentalProvider.parseValuation("not json", mapper).isEmpty());
        assertTrue(EastmoneyFundamentalProvider.parseFinancials(null, mapper).isEmpty());
    }

    @Test
    void mapsExchangeSuffixByCodePrefix() {
        assertEquals("600036.SH", EastmoneyFundamentalProvider.toSecuCode("600036"));
        assertEquals("002475.SZ", EastmoneyFundamentalProvider.toSecuCode("002475"));
        assertEquals("517400.SH", EastmoneyFundamentalProvider.toSecuCode("517400"));
    }
}
