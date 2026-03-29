package com.bidops.domain.analysis;

import com.bidops.domain.analysis.dto.RfpAnalysisResultItem;
import com.bidops.domain.analysis.enums.AnalysisResultStatus;
import com.bidops.domain.analysis.worker.AiAnalysisJobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * AI 분석 파이프라인 품질 검증.
 *
 * 3가지 RFP 시나리오의 AI 응답을 시뮬레이션하여
 * 파싱/카테고리 매핑/상태 변환/누락/중복 처리 품질을 검증.
 */
class AiAnalysisQualityTest {

    ObjectMapper mapper = new ObjectMapper();

    // parseAnalysisResponse를 직접 호출하기 위한 리플렉션
    private List<RfpAnalysisResultItem> parseResponse(String json) throws Exception {
        // AiAnalysisJobHandler 인스턴스 생성 (saveService/textExtractor/objectMapper만 필요)
        var constructor = AiAnalysisJobHandler.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object handler = constructor.newInstance(null, null, mapper);

        Method method = AiAnalysisJobHandler.class.getDeclaredMethod("parseAnalysisResponse", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<RfpAnalysisResultItem> result = (List<RfpAnalysisResultItem>) method.invoke(handler, json);
        return result;
    }

    private AnalysisResultStatus parseStatus(String value) throws Exception {
        var constructor = AiAnalysisJobHandler.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object handler = constructor.newInstance(null, null, mapper);

        Method method = AiAnalysisJobHandler.class.getDeclaredMethod("parseStatus", String.class);
        method.setAccessible(true);
        return (AnalysisResultStatus) method.invoke(handler, value);
    }

    // ── 케이스 1: 정상 공공 RFP (다양한 카테고리, page_no 포함) ──────────

    @Nested
    @DisplayName("케이스 1: 정상 공공 RFP 응답")
    class Case1NormalPublicRfp {

        static final String AI_RESPONSE = """
                {
                  "requirements": [
                    {
                      "requirement_text": "시스템은 동시 사용자 500명을 지원해야 한다.",
                      "requirement_type": "PERFORMANCE",
                      "original_evidence": "제3장 기능요구사항 3.2.1: 동시접속 500명 이상 처리",
                      "status": "확인완료",
                      "page_no": 15,
                      "clause_id": "3.2.1",
                      "section_path": "기능요구사항 > 성능",
                      "fact_basis": "원문에 명시적 수치 기재",
                      "inference_note": null,
                      "review_required_note": null
                    },
                    {
                      "requirement_text": "개인정보 암호화 저장 및 전송 시 SSL/TLS 적용",
                      "requirement_type": "SECURITY",
                      "original_evidence": "보안요구사항 4.1: 개인정보보호법 준수, 암호화 저장",
                      "status": "확인완료",
                      "page_no": 22,
                      "clause_id": "4.1",
                      "section_path": "보안요구사항",
                      "fact_basis": "법적 근거 명시",
                      "inference_note": null,
                      "review_required_note": null
                    },
                    {
                      "requirement_text": "유사 사업 실적 3건 이상 보유 필요",
                      "requirement_type": "TRACK_RECORD",
                      "original_evidence": "제안업체 자격요건: 최근 3년 내 유사 사업 실적 3건",
                      "status": "확인완료",
                      "page_no": 5,
                      "clause_id": "2.3",
                      "section_path": "제안요구사항 > 자격요건",
                      "fact_basis": "원문 명시",
                      "inference_note": null,
                      "review_required_note": null
                    },
                    {
                      "requirement_text": "데이터 백업 주기가 명확하지 않아 확인 필요",
                      "requirement_type": "NON_FUNCTIONAL",
                      "original_evidence": "운영요구사항: 정기 백업 수행",
                      "status": "질의필요",
                      "page_no": 30,
                      "clause_id": null,
                      "section_path": "운영요구사항",
                      "fact_basis": null,
                      "inference_note": "'정기'의 구체적 주기 미명시",
                      "review_required_note": "백업 주기(일/주/월) 확인 필요"
                    },
                    {
                      "requirement_text": "시스템 인수 후 1년간 무상 하자보수 제공",
                      "requirement_type": "MAINTENANCE",
                      "original_evidence": "계약조건: 하자보수 기간",
                      "status": "추정",
                      "page_no": null,
                      "clause_id": null,
                      "section_path": null,
                      "fact_basis": null,
                      "inference_note": "일반적 공공 하자보수 기간을 기준으로 추정",
                      "review_required_note": "원문에 기간 명시 여부 확인 필요"
                    }
                  ]
                }
                """;

        @Test
        @DisplayName("5건 모두 파싱 성공")
        void allItemsParsed() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items).hasSize(5);
        }

