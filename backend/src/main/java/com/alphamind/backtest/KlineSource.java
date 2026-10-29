package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;

import java.util.List;
import java.util.Optional;

/** 回测用日 K 来源；按交易日升序返回，取不到时返回 empty。 */
public interface KlineSource {
    Optional<List<DailyKline>> load(String code);

    /** 取数过程中需要写入报告的说明（如刷新失败沿用旧缓存）。 */
    default List<String> notes() {
        return List.of();
    }
}
