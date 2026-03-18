# RFP 분석 결과 표준 스키마 (초안)

- 버전: v0.1-draft
- 작성일: 2026-03-18
- 목적: AI 워커가 RFP를 분석한 결과를 백엔드에 전달할 때 사용하는 표준 JSON 스키마를 정의한다.
- 관련 문서: `RFP_ANALYSIS_RULEBOOK.md`, `RFP_ANALYSIS_PROMPT.md`, `DB_ERD.md`

---

## 1. 설계 원칙

1. AI 워커 출력과 백엔드 저장 구조 사이의 **계약(contract)** 역할을 한다.
2. 원문 근거 추적이 가능해야 한다 (페이지, 조항, 원문 인용).
3. 사실 추출 / AI 추론 / 검토 필요를 명확히 구분한다.
4. `status` 필드로 각 항목의 신뢰 수준과 후속 조치를 표현한다.
5. 이 스키마는 `Requirement` + `RequirementInsight` + `RequirementSource` 엔티티에 매핑된다.

---

## 2. status 값 정의

| 값 | 의미 | 후속 조치 |
|---|---|---|
| `확인완료` | 원문에 명시되어 있고 해석 여지가 거의 없음 | 바로 제안서 반영 가능 |
| `원문확인필요` | 원문이 존재하나 페이지/조항 매칭이 불확실하거나 부속문서 확인 필요 | 실무자가 원문 대조 후 확정 |
| `질의필요` | 모호하거나 충돌이 있어 발주처 질의가 필요 | 질의서 작성 대상 |
| `추정` | 원문을 토대로 AI가 합리적으로 도출했으나 명시적 근거 부족 | 실무자 판단 후 확정 또는 폐기 |
| `파싱한계` | OCR/레이아웃 파싱 실패, 표 구조 깨짐 등으로 정확한 추출 불가 | 원문 수동 확인 필수 |

허용 값은 위 5개뿐이다. 그 외 값은 파싱 오류로 처리한다.

### 2.1 status 결정 기준

```
원문 명확 + 페이지/조항 특정 가능       → 확인완료
원문 존재 + 위치/맥락 불확실             → 원문확인필요
모호/충돌/기준 불명확                    → 질의필요
원문 기반 합리적 도출 + 명시적 근거 부족 → 추정
OCR 실패/표 깨짐/텍스트 추출 불가        → 파싱한계
```

---

## 3. 필드 정의

| # | 필드명 | 타입 | 필수/선택 | 설명 | 매핑 엔티티 |
|---|---|---|---|---|---|
| 1 | `requirement_text` | string | **필수** | 요구사항 원문. 축약하지 않고 핵심 표현을 그대로 유지 | `Requirement.originalText` |
| 2 | `clause_id` | string | 선택 | 조항 번호 또는 표 제목 (예: "제3조 제2항", "별첨1-표3") | `SourceExcerpt.anchorLabel` |
| 3 | `page_no` | integer | 선택 | 원문 페이지 번호. null이면 status가 `확인완료`일 수 없음 | `DocumentPage.pageNo` |
| 4 | `section_path` | string | 선택 | 문서 내 위치 경로 (예: "제2장 > 제3절 > 3.2 기능요구사항") | `SourceExcerpt.anchorLabel` 보조 |
| 5 | `requirement_type` | string | **필수** | 요구사항 분류. 섹션 4 분류 체계 참조 | `Requirement.category` |
| 6 | `original_evidence` | string | **필수** | 원문에서 직접 확인 가능한 사실 정보. AI 해석이 아닌 확정 근거만 기록 | `RequirementInsight.factSummary` |
| 7 | `fact_basis` | string | 선택 | 사실 근거의 출처 설명 (어떤 문서, 어떤 조항에서 왔는지) | `RequirementSource` (linkType=PRIMARY) |
| 8 | `inference_note` | string | 선택 | AI가 원문을 토대로 도출한 추론/해석 내용. 확정적 표현 금지 | `RequirementInsight.interpretationSummary` |
| 9 | `review_required_note` | string | 선택 | 검토가 필요한 이유. 모호성, 충돌, 부속문서 미확인 등 | `RequirementInsight.riskNoteJson` 항목 |
| 10 | `status` | enum | **필수** | 항목의 신뢰 수준 및 후속 조치 구분. 섹션 2 참조 | `Requirement.analysisResultStatus` |

### 3.1 필수/선택 요약

- **필수 (4개)**: `requirement_text`, `requirement_type`, `original_evidence`, `status`
- **선택 (6개)**: `clause_id`, `page_no`, `section_path`, `fact_basis`, `inference_note`, `review_required_note`

---

## 4. requirement_type 분류 체계

`RFP_ANALYSIS_RULEBOOK.md` 섹션 5.1 기준. `RequirementCategory` enum과 정합.

| 값 | 한글명 |
|---|---|
| `BUSINESS_OVERVIEW` | 사업개요 |
| `BACKGROUND` | 추진배경 |
| `OBJECTIVE` | 추진목표 |
| `SCOPE` | 과업범위 |
| `FUNCTIONAL` | 기능요구사항 |
| `NON_FUNCTIONAL` | 비기능요구사항 |
| `PERFORMANCE` | 성능요구사항 |
| `SECURITY` | 보안요구사항 |
| `QUALITY` | 품질관리 |
| `TESTING` | 테스트/검증 |
| `DATA_INTEGRATION` | 데이터/연계 |
| `UI_UX` | UI/UX |
| `INFRASTRUCTURE` | 인프라/운영환경 |
| `PERSONNEL` | 인력요건 |
| `TRACK_RECORD` | 실적요건 |
| `SCHEDULE` | 일정/기간 |
| `DELIVERABLE` | 산출물 |
| `SUBMISSION` | 제출서류 |
| `PROPOSAL_GUIDE` | 제안서작성지침 |
| `EVALUATION` | 평가기준 |
| `PRESENTATION` | 발표평가 |
| `MAINTENANCE` | 유지보수 |
| `TRAINING` | 교육/전환 |
| `LEGAL` | 법률/규정준수 |
| `ETC` | 기타 |