        @Test
        @DisplayName("카테고리 정확히 매핑")
        void categoriesCorrect() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items.get(0).getRequirementType()).isEqualTo("PERFORMANCE");
            assertThat(items.get(1).getRequirementType()).isEqualTo("SECURITY");
            assertThat(items.get(2).getRequirementType()).isEqualTo("TRACK_RECORD");
            assertThat(items.get(3).getRequirementType()).isEqualTo("NON_FUNCTIONAL");
            assertThat(items.get(4).getRequirementType()).isEqualTo("MAINTENANCE");
        }

        @Test
        @DisplayName("status 정확히 변환")
        void statusCorrect() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items.get(0).getStatus()).isEqualTo(AnalysisResultStatus.확인완료);
            assertThat(items.get(3).getStatus()).isEqualTo(AnalysisResultStatus.질의필요);
            assertThat(items.get(4).getStatus()).isEqualTo(AnalysisResultStatus.추정);
        }

        @Test
        @DisplayName("page_no가 있는 항목은 정수로 파싱")
        void pageNoCorrect() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items.get(0).getPageNo()).isEqualTo(15);
            assertThat(items.get(1).getPageNo()).isEqualTo(22);
            assertThat(items.get(4).getPageNo()).isNull();
        }

        @Test
        @DisplayName("원문 근거 포함")
        void evidencePresent() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            for (RfpAnalysisResultItem item : items) {
                assertThat(item.getOriginalEvidence()).isNotBlank();
            }
        }
    }

    // ── 케이스 2: 엣지 케이스 (인식 불가 카테고리, 빈 필드, 누락) ────────

    @Nested
    @DisplayName("케이스 2: 엣지 케이스 응답")
    class Case2EdgeCases {

        static final String AI_RESPONSE = """
                {
                  "requirements": [
                    {
                      "requirement_text": "클라우드 네이티브 아키텍처 적용",
                      "requirement_type": "CLOUD_NATIVE",
                      "original_evidence": "아키텍처 요구사항",
                      "status": "확인완료",
                      "page_no": 10
                    },
                    {
                      "requirement_text": "",
                      "requirement_type": "FUNCTIONAL",
                      "original_evidence": "",
                      "status": "파싱한계",
                      "page_no": null
                    },
                    {
                      "requirement_text": "API 게이트웨이 구축",
                      "requirement_type": "FUNCTIONAL",
                      "original_evidence": "시스템 구성도 참조",
                      "status": "원문확인필요",
                      "page_no": 0
                    },
                    {
                      "requirement_text": "API 게이트웨이 구축",
                      "requirement_type": "FUNCTIONAL",
                      "original_evidence": "시스템 구성도 참조 (중복)",
                      "status": "확인완료",
                      "page_no": 12
                    }
                  ]
                }
                """;

        @Test
        @DisplayName("인식 불가 카테고리는 원본 문자열 유지 (매핑은 SaveService에서)")
        void unknownCategoryPreserved() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items.get(0).getRequirementType()).isEqualTo("CLOUD_NATIVE");
        }

        @Test
        @DisplayName("빈 requirement_text도 파싱은 됨 (검증은 SaveService)")
        void emptyTextParsed() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items.get(1).getRequirementText()).isEmpty();
        }

        @Test
        @DisplayName("page_no=0은 null로 변환 (유효하지 않은 페이지 번호)")
        void zeroPageNoConverted() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items.get(2).getPageNo()).isNull();
        }

        @Test
        @DisplayName("중복 텍스트도 파싱 단계에서는 모두 반환 (중복 제거는 SaveService)")
        void duplicateTextParsed() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items).hasSize(4);
            assertThat(items.get(2).getRequirementText()).isEqualTo(items.get(3).getRequirementText());
        }

        @Test
        @DisplayName("파싱한계 status 정확히 변환")
        void parseLimit() throws Exception {
            List<RfpAnalysisResultItem> items = parseResponse(AI_RESPONSE);
            assertThat(items.get(1).getStatus()).isEqualTo(AnalysisResultStatus.파싱한계);
        }
    }

    // ── 케이스 3: 비표준 AI 응답 형식 ──────────────────────────────────

    @Nested
    @DisplayName("케이스 3: 비표준 응답 형식")
    class Case3NonStandardResponse {

        @Test
        @DisplayName("requirements 키 없이 배열만 반환해도 파싱")
        void arrayOnly() throws Exception {
            String json = """
                    [
                      {
                        "requirement_text": "테스트",
                        "requirement_type": "ETC",
                        "original_evidence": "원문",
                        "status": "추정"
                      }
                    ]
                    """;
            List<RfpAnalysisResultItem> items = parseResponse(json);
            assertThat(items).hasSize(1);
        }

        @Test
        @DisplayName("빈 requirements 배열 → 0건")
        void emptyArray() throws Exception {
            String json = """
                    { "requirements": [] }
                    """;
            List<RfpAnalysisResultItem> items = parseResponse(json);
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("비정상 status 값 → 추정으로 fallback")
        void unknownStatus() throws Exception {
            assertThat(parseStatus("알수없음")).isEqualTo(AnalysisResultStatus.추정);
            assertThat(parseStatus("CONFIRMED")).isEqualTo(AnalysisResultStatus.추정);
            assertThat(parseStatus("")).isEqualTo(AnalysisResultStatus.추정);
        }

        @Test
        @DisplayName("정상 status 값 파싱")
        void validStatuses() throws Exception {
            assertThat(parseStatus("확인완료")).isEqualTo(AnalysisResultStatus.확인완료);
            assertThat(parseStatus("원문확인필요")).isEqualTo(AnalysisResultStatus.원문확인필요);
            assertThat(parseStatus("질의필요")).isEqualTo(AnalysisResultStatus.질의필요);
            assertThat(parseStatus("추정")).isEqualTo(AnalysisResultStatus.추정);
            assertThat(parseStatus("파싱한계")).isEqualTo(AnalysisResultStatus.파싱한계);
        }

        @Test
        @DisplayName("requirements가 아닌 다른 wrapper 키 → 0건")
        void wrongWrapperKey() throws Exception {
            String json = """
                    { "items": [{ "requirement_text": "test" }] }
                    """;
            List<RfpAnalysisResultItem> items = parseResponse(json);
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("누락 필드는 null/기본값으로 처리")
        void missingFields() throws Exception {
            String json = """
                    {
                      "requirements": [
                        {
                          "requirement_text": "최소 필드만"
                        }
                      ]
                    }
                    """;
            List<RfpAnalysisResultItem> items = parseResponse(json);
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getRequirementText()).isEqualTo("최소 필드만");
            assertThat(items.get(0).getRequirementType()).isEqualTo("ETC");
            assertThat(items.get(0).getOriginalEvidence()).isEmpty();
            assertThat(items.get(0).getStatus()).isEqualTo(AnalysisResultStatus.추정);
            assertThat(items.get(0).getPageNo()).isNull();
            assertThat(items.get(0).getClauseId()).isNull();
        }
    }
}
