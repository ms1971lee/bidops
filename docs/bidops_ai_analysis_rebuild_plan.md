# BidOps AI 분석 품질 전면 재설계 지시서
_Claude Code 실행용 / 근본 원인 분석 + 전면 재설계안 + 구현 순서_

## 1. 문서 목적
현재 BidOps의 AI 분석 결과가 `원문 재진술` 수준에 머물고 있으며, 제안서 작성에 필요한 **실행 가능한 전략, 구현/수행 방안, 차별화 포인트, 리스크, 질의 포인트**를 생성하지 못하고 있다.

이 문서는 아래를 동시에 달성하기 위한 **전면 재설계 기준서**다.

1. 왜 현재 구조에서 실패하는지 뿌리 원인 규명
2. 다시는 “누구나 말할 수 있는 분석 결과”가 나오지 않도록 시스템 재설계
3. Claude Code가 바로 구현할 수 있도록 아키텍처, 스키마, 프롬프트, 품질게이트, UI, 마이그레이션, 테스트까지 명시

이 문서는 **부분 패치가 아니라 뿌리부터 다시 설계해도 되는 기준본**이다.

---

## 2. 현재 실패 양상 요약

### 2.1 대표적인 실패 결과
아래 같은 결과는 모두 실패다.

- “데이터 표준 관리 방안을 구체적으로 제시해야 한다”
- “데이터 구조 관리 방안을 제시하고 주기적인 점검을 통해 품질을 유지한다”
- “체계적인 데이터 관리 방안을 제시하여 차별화한다”

### 2.2 왜 실패인가
이런 문장은 아래 특징을 가진다.

1. **원문 재진술**이다  
   - 원문 내용을 표현만 바꿔 반복
2. **Atomic requirement 분해가 없다**  
   - 복수 요구를 하나로 뭉갬
3. **How가 없다**  
   - 누가 / 언제 / 어떤 절차로 / 어떤 도구로 / 어떤 산출물로 / 어떻게 관리하는지 없음
4. **차별화가 없다**  
   - “체계적”, “신뢰”, “구체적” 같은 일반론만 존재
5. **리스크·질의·증빙이 약하다**  
   - 제출·평가 실무에서 도움이 되지 않음
6. **화면은 구조화되어 보이지만 실질은 빈 껍데기**다  
   - 필드는 있지만 내용이 전부 generic

---

## 3. 근본 원인 진단

### 3.1 1차 원인: 모델 문제가 아니라 파이프라인 설계 실패
현재 문제는 단순히 모델이 약해서가 아니라, 아래 설계가 잘못되어 발생한다.

- 문단 단위 요약을 시킴
- 요구사항 분해 전 분석 생성
- 프롬프트가 “잘 써줘” 수준에 머묾
- 필드별 품질 기준이 없음
- generic 문장 필터가 없음
- merge/save/query 단계에서 누락 추적이 약함
- UI가 “검토 가능한 정보”보다 “분석 결과 표시”에 치중

### 3.2 2차 원인: Requirement = atomic requirement 라는 규칙이 깨짐
현재 시스템에서 가장 위험한 문제는, **Requirement가 atomic requirement가 아니라 문단 요약 레코드처럼 쓰이고 있다는 점**이다.

이 상태에서는:
- 누락이 생김
- 차별화 포인트가 약해짐
- 체크리스트 연결이 부정확해짐
- 사람 검토 포인트가 흐려짐
- 제안서 초안도 generic해짐

### 3.3 3차 원인: “분석 필드”가 실무형으로 강제되지 않음
`proposalPoint`, `implementationApproach`, `differentiationPoint`는 존재해도
실제로는 다음 같은 문장이 들어와도 저장되고 있다.

- 신뢰를 줄 수 있는 방안 마련
- 체계적인 관리
- 주기적인 점검 계획 수립
- 품질 유지
- 안정적 운영 보장

