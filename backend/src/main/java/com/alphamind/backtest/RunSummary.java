package com.alphamind.backtest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 一次回测运行的全部统计结果；Map 的键为持有期 h。 */
public record RunSummary(LocalDate runDate,
                         List<SymbolCoverage> coverage,
                         List<String> excluded,
                         Map<Integer, List<BucketStats>> inSample,
                         Map<Integer, List<BucketStats>> outSample,
                         Map<Integer, List<BucketStats>> fineOutSample,
                         Map<Integer, IcStats> icIn,
                         Map<Integer, IcStats> icOut,
                         List<String> dataNotes,
                         LocalDate split,
                         int limitUpSkipped,
                         Verdict verdict) {

    /** 参与股票中最晚的 K 线日期；无参与股票时为 null。 */
    public LocalDate dataThrough() {
        return coverage.stream().map(SymbolCoverage::to).max(LocalDate::compareTo).orElse(null);
    }
}
