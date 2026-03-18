# BidOps

BidOps는 공공/민간 입찰·제안 업무에서 제안요청서(RFP)를 구조적으로 분석하고, 요구사항 누락을 방지하며, 제안서 작성과 수주 운영을 지원하는 **근거 기반 문서 운영 AI OS**입니다.

## 핵심 목표
- RFP를 요구사항 단위로 구조화 분석
- 원문 근거와 페이지 기반으로 결과 제시
- 필수 제출물/증빙/리스크 누락 방지
- 사람 검토가 가능한 신뢰형 분석 흐름 제공
- 과거 제안서/실적/산출물 재사용 기반 구축

## 프로젝트 원칙
1. 생성보다 **검증 가능한 분석**을 우선한다.
2. 모든 분석은 가능한 한 **원문 근거**와 연결한다.
3. AI 해석과 사실 추출을 구분한다.
4. 누락 탐지, 충돌 탐지, 모호성 탐지를 핵심 가치로 둔다.
5. 최종 제출 판단은 반드시 사람이 검토한다.

## 권장 폴더 구조
```text
bidops/
├─ README.md
├─ CLAUDE.md
├─ docs/
│  ├─ PROJECT_BRIEF.md
│  ├─ PRD.md
│  ├─ ARCHITECTURE.md
│  ├─ RFP_ANALYSIS_RULEBOOK.md
│  ├─ UI_GUIDELINES.md
│  ├─ API_SPEC.md
│  ├─ DB_ERD.md
│  └─ USER_FLOW.md
├─ prompts/
│  ├─ RFP_ANALYSIS_PROMPT.md
│  ├─ REVIEW_PROMPT.md
│  └─ SCREEN_PROMPT.md
├─ app/
├─ worker/
└─ infra/
```

## 중요 메모
현재 `CLAUDE.md`를 `docs/` 아래에 두셨다면, **Claude Code가 프로젝트 지침을 세션 시작 시 자동으로 읽게 하려면 `./CLAUDE.md` 또는 `./.claude/CLAUDE.md` 위치로 옮기는 것이 좋습니다.** Anthropic 공식 문서는 프로젝트 지침 파일 위치를 이 두 경로로 안내합니다.

## 문서 역할
### docs/
- `PROJECT_BRIEF.md`: 프로젝트 비전, 범위, MVP, 핵심 가치
- `PRD.md`: 제품 요구사항, 기능 범위, 목표/비목표
- `ARCHITECTURE.md`: 시스템 구조, 모듈 역할, 기술 방향
- `RFP_ANALYSIS_RULEBOOK.md`: RFP 분석 기준과 신뢰성 원칙
- `UI_GUIDELINES.md`: 화면/디자인/퍼블리싱 기준
- `API_SPEC.md`: API 설계 원칙과 엔드포인트 초안
- `DB_ERD.md`: 데이터 모델과 엔티티 관계
- `USER_FLOW.md`: 사용자 흐름과 상태 전이

### prompts/
- `RFP_ANALYSIS_PROMPT.md`: RFP 요구사항 구조화 분석 프롬프트
- `REVIEW_PROMPT.md`: 분석/초안 검토 및 누락 점검 프롬프트
- `SCREEN_PROMPT.md`: 화면 정의와 UI 설계 프롬프트

### app/
- 프론트엔드 및 백엔드 애플리케이션 코드

### worker/
- OCR 후처리, 문서 구조화, 요구사항 추출, 배치/비동기 작업

### infra/
- 배포 설정, 환경 구성, 인프라 스크립트

## 추천 기술 조합
- 개발: Claude Code + IntelliJ
- OCR/문서 구조화: Azure Document Intelligence
- RFP 분석 주엔진: OpenAI GPT-5.4
- 검증: 규칙 엔진 + 사람 검토
- 프론트엔드: Next.js
- 백엔드: Spring Boot
- AI 워커: Python
- DB: PostgreSQL
- 파일 저장: S3 또는 동급 객체 스토리지

## 개발 시작 순서
1. `CLAUDE.md`를 프로젝트 루트로 이동
2. `docs/`와 `prompts/` 문서 최신화
3. `app/` 기본 모듈 구조 생성
4. 로그인 / 프로젝트 생성 / 문서 업로드 기능 구현
5. OCR/레이아웃 분석 파이프라인 연결
6. 요구사항 구조화 추출 및 저장
7. RFP 분석 결과 화면 구현
8. 누락/증빙 체크리스트 구현

## 각 AI에 공유하는 권장 방식
- **Claude Code**: 프로젝트 루트의 `CLAUDE.md` + 로컬 레포 직접 연결
- **ChatGPT Projects**: `docs/` 핵심 문서 업로드 + 프로젝트 지침 입력
- **Gemini Gem**: `docs/` 핵심 문서 또는 Google Drive 원본 연결 + 전용 instructions 설정

## 첫 번째 체크포인트
아래 3가지가 동작하면 MVP의 뼈대가 잡힌 것입니다.
- 프로젝트 생성
- RFP PDF 업로드
- 분석 결과 화면에서 요구사항/근거/체크리스트 표시
