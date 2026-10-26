package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;

import java.util.List;
import java.util.Optional;

/** 回测用日 K 来源；按交易日升序返回，取不到时返回 empty。 */
public interface KlineSource {
    Optional<List<DailyKline>> load(String code);
}
