# BidOps 분석 엔진 재설계안 v1

## 1. 현재 문제 진단

### 1.1 추출 정확도
- 원문 23건 → 시스템 10~15건 (커버리지 43~65%)
- GPT가 요약/병합하는 경향 제어 불가
- multi-pass 보완으로도 100% 달성 어려움
- 원인: PDF raw text를 GPT에 직접 투입 → 구조 정보 손실

### 1.2 검토 효율
- PDF-first 상세 화면은 PDF 로딩/렌더링에 의존
- bbox 없으면 위치 특정 불가
- 검토자가 원문과 분석 결과를 대조하기 어려움

### 1.3 분석 품질
- AI 분석이 일반론 수준에 머무름
- 제안서 작성에 바로 쓸 수 있는 구체성 부족
- 문서 근거 분석과 외부 참고자료 추천이 혼재

---

## 2. 유지할 것 / 바꿀 것

### 유지
- Spring Boot + Next.js + PostgreSQL(MariaDB) 아키텍처
- Requirement / RequirementInsight / RequirementReview 3계층 분리
- SourceExcerpt + RequirementSource 근거 연결 구조
- Organization 기반 멀티테넌시
- 검토 워크플로우 (연속 검토, 자동이동, 임시저장)
- AnalysisJob + Worker 비동기 처리 구조

### 바꿀 것
| 현재 | 변경 |
|------|------|
| PDF raw text → GPT 직접 투입 | **중간 분석 포맷(md+json)** 생성 후 GPT 투입 |
| OCR 항상 시도 | **native text 우선** + OCR fallback |
| PDF-first 상세 화면 | **text-first** 상세 화면 |
| 단일 GPT 호출로 추출+분석 | **2단계 분리**: 추출(구조화) → 분석(심화) |
| 커버리지 미추적 | **coverage audit** 핵심 기능화 |
| 카테고리만 분류 | **제안서 목차 매핑** 추가 |

---

## 3. 새 분석 파이프라인

```
[PDF 업로드]
    │
    ▼
[Stage 1: 텍스트 추출]
    ├── PDFBox native text 추출
    ├── 페이지별 구조 감지 (표/목록/제목/본문)
    ├── OCR fallback (빈 페이지/이미지 페이지만)
    └── 출력: DocumentExtractionResult
    │
    ▼
[Stage 2: 중간 분석 포맷 생성]  ← 새로 추가
    ├── 요구사항 표 감지 (MAR/DAR/SER 등 패턴)
    ├── 요구사항 후보 목록 추출 (rule-based + AI)
    ├── 기대 건수 산출
    └── 출력: IntermediateAnalysis (md + json)
    │
    ▼
[Stage 3: AI 심화 분석]
    ├── 입력: IntermediateAnalysis (구조화된 후보 목록)
    ├── 각 후보별 심화 분석 (제안포인트/구현방향/리스크)
    ├── 커버리지 검증 (기대 건수 vs 실제 건수)
    └── 출력: AnalysisResult[]
    │
    ▼
[Stage 4: 저장 + Coverage Audit]
    ├── Requirement + Insight + Source 저장
    ├── Coverage Audit 결과 저장
    ├── 제안서 목차 매핑
    └── 누락 후보 별도 저장
```

---

## 4. 중간 분석 포맷 (IntermediateAnalysis)

### 4.1 구조
```json
{
  "document_id": "uuid",
  "extraction_method": "PDFBOX | AZURE_DI",
  "total_pages": 47,
  "total_chars": 38115,

  "sections": [
    {
      "section_id": "sec-01",
      "title": "제3장 요구사항",
      "page_range": [8, 15],
      "section_type": "REQUIREMENT_TABLE"
    }
  ],

  "requirement_candidates": [
    {
      "candidate_id": "cand-001",
      "original_no": "MAR-001",
      "original_category": "모바일기기 분석",
      "original_text": "SMS · 카카오톡 대화 내용을 채팅방 형태 출력 및 PDF로 제공",
      "page_no": 9,
      "clause_id": "3.1",
      "source_section_id": "sec-01",
      "detection_method": "TABLE_ROW",
      "confidence": 0.95
    },
    {
      "candidate_id": "cand-002",
      "original_no": "MAR-002",
      "...": "..."
    }
  ],

  "expected_count": {
    "MAR": 6,
    "DAR": 4,
    "MHR": 1,
    "SER": 4,
    "QUR": 2,
    "COR": 1,
    "PMR": 4,
    "PSR": 1,
    "total": 23
  },

  "extraction_stats": {
    "tables_found": 3,
    "list_items_found": 15,
    "heading_sections": 8,
    "empty_pages": 2
  }
}
```

