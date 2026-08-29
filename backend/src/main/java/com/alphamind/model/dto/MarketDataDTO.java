package com.alphamind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataDTO {
    private String stockCode;
    private String stockName;
    private Double currentPrice;
    private Double change;
    private Double changePercent;
    private Double open;
    private Double high;
    private Double low;
    private Long volume;
    private Double amount;
    private Double turnoverRate;
    private Double pe;
    private Double pb;
    private Long marketCap;
    private String updateTime;
    /** 实际行情来源，例如 SINA+EASTMONEY；不允许用笼统的“实时”掩盖来源。 */
    private String dataSource;
    /** LIVE、FRESH_CACHE 或 STALE_CACHE。 */
    private String dataStatus;
    /** 是否已超过新鲜缓存窗口。 */
    private Boolean stale;
    /** 返回时相对抓取时间的缓存年龄，实时抓取为 0。 */
    private Long cacheAgeSeconds;
    /** 降级、数据缺口或模拟数据提示。 */
    private String dataWarning;
    // LLM生成的行情摘要
    private String aiSummary;
    // K线数据
    private List<String> klineDates;
    private List<double[]> klines;       // [open, close, low, high]
    private List<Long> klineVolumes;
    private List<Double> ma5;
    private List<Double> ma10;
    private List<Double> ma20;
    private List<Double> ma60;
}
