# BidOps_ARCHITECTURE (Updated)

## 1. 문서 목적
이 문서는 BidOps의 1차 시스템 아키텍처와 기술 역할 분담을 정의한다.
핵심 목표는 **RFP 분석 신뢰성**과 **혼자서도 개발 가능한 단순한 구조**를 동시에 만족하는 것이다.

---

## 2. 아키텍처 한 줄 정의
BidOps는 **문서 업로드 → OCR/레이아웃 추출 → 요구사항 구조화 분석 → 규칙 검증 → 사람 검토/승인**의 흐름으로 동작하는 문서 운영형 SaaS다.

---

## 3. 핵심 설계 원칙
1. 한 모델이 모든 것을 처리하지 않는다.
2. OCR/레이아웃과 요구사항 해석을 분리한다.
3. AI 분석 결과는 반드시 구조화 데이터로 저장한다.
4. 규칙 검증과 사람 승인 단계를 제품 흐름에 포함한다.
5. 처음부터 MSA로 가지 않고, **모듈형 모놀리스 + AI 워커 분리**로 시작한다.
6. AI 분석 결과와 사람 검토 상태는 서로 다른 엔티티/상태축으로 관리한다.

---

## 4. 권장 기술 조합
- 프론트엔드: Next.js
- 백엔드 API: Spring Boot
- AI 워커: Python
- DB: PostgreSQL
- 파일 저장: S3 또는 동급 객체 스토리지
- OCR/문서 구조화: Azure Document Intelligence
- RFP 분석 주엔진: OpenAI GPT-5.4
- 개발 보조: Claude Code + IntelliJ
- 초기 배포: AWS App Runner + RDS + S3

---

## 5. OCR이 이 프로젝트에서 쓰이는 위치
OCR은 단순히 “PDF를 글자로 바꾸는 기능”이 아니다. BidOps에서는 아래 목적에 쓴다.

### 5.1 스캔 PDF 텍스트 추출
일부 RFP는 이미지 스캔본이라 일반 텍스트 추출이 거의 안 된다.
이 경우 OCR이 페이지의 실제 텍스트를 복원한다.

### 5.2 표/체크박스/레이아웃 인식
RFP에는 표, 제출서류 목록, 평가표, 체크 항목, 페이지 헤더/푸터가 섞여 있다.
OCR + 레이아웃 분석으로 이를 구조화해야 뒤의 AI가 정확히 읽을 수 있다.

### 5.3 원문 근거 연결
요구사항 분석 결과를 “몇 페이지 어떤 문단 근거인지” 연결하려면 페이지별 위치 정보가 필요하다.
OCR/레이아웃 단계가 이 근거 좌표를 만든다.

### 5.4 누락 탐지 품질 향상
필수 제출물, 증빙, 인력조건, 평가기준은 표나 목록으로 들어가는 경우가 많다.
OCR 품질이 낮으면 누락 탐지 품질도 같이 떨어진다.

정리하면, OCR은 분석 보조 기능이 아니라 **RFP를 기계가 읽을 수 있는 구조로 바꾸는 첫 번째 엔진**이다.

---

## 6. 전체 시스템 구성
### 6.1 사용자 계층
- 제안 PM
- 작성 실무자
- 관리자

### 6.2 애플리케이션 계층
- 웹 UI (Next.js)
- API 서버 (Spring Boot)
- AI 워커 (Python)
- 작업 큐/비동기 처리
- 규칙 검증 엔진

### 6.3 데이터 계층
- PostgreSQL
- 객체 스토리지(S3)
- 검색 인덱스(초기 PostgreSQL FTS, 이후 Elasticsearch/OpenSearch 검토)

### 6.4 외부 AI/문서 서비스
- Azure Document Intelligence
- OpenAI API
- 향후 보조 검증용 모델 추가 가능

---

## 7. 데이터 흐름
### 7.1 1차 흐름
1. 사용자가 프로젝트를 생성한다.
2. RFP PDF를 업로드한다.
3. 백엔드는 파일 메타데이터를 저장하고 원본을 객체 스토리지에 저장한다.
4. AI 워커가 OCR/레이아웃 분석 작업을 실행한다.
5. 구조화된 페이지/문단/표 데이터를 DB에 저장한다.
6. RFP 분석 주엔진이 요구사항을 구조화 추출한다.
7. 규칙 엔진이 누락/증빙/충돌 가능성을 검증한다.
8. 결과를 UI에 표시한다.
9. 사용자가 검토/수정/승인한다.

### 7.2 산출물 흐름
- 요구사항 리스트
- 요구사항 상세 분석
- 필수 제출물 체크리스트
- 질의 필요 항목
- 제안 반영 포인트

---

## 8. 모듈 설계
## 8.1 프론트엔드
주요 역할:
- 프로젝트 목록/상세 화면
- 문서 업로드 UI
- RFP 분석 결과 화면
- 요구사항 목록/필터
- 요구사항 상세/검토 UI
- 체크리스트/상태 표시

핵심 원칙:
- 업무형 정보구조 우선
- 표/필터/상태배지 명확화
- 원문 근거 페이지로 빠른 이동

## 8.2 백엔드 API
주요 역할:
- 인증/권한
- 프로젝트/문서 메타데이터 관리
- 분석 작업 요청/상태 조회
- 요구사항/체크리스트/검토 이력 API
- 감사로그 관리

