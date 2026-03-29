# BidOps_API_SPEC (Updated)

## 1. 문서 목적
이 문서는 BidOps MVP 백엔드 API의 갱신 명세다. 목적은 프론트엔드, 백엔드, AI 워커가 같은 인터페이스 기준으로 개발되도록 하는 것이다. 본 문서는 OpenAPI YAML(`docs/openapi.yaml`)과 동일한 방향을 유지하는 **도메인/API 기준 문서**다.

---

## 2. 이번 업데이트의 핵심 반영 사항
1. `Requirement`와 `RequirementInsight(AI 분석)`, `RequirementReview(사람 검토)`를 분리했다.
2. `pageRefs`, `clauseRefs`는 `Requirement` 본문에서 제거하고 `GET /requirements/{id}/sources` 응답에서만 조합해 제공한다.
3. 원문 근거는 `SourceExcerpt`와 `RequirementSource` 연결을 기준으로 추적한다.
4. 요구사항 상태를 **AI 처리 상태(`analysisStatus`)** 와 **사람 검토 상태(`reviewStatus`)** 로 분리했다.
5. 체크리스트 상태(`currentStatus`)와 위험도(`riskLevel`)를 분리했다.
6. `GET /requirements/{id}/review` 엔드포인트를 추가했다.

---

## 3. API 설계 원칙
1. API는 단순 CRUD보다 **업무 흐름 중심**으로 설계한다.
2. 프로젝트, 문서, 요구사항, 체크리스트, 검토 상태가 핵심 리소스다.
3. 장시간 작업은 동기 처리하지 않고 Job/Task 상태로 관리한다.
4. AI 분석 결과는 반드시 구조화된 객체로 저장/조회한다.
5. “확정 정보 / 추론 정보 / 검토 필요 정보” 구분이 가능해야 한다.
6. 모든 주요 변경은 이력 추적이 가능해야 한다.
7. AI 분석 결과와 사람 검토 결과를 같은 엔티티/응답에 혼합하지 않는다.

---

## 4. 기본 규칙
### 4.1 Base URL
- `/api/v1`

### 4.2 응답 원칙
- 성공/실패 구조를 일관되게 유지
- 리스트는 페이징 기본 제공
- 날짜/시간은 ISO 8601
- 상태값은 문자열 enum 사용

### 4.3 공통 응답 예시
```json
{
  "success": true,
  "data": {},
  "meta": {},
  "error": null
}
```

실패 예시
```json
{
  "success": false,
  "data": null,
  "meta": {},
  "error": {
    "code": "PROJECT_NOT_FOUND",
    "message": "프로젝트를 찾을 수 없습니다."
  }
}
```

---

## 5. 인증/권한
### 5.1 인증
- 이메일/비밀번호 또는 사내 인증 확장 가능
- MVP 기준: JWT 기반 세션

### 5.2 권한 레벨(초안)
- OWNER
- ADMIN
- EDITOR
- REVIEWER
- VIEWER

### 5.3 권한 원칙
- 프로젝트 단위 접근 제어
- 문서 업로드/삭제, 분석 실행, 승인 액션은 권한 분리
- 감사 로그 대상 액션 구분 필요

---

## 6. 상태값(enum) 기준
### 6.1 ProjectStatus
- DRAFT
- READY
- ANALYZING
- REVIEWING
- SUBMISSION_PREP
- CLOSED

### 6.2 DocumentParseStatus
- UPLOADED
- PARSING
- PARSED
- FAILED

### 6.3 AnalysisJobStatus
- PENDING
- RUNNING
- COMPLETED
- FAILED

### 6.4 FactLevel
- FACT
- INFERENCE
- REVIEW_NEEDED

### 6.5 RequirementAnalysisStatus
- EXTRACTED
- ENRICHED
- REVIEW_NEEDED

### 6.6 RequirementReviewStatus
- NOT_REVIEWED
- IN_REVIEW
- APPROVED
- HOLD
- NEEDS_UPDATE

### 6.7 ChecklistStatus
- TODO
- IN_PROGRESS
- DONE
- BLOCKED

### 6.8 RiskLevel
- NONE
- LOW
- MEDIUM
- HIGH

### 6.9 QueryStatus
- DRAFT
- REVIEWING
- SENT
- ANSWERED
- CLOSED

---

## 7. 핵심 도메인 모델
### 7.1 User
- id
- email
- name
- role
- status
- createdAt

