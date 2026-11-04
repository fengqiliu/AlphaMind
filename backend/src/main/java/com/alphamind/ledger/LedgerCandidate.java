package com.alphamind.ledger;

import java.time.Instant;

/** 待评估报告的最小视图（原生查询投影），不加载报告的大 JSON 字段。 */
public interface LedgerCandidate {
    String getId();
    String getStockCode();
    /** 毫秒时间戳：原生查询里 TIMESTAMPTZ 的 Java 映射因驱动/版本而异，数字最稳妥。 */
    Long getCreatedAtMillis();
    String getSignalType();
    Double getConfidenceValue();
    String getStrategy();
    String getAnalysisMode();
    String getPersonalLevel();

    default Instant createdAt() {
        return Instant.ofEpochMilli(getCreatedAtMillis());
    }
}
