# BidOps_DB_ERD (Updated)

## 1. 문서 목적
이 문서는 BidOps MVP 기준 데이터베이스 구조 갱신안을 정의한다. 목적은 엔티티 경계를 명확히 하고, 백엔드 구현과 API 설계, RFP 분석 결과 저장 구조가 같은 기준 위에서 움직이도록 하는 것이다. 본 문서는 실제 물리 스키마 설계 전 단계의 **논리 ERD 기준 문서**다.

---

## 2. 설계 원칙
1. 제안 업무의 핵심 단위는 **프로젝트(Project)** 이다.
2. 모든 문서, 요구사항, 체크리스트, 검토 이력은 프로젝트에 귀속된다.
3. AI 분석 결과는 자유 텍스트가 아니라 **구조화 엔티티**로 저장한다.
4. 원문 근거 추적을 위해 문서, 페이지, 원문 블록 연결이 가능해야 한다.
5. 사람 검토와 승인 이력을 별도 엔티티로 분리해 감사 가능성을 확보한다.
6. MVP는 과도한 정규화보다 **실무 구현 우선 + 추적 가능성 우선** 원칙을 따른다.
7. AI 분석 상태와 사람 검토 상태는 서로 다른 상태축으로 관리한다.

---

## 3. 핵심 엔티티 목록
### 3.1 사용자/권한 영역
- User
- Organization
- ProjectMember

### 3.2 프로젝트/문서 영역
- Project
- Document
- DocumentPage
- SourceExcerpt

### 3.3 분석/요구사항 영역
- AnalysisJob
- Requirement
- RequirementSource
- RequirementInsight
- RequirementReview
- RequirementTag

### 3.4 누락/체크리스트 영역
- SubmissionChecklist
- ChecklistItem
- ChecklistReview

### 3.5 운영/이력 영역
- ProjectActivityLog
- AttachmentAsset

---

## 4. 엔티티 상세 정의
## 4.1 Organization
조직 단위 테넌시를 표현한다.

주요 컬럼:
- id (PK)
- name
- businessNumber (nullable)
- status
- createdAt
- updatedAt

비고:
- MVP에서는 단일 조직으로 시작 가능하나, 향후 다중 조직 확장을 고려해 논리 모델에는 포함한다.

## 4.2 User
시스템 사용자.

주요 컬럼:
- id (PK)
- organizationId (FK)
- email
- passwordHash
- name
- globalRole
- status
- lastLoginAt
- createdAt
- updatedAt

비고:
- globalRole은 시스템 레벨 역할이고, 프로젝트별 역할은 ProjectMember에서 관리한다.

## 4.3 Project
수주/입찰 단위의 상위 엔티티.

주요 컬럼:
- id (PK)
- organizationId (FK)
- projectCode
- name
- clientName
- businessName
- announcementNumber (nullable)
- status
- ownerUserId (FK)
- description
- createdAt
- updatedAt

비고:
- 모든 실질 데이터의 루트.

## 4.4 ProjectMember
프로젝트 참여자 및 권한.

주요 컬럼:
- id (PK)
- projectId (FK)
- userId (FK)
- projectRole (OWNER / ADMIN / EDITOR / REVIEWER / VIEWER)
- joinedAt

## 4.5 Document
업로드된 문서 메타데이터.

주요 컬럼:
- id (PK)
- projectId (FK)
- documentType (RFP / ANNEX / FORM / QNA / REFERENCE)
- title
- fileName
- mimeType
- storagePath
- fileSize
- versionNo
- parseStatus
- uploadedByUserId (FK)
- uploadedAt

비고:
- 같은 프로젝트 내 문서 버전 관리 대상.
- TODO: 레거시 초안의 ADDENDUM / QA / TEMPLATE / PROPOSAL_REFERENCE 명칭 매핑 확정 필요

## 4.6 DocumentPage
문서의 페이지 단위 구조.

주요 컬럼:
- id (PK)
- documentId (FK)
- pageNo
- extractedText
- layoutJson
- ocrConfidence
- createdAt

비고:
- OCR/레이아웃 분석 결과의 페이지 단위 저장소.

## 4.7 SourceExcerpt
원문 근거 블록.

주요 컬럼:
- id (PK)
- documentId (FK)
- documentPageId (FK)
- excerptType (PARAGRAPH / TABLE / LIST / HEADER / FOOTNOTE)
- anchorLabel
- rawText
- normalizedText
- bboxJson (nullable)
- createdAt

