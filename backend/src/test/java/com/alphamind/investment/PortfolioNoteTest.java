package com.alphamind.investment;

import com.alphamind.portfolio.RiskBudget;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioNoteTest {
    private static final RiskBudget BASIS = new RiskBudget("600036", "银行", 2000, true, 48000.0, 83.33, 83.33, 16.67, 0.0,
            48000.0, List.of(), List.of(), OffsetDateTime.parse("2026-11-06T10:00:00+08:00"));

    private static PersonalModelReview review(Double position) {
        var r = new PersonalModelReview();
        r.setPortfolioBasis(BASIS);
        r.setProjectedPositionPct(position);
        r.setProjectedThemePct(83.33);
        r.setCashAfterTradePct(16.67);
        r.setPortfolioDrawdownPct(0.0);
        return r;
    }

    @Test
    void manualEntryKeepsTheOriginalNote() {
        assertTrue(PersonalModelEngine.portfolioNote(new PersonalModelReview(), "600036").startsWith("风险数值为人工输入"));
    }

    @Test
    void matchingBasisNamesTheSnapshot() {
        String note = PersonalModelEngine.portfolioNote(review(83.33), "600036");
        assertTrue(note.contains("2026-11-06T10:00 的持仓快照推算"), note);
        assertFalse(note.contains("手动修改"));
    }

    @Test
    void editedValuesOrForeignBasisAreFlagged() {
        assertTrue(PersonalModelEngine.portfolioNote(review(4.0), "600036").contains("已被手动修改，以表单为准"));
        assertTrue(PersonalModelEngine.portfolioNote(review(83.33), "601138").contains("按人工输入处理"));
    }
}
