package com.alphamind.market.service;

import com.alphamind.market.provider.MarketQuote;

import java.time.Instant;

/**
 * 一条带来源与抓取时间的实时报价。
 *
 * <p>{@link MarketQuote#quotedAt()} 是交易所/上游给出的行情时间，而 {@code retrievedAt}
 * 是本系统成功拿到它的时间——缓存新鲜度必须按后者计算，否则收盘后的行情会被永远判定为"过期"。
 */
public record QuoteSnapshot(MarketQuote quote, String sourceName, Instant retrievedAt) {

    public String stockCode() {
        return quote.stockCode();
    }

    /** 相对昨收的涨跌幅（%）。昨收缺失时返回 null，绝不用 0 冒充"平盘"。 */
    public Double changePercent() {
        if (quote.previousClose() <= 0) {
            return null;
        }
        return (quote.current() - quote.previousClose()) / quote.previousClose() * 100.0;
    }
}
