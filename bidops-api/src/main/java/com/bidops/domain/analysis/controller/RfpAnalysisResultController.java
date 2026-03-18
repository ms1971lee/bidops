package com.bidops.domain.analysis.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.domain.analysis.dto.RfpAnalysisResultRequest;
import com.bidops.domain.analysis.dto.RfpAnalysisResultSaveResponse;
import com.bidops.domain.analysis.dto.RfpAnalysisValidationResponse;
import com.bidops.domain.analysis.service.RfpAnalysisResultSaveService;
import com.bidops.domain.analysis.service.RfpAnalysisValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rfp-analysis/results")
@RequiredArgsConstructor
@Tag(name = "RFP Analysis Results", description = "RFP 분석 결과 검증·저장 API")
public class RfpAnalysisResultController {

    private final RfpAnalysisValidationService validationService;
    private final RfpAnalysisResultSaveService saveService;

    /**
     * POST /api/rfp-analysis/results/validate
     * Bean Validation + 비즈니스 규칙 검증. DB 저장 없음.
     */
    @PostMapping("/validate")
    @Operation(summary = "RFP 분석 결과 검증 (dry-run)", operationId = "validateRfpAnalysisResults")
    public ApiResponse<RfpAnalysisValidationResponse> validate(
            @RequestBody @Valid RfpAnalysisResultRequest request) {

        return ApiResponse.ok(validationService.validate(request));
    }

    /**
     * POST /api/rfp-analysis/results
     * 검증 후 Requirement + RequirementInsight 저장.
     * RequirementSource 연결은 미구현 (TODO).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "RFP 분석 결과 저장", operationId = "saveRfpAnalysisResults")
    public ApiResponse<RfpAnalysisResultSaveResponse> save(
            @RequestBody @Valid RfpAnalysisResultRequest request) {

        return ApiResponse.ok(saveService.save(request));
    }
}