## 8.3 AI 워커
주요 역할:
- OCR/레이아웃 분석 호출
- 문서 구조 정규화
- 요구사항 구조화 추출
- 규칙 검증 전처리
- 분석 결과 저장

---

## 9. 핵심 도메인 엔티티
### 9.1 Project
- project_id
- name
- client_name
- bid_type
- status
- created_by

### 9.2 Document
- document_id
- project_id
- type
- original_filename
- storage_path
- version
- parse_status

### 9.3 SourceExcerpt
- source_excerpt_id
- document_id
- page_no
- excerpt_type
- anchor_label
- raw_text
- normalized_text
- bbox_json

설명:
- OCR/레이아웃 분석 결과에서 나온 원문 근거 블록
- Requirement와 Checklist의 근거 추적 기준

### 9.4 Requirement
- requirement_id
- project_id
- document_id
- original_text
- category
- mandatory_flag
- evidence_required_flag
- analysis_status
- review_status
- confidence_score
- query_needed_flag

설명:
- Requirement는 “기본 요구사항 레코드”다
- 페이지/조항/좌표 등 근거 위치는 Requirement 본문에 직접 저장하지 않는다

### 9.5 RequirementSource
- requirement_source_id
- requirement_id
- source_excerpt_id
- link_type (PRIMARY / SUPPORTING)

설명:
- Requirement와 SourceExcerpt의 연결 엔티티
- 하나의 요구사항이 여러 원문 블록에 근거할 수 있다

### 9.6 RequirementInsight
- requirement_insight_id
- requirement_id
- fact_summary
- interpretation_summary
- intent_summary
- proposal_point
- implementation_approach
- expected_deliverables
- differentiation_point
- risk_note
- query_needed_flag
- fact_level
- generated_by_job_id

설명:
- AI 분석 결과 저장 전용
- 사람 승인 상태를 여기에 저장하지 않는다

### 9.7 RequirementReview
- requirement_review_id
- requirement_id
- review_status
- review_comment
- reviewed_by_user_id
- reviewed_at

설명:
- 사람 검토/승인 이력 전용
- AI 분석 결과와 별도 엔티티로 유지

### 9.8 ChecklistItem
- checklist_item_id
- project_id
- requirement_id
- item_type
- description
- current_status
- risk_level

### 9.9 ReviewLog
- review_log_id
- target_type
- target_id
- reviewer
- action
- comment
- created_at

---

## 10. 비동기 처리 설계
다음 작업은 비동기로 처리한다.
- OCR/레이아웃 분석
- 요구사항 추출
- 규칙 검증
- 검색 인덱스 반영

초기에는 단순한 작업 큐로 시작하고, 이후 필요 시 durable workflow로 확장한다.

---

## 11. 검색 설계
### 11.1 초기
- PostgreSQL Full Text Search
- 프로젝트 내 요구사항/과거 문구 검색

### 11.2 확장
- 벡터 검색/하이브리드 검색
- 유사 제안서, 유사 요구사항, 실적 문구 재사용

---

## 12. 신뢰성 설계
### 12.1 출력 구분
모든 분석 결과를 다음 세 계층으로 구분한다.
- 확정 정보
- 추론 정보
- 검토 필요 정보

### 12.2 근거 연결
- 페이지 번호
- 조항 번호
- 원문 일부
- 표/문단 위치

설명:
- 위 근거값은 `RequirementSource + SourceExcerpt` 조합으로 추적한다
- `page_no`, `clause_ref`를 Requirement 엔티티 자체에 중복 저장하지 않는다

### 12.3 사람 승인
- AI 결과는 초안
- 실무자 검토 후 승인
- 승인된 결과만 후속 단계에 사용

### 12.4 상태축 분리
- 분석 상태: EXTRACTED / ENRICHED / REVIEW_NEEDED
- 검토 상태: NOT_REVIEWED / IN_REVIEW / APPROVED / HOLD / NEEDS_UPDATE
- Fact Level: FACT / INFERENCE / REVIEW_NEEDED

---

## 13. 보안/운영
- 프로젝트 단위 접근제어
- 사용자/역할 기반 권한(RBAC)
- 파일 접근 통제
- 감사 로그
- 백업/복구
- API 인증
- 비밀값은 환경변수/시크릿 매니저 관리

---

## 14. 배포 구조 (초기)
- Next.js 프론트
- Spring Boot API
- Python AI 워커
- PostgreSQL
- S3
- 외부 AI API 연동

배포는 초기엔 단순하게 가져간다.
- App Runner 또는 동급 PaaS
- RDS for PostgreSQL
- S3
- 별도 워커 실행 환경

---

## 15. 개발 단계별 구조
### Phase 1
- 프로젝트/문서 업로드
- OCR 결과 저장
- 요구사항 리스트 생성

### Phase 2
- 요구사항 상세 분석
- 체크리스트 생성
- 검토/승인 UI

### Phase 3
- 유사 제안 검색
- 재사용 추천
- 질의응답 이력

---

## 16. 이번 구조에서 가장 중요한 포인트
1. OCR이 기초 체력이다.
2. RFP 분석은 자유서술이 아니라 구조화 데이터여야 한다.
3. AI만 믿지 않고 규칙 엔진과 사람 검토를 포함해야 한다.
4. 처음엔 단순한 구조로 시작해야 혼자서도 개발이 가능하다.
5. AI 분석 결과와 사람 검토 이력을 분리해야 추적성과 신뢰성이 올라간다.
