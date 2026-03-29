# BidOps 중간 분석 포맷 스키마 초안 (v0.1)

## 1. 목적
이 문서는 BidOps에서 PDF 원문을 바로 GPT에 투입하지 않고,
`PDF -> 구조화 텍스트/레이아웃 추출 -> md/json 중간 포맷 -> AI 분석`
흐름으로 전환하기 위한 중간 포맷 기준안을 정의한다.

핵심 목적:
- 원문 요구사항 누락 방지
- 병합/누락 설명 가능성 확보
- 사람 검토용 가독성 확보
- AI 입력 안정성 확보
- Requirement / SourceExcerpt / CoverageAudit 저장 기준 통일

---

## 2. 권장 출력물 묶음
중간 포맷은 "단일 파일 1개"보다 아래 4종 묶음으로 생성하는 것을 권장한다.

1. `normalized.md`
   - 사람이 읽기 좋은 문서 구조본
   - 섹션/조항/표/목록/페이지가 드러남
   - AI 1차 분석용 텍스트 입력

2. `block_index.json`
   - 블록 단위 정규화 결과
   - page_no, block_id, clause_id, excerpt_type, bbox, raw_text 포함
   - AI/백엔드/프론트 공용 원장

3. `requirement_catalog.json`
   - 원문에서 감지한 요구사항 번호/카테고리/기대 건수
   - 예: MAR 6, DAR 4 ... total 23
   - 누락 감사의 기준값

4. `coverage_audit.json`
   - 기대 수 / 추출 수 / 저장 수 / 병합 수 / 누락 수
   - 누락/병합 번호를 설명하는 감사 레이어

권장 원칙:
- `md`는 "읽기 좋은 입력"
- `json`은 "검증 가능한 입력"
- AI 분석은 가능하면 `normalized.md + requirement_catalog.json + block_index.json`을 함께 본다.

---

## 3. 파이프라인
1. PDF 업로드
2. native text extraction
3. 필요 시 OCR fallback
4. 페이지/블록 구조화
5. `normalized.md` 생성
6. `block_index.json` 생성
7. `requirement_catalog.json` 생성
8. AI 1차: 요구사항 분해
9. AI 2차: 심화 분석
10. `coverage_audit.json` 생성
11. Requirement / RequirementSource / RequirementInsight 저장

---

## 4. 파일별 스키마

## 4.1 normalized.md
사람과 AI가 함께 읽는 문서 본문.
좌표/상태 정보는 최소화하고, 구조 정보와 블록 ID를 유지한다.

### 예시 형식
```md
---
document_id: doc_001
project_id: prj_001
title: 2026 읽쓰북 구축 사업 제안요청서
document_type: RFP
version_no: 1
page_count: 12
source_file_name: rfp.pdf
expected_requirement_total: 23
expected_requirement_breakdown:
  MAR: 6
  DAR: 4
  MHR: 1
  SER: 4
  QUR: 2
  COR: 1
  PMR: 4
  PSR: 1
---

# 문서 개요

## [PAGE 4]
### [BLOCK blk_p4_001] [SECTION 4] [CLAUSE MAR-001] 유지보수 수행 요구사항
- 시스템의 원활한 운영을 위하여 유지보수 관리 방안 및 지원 체계를 제시하여야 한다.

### [BLOCK blk_p4_002] [CLAUSE MAR-002]
- 제안업체는 신속한 장애처리 및 예방을 위하여 제조사별 기술 지원 체계를 제시하여야 한다.

## [PAGE 5]
### [BLOCK blk_p5_tbl_001] [TABLE] 요구사항 총괄표
| 요구사항번호 | 구분 | 내용 |
| --- | --- | --- |
| MAR-001 | 유지보수 | 시스템 운영 지원 |
| MAR-002 | 유지보수 | 장애 예방/기술지원 |
```

### 필수 메타 필드
- document_id
- project_id
- title
- document_type
- version_no
- page_count
- source_file_name
- expected_requirement_total
- expected_requirement_breakdown

### 블록 표기 규칙
- 각 문단/표/목록은 반드시 block_id를 가진다
- clause_id가 있으면 같이 노출한다
- page 구분은 유지한다
- 표는 markdown table로 풀되 원본 표 의미가 깨지지 않게 한다

---

## 4.2 block_index.json
중간 포맷의 핵심 원장.
프론트 하이라이트, RequirementSource 연결, 감사 추적의 기준이다.