비고:
- Requirement와 Checklist가 “어디서 왔는지” 추적하는 핵심 엔티티.

## 4.8 AnalysisJob
비동기 분석 실행 이력.

주요 컬럼:
- id (PK)
- projectId (FK)
- documentId (FK)
- jobType (OCR / PARSE / REQUIREMENT_EXTRACTION / CHECKLIST_BUILD / QUERY_BUILD / SEARCH_INDEX / RFP_PARSE)
- status
- requestedByUserId (FK)
- startedAt
- finishedAt
- errorCode
- errorMessage

## 4.9 Requirement
요구사항 기본 엔티티.

주요 컬럼:
- id (PK)
- projectId (FK)
- documentId (FK)
- requirementCode
- title
- originalText
- category
- mandatoryFlag
- evidenceRequiredFlag
- confidenceScore
- analysisStatus
- reviewStatus
- queryNeededFlag
- createdAt
- updatedAt

비고:
- RFP 분석 결과의 중심 테이블
- `pageRefs`, `clauseRefs`는 Requirement 본문 컬럼으로 직접 저장하지 않는다

## 4.10 RequirementSource
Requirement와 SourceExcerpt 연결 엔티티.

주요 컬럼:
- id (PK)
- requirementId (FK)
- sourceExcerptId (FK)
- linkType (PRIMARY / SUPPORTING)
- createdAt

비고:
- 단일 sourceExcerptId 직접 연결보다 현실적인 다중 근거 추적 구조
- PRIMARY는 대표 근거, SUPPORTING은 보조 근거

## 4.11 RequirementInsight
요구사항 해석/제안 포인트(AI 분석 결과).

주요 컬럼:
- id (PK)
- requirementId (FK)
- factSummary
- interpretationSummary
- intentSummary
- proposalPoint
- implementationApproach
- expectedDeliverables
- differentiationPoint
- riskNote
- queryNeededFlag
- factLevel (FACT / INFERENCE / REVIEW_NEEDED)
- generatedByJobId (FK)
- createdAt
- updatedAt

비고:
- 구조화된 분석 결과 저장
- 사람 검토 상태는 여기에 저장하지 않는다

## 4.12 RequirementReview
사람 검토/승인 이력.

주요 컬럼:
- id (PK)
- requirementId (FK)
- reviewerUserId (FK)
- reviewStatus (NOT_REVIEWED / IN_REVIEW / APPROVED / HOLD / NEEDS_UPDATE)
- reviewComment
- reviewedAt

비고:
- RequirementInsight와 분리된 사람 검토 축

## 4.13 RequirementTag
요구사항 태그.

주요 컬럼:
- id (PK)
- requirementId (FK)
- tagType
- tagValue

예시:
- 기능영역: 보안, 데이터연계, UI/UX
- 문서성격: 평가항목, 제안요청, 제출서류

## 4.14 SubmissionChecklist
프로젝트별 제출 체크리스트 묶음.

주요 컬럼:
- id (PK)
- projectId (FK)
- checklistType (SUBMISSION / EVALUATION / EVIDENCE)
- title
- createdByJobId (FK)
- createdAt

## 4.15 ChecklistItem
체크리스트 상세 항목.

주요 컬럼:
- id (PK)
- checklistId (FK)
- sourceExcerptId (FK, nullable)
- linkedRequirementId (FK, nullable) — 자동 생성 시 요구사항과 연결
- itemCode (예: CHK-001)
- itemText
- mandatoryFlag
- dueHint
- currentStatus (TODO / IN_PROGRESS / DONE / BLOCKED)
- riskLevel (NONE / LOW / MEDIUM / HIGH)
- riskNote
- ownerUserId (FK, nullable) — 담당자
- actionComment — 조치 메모
- createdAt
- updatedAt

참고: `POST /checklists/generate` 호출 시 요구사항 기반으로 자동 생성됨.
위험도는 mandatory+evidenceRequired→HIGH, mandatory|queryNeeded→MEDIUM, 나머지→LOW.

## 4.16 ChecklistReview
체크리스트 검토/조치 이력.

주요 컬럼:
- id (PK)
- checklistItemId (FK)
- reviewerUserId (FK)
- actionType
- actionComment
- actedAt

## 4.17 Artifact (구 AttachmentAsset)
프로젝트 산출물.

