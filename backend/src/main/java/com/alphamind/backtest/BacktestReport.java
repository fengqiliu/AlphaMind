package com.alphamind.backtest;

import java.util.List;
import java.util.Locale;

/** 把回测结果渲染为中文 Markdown 报告。 */
public final class BacktestReport {

    private BacktestReport() {}

    public static String render(RunSummary s) {
        StringBuilder md = new StringBuilder();
        md.append("# 技术分回测报告\n\n数据截至 ").append(s.dataThrough() == null ? "—" : s.dataThrough())
                .append(" · 生成于 ").append(s.runDate()).append("\n\n");
        md.append("> 历史统计，不构成投资建议。本报告只检验技术规则 V1 的分数对未来收益的区分力，不涉及舆情分、综合分或规则一致度。\n\n");

        md.append("## 结论\n\n");
        md.append("**判定：").append(s.verdict().label()).append("**\n\n");
        md.append("判定规则（事先写定）：样本外、持有 ").append(TechnicalBacktestMain.VERDICT_HORIZON)
                .append(" 日时，同时满足 ①“≥70 档平均超额 − <40 档平均超额 > 0” ②“IC 均值 > 0 且重叠修正 t 值 > 2” 为有区分力；只满足一项为弱/不确定；都不满足为未见区分力。参与股票少于 5 只时判定为数据不足，不做判定。\n\n");
        List<BucketStats> verdictBuckets = s.outSample().get(TechnicalBacktestMain.VERDICT_HORIZON);
        IcStats verdictIc = s.icOut().get(TechnicalBacktestMain.VERDICT_HORIZON);
        if (verdictBuckets != null && verdictIc != null) {
            BucketStats high = verdictBuckets.get(0), low = verdictBuckets.get(verdictBuckets.size() - 1);
            md.append("- 样本外 h=10：").append(high.label()).append(" 档平均超额 ").append(pct(high.meanExcess()))
                    .append("（").append(high.count()).append(" 个样本），").append(low.label()).append(" 档 ")
                    .append(pct(low.meanExcess())).append("（").append(low.count()).append(" 个样本）\n");
            md.append("- 样本外 h=10：IC 均值 ").append(num(verdictIc.meanIc())).append("，重叠修正 t 值 ")
                    .append(num(verdictIc.tOverlapAdjusted())).append("\n\n");
        }

        md.append("## 方法\n\n");
        md.append("- 评分：与线上同一实现 `TechnicalScoring.score`（技术规则 V1：基础 50 分，MACD 柱 / RSI12 / KDJ J / 布林位置加减，限幅 10–95）。\n");
        md.append("- 时间对齐：每个交易日 t 只用截至 t 的最近 ").append(TechnicalBacktestMain.WINDOW)
                .append(" 根日 K（与线上默认行情长度一致），现价取 t 日收盘。\n");
        md.append("- 交易：t+1 开盘买入，第 t+h 根 K 线收盘卖出，h ∈ {5, 10, 20}（按交易日计，停牌日自然跳过）。t+1 一字涨停视为无法买入，共剔除 ")
                .append(s.limitUpSkipped()).append(" 个信号日。\n");
        md.append("- 成本：买入佣金 0.025% + 滑点 0.05%；卖出佣金 0.025% + 滑点 0.05% + 印花税 0.05%，往返约 0.2%。下表收益均为扣费后净收益。\n");
        md.append("- 超额收益：净收益减去同一信号日、同一持有期全部样本的等权平均净收益，剔除大盘整体涨跌。\n");
        md.append("- IC：每个交易日至少 5 只股票时计算分数与未来收益的 Spearman 秩相关；t 值用全部交易日 IC 的均值，标准误按有效独立样本数（交易日数 / h）放大，避免持有期重叠夸大显著性，且不依赖抽样起点。\n");
        md.append("- 样本切分：按信号日期前 70% 为样本内、后 30% 为样本外，切分日 ").append(s.split() == null ? "—" : s.split())
                .append("（含当日起为样本外）；持有 h 日时样本内再留出切分前最后 h 个信号日，避免持有期跨入样本外。同一信号日不足 5 只股票的样本不计入统计。结论以样本外为准。\n\n");

        md.append("## 数据\n\n");
        md.append("东方财富前复权日 K（技术分四个指标对价格的线性缩放/平移不变，前复权对信号影响很小；收益按复权价计算，包含分红）。参与 ")
                .append(s.coverage().size()).append(" 只，排除 ").append(s.excluded().size()).append(" 只。\n\n");
        if (!s.dataNotes().isEmpty()) {
            md.append("数据说明：\n\n");
            s.dataNotes().forEach(n -> md.append("- ").append(n).append('\n'));
            md.append('\n');
        }
        if (!s.excluded().isEmpty()) {
            md.append("排除：\n\n");
            s.excluded().forEach(e -> md.append("- ").append(e).append('\n'));
            md.append('\n');
        }
        md.append("<details><summary>参与股票的数据区间</summary>\n\n| 代码 | 名称 | 起 | 止 | 根数 |\n| --- | --- | --- | --- | --- |\n");
        s.coverage().forEach(c -> md.append("| ").append(c.code()).append(" | ").append(c.name()).append(" | ")
                .append(c.from()).append(" | ").append(c.to()).append(" | ").append(c.bars()).append(" |\n"));
        md.append("\n</details>\n\n");

        md.append("## 分档结果\n\n");
        for (Integer h : s.outSample().keySet()) {
            md.append("### 持有 ").append(h).append(" 日\n\n");
            md.append("样本外：\n\n").append(table(s.outSample().get(h)));
            md.append("样本内：\n\n").append(table(s.inSample().get(h)));
            md.append("样本外 10 分细分：\n\n").append(table(s.fineOutSample().get(h)));
        }

        md.append("## 排序能力（IC）\n\n| 持有期 | 区间 | 交易日数 | IC 均值 | IC>0 比例 | 重叠修正 t 值 |\n| --- | --- | --- | --- | --- | --- |\n");
        for (Integer h : s.icOut().keySet()) {
            md.append(icRow(h, "样本内", s.icIn().get(h))).append(icRow(h, "样本外", s.icOut().get(h)));
        }
        md.append('\n');

        md.append("## 局限\n\n");
        md.append("- 幸存者偏差：股票池是 `StockCatalog` 当前名单，历史上退市或被剔除的股票不在其中，结果可能偏乐观。\n");
        md.append("- 样本有限：约 70 个观察标的（含少量 ETF）、约 5 年单一市场阶段，不同市场环境下结论可能不同。\n");
        md.append("- 成本为假设值；未考虑冲击成本、一字跌停无法卖出和资金容量。\n");
        md.append("- 技术分只是线上综合分的一半，本结论不能推广到综合信号、BUY/HOLD/SELL 或规则一致度。\n");
        return md.toString();
    }

    private static String table(List<BucketStats> buckets) {
        StringBuilder t = new StringBuilder("| 分数段 | 样本数 | 平均净收益 | 中位数净收益 | 胜率 | 平均超额 |\n| --- | --- | --- | --- | --- | --- |\n");
        for (BucketStats b : buckets) {
            t.append("| ").append(b.label()).append(" | ").append(b.count()).append(" | ").append(pct(b.meanNet()))
                    .append(" | ").append(pct(b.medianNet())).append(" | ").append(pct(b.winRate()))
                    .append(" | ").append(pct(b.meanExcess())).append(" |\n");
        }
        return t.append('\n').toString();
    }

    private static String icRow(int h, String label, IcStats ic) {
        return "| " + h + " 日 | " + label + " | " + ic.dates() + " | " + num(ic.meanIc()) + " | "
                + pct(ic.positiveShare()) + " | " + num(ic.tOverlapAdjusted()) + " |\n";
    }

    private static String pct(Double value) {
        return value == null ? "—" : String.format(Locale.ROOT, "%.2f%%", value * 100);
    }

    private static String num(Double value) {
        return value == null ? "—" : String.format(Locale.ROOT, "%.3f", value);
    }
}