### 7.2 Project
- id
- name
- clientName
- businessName
- status
- description
- createdBy
- createdAt
- updatedAt

### 7.3 Document
- id
- projectId
- type
- fileName
- storagePath
- version
- parseStatus
- pageCount (nullable — 파싱 완료 후 AI 워커가 설정)
- uploadedBy
- uploadedAt
- viewerUrl

### 7.4 AnalysisJob
- id
- projectId
- documentId
- jobType
- status
- startedAt
- finishedAt
- errorMessage

### 7.5 Requirement
- id
- projectId
- documentId
- requirementCode
- title
- originalText
- category
- mandatory
- evidenceRequired
- analysisStatus
- reviewStatus
- confidenceLevel
- queryNeeded

### 7.6 RequirementInsight (AI 분석 전용)
- id
- requirementId
- factSummary
- interpretationSummary
- intentSummary
- proposalPoint
- implementationApproach
- expectedDeliverables
- differentiationPoint
- riskNote
- queryNeeded
- factLevel
- generatedByJobId

### 7.7 RequirementReview (사람 검토 전용)
- id
- requirementId
- reviewStatus
- reviewComment
- reviewedBy
- reviewedAt

### 7.8 SourceExcerpt
- id
- documentId
- pageNo
- excerptType
- anchorLabel
- rawText
- normalizedText
- bboxJson

### 7.9 RequirementSource
- id
- requirementId
- sourceExcerptId
- linkType (PRIMARY / SUPPORTING)

### 7.10 ChecklistItem
- id
- projectId
- requirementId
- type
- title
- description
- mandatory
- currentStatus
- riskLevel
- ownerUserId
- dueDate

### 7.11 AuditLog
- id
- projectId
- actorUserId
- actionType
- targetType
- targetId
- beforeValue
- afterValue
- createdAt

---

## 8. 프로젝트 API
### 8.1 프로젝트 목록 조회
`GET /projects`

Query:
- page
- size
- keyword
- status

Response data:
- items[]
- totalCount

### 8.2 프로젝트 생성
`POST /projects`

Request:
```json
{
  "name": "한국인 초고령자 코호트 구축 사업",
  "clientName": "OO기관",
  "businessName": "건강노화 연구 플랫폼 구축",
  "description": "제안 준비용 프로젝트"
}
```

### 8.3 프로젝트 상세 조회
`GET /projects/{projectId}`

### 8.4 프로젝트 수정
`PATCH /projects/{projectId}`

### 8.5 프로젝트 상태 변경
`POST /projects/{projectId}/status`

예:
```json
{
  "status": "ANALYZING"
}
```

---

## 9. 문서(Document) API
### 9.1 문서 업로드
`POST /projects/{projectId}/documents`

형식:
- multipart/form-data

필드:
- file
- type (`RFP`, `ANNEX`, `FORM`, `QNA`, `REFERENCE`)
- versionNote

> TODO: 기존 문서/ERD 초안의 `ADDENDUM`, `QA`, `TEMPLATE`, `PROPOSAL_REFERENCE` 명칭은 최종 enum 기준으로 추가 매핑 검토 필요

### 9.2 문서 목록 조회
`GET /projects/{projectId}/documents`

### 9.3 문서 상세 조회
`GET /projects/{projectId}/documents/{documentId}`

### 9.4 문서 삭제
`DELETE /projects/{projectId}/documents/{documentId}`

주의:
- 삭제는 실제 삭제보다 soft delete 우선 고려

### 9.5 문서 버전 목록
`GET /projects/{projectId}/documents/{documentId}/versions`

### 9.6 SourceExcerpt 단건 조회
`GET /source-excerpts/{id}`

source_excerpt_id로 원문 발췌 블록을 직접 조회한다.
체크리스트 등에서 requirement sources API를 경유하지 않고 바로 근거 데이터를 가져올 때 사용.

응답:
```json
{
  "id": "se_001",
  "document_id": "doc_001",
  "page_no": 5,
  "excerpt_type": "PARAGRAPH",
  "anchor_label": "3.1.2",
  "raw_text": "시스템은 24시간 무중단 운영이 가능해야 한다.",
  "normalized_text": "...",
  "bbox_json": "{\"x\":10,\"y\":20,\"w\":80,\"h\":5}"
}
```

권한: 해당 문서가 속한 프로젝트의 DOCUMENT_VIEW 권한 필요.

### 9.7 문서 파싱 상태 업데이트 (워커 콜백)
`PATCH /projects/{projectId}/documents/{documentId}/parse-status`