이런 문장은 **실패 결과**로 봐야 한다.

---

## 4. 목표 상태 정의

## 4.1 새 시스템의 핵심 목표
새 시스템은 아래를 충족해야 한다.

1. **문단이 아니라 atomic requirement 단위로 저장**
2. **원문 근거 → 요구사항 분해 → 구조화 분석 → 품질게이트 → 사람 검토** 흐름 강제
3. **사실 / 추론 / 검토 필요**를 엄격히 분리
4. **제안서에 바로 반영 가능한 수준의 분석** 생성
5. **generic 문장 자동 차단**
6. **누락/병합/저장/조회 손실을 original_no 기준으로 추적**
7. **요구사항 상세 화면에서 검토와 수정이 쉬운 구조** 제공

### 4.2 새 시스템에서 허용되지 않는 결과
아래 유형은 저장 전 실패 처리한다.

- 원문을 거의 그대로 바꿔 쓴 문장
- “체계적으로 관리한다” 같은 추상 문장만 있는 결과
- implementationApproach에 절차/주기/주체/산출물이 없는 경우
- differentiationPoint가 실제 차별점이 아닌 경우
- interpretationSummary가 비어 있는 경우
- 원문 없는 내용을 FACT로 저장하는 경우
- 하나의 항목이 여러 원문 번호를 뭉개서 저장되는 경우

---

## 5. 전면 재설계 원칙

### 5.1 Requirement는 반드시 atomic requirement
문단이 아니라 **실행/제출/충족/협의/점검/보고/산출물 단위**로 분해한다.

예:
원문:
- 사업 기간 동안 데이터 표준 관리 방안을 제시하고
- 변경이력을 포함하고
- DB표준-데이터 항목 일치 여부를 주기적으로 점검하고
- 진단도구로 표준준수율을 관리하고
- 추가 데이터는 경찰청 표준을 준수하고
- 상충 요소는 경찰청과 협의하고
- 협의 이력을 남기고
- 테이블 및 컬럼명을 한글화할 것

이 문장은 최소 8개 requirement로 쪼개야 한다.

### 5.2 분석은 “요약”이 아니라 “실무형 구조화”
각 requirement마다 반드시 아래를 생성한다.

- requirement_text
- fact_basis
- inference_note
- review_required_note
- intent_summary
- proposal_point
- implementation_approach
- expected_deliverables
- differentiation_point
- risk_note
- query_needed
- draft_snippet
- fact_level
- status

### 5.3 generic 금지
아래 표현은 **단독 문장으로 저장 불가** 처리한다.

- 신뢰를 준다
- 체계적으로 관리한다
- 품질을 유지한다
- 구체적으로 제시한다
- 안정적으로 운영한다
- 경쟁사 대비 차별화한다
- 효율적으로 수행한다

이 표현이 들어가더라도 뒤에 **구체 수단, 절차, 조직, 도구, 산출물, KPI**가 없으면 실패다.

### 5.4 AI는 초안 생성기이지 확정기가 아님
AI는 구조화 초안을 만들고, 규칙 엔진이 품질을 검사하며, 사람 검토 상태는 별도 축으로 유지한다.

---

## 6. 권장 아키텍처 v2

## 6.1 전체 흐름
```text
PDF 업로드
  → OCR / 레이아웃 추출
  → 문서 정규화(normalized blocks)
  → clause segmentation
  → atomic requirement extraction
  → requirement coverage audit
  → requirement enrichment (AI 분석)
  → quality gate
  → save
  → review UI
  → 수정/승인
```

## 6.2 단계별 책임 분리

### Stage A. 문서 구조화
목적:
- PDF를 사람이 아닌 기계가 읽기 쉬운 블록 구조로 변환

출력:
- page blocks
- block_id
- original_no
- clause path
- block type
- normalized text
- bbox

### Stage B. 조항/요구사항 분해
목적:
- 문단을 atomic requirement 단위로 자르기

