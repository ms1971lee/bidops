# RFP 분석 결과 → 엔티티 저장 매핑 규칙

- 버전: v0.1-draft
- 작성일: 2026-03-18
- 목적: `RfpAnalysisResultItem` (AI 워커 출력) → DB 엔티티 저장 시 필드 매핑과 변환 규칙을 정의한다.
- 관련 문서: `RFP_ANALYSIS_RESULT_SCHEMA.md`, `DB_ERD.md`

---

## 1. 저장 대상 엔티티

AI 워커 결과 1건(`RfpAnalysisResultItem`)은 아래 4개 엔티티로 저장된다.

| 엔티티 | 역할 | 생성 시점 |
|---|---|---|
| `Requirement` | 요구사항 원문, 분류, 상태 | 즉시 생성 |
| `RequirementInsight` | AI 분석 내용 (사실/추론/검토사유) | Requirement 생성 직후 |
| `SourceExcerpt` | 원문 발췌 블록 (페이지/조항 기준) | page_no가 있을 때 생성 또는 기존 매칭 |
| `RequirementSource` | Requirement ↔ SourceExcerpt 연결 | SourceExcerpt 생성 직후 |

### 1.1 조건부 저장

| 엔티티 | 조건 |
|---|---|
| `SourceExcerpt` | `page_no`가 null이면 생성하지 않음. 위치 정보를 factSummary에 임시 보존 |
| `RequirementSource` | SourceExcerpt가 생성/매칭된 경우에만 연결 |

---

## 2. Requirement 매핑

| Requirement 필드 | 소스 | 변환 규칙 |
|---|---|---|
| `projectId` | `RfpAnalysisResultRequest` 컨텍스트 | AnalysisJob에서 조회 |
| `documentId` | `RfpAnalysisResultRequest.documentId` | 그대로 |
| `requirementCode` | 자동 생성 | `{카테고리 prefix}-{순번}` (예: SEC-001) |
| `title` | `requirementText` 앞 80자 | 원문 앞부분 잘라서 사용 |
| `originalText` | `requirementText` | 그대로 |
| `category` | `requirementType` | `RequirementCategory` enum 변환 (섹션 4 참조) |
| `mandatoryFlag` | - | 기본값 `false`. AI 워커가 판단하지 않음 |
| `evidenceRequiredFlag` | - | 기본값 `false`. AI 워커가 판단하지 않음 |
| `confidenceScore` | - | 현재 미제공. null |
| `analysisStatus` | 고정 | `EXTRACTED` |
| `reviewStatus` | 고정 | `NOT_REVIEWED` |
| `factLevel` | `status` | 섹션 3 변환 규칙 참조 |
| `queryNeeded` | `status` | `status == '질의필요'`이면 true |

### 2.1 저장하지 않는 필드 (Requirement에 직접 넣지 않음)

| DTO 필드 | 사유 |
|---|---|
| `page_no` | 원문 위치 정보. RequirementSource → SourceExcerpt 경로로 관리 |
| `clause_id` | 조항 번호. RequirementSource → SourceExcerpt.anchorLabel로 관리 |
| `section_path` | 문서 내 경로. RequirementSource → SourceExcerpt 보조 정보 |

> 이 값들은 RequirementSource 연결이 구현될 때까지 **RequirementInsight의 factSummary에 참고 텍스트로 포함**하여 유실을 방지한다.

---

## 3. status → FactLevel 변환 규칙

| status (한글) | FactLevel | queryNeeded |
|---|---|---|
| `확인완료` | `FACT` | `false` |
| `원문확인필요` | `REVIEW_NEEDED` | `false` |
| `질의필요` | `REVIEW_NEEDED` | `true` |
| `추정` | `INFERENCE` | `false` |
| `파싱한계` | `REVIEW_NEEDED` | `false` |

---

## 4. requirementType → RequirementCategory 변환 규칙

현재 `RequirementCategory` enum은 9개 값만 존재한다.
스키마에 정의된 25개 분류를 매핑하려면 enum 확장이 필요하다.

### 4.1 현재 매핑 가능

