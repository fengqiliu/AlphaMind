package com.alphamind.controller;

import com.alphamind.investment.*;
import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/investment-model")
@RequiredArgsConstructor
public class PersonalInvestmentModelController {
    private final PersonalModelCatalog catalog;
    private final PersonalModelAnalysisService service;
    private final PersonalReviewReuseService reuseService;

    public record Request(@NotBlank @Pattern(regexp = "\\d{6}") String stockCode, @Valid PersonalModelReview review) {}

    @GetMapping
    public ApiResponse<PersonalModelDefinition> definition() { return ApiResponse.success(catalog.definition()); }

    @GetMapping("/reviews/{stockCode}/latest")
    public ApiResponse<PersonalReviewReuseService.Draft> latestReview(@PathVariable String stockCode) {
        try {
            return reuseService.latest(stockCode).map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.error(404, "该标的还没有有来源的历史复核，请填写本次证据"));
        } catch (PersonalReviewReuseService.ModelVersionMismatch failure) {
            return ApiResponse.error(409, failure.getMessage());
        } catch (IllegalArgumentException failure) {
            return ApiResponse.error(400, failure.getMessage());
        }
    }

    @PostMapping("/analyze")
    public ApiResponse<AnalysisReportDTO> analyze(@Valid @RequestBody Request request) {
        try { return ApiResponse.success(service.analyze(request.stockCode(), request.review())); }
        catch (PersonalReviewReuseService.ModelVersionMismatch failure) { return ApiResponse.error(409, failure.getMessage()); }
        catch (IllegalArgumentException failure) { return ApiResponse.error(400, failure.getMessage()); }
        catch (RuntimeException failure) { return ApiResponse.error(failure.getMessage()); }
    }
}