출력:
- atomic_requirement_id
- parent_block_id
- original_no
- requirement_text
- action_type
- object_type
- mandatory signal
- deliverable signal

### Stage C. Coverage Audit
목적:
- 원문 기대 건수와 실제 분해 건수의 차이를 추적

출력:
- expected_count
- detected_original_no_count
- ai_extracted_count
- merged_count
- saved_requirement_count
- visible_requirement_count
- missing_count
- missing_original_nos

### Stage D. 분석 Enrichment
목적:
- atomic requirement별 실무형 분석 생성

출력:
- fact / inference / review needed
- proposal / implementation / deliverables / differentiation / risk / query / draft

### Stage E. Quality Gate
목적:
- generic 결과 차단

출력:
- PASS / FAIL
- fail reason codes
- retry prompt or reviewer fallback

### Stage F. Human Review
목적:
- 승인/보류/수정요청
- AI 결과 편집
- 근거 대조

---

## 7. 데이터 모델 전면 개편안

## 7.1 유지할 엔티티
- Project
- Document
- DocumentPage
- SourceExcerpt
- AnalysisJob
- Requirement
- RequirementSource
- RequirementReview
- ProjectActivityLog

## 7.2 새로 추가할 엔티티
### 7.2.1 RequirementExtractionAudit
용도:
- 23 → 18 같은 누락 문제 추적

필드 예시:
- id
- projectId
- documentId
- analysisJobId
- expectedCount
- detectedOriginalNoCount
- aiExtractedCount
- mergedCount
- savedRequirementCount
- visibleRequirementCount
- missingCount
- expectedOriginalNosJson
- detectedOriginalNosJson
- aiExtractedOriginalNosJson
- mergedOriginalNosJson
- savedOriginalNosJson
- visibleOriginalNosJson
- missingOriginalNosJson
- droppedAfterDetectionJson
- droppedAfterAiJson
- droppedAfterMergeJson
- droppedAfterSaveJson
- hiddenAfterQueryJson
- createdAt

### 7.2.2 RequirementAtomicMeta
용도:
- requirement를 atomic 기준으로 추적

필드 예시:
- id
- requirementId
- parentBlockId
- originalNo
- clauseId
- sectionPath
- sourceDocumentId
- sourcePageNo
- sequenceNo
- actionType
- objectType
- mergeGroupKey
- createdAt

### 7.2.3 RequirementAnalysisIssue
용도:
- 분석 실패 원인 기록

필드 예시:
- id
- requirementId
- issueType
  - GENERIC_PROPOSAL
  - EMPTY_INTERPRETATION
  - FACT_WITHOUT_EVIDENCE
  - WEAK_DIFFERENTIATION
  - MISSING_DELIVERABLES
  - MERGE_RISK
- severity
- detailJson
- createdAt

### 7.2.4 PromptTemplateVersion
용도:
- 어떤 프롬프트 버전으로 생성되었는지 추적

필드 예시:
- id
- promptType
  - EXTRACTION
  - ENRICHMENT
  - RETRY
  - DRAFT
- version
- modelName
- templateBody
- isActive
- createdAt

### 7.2.5 RequirementDraftAssist
용도:
- 요구사항 분석과 제안서 문안 초안을 분리 저장

필드 예시:
- id
- requirementId
- sectionMapping
- evaluationFocus
- requiredEvidence
- draftSnippet
- draftTableJson
- createdAt
- updatedAt

## 7.3 RequirementInsight 확장
기존 `RequirementInsight`에 아래 필드 추가 권장

- evaluationFocus
- requiredEvidence
- proposalSectionMapping
- draftSnippet
- confidenceReason
- genericScore
- qualityGatePassed
- qualityGateFailureReasonsJson

---

## 8. API 재설계안