### 4.2 생성 방식
1. **rule-based**: 표 패턴 감지 (`MAR-\d+`, `DAR-\d+` 등)
2. **구조 감지**: 표 행/열 파싱 → 각 행을 후보로 등록
3. **AI 보조**: rule로 못 잡은 서술형 요구사항은 AI로 추출
4. **기대 건수**: 표 헤더/카테고리에서 자동 산출

---

## 5. Coverage Audit 데이터 구조

### 5.1 엔티티: CoverageAudit
```
CoverageAudit
├── id (PK)
├── projectId
├── documentId
├── analysisJobId
├── expectedCount: int          // 원문 기대 건수
├── extractedCount: int         // AI 추출 건수
├── savedCount: int             // DB 저장 건수
├── mergedCount: int            // 병합 건수
├── droppedCount: int           // 저장 중 탈락
├── missingCount: int           // 누락 의심
├── coverageRate: float         // savedCount / expectedCount
├── auditDetails: TEXT (JSON)   // 항목별 매핑 상세
├── createdAt
```

### 5.2 auditDetails JSON
```json
{
  "mappings": [
    {
      "original_no": "MAR-001",
      "original_text": "SMS · 카카오톡...",
      "mapped_requirement_id": "uuid-xxx",
      "mapped_code": "SFR-001",
      "status": "MAPPED"
    },
    {
      "original_no": "MAR-005",
      "original_text": "...",
      "mapped_requirement_id": null,
      "mapped_code": null,
      "status": "MISSING",
      "reason": "AI 추출에서 누락됨"
    },
    {
      "original_no": "PMR-001, PMR-002",
      "original_text": "...",
      "mapped_requirement_id": "uuid-yyy",
      "mapped_code": "PSN-001",
      "status": "MERGED",
      "reason": "투입 인력 조건 통합"
    }
  ],
  "category_summary": {
    "MAR": { "expected": 6, "mapped": 5, "missing": 1 },
    "DAR": { "expected": 4, "mapped": 4, "missing": 0 },
    "...": "..."
  }
}
```

### 5.3 화면
```
┌─────────────────────────────────────────┐
│ 추출 커버리지 감사                        │
│                                         │
│ 원문: 23건  추출: 21건  저장: 21건        │
│ 병합: 2건   누락: 2건                    │
│ 커버리지: 91%  ████████████░░ 91%        │
│                                         │
│ ┌─────────┬──────┬──────┬──────┐        │
│ │ 카테고리  │ 원문  │ 추출  │ 누락  │        │
│ ├─────────┼──────┼──────┼──────┤        │
│ │ MAR     │  6   │  5   │  1   │        │
│ │ DAR     │  4   │  4   │  0   │        │
│ │ SER     │  4   │  4   │  0   │        │
│ │ PMR     │  4   │  3   │  1   │ ← 병합 │
│ │ ...     │      │      │      │        │
│ └─────────┴──────┴──────┴──────┘        │
│                                         │
│ [누락 2건 상세 보기] [병합 2건 상세 보기]   │
└─────────────────────────────────────────┘
```

---

## 6. 상세 화면 text-first 재설계

### 6.1 현재 (PDF-first)
```
[PDF뷰어 38%] [분석탭 40%] [검토 22%]
```

### 6.2 새 구조 (text-first)
```
┌──────────────────────────────────────────────────┐
│ ← 목록  [3/23]  SFR-001  필수  확정  미검토      │
│ MAR-001 | p.9 | 3.1                              │
├──────────────────────────────────────────────────┤
│                                                  │
│ ■ 원문 (text-first)                              │
│ ┌──────────────────────────────────────────────┐ │
│ │ SMS · 카카오톡 대화 내용을 채팅방 형태         │ │
│ │ 출력 및 PDF로 제공                            │ │
│ │                            [PDF에서 보기 →]   │ │
│ └──────────────────────────────────────────────┘ │
│                                                  │
│ [사실확인] [AI해석] [제안전략] [리스크] [검토]     │
│                                                  │
│ ■ 사실 확인                                      │
│ ┌──────────────────────────────────────────────┐ │
│ │ 근거: p.9 조항 3.1                           │ │
│ │ 원문에 "~제공해야 한다" 의무 표현 포함         │ │
│ │ 확정 수준: FACT                               │ │
│ └──────────────────────────────────────────────┘ │
│                                                  │
│ ■ 제안 전략                                      │
│ ┌──────────────────────────────────────────────┐ │
│ │ 제안 포인트: ...                              │ │
│ │ 구현 방향: ...                                │ │
│ │ 차별화: ...                                   │ │
│ │ 산출물: [설계서] [소스코드] [테스트결과서]       │ │
│ │ 리스크: ...                                   │ │
│ └──────────────────────────────────────────────┘ │
│                                                  │
│ ■ 제안서 목차 매핑                               │
│ │ 기술 부문 > 시스템 구현 > 모바일 분석          │ │
│                                                  │
│ ■ 검토                                          │
│ │ [승인] [보류] [수정필요]  코멘트: [___]        │ │
│ │                    [검토 저장 → 다음 항목]     │ │
└──────────────────────────────────────────────────┘
```

