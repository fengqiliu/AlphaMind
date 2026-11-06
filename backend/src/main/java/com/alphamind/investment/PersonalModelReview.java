package com.alphamind.investment;

import jakarta.validation.constraints.*;
import com.alphamind.portfolio.RiskBudget;
import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 人工证据快照；UNKNOWN 不等于通过。持仓文档不作为当前组合事实。 */
@Data
public class PersonalModelReview {
    @Pattern(regexp = "[A-Za-z0-9-]{1,36}") private String sourceReportId;
    public enum Decision { UNKNOWN, PASS, FAIL }
    public enum Valuation { UNKNOWN, REASONABLE, UNDERVALUED, OVERVALUED }
    @PastOrPresent private LocalDate reviewedAt;
    private LocalDate validUntil;
    @Size(max = 4000) private String evidenceSource;
    @Size(max = 4000) private String counterArgument;
    private Valuation valuation = Valuation.UNKNOWN;
    private Decision earnings = Decision.UNKNOWN;
    private Decision cashFlow = Decision.UNKNOWN;
    private Decision businessLogic = Decision.UNKNOWN;
    private Decision valueTrapClear = Decision.UNKNOWN;
    private Decision noLeverage = Decision.UNKNOWN;
    private Decision weeklyTiming = Decision.UNKNOWN;
    private Decision bottomDivergence = Decision.UNKNOWN;
    private Decision volatilityContraction = Decision.UNKNOWN;
    private Map<@Size(max = 60) String, Decision> specialChecks = new LinkedHashMap<>();
    @DecimalMin("0") @DecimalMax("100") private Double projectedPositionPct;
    @DecimalMin("0") @DecimalMax("100") private Double projectedThemePct;
    @DecimalMin("0") @DecimalMax("100") private Double cashAfterTradePct;
    @DecimalMin("0") @DecimalMax("100") private Double portfolioDrawdownPct;
    @DecimalMin("0.01") @DecimalMax("100") private Double goldPositionLimitPct;
    /** 由"我的持仓"推算 Gate 5 时冻结的组合快照；手动填写时为 null。只用于说明数值来源，不参与门判定。 */
    private RiskBudget portfolioBasis;
}