## 8.1 기존 유지
- `GET /projects/{projectId}/requirements`
- `GET /projects/{projectId}/requirements/{requirementId}`
- `GET /projects/{projectId}/requirements/{requirementId}/sources`
- `GET /projects/{projectId}/requirements/{requirementId}/analysis`
- `PATCH /projects/{projectId}/requirements/{requirementId}/analysis`
- `POST /projects/{projectId}/requirements/{requirementId}/review-status`

## 8.2 신규 API
### 8.2.1 Coverage Audit 조회
`GET /projects/{projectId}/documents/{documentId}/coverage-audit`

응답 예시:
```json
{
  "expectedCount": 23,
  "detectedOriginalNoCount": 23,
  "aiExtractedCount": 23,
  "mergedCount": 18,
  "savedRequirementCount": 18,
  "visibleRequirementCount": 18,
  "missingCount": 5,
  "missingOriginalNos": ["MAR-001", "MAR-007", "MAR-011", "MAR-014", "MAR-019"]
}
```

### 8.2.2 분석 이슈 조회
`GET /projects/{projectId}/requirements/{requirementId}/analysis-issues`

### 8.2.3 재분석 요청
`POST /projects/{projectId}/requirements/{requirementId}/re-analyze`

Request:
```json
{
  "mode": "FULL",
  "promptVersion": "enrichment-v3",
  "forceRetry": true
}
```

### 8.2.4 Draft Assist 조회
`GET /projects/{projectId}/requirements/{requirementId}/draft-assist`

### 8.2.5 문서 단위 분석 품질 리포트
`GET /projects/{projectId}/documents/{documentId}/analysis-quality-report`

---

## 9. 프롬프트 설계 전면 개편안

## 9.1 프롬프트를 한 번에 끝내지 말 것
단일 거대 프롬프트로
- 분해
- 해석
- 제안 포인트
- 차별화
- 초안
을 한 번에 시키지 않는다.

반드시 3단계로 나눈다.

### 9.1.1 Prompt A: Atomic Extraction
목표:
- 문단을 atomic requirement로 분해

출력:
- original_no
- requirement_text
- action
- object
- condition
- deliverable
- mandatory_flag
- evidence_required_flag

### 9.1.2 Prompt B: Enrichment
목표:
- atomic requirement별 구조화 분석 생성

출력:
- fact_basis
- inference_note
- review_required_note
- intent_summary
- proposal_point
- implementation_approach
- expected_deliverables
- differentiation_point
- risk_note
- query_needed
- evaluation_focus
- required_evidence
- proposal_section_mapping
- draft_snippet

### 9.1.3 Prompt C: Draft Assist
목표:
- 실제 제안서에 반영 가능한 문안/표/프로세스 구조 제안

출력:
- draft paragraph
- draft table
- suggested figure/process
- evidence insertion point

## 9.2 Prompt B 품질 규칙
Claude Code는 Prompt B에 아래 규칙을 반드시 넣어야 한다.

```text
1. requirement_text를 반복하지 말 것
2. proposal_point는 "무엇을 제안서에 강조할지"를 적을 것
3. implementation_approach는 반드시 아래 5개 중 4개 이상 포함
   - 관리 대상
   - 수행 절차
   - 수행 주기
   - 책임 주체 또는 협의 주체
   - 산출물/대장/보고서
4. differentiation_point는 반드시 아래 중 1개 이상 포함
   - KPI
   - 진단도구 활용
   - 거버넌스/승인체계
   - 변경이력 관리
   - 자동/반자동 점검
   - 사전 적합성 검토
   - 영향도 분석
   - 표준화/재사용 구조
5. 아래 표현만 단독으로 쓰지 말 것
   - 체계적 관리
   - 신뢰 제공
   - 품질 유지
   - 안정적 운영
   - 경쟁사 대비 차별화
6. 원문에 없는 내용은 FACT로 쓰지 말고 INFERENCE로 분리
7. interpretationSummary 비어 있으면 실패
8. expected_deliverables는 문서명/대장/보고서/기준서 수준으로 구체화
```