### 스키마
```json
{
  "schema_version": "0.1",
  "document": {
    "document_id": "doc_001",
    "project_id": "prj_001",
    "title": "2026 읽쓰북 구축 사업 제안요청서",
    "document_type": "RFP",
    "version_no": 1,
    "page_count": 12,
    "source_file_name": "rfp.pdf",
    "text_extraction_mode": "native|ocr|hybrid"
  },
  "pages": [
    {
      "page_no": 4,
      "text_source": "native",
      "ocr_confidence": 0.98,
      "blocks": [
        {
          "block_id": "blk_p4_001",
          "page_no": 4,
          "excerpt_type": "PARAGRAPH",
          "section_path": ["4. 유지보수 요구사항"],
          "clause_id": "MAR-001",
          "anchor_label": "MAR-001",
          "table_ref": null,
          "list_index": null,
          "raw_text": "시스템의 원활한 운영을 위하여 유지보수 관리 방안 및 지원 체계를 제시하여야 한다.",
          "normalized_text": "시스템의 원활한 운영을 위하여 유지보수 관리 방안 및 지원 체계를 제시하여야 한다.",
          "bbox_json": {"x": 11.2, "y": 18.4, "w": 76.1, "h": 5.2},
          "is_requirement_candidate": true,
          "requirement_signals": ["하여야 한다", "제시"],
          "source_rank": "PRIMARY"
        }
      ]
    }
  ]
}
```

### 주요 필드 설명
- `text_extraction_mode`: native / ocr / hybrid
- `excerpt_type`: PARAGRAPH / TABLE / LIST / HEADER / FOOTNOTE
- `section_path`: 목차/장/절 경로
- `clause_id`: 원문 요구사항 번호
- `anchor_label`: PDF/문단 표시용
- `bbox_json`: 좌표. 없으면 null
- `is_requirement_candidate`: 요구사항 후보 여부
- `requirement_signals`: "필수", "제출", "준수", "이상", "이내" 같은 신호
- `source_rank`: PRIMARY / SUPPORTING

---

## 4.3 requirement_catalog.json
원문 문서 기준의 "기대 요구사항 카탈로그".
AI가 뽑은 결과가 아니라, 문서에서 감지된 요구사항 기준표다.

### 스키마
```json
{
  "schema_version": "0.1",
  "document_id": "doc_001",
  "expected_requirement_total": 23,
  "expected_breakdown": {
    "MAR": 6,
    "DAR": 4,
    "MHR": 1,
    "SER": 4,
    "QUR": 2,
    "COR": 1,
    "PMR": 4,
    "PSR": 1
  },
  "catalog_items": [
    {
      "original_requirement_no": "MAR-001",
      "group_code": "MAR",
      "sequence_no": 1,
      "original_title": "시스템의 원활한 운영을 위하여 유지보수 관리 방안 및 지원 체계",
      "original_category": "유지보수",
      "page_no": 4,
      "clause_id": "MAR-001",
      "source_block_ids": ["blk_p4_001"],
      "status": "DETECTED"
    }
  ]
}
```

### status enum
- `DETECTED`
- `UNCERTAIN`
- `MISSING_FROM_TEXT`
- `DERIVED_FROM_TABLE`

---

## 4.4 ai_input_bundle.json (선택)
AI 워커가 실제로 넘겨받는 단일 묶음 파일.
운영 편의상 내부적으로는 이 형태를 만드는 것을 권장한다.

```json
{
  "document_meta": {
    "document_id": "doc_001",
    "project_id": "prj_001",
    "title": "2026 읽쓰북 구축 사업 제안요청서",
    "document_type": "RFP",
    "version_no": 1
  },
  "analysis_policy": {
    "goal": "문서 전체 요구사항을 최소 단위로 분해",
    "must_keep_original_requirement_no": true,
    "must_report_missing_items": true,
    "must_split_multi_requirements": true,
    "exclude_vendor_general_section": true
  },
  "expected_counts": {
    "total": 23,
    "breakdown": {
      "MAR": 6,
      "DAR": 4,
      "MHR": 1,
      "SER": 4,
      "QUR": 2,
      "COR": 1,
      "PMR": 4,
      "PSR": 1
    }
  },
  "normalized_markdown_path": "normalized.md",
  "block_index_path": "block_index.json",
  "catalog_path": "requirement_catalog.json"
}
```

---

## 4.5 coverage_audit.json
이 파일이 없으면 누락을 설명할 수 없다.
BidOps 핵심 감사 파일로 취급한다.

### 스키마
```json
{
  "schema_version": "0.1",
  "document_id": "doc_001",
  "job_id": "job_001",
  "expected_count": 23,
  "catalog_detected_count": 23,
  "ai_extracted_count": 19,
  "saved_requirement_count": 17,
  "merged_count": 2,
  "missing_count": 6,
  "missing_requirement_nos": ["MHR-001", "PSR-001", "PMR-003"],
  "merged_groups": [
    {
      "merged_requirement_code": "REQ-007",
      "original_requirement_nos": ["MAR-002", "MAR-003"],
      "reason": "AI merged similar maintenance response items"
    }
  ],
  "unmapped_extracted_items": [
    {
      "temp_id": "tmp_014",
      "title": "운영 안정성 확보",
      "reason": "original requirement no not assigned"
    }
  ],
  "notes": [
    "표 기반 요구사항 2건은 OCR 품질 저하로 불확실",
    "PSR-001은 교육 관련 문장 감지 실패"
  ],
  "status": "REVIEW_NEEDED"
}
```