Request:
```json
{
  "status": "PARSED",
  "page_count": 42
}
```

page_count는 선택 필드. 파싱 완료 시 워커가 함께 전달하면 Document.pageCount에 저장된다.

---

## 10. 분석 Job API
### 10.1 분석 시작
`POST /projects/{projectId}/analysis-jobs`

Request:
```json
{
  "documentId": "doc_001",
  "jobType": "RFP_PARSE"
}
```

### 10.2 Job 상태 조회
`GET /projects/{projectId}/analysis-jobs/{jobId}`

### 10.3 프로젝트 기준 Job 목록 조회
`GET /projects/{projectId}/analysis-jobs`

---

## 11. 요구사항(Requirement) API
### 11.1 요구사항 목록 조회
`GET /projects/{projectId}/requirements`

Query:
- page
- size
- category
- mandatory
- evidenceRequired
- analysisStatus
- reviewStatus
- factLevel
- queryNeeded
- keyword

목록 응답의 핵심 필드:
- requirementCode
- title
- category
- mandatory
- evidenceRequired
- analysisStatus
- reviewStatus
- factLevel
- queryNeeded

> `pageRefs`, `clauseRefs`는 목록/기본 Requirement 응답에 직접 싣지 않는다.

### 11.2 요구사항 상세 조회
`GET /projects/{projectId}/requirements/{requirementId}`

Response data 구조:
```json
{
  "requirement": {},
  "insight": {},
  "review": {}
}
```

### 11.3 요구사항 기본 정보 수정
`PATCH /projects/{projectId}/requirements/{requirementId}`

수정 대상:
- title
- category
- mandatory
- evidenceRequired
- analysisStatus

### 11.4 요구사항 원문 근거 조회
`GET /projects/{projectId}/requirements/{requirementId}/sources`

Response data 예시:
```json
{
  "page_refs": [12, 13],
  "clause_refs": ["3.2.1", "별지1"],
  "source_text_blocks": [
    {
      "id": "abc-123",
      "page_no": 12,
      "excerpt_type": "PARAGRAPH",
      "anchor_label": "3.2.1",
      "raw_text": "사업관리자를 1인 이상 배치하여야 한다.",
      "normalized_text": "사업관리자를 1인 이상 배치하여야 한다.",
      "bbox_json": "{\"x\":5.9,\"y\":14.2,\"w\":88.2,\"h\":3.6}",
      "link_type": "PRIMARY"
    }
  ]
}
```

필드 설명:
- `page_refs`: 연결된 SourceExcerpt의 pageNo 유니크 정렬 목록
- `clause_refs`: 연결된 SourceExcerpt의 anchorLabel 유니크 정렬 목록
- `source_text_blocks[]`:
  - `id`: SourceExcerpt ID
  - `page_no`: 페이지 번호 (1-based)
  - `excerpt_type`: `PARAGRAPH | TABLE | LIST | HEADER | FOOTNOTE`
  - `anchor_label`: 조항 번호 (예: "3.2.1", "제4조")
  - `raw_text`: 원문 텍스트
  - `normalized_text`: 정규화된 텍스트
  - `bbox_json`: 페이지 내 위치 좌표 JSON (% 단위). Azure DI 분석 시 제공. null이면 위치 미확인.
  - `link_type`: `PRIMARY` (핵심 원문) 또는 `SUPPORTING` (보조 근거)

설명:
- `page_refs`, `clause_refs`는 `RequirementSource` + `SourceExcerpt` 조합 결과다.
- DB에서 `Requirement` 본문 컬럼으로 직접 저장하지 않는다.

### 11.5 요구사항 AI 분석 조회
`GET /projects/{projectId}/requirements/{requirementId}/analysis`

Response data:
- factSummary
- interpretationSummary
- intentSummary
- proposalPoint
- implementationApproach
- expectedDeliverables
- differentiationPoint
- riskNote
- queryNeeded
- factLevel

### 11.6 요구사항 AI 분석 수정
`PATCH /projects/{projectId}/requirements/{requirementId}/analysis`

주의:
- 이 API는 AI 분석 결과만 수정한다.
- 사람 검토 상태/코멘트는 별도 review API로 관리한다.

### 11.7 요구사항 검토 조회
`GET /projects/{projectId}/requirements/{requirementId}/review`

Response data:
- reviewStatus
- reviewComment
- reviewedBy
- reviewedAt