## 9.3 Retry Prompt
품질게이트에서 실패하면 아래 기준으로 재시도한다.

- generic proposal → 구체화 요청
- empty interpretation → 발주처 의도 재추론 요청
- weak differentiation → 경쟁사 일반안과 비교하여 재생성
- missing deliverables → 문서/표/보고서/대장 중심으로 재생성

---

## 10. 품질게이트 설계

## 10.1 목적
AI 결과를 그냥 저장하지 않고, **실패한 분석을 저장 단계에서 차단**한다.

## 10.2 품질게이트 체크 항목
### Gate 1. Atomic completeness
- requirement가 단일 의무인지
- 복수 요구 혼합 여부

### Gate 2. Evidence integrity
- 원문 근거 존재 여부
- clause/page/source link 여부

### Gate 3. Interpretation presence
- interpretationSummary 존재 여부
- intent_summary 존재 여부

### Gate 4. Proposal specificity
아래가 있으면 FAIL 가능
- 25자 이하 generic 문장만 존재
- 원문 반복률 높음
- 구체 명사/절차/도구/조직 없음

### Gate 5. Implementation specificity
다음 중 4개 미만이면 FAIL
- 대상
- 절차
- 주기
- 주체
- 산출물
- 도구
- KPI

### Gate 6. Differentiation validity
다음 중 하나도 없으면 FAIL
- KPI
- 거버넌스
- 승인절차
- 진단도구
- 자동화
- 이력관리
- 영향도 분석
- 템플릿/샘플 산출물

### Gate 7. Draft usability
- draft_snippet이 제안서 본문 수준인지
- 일반론만 반복하는지

## 10.3 품질 점수
각 항목 0~2점으로 평가

- decomposition_score
- evidence_score
- interpretation_score
- proposal_score
- implementation_score
- differentiation_score
- draft_score

합계 기준:
- 11점 이상: PASS
- 8~10점: REVIEW_NEEDED
- 7점 이하: FAIL + 재생성

## 10.4 금지어 기반 soft fail
다음 표현이 단독 핵심문장인 경우 soft fail

- 체계적
- 신뢰
- 효율적
- 품질 유지
- 안정적
- 고도화
- 차별화
- 최적
- 우수

주의:
- 문장 전체 금지가 아니라 **구체 근거 없는 단독 사용 금지**

---

## 11. 모델 전략

## 11.1 기본 판단
지금 문제는 모델 교체보다 **분해/프롬프트/품질게이트** 문제다.

## 11.2 권장 모델 전략
- OCR/구조화: Azure Document Intelligence
- 주 추출/분석 엔진: GPT-5.4
- 규칙 검증: 코드 기반 Rule Engine
- 재시도/비교 검증: 필요 시 보조 모델 추가 가능

## 11.3 모델 교체 검토 조건
아래일 때만 상위/보조 모델 도입 검토
- atomic extraction 누락률이 계속 높음
- 표/부속서 분해 실패가 반복
- enrichment가 generic 결과를 반복
- long context에서 clause tracking이 불안정

즉, **모델 교체는 2차 수단**이다.  
1차 수단은 시스템 재설계다.

---

## 12. UI/UX 개편안

## 12.1 요구사항 목록
컬럼 추가/강화:
- Requirement ID
- original_no
- 분류
- 필수 여부
- 증빙 여부
- analysisStatus
- reviewStatus
- factLevel
- qualityGate
- genericRisk
- queryNeeded
- 근거 페이지
- missing/merged 경고

## 12.2 요구사항 상세 패널
현재보다 아래를 명확히 분리

### 탭 1. 원문
- requirement_text
- source blocks
- page/clause
- bbox link

### 탭 2. 구조화 분석
- fact_basis
- inference_note
- review_required_note
- intent_summary
- proposal_point
- implementation_approach
- expected_deliverables
- differentiation_point
- risk_note
- query_needed

