package com.alphamind.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;

/** 当前组合：持仓、现金、低波动资产（货基/短债等）与组合历史高点。金额单位均为元。 */
public record PortfolioProfile(
        @NotNull @Size(max = 100) List<@Valid @NotNull Holding> holdings,
        @NotNull @DecimalMin("0") Double cash,
        @NotNull @DecimalMin("0") Double lowVolatility,
        @DecimalMin("0") Double peakValue,
        OffsetDateTime updatedAt) {}