### status enum
- `PASS`
- `REVIEW_NEEDED`
- `FAIL`

#### 권장 기준
- expected_count == saved_requirement_count 이고 missing_count == 0 -> PASS
- 일부 병합/누락 있으나 설명 가능 -> REVIEW_NEEDED
- 기대 수 대비 큰 차이, 번호 매핑 실패 -> FAIL

---

## 5. AI 출력 스키마
중간 포맷을 입력받은 AI는 아래 구조로 반환하는 것을 권장한다.

```json
{
  "requirements": [
    {
      "requirement_code": "MNT-001",
      "original_requirement_nos": ["MAR-001"],
      "mapping_status": "SINGLE",
      "title": "시스템 운영을 위한 유지보수 관리 방안",
      "original_text": "시스템의 원활한 운영을 위하여 유지보수 관리 방안 및 지원 체계를 제시하여야 한다.",
      "category": ["유지보수"],
      "proposal_section_major": "관리부문",
      "proposal_section_minor": "유지보수 수행방안",
      "mandatory_flag": true,
      "evidence_required_flag": false,
      "query_needed_flag": false,
      "fact_basis": "유지보수 관리 방안 및 지원 체계를 제시하여야 한다는 원문이 직접 존재한다.",
      "interpretation_summary": "운영 안정성 확보와 장애 대응 체계 제시를 평가하려는 항목이다.",
      "intent_summary": "발주처는 운영지원 체계와 실질적 대응 능력을 중시한다.",
      "proposal_point": "유지보수 조직, 보고 체계, 제조사 협력 구조를 표와 프로세스로 제시한다.",
      "implementation_approach": "장애 접수-분석-조치-보고 프로세스와 역할 분담을 명시한다.",
      "expected_deliverables": ["유지보수 수행계획서", "장애 대응 프로세스도", "정기 보고서 양식"],
      "differentiation_point": "제조사 기술지원 연계 체계를 명확히 제시해 실행 가능성을 높인다.",
      "risk_note": "기술지원 SLA 또는 제조사 협력 범위가 불명확하면 질의가 필요하다.",
      "fact_level": "FACT",
      "confidence_score": 0.88,
      "source_block_ids": ["blk_p4_001"],
      "page_refs": [4]
    }
  ]
}
```

### mapping_status enum
- `SINGLE`
- `MERGED`
- `MISSING_CANDIDATE`
- `REQUIRES_REVIEW`

---

## 6. 제안서 목차 매핑 필드
각 Requirement에는 아래 필드를 추가하는 것을 권장한다.

- `proposal_section_major`
  - 개요
  - 기술부문
  - 관리부문
  - 지원부문
  - 기타

- `proposal_section_minor`
  - 예: 사업이해, 시스템 구성, 기능 대응, 보안/품질, 수행조직, 일정관리, 유지보수, 교육지원, 제출서류 등

- `proposal_write_hint`
  - 제안서 어느 장/절에 어떤 형태(표/프로세스/도식/서술)로 넣을지 힌트

- `evidence_needed`
  - 실적증명, 인증서, 투입인력 이력 등

---

## 7. 구현 원칙
1. md만으로 운영하지 않는다
2. json만으로 운영하지 않는다
3. block_id는 전 단계에서 절대 변하지 않게 유지한다
4. original_requirement_no는 AI가 못 붙이면 rule-based 보정 후보를 남긴다
5. coverage_audit는 저장 실패 시에도 남긴다
6. OCR은 기본이 아니라 fallback 또는 hybrid로 사용한다
7. 표/목록/체크리스트는 paragraph와 다른 excerpt_type으로 유지한다

---

## 8. 최소 도입 순서
### Phase A
- `normalized.md`
- `block_index.json`
- `requirement_catalog.json`
- `coverage_audit.json`

### Phase B
- AI 2단계 분리
  - 1차: 요구사항 분해/번호 매핑
  - 2차: 심화 분석/제안서 반영 포인트 생성

### Phase C
- 제안서 목차 매핑
- 외부 참고자료 검색 레이어 분리
- text-first 상세 UI 전환

---

## 9. 지금 바로 적용 가능한 최소 규칙
1. PDF 텍스트에서 요구사항 번호 패턴을 먼저 수집한다
2. 기대 건수를 AI 프롬프트에 명시한다
3. AI 출력에 `original_requirement_nos`를 강제한다
4. 저장 전 `coverage_audit.json`을 생성한다
5. expected_count와 saved_requirement_count가 다르면 자동 PASS 금지