### 탭 3. Draft Assist
- 제안서 문안 초안
- 표/프로세스 추천
- 증빙 삽입 위치
- 평가위원 관점 포인트

### 탭 4. 품질 진단
- qualityGatePassed
- failure reasons
- generic score
- source coverage score

### 탭 5. 이력
- 생성 이력
- 프롬프트 버전
- 모델 버전
- 수정 이력
- 승인 이력

## 12.3 재분석 UX
조건:
- quality gate fail
- reviewStatus = NEEDS_UPDATE
- missing/merge risk 존재

액션:
- “재분석”
- “분해 다시 하기”
- “차별화 다시 생성”
- “초안 다시 생성”

---

## 13. 요구사항 예시: 데이터 표준 관리 조항의 올바른 분해 기준

## 13.1 잘못된 결과
“데이터 표준 관리 방안을 수립하고 주기적인 점검 계획을 제안한다.”

문제:
- 8개 의무를 1개로 축소
- 변경이력/도구/준수율/협의/한글화 누락
- How 없음

## 13.2 올바른 분해 예시
- DAR-001-1: 사업 기간 동안 데이터 표준(DB표준) 관리 방안을 제시해야 함
- DAR-001-2: 데이터 표준 관리 방안에는 변경이력이 포함되어야 함
- DAR-001-3: DB표준과 데이터 항목의 일치 여부를 주기적으로 점검해야 함
- DAR-001-4: 품질관리 진단도구(경찰청 메타데이터관리시스템 등)를 이용하여 표준준수율을 관리해야 함
- DAR-001-5: 추가되는 데이터는 공통표준 및 경찰청 표준을 준수해야 함
- DAR-001-6: 상충 요소 발생 시 경찰청과 협의해야 함
- DAR-001-7: 협의 결과에 따라 대응 방안을 수립하고 이력을 남겨야 함
- DAR-001-8: 모든 DB 테이블 및 칼럼명을 한글화해야 함

이 기준을 시스템 레벨에서 강제해야 한다.

---

## 14. Claude Code 구현 순서

## 14.1 Phase 0 - 브랜치 및 기준문서 반영
1. 이 문서를 `docs/ai-analysis-rebuild-plan.md`로 추가
2. 기존 RFP 분석 관련 문서에서 이 문서를 참조하도록 링크 추가
3. PromptTemplateVersion 관리 구조 추가

## 14.2 Phase 1 - 데이터 모델/DDL 수정
1. `RequirementExtractionAudit` 테이블 추가
2. `RequirementAtomicMeta` 테이블 추가
3. `RequirementAnalysisIssue` 테이블 추가
4. `RequirementDraftAssist` 테이블 추가
5. `RequirementInsight` 확장 필드 추가
6. JPA/Entity/Repository/DDL 정합화

## 14.3 Phase 2 - AI 워커 파이프라인 분리
1. normalized block 생성기 정리
2. atomic extraction 단계 분리
3. enrichment 단계 분리
4. quality gate 단계 추가
5. retry prompt 단계 추가
6. audit 저장 단계 추가

## 14.4 Phase 3 - API 추가
1. coverage audit API
2. analysis issues API
3. re-analyze API
4. draft assist API
5. analysis quality report API

## 14.5 Phase 4 - 프론트 개편
1. 요구사항 목록에 quality/generic/merge 경고 컬럼 추가
2. 상세 탭 구조 개편
3. 재분석 버튼 추가
4. 품질진단 탭 추가
5. 이력 탭에 prompt/model version 노출

## 14.6 Phase 5 - 테스트/QA
1. atomic extraction snapshot test
2. coverage audit test
3. generic fail case test
4. differentiation validation test
5. draft usability test
6. save/query visibility chain test

---

## 15. Claude Code용 작업지시서