| requirementType | RequirementCategory |
|---|---|
| `FUNCTIONAL` | `FUNCTIONAL` |
| `NON_FUNCTIONAL` | `NON_FUNCTIONAL` |
| `SCHEDULE` | `SCHEDULE` |
| `PERSONNEL` | `STAFFING` |
| `TRACK_RECORD` | `EXPERIENCE` |
| `SECURITY` | `SECURITY` |
| `EVALUATION` | `EVALUATION` |
| `DELIVERABLE` | `DELIVERABLE` |

### 4.2 현재 매핑 불가 → ETC로 fallback

| requirementType | 임시 매핑 | 비고 |
|---|---|---|
| `BUSINESS_OVERVIEW` | `ETC` | enum 확장 필요 |
| `BACKGROUND` | `ETC` | enum 확장 필요 |
| `OBJECTIVE` | `ETC` | enum 확장 필요 |
| `SCOPE` | `ETC` | enum 확장 필요 |
| `PERFORMANCE` | `NON_FUNCTIONAL` | 성격 유사 |
| `QUALITY` | `NON_FUNCTIONAL` | 성격 유사 |
| `TESTING` | `ETC` | enum 확장 필요 |
| `DATA_INTEGRATION` | `FUNCTIONAL` | 성격 유사 |
| `UI_UX` | `FUNCTIONAL` | 성격 유사 |
| `INFRASTRUCTURE` | `NON_FUNCTIONAL` | 성격 유사 |
| `SUBMISSION` | `ETC` | enum 확장 필요 |
| `PROPOSAL_GUIDE` | `ETC` | enum 확장 필요 |
| `PRESENTATION` | `EVALUATION` | 성격 유사 |
| `MAINTENANCE` | `ETC` | enum 확장 필요 |
| `TRAINING` | `ETC` | enum 확장 필요 |
| `LEGAL` | `ETC` | enum 확장 필요 |

> **TODO**: `RequirementCategory` enum을 스키마 25개 값으로 확장하면 fallback 없이 1:1 매핑 가능.

---

## 5. RequirementInsight 매핑

| RequirementInsight 필드 | 소스 | 변환 규칙 |
|---|---|---|
| `requirementId` | 생성된 Requirement.id | FK 연결 |
| `factSummary` | `originalEvidence` + 위치 참고 | 아래 규칙 참조 |
| `interpretationSummary` | `inferenceNote` | 그대로. null 허용 |
| `intentSummary` | - | 현재 미제공. null |
| `proposalPoint` | - | 현재 미제공. null |
| `implementationApproach` | - | 현재 미제공. null |
| `expectedDeliverablesJson` | - | 현재 미제공. null |
| `differentiationPoint` | - | 현재 미제공. null |
| `riskNoteJson` | `reviewRequiredNote` | 단건 문자열을 JSON array로 변환 |
| `queryNeeded` | `status` | `status == '질의필요'`이면 true |
| `factLevel` | `status` | 섹션 3 변환 규칙과 동일 |
| `generatedByJobId` | `RfpAnalysisResultRequest.analysisJobId` | 그대로 |

### 5.1 factSummary 조합 규칙

RequirementSource가 아직 없으므로, 위치 정보 유실을 방지하기 위해 factSummary에 포함한다.

```
{original_evidence}
[위치: p.{page_no}, {clause_id}, {section_path}]
[근거: {fact_basis}]
```

null인 위치 필드는 생략한다.

---

## 6. SourceExcerpt + RequirementSource 연결 전략

### 6.1 연결 조건

- `page_no`가 있으면 SourceExcerpt 생성/매칭 + RequirementSource 연결
- `page_no`가 없으면 연결하지 않음 → factSummary에 위치 정보 임시 보존

### 6.2 SourceExcerpt 매칭/생성 규칙

```
1. documentId + pageNo + anchorLabel 로 기존 SourceExcerpt 조회
   - anchorLabel = clause_id (우선) 또는 section_path
2. 있으면 → 기존 SourceExcerpt 재사용
3. 없으면 → 새 SourceExcerpt 생성:
   - excerptType: PARAGRAPH (기본값)
   - rawText: original_evidence + fact_basis
   - normalizedText: rawText와 동일 (정규화는 OCR 파이프라인에서 수행)
```

### 6.3 RequirementSource 매핑