### 11.8 요구사항 검토 상태 변경
`POST /projects/{projectId}/requirements/{requirementId}/review-status`

Request:
```json
{
  "reviewStatus": "APPROVED",
  "reviewComment": "원문 근거와 제안 포인트 확인 완료"
}
```

Response:
- `RequirementReview` DTO 반환

---

## 12. 체크리스트 API
### 12.1 체크리스트 항목 목록 조회
`GET /projects/{projectId}/checklists/{checklistId}/items`

Query (모두 optional, AND 조합):
- status (ChecklistItemStatus)
- risk_level (RiskLevel)
- mandatory (boolean)
- requirement_id (string)
- owner_user_id (string)
- keyword (string — item_text LIKE 검색)

### 12.2 체크리스트 항목 생성
`POST /projects/{projectId}/checklists`

### 12.3 체크리스트 항목 수정
`PATCH /projects/{projectId}/checklists/{checklistItemId}`

핵심 필드:
- currentStatus (`TODO / IN_PROGRESS / DONE / BLOCKED`)
- riskLevel (`NONE / LOW / MEDIUM / HIGH`)
- riskNote
- dueDate

### 12.4 체크리스트 자동 생성
`POST /projects/{projectId}/checklists/generate`

---

## 13. 질의(Query) API
### 13.1 질의 목록 조회
`GET /projects/{projectId}/queries`

### 13.2 질의 항목 저장
`POST /projects/{projectId}/queries`

### 13.3 질의 초안 생성
`POST /projects/{projectId}/queries/generate`

### 13.4 질의 상태 변경
`PATCH /projects/{projectId}/queries/{queryId}`

---

## 14. 산출물(Artifact) API

### 14.1 산출물 목록 조회
`GET /projects/{projectId}/artifacts`

### 14.2 산출물 생성
`POST /projects/{projectId}/artifacts`

Request:
```json
{
  "title": "요구사항 정의서",
  "asset_type": "PROPOSAL",
  "description": "...",
  "linked_requirement_id": null,
  "linked_checklist_item_id": null
}
```

`asset_type` 값: `PROPOSAL`, `DESIGN`, `PLAN`, `REPORT`, `EVIDENCE`, `PRESENTATION`, `ETC`

### 14.3 산출물 상태 변경
`POST /projects/{projectId}/artifacts/{artifactId}/status`

Request: `{ "status": "DRAFT|IN_PROGRESS|REVIEW|APPROVED|SUBMITTED" }`

### 14.4 산출물 버전 목록
`GET /projects/{projectId}/artifacts/{artifactId}/versions`

### 14.5 산출물 수정
`PATCH /projects/{projectId}/artifacts/{artifactId}`

Request (모든 필드 선택):
```json
{
  "title": "수정된 제목",
  "asset_type": "DESIGN",
  "description": "수정된 설명",
  "linked_requirement_id": "req-id",
  "linked_checklist_item_id": null
}
```

### 14.6 산출물 삭제
`DELETE /projects/{projectId}/artifacts/{artifactId}`

### 14.7 산출물 버전 업로드
`POST /projects/{projectId}/artifacts/{artifactId}/versions` (multipart/form-data)

Form fields:
- `file` (필수): 업로드 파일
- `version_note` (선택): 버전 메모

Response:
```json
{
  "id": "...",
  "version": 1,
  "file_name": "요구사항정의서_v1.pdf",
  "version_note": "초안",
  "uploaded_by": "user-id",
  "viewer_url": "http://localhost:8080/files/projects/...",
  "created_at": "..."
}
```

---

## 15. 감사 로그 API
### 15.1 감사 로그 목록 조회
`GET /projects/{projectId}/audit-logs`

필터:
- actorUserId
- actionType
- targetType
- dateFrom
- dateTo

---

## 16. 주의 및 오픈 이슈
1. `auth/login`, `auth/me`, refresh token 등 인증 상세 API는 아직 별도 명세가 필요하다.
2. 프로젝트 멤버 초대/권한 변경 API는 후속 범위다.
3. 문서 버전 비교 API는 후속 범위다.
4. 검색 API 분리 여부는 MVP 후반에 확정한다.
5. `DocumentType`의 레거시 명칭 매핑은 DDL 전 확정 필요하다.

---

## 17. 다음 단계
1. 본 문서 기준으로 `docs/openapi.yaml` 유지/보강
2. PostgreSQL DDL 1차 초안 작성
3. Spring Boot package/controller/service/dto 구현 정합화
4. 프론트 API client 타입 생성