```text
BidOps AI 분석 품질 전면 재설계 작업.

목표:
현재 원문 재진술 수준의 AI 분석 결과를 근절하고,
atomic requirement 기반 구조화 분석 + quality gate + human review 체계로 재구축한다.

반드시 할 일:
1. Requirement를 atomic requirement 기준으로 재정의
2. 분석 파이프라인을 아래 6단계로 분리
   - normalized blocks
   - atomic extraction
   - coverage audit
   - enrichment
   - quality gate
   - save/review
3. 아래 신규 엔티티 추가
   - RequirementExtractionAudit
   - RequirementAtomicMeta
   - RequirementAnalysisIssue
   - RequirementDraftAssist
   - PromptTemplateVersion
4. RequirementInsight 확장
   - evaluationFocus
   - requiredEvidence
   - proposalSectionMapping
   - draftSnippet
   - qualityGatePassed
   - qualityGateFailureReasonsJson
5. quality gate 구현
   - generic 문장 차단
   - interpretation 빈값 차단
   - implementation specificity 검사
   - differentiation validity 검사
6. coverage audit 구현
   - expected_count
   - detected_original_no_count
   - ai_extracted_count
   - merged_count
   - saved_requirement_count
   - visible_requirement_count
   - missing_count
   - missing_original_nos
   - 단계별 original_no diff 저장
7. API 추가
   - coverage audit
   - analysis issues
   - re-analyze
   - draft assist
   - analysis quality report
8. 프론트 개편
   - 요구사항 목록에 quality/generic/merge 경고 표시
   - 상세에 구조화 분석 / draft assist / 품질 진단 / 이력 탭 추가
9. 테스트 추가
   - atomic extraction
   - generic fail
   - differentiation fail
   - coverage audit diff
   - save/query chain

금지:
- 문단 단위 요약 저장
- generic proposal 저장
- 하나의 requirement에 여러 original_no를 무의미하게 병합
- 원문 없는 내용을 FACT로 저장

완료 기준:
- 데이터 표준 관리 조항 같은 복합 문장을 8개 atomic requirement로 분해 가능
- proposalPoint / implementationApproach / differentiationPoint가 원문 재진술이 아닌 상태
- quality gate fail 결과는 저장되지 않거나 REVIEW_NEEDED로 강등
- coverage audit로 23→18 감소 원인을 단계별로 추적 가능
```

---

## 16. 수용 기준(Acceptance Criteria)

### 16.1 기능 기준
- 복합 조항을 atomic requirement로 안정적으로 분해한다.
- 각 requirement에 fact / inference / review needed가 분리된다.
- generic 결과는 quality gate에서 차단된다.
- coverage audit로 누락 단계가 확정된다.
- 요구사항 상세에서 원문/분석/초안/이력/품질진단을 모두 볼 수 있다.

### 16.2 품질 기준
- `interpretationSummary` 빈값 저장 금지
- `implementationApproach`에 절차/주기/주체/산출물 부족 시 FAIL
- `differentiationPoint` generic 시 FAIL
- `draftSnippet`가 제안서 문안 수준이 아니면 REVIEW_NEEDED

### 16.3 운영 기준
- 어떤 프롬프트/모델/버전으로 생성됐는지 추적 가능
- 사람이 수정한 결과가 AI 재실행보다 우선
- AI 분석과 사람 검토 상태가 섞이지 않음

---

## 17. 최종 결론
현재 문제는 “모델이 멍청해서”가 아니라 **요구사항 분해 없는 요약형 분석 구조** 때문이며,
이를 고치려면 단순 프롬프트 수정이 아니라 아래를 함께 바꿔야 한다.

- 파이프라인
- 데이터 모델
- 품질게이트
- API
- UI
- 이력/감사 구조
- 테스트 기준

즉, **이번 이슈는 프롬프트 패치가 아니라 분석 엔진의 재기획 과제**다.

이 문서를 기준으로 Claude Code가 구현을 시작하면 된다.
