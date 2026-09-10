package com.alphamind.market.provider;

import java.util.Collection;
import java.util.Map;

/**
 * 可选能力：一次请求获取多只股票的实时报价。
 *
 * <p>与 {@link MarketDataProvider} 分开定义，是为了让"逐只抓取"的基础契约保持不变——
 * 只有真正支持批量端点的来源才实现本接口。股票池打分、自选股列表这类场景需要几十只
 * 股票的报价，逐只请求既慢又容易触发上游限流。
 *
 * <p>与基础契约一致：不返回的股票代表上游没有给出可用数据，实现方绝不编造占位值。
 */
public interface BatchQuoteProvider {

    String sourceName();

    /**
     * @param stockCodes 6 位 A 股代码集合
     * @return 代码 → 报价；仅包含成功解析的条目，失败或无数据的代码直接缺席
     */
    Map<String, MarketQuote> fetchQuotes(Collection<String> stockCodes);
}