| RequirementSource 필드 | 소스 |
|---|---|
| `requirementId` | 생성된 Requirement.id |
| `sourceExcerptId` | 매칭/생성된 SourceExcerpt.id |
| `linkType` | `PRIMARY` (AI 분석 결과의 1차 근거) |

### 6.4 factSummary 위치 정보 유지

SourceExcerpt가 연결되더라도, factSummary의 `[위치: ...]` 텍스트는 유지한다.
이유: SourceExcerpt의 anchorLabel만으로는 section_path 전체를 표현할 수 없으므로, 사람이 읽을 수 있는 참고 텍스트로 보존한다.

---

## 7. 중복 저장 방지 정책

### 7.1 중복 판정 기준

`document_id` + `requirement_text`(= `Requirement.originalText`) 조합이 동일하면 중복으로 판정한다.

- 같은 문서에서 동일한 원문을 가진 요구사항은 1건만 저장한다.
- `analysis_job_id`가 달라도, 원문이 같으면 중복이다.

### 7.2 중복 발생 시 동작

| 중복 유형 | 동작 |
|---|---|
| DB에 이미 존재 | 저장 건너뜀 + warning 반환 |
| 같은 요청 배치 내 중복 | 첫 번째만 저장 + 나머지 warning 반환 |

### 7.3 응답 구조

```json
{
  "saved_count": 1,
  "skipped_count": 1,
  "requirement_ids": ["..."],
  "warnings": [
    { "index": 1, "field": "requirement_text", "message": "이미 저장된 요구사항입니다..." }
  ]
}
```

### 7.4 선택하지 않은 대안

- `analysis_job_id` 단위 전체 차단: 부분 재시도가 불가능해지므로 채택하지 않음
- upsert (덮어쓰기): 사람 검토 후 수정된 데이터를 AI가 덮어쓸 위험이 있으므로 채택하지 않음

---

## 8. AnalysisJob 상태 관리

### 8.1 상태 흐름

```
PENDING → RUNNING → COMPLETED
                  → FAILED
```

| 상태 | 전환 시점 | 설정 값 |
|---|---|---|
| `PENDING` | Job 생성 시 | progress=0 |
| `RUNNING` | save API 호출 시작 | startedAt 기록 |
| `COMPLETED` | save 처리 완료 후 | progress=100, resultCount, finishedAt |
| `FAILED` | 저장 중 예외 발생 시 | errorCode, errorMessage, finishedAt |

### 8.2 Job-Requirement 연결

- `RequirementInsight.generatedByJobId` → 어떤 Job이 생성한 분석 결과인지 추적
- `AnalysisJob.resultCount` → 해당 Job으로 저장된 요구사항 수

### 8.3 중복 Job 방지

같은 프로젝트 내 동일 jobType의 PENDING/RUNNING Job이 있으면 생성 거부 (409 Conflict).

---

## 9. 저장 순서

```
1. AnalysisJob 존재 확인 (analysisJobId)
2. projectId 조회 (AnalysisJob.projectId)
3. AnalysisJob 상태 → RUNNING
4. for each RfpAnalysisResultItem:
   4-0. 중복 체크 (배치 내 + DB)
        → 중복이면 skip + warning
   4-1. requirementCode 자동 채번
   4-2. Requirement 생성 및 저장
   4-3. RequirementInsight 생성 및 저장 (requirementId + generatedByJobId 연결)
   4-3b. RequirementReview 초기 생성 (NOT_REVIEWED)
   4-4. page_no가 있으면:
        a. SourceExcerpt 매칭 (documentId + pageNo + anchorLabel)
        b. 없으면 SourceExcerpt 신규 생성
        c. RequirementSource 생성 (PRIMARY)
5. AnalysisJob 상태 → COMPLETED (resultCount 기록)
```

---

## 10. 다음 단계

- [ ] `RequirementCategory` enum 확장 (25개 값)
- [ ] OCR 파이프라인에서 생성된 SourceExcerpt와 AI 분석 결과 SourceExcerpt 병합 정책
- [ ] SourceExcerpt.excerptType 자동 분류 (현재 PARAGRAPH 고정)
- [ ] 저장 실패 시 FAILED 상태 전환 (현재 트랜잭션 롤백만 수행)
