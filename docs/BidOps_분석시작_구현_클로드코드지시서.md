# BidOps 분석 시작 구현 지시서

## 작업 목표
현재 `/projects/[id]/documents` 화면에서 업로드된 RFP 문서에 대해 **분석 시작**을 누르면 실제로 분석 Job이 생성되고, 문서/프로젝트 상태가 갱신되며, 사용자가 분석 진행상태와 결과 진입 경로를 확인할 수 있도록 구현한다.

이 작업은 단순 버튼 연결이 아니라 **문서 업로드 → 분석 시작 → Job 상태 추적 → 완료 후 결과 화면 진입** 흐름을 완성하는 작업이다.

---

## 반드시 지킬 원칙
- BidOps는 제안서 자동작성기가 아니라 **근거 기반 RFP 분석 시스템**이다.
- AI 분석 상태와 사람 검토 상태를 섞지 말 것.
- 상태값은 기존 명세 enum만 사용할 것.
- UI는 화려함보다 **상태/다음 액션/원문 근거 추적 가능성**이 우선이다.
- 기존 퍼블리싱/레이아웃을 임의로 갈아엎지 말고 필요한 범위만 보강할 것.

---

## 확인된 기준 문서
- API: `POST /projects/{projectId}/analysis-jobs`, `GET /projects/{projectId}/analysis-jobs/{jobId}`, `GET /projects/{projectId}/analysis-jobs` 사용 기준
- 문서 상태: `UPLOADED / PARSING / PARSED / FAILED`
- Job 상태: `PENDING / RUNNING / COMPLETED / FAILED`
- 프로젝트 상태: `DRAFT / READY / ANALYZING / REVIEWING / SUBMISSION_PREP / CLOSED`
- 사용자 흐름: 문서 업로드 후 분석 시작 → OCR/레이아웃 분석 → 요구사항 추출 완료 → 분석 결과 대시보드 진입

---

## 구현 범위

### 1) 프론트: 문서 목록 화면 분석 시작 액션 연결
대상 화면:
- `/projects/[id]/documents`

해야 할 일:
- 각 문서 Row의 `분석 시작` 액션을 실제 API 호출로 연결
- 호출 API:
  - `POST /api/v1/projects/{projectId}/analysis-jobs`
- 요청 body 기본값:
```json
{
  "documentId": "{selectedDocumentId}",
  "jobType": "RFP_PARSE"
}
```

버튼 동작 규칙:
- `UPLOADED` 또는 재분석 허용 상태에서만 실행 가능
- 이미 `PENDING`/`RUNNING` Job이 있으면 중복 실행 막기
- 실행 중에는 해당 Row 버튼 disabled + 로딩 상태 표시
- 성공 시 토스트 표시: `분석 작업을 시작했습니다.`
- 실패 시 에러 메시지 표시

---

### 2) 프론트: Job 상태 polling 구현
문서 탭에서 분석 시작 후 사용자가 상태를 볼 수 있어야 한다.

해야 할 일:
- Job 생성 성공 시 반환된 `jobId` 또는 최신 Job 목록 기준으로 polling 시작
- polling API:
  - `GET /api/v1/projects/{projectId}/analysis-jobs/{jobId}`
  - 또는 필요 시 `GET /api/v1/projects/{projectId}/analysis-jobs?documentId=...` 방식으로 클라이언트 보조 조회
- polling 간격: 3~5초
- 종료 조건:
  - `COMPLETED`
  - `FAILED`

UI 반영:
- 문서 상태 표시:
  - Job `PENDING/RUNNING` 동안 문서 상태를 화면에서 `PARSING` 또는 `분석중`으로 보이게 처리
  - 완료 시 `PARSED`
  - 실패 시 `FAILED`
- 프로젝트 상단 또는 우측 영역에 “분석 진행 중” 상태 박스 추가
- 상태 박스에 다음 정보 표시:
  - 현재 Job 상태
  - 시작 시각
  - 대상 문서명
  - 완료 후 이동 CTA

---

### 3) 프론트: 완료 후 결과 진입 UX 추가
분석 완료 후 사용자가 어디로 가야 하는지 명확해야 한다.

해야 할 일:
- Job 완료 시 CTA 노출:
  - `분석 결과 보기`
- 이동 우선순위:
  1. `/projects/[id]` 분석 대시보드
  2. 또는 `/projects/[id]/requirements`
- 문서 상세/우측 패널이 비어 있다면 아래 정보로 대체:
  - 선택 문서 기본 정보
  - 최신 분석 Job 상태
  - 최근 실행 이력
  - 분석 결과 보기 버튼

Empty/Placeholder 문구 예시:
- 분석 전: `문서를 업로드한 뒤 분석 시작을 누르면 요구사항 추출을 진행합니다.`
- 분석 중: `현재 문서 구조 분석과 요구사항 추출이 진행 중입니다.`
- 완료: `분석이 완료되었습니다. 결과 화면으로 이동해 요구사항을 검토하세요.`
- 실패: `분석에 실패했습니다. 오류 내용을 확인한 뒤 다시 실행하세요.`

---

### 4) 백엔드: 분석 시작 API 점검 및 보강
대상:
- `POST /api/v1/projects/{projectId}/analysis-jobs`
- `GET /api/v1/projects/{projectId}/analysis-jobs/{jobId}`
- `GET /api/v1/projects/{projectId}/analysis-jobs`

