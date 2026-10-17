package com.alphamind.investment;

import com.alphamind.market.service.MarketDataMapper;
import com.alphamind.market.service.ResilientMarketDataService;
import com.alphamind.model.dto.*;
import com.alphamind.model.enums.SignalType;
import com.alphamind.service.AnalysisHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 个人模型独立用例：真实行情 + 明示人工证据 + 确定性决策门，不调用 LLM 或交易接口。 */
@Service
@RequiredArgsConstructor
public class PersonalModelAnalysisService {
    private final PersonalModelCatalog catalog;
    private final PersonalModelEngine engine;
    private final ResilientMarketDataService marketData;
    private final AnalysisHistoryService history;
    private final PersonalReviewReuseService reuseService;

    public AnalysisReportDTO analyze(String code, PersonalModelReview review) {
        var asset = catalog.asset(code);
        reuseService.validateSource(code, review);
        var market = MarketDataMapper.toDTO(marketData.fetch(code, asset.name()));
        var assessment = engine.evaluate(catalog.definition(), asset, market, review, LocalDate.now());
        SignalType signal = assessment.entryCandidate() ? SignalType.BUY : SignalType.HOLD;
        // 价格只触发复核：不继承通用策略的固定目标价、止损价和短线持有天数。
        var report = AnalysisReportDTO.builder().id(UUID.randomUUID().toString()).stockCode(code).stockName(asset.name())
                .marketData(market).personalModel(assessment).finalSignal(signal)
                .tradeSignal(TradeSignalDTO.builder().type(signal).entryPrice(market.getCurrentPrice()).rationale(assessment.action()).build())
                .createdAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)).build();
        try { history.save(report); }
        catch (RuntimeException failure) { throw new IllegalStateException("个人模型报告保存失败，请稍后重试", failure); }
        return report;
    }
}