### 6.3 핵심 변경
- PDF 뷰어는 "PDF에서 보기" 링크로 필요 시만 접근
- 원문 텍스트가 최상단에 직접 표시
- 탭 대신 섹션 스크롤 (한 화면에 모든 정보)
- 검토 액션이 하단에 통합 (우측 패널 제거)

---

## 7. 제안서 목차 매핑

### 7.1 표준 목차 구조
```
1. 사업 이해 (개요/배경/목표/범위)
2. 기술 부문
   2.1 시스템 아키텍처
   2.2 기능 구현
   2.3 성능/품질/보안
   2.4 데이터/연계
   2.5 UI/UX
3. 관리 부문
   3.1 프로젝트 관리 (일정/인력/조직)
   3.2 품질 관리
   3.3 위험 관리
4. 지원 부문
   4.1 교육/기술이전
   4.2 유지보수/운영
5. 기타
   5.1 실적/자격
   5.2 법적/계약
```

### 7.2 Requirement 필드 추가
```
proposal_section: "2.2"          // 제안서 목차 번호
proposal_section_name: "기능 구현" // 목차명
```

### 7.3 매핑 규칙
| 카테고리 | 제안서 목차 |
|----------|-----------|
| BUSINESS_OVERVIEW, BACKGROUND, OBJECTIVE, SCOPE | 1. 사업 이해 |
| FUNCTIONAL, DATA_INTEGRATION, UI_UX | 2.2 기능 구현 |
| PERFORMANCE, SECURITY, QUALITY | 2.3 성능/품질/보안 |
| INFRASTRUCTURE | 2.1 시스템 아키텍처 |
| SCHEDULE, PERSONNEL | 3.1 프로젝트 관리 |
| TESTING | 3.2 품질 관리 |
| TRAINING, MAINTENANCE | 4. 지원 부문 |
| TRACK_RECORD, PERSONNEL | 5.1 실적/자격 |
| LEGAL | 5.2 법적/계약 |
| DELIVERABLE, SUBMISSION | 5. 기타 |
| EVALUATION, PRESENTATION | 5. 기타 |

---

## 8. 구현 우선순위

### Phase A: 중간 포맷 + 커버리지 (1주)
1. IntermediateAnalysis 생성 로직 (rule-based 표 감지)
2. 기대 건수 자동 산출
3. CoverageAudit 엔티티/저장/API
4. 커버리지 감사 화면

### Phase B: 2단계 분석 분리 (1주)
1. Stage 2: 후보 추출 (구조 기반)
2. Stage 3: 심화 분석 (후보별 GPT 호출)
3. 커버리지 검증 + 누락 보완

### Phase C: text-first 상세 화면 (3일)
1. 상세 화면 재설계
2. PDF 뷰어를 옵션화
3. 섹션 스크롤 레이아웃

### Phase D: 제안서 목차 매핑 (2일)
1. proposal_section 필드 추가
2. 카테고리 → 목차 자동 매핑
3. 목차별 그룹 뷰

---

## 9. 다음 즉시 작업

현재 시스템에서 바로 개선 가능한 것:
1. **rule-based 표 감지**: PDF 텍스트에서 `MAR-\d+`, `DAR-\d+` 패턴 찾기 → 기대 건수 산출
2. **GPT 입력에 기대 건수 명시**: "이 문서에 MAR 6건, DAR 4건... 총 23건이 있습니다. 모두 추출하세요."
3. **커버리지 로그 강화**: 기대 vs 실제 비교표 자동 생성