주요 컬럼:
- id (PK)
- projectId (FK)
- title
- assetType (PROPOSAL / DESIGN / PLAN / REPORT / EVIDENCE / PRESENTATION / ETC)
- status (DRAFT / IN_PROGRESS / REVIEW / APPROVED / SUBMITTED)
- description
- linkedRequirementId (FK, 선택)
- linkedChecklistItemId (FK, 선택)
- deleted (soft delete)

## 4.17b ArtifactVersion
산출물 버전별 파일.

주요 컬럼:
- id (PK)
- artifactId (FK)
- version (정수, 자동 채번)
- fileName
- storagePath
- versionNote
- uploadedBy (FK)

## 4.18 ProjectActivityLog
주요 활동 감사 로그.

주요 컬럼:
- id (PK)
- projectId (FK)
- actorUserId (FK)
- activityType
- targetType
- targetId
- detailJson
- createdAt

---

## 5. 관계 요약
### 5.1 핵심 관계
- Organization 1:N User
- Organization 1:N Project
- Project 1:N Document
- Project 1:N ProjectMember
- Document 1:N DocumentPage
- DocumentPage 1:N SourceExcerpt
- Project 1:N Requirement
- Requirement 1:N RequirementSource
- RequirementSource N:1 SourceExcerpt
- Requirement 1:1 RequirementInsight (MVP 기준)
- Requirement 1:N RequirementReview
- Project 1:N SubmissionChecklist
- SubmissionChecklist 1:N ChecklistItem
- ChecklistItem 1:N ChecklistReview
- Project 1:N AttachmentAsset
- Project 1:N ProjectActivityLog

### 5.2 추적 관계
- RequirementSource.requirementId -> Requirement.id
- RequirementSource.sourceExcerptId -> SourceExcerpt.id
- ChecklistItem.sourceExcerptId -> SourceExcerpt.id
- RequirementInsight.generatedByJobId -> AnalysisJob.id

---

## 6. 텍스트 ERD
```text
Organization
 ├─< User
 └─< Project
      ├─< ProjectMember >─ User
      ├─< Document
      │    └─< DocumentPage
      │         └─< SourceExcerpt
      ├─< AnalysisJob
      ├─< Requirement
      │    ├─< RequirementSource >─ SourceExcerpt
      │    ├─1 RequirementInsight
      │    ├─< RequirementReview
      │    └─< RequirementTag
      ├─< SubmissionChecklist
      │    └─< ChecklistItem
      │         └─< ChecklistReview
      ├─< AttachmentAsset
      └─< ProjectActivityLog
```

---

## 7. 저장 전략
### 7.1 정형 컬럼 vs JSON
정형 컬럼으로 둘 값:
- 상태값(enum)
- FK
- 식별자
- 필수 검색/필터 기준값

JSON으로 둘 수 있는 값:
- DocumentPage.layoutJson
- SourceExcerpt.bboxJson
- ProjectActivityLog.detailJson

원칙:
- 자주 필터/조회하는 값은 정형 컬럼 우선
- 레이아웃/좌표/이력 상세처럼 구조 가변성이 큰 값은 JSON 허용

### 7.2 원문 근거 저장 전략
- 원문 블록은 `SourceExcerpt.rawText`
- 정규화 텍스트는 `SourceExcerpt.normalizedText`
- Requirement 자체에는 원문 분석 대상 `originalText`만 저장
- 실제 근거 위치(`pageNo`, `anchorLabel`, `clauseRef`)는 `RequirementSource + SourceExcerpt` 조합으로 조회

---

## 8. 인덱스 권장
- Project(projectCode)
- Project(status)
- Document(projectId, documentType)
- Document(projectId, uploadedAt desc)
- DocumentPage(documentId, pageNo)
- SourceExcerpt(documentId, documentPageId)
- Requirement(projectId, category)
- Requirement(projectId, analysisStatus)
- Requirement(projectId, reviewStatus)
- Requirement(projectId, mandatoryFlag)
- RequirementSource(requirementId, linkType)
- RequirementSource(sourceExcerptId)
- RequirementReview(requirementId, reviewStatus)
- ChecklistItem(checklistId, currentStatus)
- ChecklistItem(checklistId, riskLevel)
- AnalysisJob(projectId, status)
- AnalysisJob(documentId, jobType)

---

## 9. 다음 단계
1. 본 문서를 기준으로 PostgreSQL DDL 1차 초안 생성
2. JPA Entity/Repository와 필드명 정합화
3. API Spec과 enum 값 재점검
4. SourceExcerpt 조회 성능 검토
