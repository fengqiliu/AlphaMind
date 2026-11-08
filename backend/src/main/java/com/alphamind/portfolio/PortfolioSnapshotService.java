package com.alphamind.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 组合净值的每日快照。只记录按当期行情算出的完整总资产；缺行情的日子留空，不补数。
 * 快照记录的是总资产而不是收益率：期间的追加资金或取出资金会直接改变曲线，回撤因此只是参考。
 */
@Slf4j
@Service
public class PortfolioSnapshotService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    /** 曲线上的一个点。drawdownPct 是相对此前最高快照的回撤百分比。 */
    public record Point(LocalDate date, double totalValue, double cashRatioPct, int holdingsCount, double drawdownPct) {}

    public enum Outcome { RECORDED, NO_PORTFOLIO, INCOMPLETE }

    /** 一次记录的结果；INCOMPLETE 时 problems 说明缺哪些行情。 */
    public record Result(Outcome outcome, LocalDate date, Double totalValue, List<String> problems) {}

    private final PortfolioService portfolio;
    private final PortfolioSnapshotRepository repository;

    public PortfolioSnapshotService(PortfolioService portfolio, PortfolioSnapshotRepository repository) {
        this.portfolio = portfolio;
        this.repository = repository;
    }

    /** 记录今天的快照；同一天重复调用只覆盖当天一行。 */
    public Result record() {
        LocalDate today = LocalDate.now(MARKET_ZONE);
        var profile = portfolio.get();
        if (profile.isEmpty()) return new Result(Outcome.NO_PORTFOLIO, today, null, List.of());
        var valuation = portfolio.valuation(profile.get());
        Double total = valuation.totalValue();
        if (total == null) return new Result(Outcome.INCOMPLETE, today, null, valuation.problems());
        double liquid = profile.get().cash() + profile.get().lowVolatility();
        repository.save(new PortfolioSnapshotEntity(today, total, ratio(liquid, total),
                profile.get().holdings().size(), OffsetDateTime.now(MARKET_ZONE)));
        return new Result(Outcome.RECORDED, today, total, List.of());
    }

    /** 最近 days 天的快照，按日期升序；回撤按全部历史快照的滚动最高值计算，不受窗口截断影响。 */
    public List<Point> history(int days) {
        return toPoints(repository.findAllByOrderBySnapshotDateAsc(), LocalDate.now(MARKET_ZONE).minusDays(days));
    }

    static List<Point> toPoints(List<PortfolioSnapshotEntity> ascending, LocalDate from) {
        List<Point> points = new ArrayList<>();
        double peak = 0;
        for (PortfolioSnapshotEntity snapshot : ascending) {
            peak = Math.max(peak, snapshot.getTotalValue());
            if (snapshot.getSnapshotDate().isBefore(from)) continue;
            double drawdown = peak <= 0 ? 0 : Math.round((peak - snapshot.getTotalValue()) / peak * 10000.0) / 100.0;
            points.add(new Point(snapshot.getSnapshotDate(), snapshot.getTotalValue(), snapshot.getCashRatio(),
                    snapshot.getHoldingsCount(), drawdown));
        }
        return List.copyOf(points);
    }

    private static double ratio(double part, double whole) {
        return Math.round(part / whole * 10000.0) / 100.0;
    }
}