점검/보강 항목:
- `documentId`, `jobType=RFP_PARSE` 요청 정상 처리
- Job 생성 시 상태 기본값 `PENDING`
- 프로젝트 상태 `ANALYZING` 반영
- 문서 parse status 적절히 갱신
- 동일 문서에 대해 진행 중 Job 중복 생성 방지
- 응답 DTO에 프론트가 바로 쓸 수 있는 필드 포함:
  - `jobId`
  - `status`
  - `jobType`
  - `documentId`
  - `startedAt`
  - `finishedAt`
  - `errorMessage`

가능하면 목록 API도 문서별 최신 Job 1건을 쉽게 쓰도록 정리할 것.

---

### 5) 백엔드: 분석 완료 후 결과 저장 연결 확인
이 단계는 “버튼만 눌리는 척”이 아니라 실제 결과 화면으로 이어져야 한다.

최소 확인 항목:
- 분석 완료 후 `Requirement` 데이터가 저장되는지
- `RequirementInsight` 저장되는지
- 필요 시 `Checklist` 생성되는지
- 결과 화면에서 요구사항 수가 0이 아니도록 실제 저장 플로우 확인

만약 AI 워커/비동기 파이프라인이 아직 미완성이면 임시로라도 아래 둘 중 하나 구현:
1. mock parser/service로 샘플 Requirement 생성
2. 동기 fallback service로 최소 Requirement 1건 이상 생성

단, 임시 구현이라면 `TODO`, `stub`, `temporary fallback` 주석을 명확히 남길 것.

---

### 6) 상태값 정합성 정리
아래 상태축을 혼용하지 말 것.

- 프로젝트 상태
  - `DRAFT / READY / ANALYZING / REVIEWING / SUBMISSION_PREP / CLOSED`
- 문서 상태
  - `UPLOADED / PARSING / PARSED / FAILED`
- 분석 Job 상태
  - `PENDING / RUNNING / COMPLETED / FAILED`
- 요구사항 분석 상태
  - `EXTRACTED / ENRICHED / REVIEW_NEEDED`
- 요구사항 검토 상태
  - `NOT_REVIEWED / IN_REVIEW / APPROVED / HOLD / NEEDS_UPDATE`

프론트 배지는 위 enum 기준으로만 매핑할 것.
새로운 임의 문구 enum 만들지 말 것.

---

## UI 보강 가이드
기존 디자인 틀은 유지하되 아래만 보강.

### 문서 목록 테이블
권장 컬럼/표시:
- 파일명
- 유형
- 문서 상태
- 최신 Job 상태
- 버전
- 액션

액션 규칙:
- 분석 시작
- 진행중 표시
- 실패 시 재실행
- 삭제는 danger 스타일 유지

### 우측 상세 패널
선택 문서 기준으로 표시:
- 문서명
- 유형
- 업로드 일시
- 문서 상태
- 최신 Job 상태
- 최근 오류 메시지
- 분석 결과 보기 버튼

### 상단 요약 영역
가능하면 작은 summary badge/card 추가:
- 업로드 문서 수
- 분석중 문서 수
- 분석완료 문서 수
- 실패 문서 수

---

## 예외 처리
- 업로드된 RFP가 없는데 분석 시작 시도 → 버튼 비활성 또는 안내
- 지원하지 않는 문서 유형 → 실행 제한
- 같은 문서에 실행 중 Job 존재 → 중복 실행 차단
- Job 실패 → 오류 메시지 + 재실행 버튼
- 결과 저장 0건 → 실패 또는 검토 필요로 명확히 표시

---

## 완료 기준
아래가 되면 완료로 본다.

1. 문서 탭에서 `분석 시작` 클릭 시 실제 API 호출이 된다.
2. Job 상태가 화면에서 자동 갱신된다.
3. 분석 중/완료/실패 상태가 사용자에게 명확히 보인다.
4. 완료 후 `분석 결과 보기`로 자연스럽게 이동할 수 있다.
5. 결과 화면에서 실제 Requirement 데이터가 확인된다.
6. 기존 디자인/레이아웃이 불필요하게 깨지지 않는다.

---

## 산출물
작업 후 아래를 같이 정리해라.
- 변경 파일 목록
- API 연결한 화면 목록
- 상태 매핑표
- 남은 TODO
- mock/fallback 사용 여부
- 수동 테스트 결과

---

## 수동 테스트 시나리오
1. 프로젝트 진입
2. 문서 탭에서 RFP 1건 확인
3. `분석 시작` 클릭
4. 버튼 disabled 및 진행 상태 표시 확인
5. polling으로 Job 상태 변경 확인
6. 완료 후 `분석 결과 보기` 클릭
7. 요구사항 목록 또는 분석 대시보드에서 결과 존재 확인
8. 실패 케이스에서 재실행 가능한지 확인

---

## 작업 방식
- 기존 공통 컴포넌트/타입 최대 재사용
- ad-hoc string/status 남발 금지
- 프론트/백엔드 타입 불일치 정리
- 필요 시 최소 범위 리팩토링은 허용
- 하지만 화면 전체 갈아엎는 리디자인 금지

