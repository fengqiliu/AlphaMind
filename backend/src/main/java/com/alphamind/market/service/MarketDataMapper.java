package com.alphamind.market.service;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.MarketQuote;
import com.alphamind.model.dto.MarketDataDTO;
import java.util.ArrayList;
import java.util.List;

/** 将带来源/降级状态的快照映射为展示数据，供行情 Agent 和个人模型复用。 */
public final class MarketDataMapper {
    private MarketDataMapper() {}
    public static MarketDataDTO toDTO(MarketDataResolution resolution) {
        MarketDataSnapshot snapshot = resolution.snapshot();
        MarketQuote quote = snapshot.quote();
        int pricePrecision = snapshot.stockCode().startsWith("5") || snapshot.stockCode().startsWith("1") ? 3 : 2;
        List<DailyKline> bars = snapshot.dailyKlines();
        List<double[]> klines = bars.stream()
                .map(bar -> new double[]{bar.open(), bar.close(), bar.low(), bar.high()})
                .toList();
        List<Long> volumes = bars.stream().map(DailyKline::volume).toList();
        double change = round(quote.current() - quote.previousClose(), pricePrecision);
        double changePercent = quote.previousClose() > 0
                ? round(change * 100 / quote.previousClose(), 2) : 0D;
        double turnoverRate = bars.isEmpty() ? 0D : bars.get(bars.size() - 1).turnoverRate();

        return MarketDataDTO.builder()
                .stockCode(snapshot.stockCode())
                .stockName(snapshot.stockName())
                .currentPrice(round(quote.current(), pricePrecision))
                .change(change)
                .changePercent(changePercent)
                .open(round(quote.open(), pricePrecision))
                .high(round(quote.high(), pricePrecision))
                .low(round(quote.low(), pricePrecision))
                .volume(quote.volume())
                .amount(quote.amount())
                .turnoverRate(round(turnoverRate, 2))
                // 新浪报价与东方财富K线接口不返回这些字段，保持 null，禁止把 0 当成估值结论。
                .pe(null)
                .pb(null)
                .marketCap(null)
                .updateTime(quote.quotedAt().toString())
                .dataSource(snapshot.quoteSource().toUpperCase() + "+" + snapshot.klineSource().toUpperCase())
                .dataStatus(resolution.status().name())
                .stale(resolution.status() == MarketDataResolution.Status.STALE_CACHE)
                .cacheAgeSeconds(resolution.cacheAgeSeconds())
                .dataWarning(resolution.warning())
                .klineDates(bars.stream().map(bar -> bar.tradeDate().toString()).toList())
                .klines(klines)
                .klineVolumes(volumes)
                .ma5(calculateMA(klines, 5))
                .ma10(calculateMA(klines, 10))
                .ma20(calculateMA(klines, 20))
                .ma60(calculateMA(klines, 60))
                .build();
    }

    private static List<Double> calculateMA(List<double[]> klines, int period) {
        List<Double> averages = new ArrayList<>();
        for (int index = 0; index < klines.size(); index++) {
            if (index < period - 1) {
                averages.add(null);
                continue;
            }
            double total = 0;
            for (int cursor = index - period + 1; cursor <= index; cursor++) {
                total += klines.get(cursor)[1];
            }
            averages.add(round(total / period, 2));
        }
        return averages;
    }

    private static double round(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }
}