---

## 5. JSON 예시

### 5.1 확인완료

```json
{
  "requirement_text": "시스템은 개인정보보호법 등 관련 법령을 준수하여 개인정보를 안전하게 처리하여야 한다.",
  "clause_id": "제4장 제12조",
  "page_no": 23,
  "section_path": "제4장 > 비기능 요구사항 > 12. 보안 요구사항",
  "requirement_type": "SECURITY",
  "original_evidence": "개인정보보호법 준수 의무 명시. 안전한 처리 요구.",
  "fact_basis": "본문 23페이지 제12조 원문 직접 인용",
  "inference_note": "저장 구간 암호화, 전송 구간 TLS, 접근권한 통제, 접속기록 관리, 마스킹 정책이 필요할 것으로 판단됨",
  "review_required_note": null,
  "status": "확인완료"
}
```

### 5.2 질의필요

```json
{
  "requirement_text": "유사 사업 수행 경험이 풍부한 인력을 우선 배치한다.",
  "clause_id": "별첨2-투입인력 조건",
  "page_no": 45,
  "section_path": "별첨2 > 투입인력 자격 요건",
  "requirement_type": "PERSONNEL",
  "original_evidence": "유사 사업 경험 인력 우선 배치 조건 명시",
  "fact_basis": "별첨2 45페이지 표 내 조건",
  "inference_note": "'풍부한'의 구체적 기준(연수, 건수)이 명시되지 않아 해석 필요",
  "review_required_note": "'풍부한 경험'의 정량 기준 부재. 질의 또는 유사 사업 관례 기준 적용 필요",
  "status": "질의필요"
}
```

### 5.3 파싱한계

```json
{
  "requirement_text": "[표 내용 파싱 불가 - 이미지 기반 표]",
  "clause_id": "별첨3-평가기준표",
  "page_no": 52,
  "section_path": "별첨3 > 기술평가 배점표",
  "requirement_type": "EVALUATION",
  "original_evidence": "평가기준표 존재 확인. 내용 추출 불가.",
  "fact_basis": null,
  "inference_note": null,
  "review_required_note": "OCR이 표 구조를 인식하지 못함. 원문 수동 확인 필수",
  "status": "파싱한계"
}
```

### 5.4 추정

```json
{
  "requirement_text": "운영 안정성을 고려한 시스템 아키텍처를 설계한다.",
  "clause_id": "제3장 제8조",
  "page_no": 18,
  "section_path": "제3장 > 기술 요구사항 > 8. 시스템 구조",
  "requirement_type": "INFRASTRUCTURE",
  "original_evidence": "운영 안정성 고려 요구 명시",
  "fact_basis": "본문 18페이지 제8조",
  "inference_note": "이중화, 장애 복구, 모니터링 체계를 요구하는 것으로 추정됨. 구체적 가용률 수치는 미명시",
  "review_required_note": null,
  "status": "추정"
}
```

### 5.5 원문확인필요

```json
{
  "requirement_text": "사업 수행 시 관련 인증을 보유한 업체를 우대한다.",
  "clause_id": null,
  "page_no": null,
  "section_path": null,
  "requirement_type": "TRACK_RECORD",
  "original_evidence": "관련 인증 보유 업체 우대 조건 존재",
  "fact_basis": null,
  "inference_note": "어떤 인증(ISO, ISMS 등)을 의미하는지 특정 불가",
  "review_required_note": "원문 위치 미특정. 본문 또는 별첨에서 해당 조건 재확인 필요",
  "status": "원문확인필요"
}
```

---

## 6. 엔티티 매핑 관계

```
JSON 결과 1건
  ├─ Requirement          ← requirement_text, requirement_type, status
  ├─ RequirementInsight   ← original_evidence, inference_note, review_required_note
  └─ RequirementSource    ← clause_id, page_no, section_path (→ SourceExcerpt 연결)
       └─ SourceExcerpt   ← 원문 블록 저장
```

---

## 7. 제약사항 및 유의점

1. `status`는 반드시 `확인완료`, `원문확인필요`, `질의필요`, `추정`, `파싱한계` 중 하나여야 한다.
2. 필수 필드(`requirement_text`, `requirement_type`, `original_evidence`, `status`)가 누락되면 파싱 오류로 처리한다.
3. `original_evidence`는 AI 해석이 아닌 **원문에서 직접 확인 가능한 사실**만 기록한다.
4. `inference_note`는 반드시 추론임을 전제로 작성한다. 확정적 표현을 사용하지 않는다.
5. `page_no`가 null인 경우 `status`는 `확인완료`가 될 수 없다.
6. 하나의 RFP 문장에 여러 요구사항이 포함된 경우 각각 별도 항목으로 분리한다.
7. 이 스키마는 AI 워커 → 백엔드 전달용이며, 프론트 응답 DTO와는 별도로 관리한다.

---

## 8. 다음 단계

- [ ] 백엔드 파싱/저장 서비스에서 이 스키마 기준 validation 구현
- [ ] AI 워커 프롬프트에 이 스키마 출력 형식 반영
- [ ] status 값과 기존 `FactLevel` enum 간 매핑 규칙 확정
- [ ] 배치 입력(한 번에 여러 요구사항) wrapper 스키마 정의
