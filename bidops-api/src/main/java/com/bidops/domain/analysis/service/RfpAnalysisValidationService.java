package com.bidops.domain.analysis.service;

import com.bidops.domain.analysis.dto.RfpAnalysisResultItem;
import com.bidops.domain.analysis.dto.RfpAnalysisResultRequest;
import com.bidops.domain.analysis.dto.RfpAnalysisValidationResponse;
import com.bidops.domain.analysis.dto.RfpAnalysisValidationResponse.ItemWarning;
import com.bidops.domain.analysis.enums.AnalysisResultStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RFP 분석 결과의 비즈니스 규칙 검증.
 * Bean Validation(@Valid)이 처리하지 못하는 교차 필드 규칙을 검증한다.
 */
@Service
public class RfpAnalysisValidationService {

    public RfpAnalysisValidationResponse validate(RfpAnalysisResultRequest request) {
        List<RfpAnalysisResultItem> items = request.getResults();
        List<ItemWarning> warnings = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            RfpAnalysisResultItem item = items.get(i);
            validateItem(i, item, warnings);
        }

        int warningCount = warnings.size();
        return RfpAnalysisValidationResponse.builder()
                .analysisJobId(request.getAnalysisJobId())
                .documentId(request.getDocumentId())
                .totalCount(items.size())
                .validCount(items.size() - warningCount)
                .warningCount(warningCount)
                .results(items)
                .warnings(warnings)
                .build();
    }

    private void validateItem(int index, RfpAnalysisResultItem item, List<ItemWarning> warnings) {
        // 규칙: page_no가 null이면 status가 '확인완료'일 수 없다
        if (item.getPageNo() == null && item.getStatus() == AnalysisResultStatus.확인완료) {
            warnings.add(ItemWarning.builder()
                    .index(index)
                    .field("status")
                    .message("page_no가 없으면 status는 '확인완료'일 수 없습니다")
                    .build());
        }

        // 규칙: status가 '질의필요'인데 review_required_note가 비어있으면 경고
        if (item.getStatus() == AnalysisResultStatus.질의필요
                && (item.getReviewRequiredNote() == null || item.getReviewRequiredNote().isBlank())) {
            warnings.add(ItemWarning.builder()
                    .index(index)
                    .field("review_required_note")
                    .message("status가 '질의필요'이면 review_required_note를 작성해야 합니다")
                    .build());
        }

        // 규칙: status가 '추정'인데 inference_note가 비어있으면 경고
        if (item.getStatus() == AnalysisResultStatus.추정
                && (item.getInferenceNote() == null || item.getInferenceNote().isBlank())) {
            warnings.add(ItemWarning.builder()
                    .index(index)
                    .field("inference_note")
                    .message("status가 '추정'이면 inference_note를 작성해야 합니다")
                    .build());
        }

        // 규칙: status가 '파싱한계'인데 review_required_note가 비어있으면 경고
        if (item.getStatus() == AnalysisResultStatus.파싱한계
                && (item.getReviewRequiredNote() == null || item.getReviewRequiredNote().isBlank())) {
            warnings.add(ItemWarning.builder()
                    .index(index)
                    .field("review_required_note")
                    .message("status가 '파싱한계'이면 review_required_note를 작성해야 합니다")
                    .build());
        }
    }
}
