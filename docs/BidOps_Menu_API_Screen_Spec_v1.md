# BidOps 메뉴별 API 명세서 및 화면정의서

버전 v1.0

## 1. 작성 원칙
- 프로젝트는 조직(Organization)과 프로젝트 멤버(ProjectMember) 기준으로 접근 제어한다.
- AI 분석 상태와 사람 검토 상태를 분리해서 화면과 API에 반영한다.
- 원문 근거는 Requirement/Checklist 본문에 중복 저장하지 않고 SourceExcerpt 기반으로 조회한다.
- 목록형 화면은 검색·필터·상태배지·빈상태·오류상태를 기본 포함한다.

## 2. 공통 API/권한 기준
| 구분 | 핵심 API | 비고 |
|---|---|---|
| 인증 | POST /auth/login, GET /auth/me | 로그인, 세션 확인, 권한 기반 라우팅 |
| 프로젝트 스코프 | GET /projects, GET /projects/{projectId} | 조직/멤버 권한 기준으로 자기 프로젝트만 노출 |
| 문서/근거 스코프 | GET /projects/{projectId}/documents/*, GET /source-excerpts/{id} | 프로젝트 하위 리소스는 모두 project scope 검사 |
| 상태 값 | ProjectStatus / DocumentParseStatus / RequirementAnalysisStatus / RequirementReviewStatus / ChecklistStatus / RiskLevel | 화면 배지와 필터에 동일하게 사용 |

## 3. 메뉴별 화면정의 및 API 매핑

### 1. 로그인
- 권장 라우트: `/login`
- 목적: 사용자 인증과 초기 진입
- 주요 사용자: 전체
- 추가 검토/TODO: 회원가입/조직 등록 플로우 정의 필요
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 브랜드 영역<br>이메일/비밀번호 입력<br>로그인 버튼<br>오류 메시지<br>비밀번호 찾기 링크 | 로그인<br>비밀번호 찾기<br>세션 유지 | POST /auth/login<br>GET /auth/me | 기본<br>입력 오류<br>인증 실패<br>권한 없음 |

### 2. 대시보드
- 권장 라우트: `/dashboard`
- 목적: 전체 프로젝트 진행 현황과 리스크 요약 확인
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER
- 추가 검토/TODO: 프로젝트 summary 집계 API 보강 권장
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 요약 KPI 카드<br>최근 프로젝트<br>검토 필요/위험 항목<br>바로가기 CTA | 프로젝트 진입<br>검토 필요 항목 보기<br>새 프로젝트 이동 | GET /projects?page=1&size=...<br>GET /projects/{projectId}<br>요약 집계 API(추가 권장) | 기본<br>로딩<br>빈 상태<br>집계 없음 |

### 3. 프로젝트 목록
- 권장 라우트: `/projects`
- 목적: 사용자에게 허용된 프로젝트만 검색/필터링
- 주요 사용자: 전체
- 추가 검토/TODO: 조직/멤버 스코프가 API에서 일관되게 강제되는지 점검
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 검색<br>상태 필터<br>프로젝트 테이블/카드<br>최근 수정일<br>문서 수/요구사항 수<br>새 프로젝트 버튼 | 프로젝트 생성<br>프로젝트 상세 이동<br>상태 필터링 | GET /projects?page&size&keyword&status<br>POST /projects | 기본<br>빈 상태<br>권한 없음<br>생성 성공 |

### 4. 프로젝트 상세(개요)
- 권장 라우트: `/projects/[id]`
- 목적: 프로젝트 기본정보, 최근 활동, 분석 진입 허브
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
- 추가 검토/TODO: 프로젝트 summary/최근 활동 응답 정리 필요
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 프로젝트 개요 카드<br>상태 배지<br>최근 분석 Job<br>멤버 요약<br>문서/분석/체크리스트 바로가기 | 상태 변경<br>문서 화면 이동<br>분석 결과 대시보드 이동 | GET /projects/{projectId}<br>PATCH /projects/{projectId}<br>POST /projects/{projectId}/status<br>GET /projects/{projectId}/analysis-jobs | 기본<br>권한 없음<br>프로젝트 없음 |

### 5. 문서함
- 권장 라우트: `/projects/[id]/documents`
- 목적: RFP 및 부속문서 업로드/버전/파싱 상태 관리
- 주요 사용자: OWNER, ADMIN, EDITOR, VIEWER(조회)
- 추가 검토/TODO: 드래그앤드롭 업로드, 업로드 실패 재시도 UX 보강
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 문서 목록<br>문서 유형 태그<br>parseStatus 배지<br>업로드 버튼<br>분석 시작 버튼<br>page_count/버전 정보 | 문서 업로드<br>문서 삭제<br>버전 보기<br>분석 시작/재실행 | POST /projects/{projectId}/documents<br>GET /projects/{projectId}/documents<br>GET /projects/{projectId}/documents/{documentId}<br>DELETE /projects/{projectId}/documents/{documentId}<br>GET /projects/{projectId}/documents/{documentId}/versions<br>POST /projects/{projectId}/analysis-jobs | 업로드 전<br>UPLOADED<br>PARSING<br>PARSED<br>FAILED |

### 6. 분석 결과 대시보드
- 권장 라우트: `/projects/[id]/analysis`
- 목적: 분석 결과 총량·리스크·검토 우선순위 파악
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
- 추가 검토/TODO: 대시보드 통계 최적화 및 집계 전용 API 권장
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 요구사항 총량<br>분류별 개수<br>필수 제출물 요약<br>누락 위험<br>질의 필요 항목<br>최근 검토 항목 | 요구사항 목록 이동<br>체크리스트 이동<br>리스크 항목 필터 적용 | GET /projects/{projectId}<br>GET /projects/{projectId}/requirements<br>GET /projects/{projectId}/checklists<br>GET /projects/{projectId}/analysis-jobs<br>요약 통계 API(추가 권장) | 분석 전<br>분석 중<br>분석 완료<br>결과 없음 |

### 7. 요구사항 목록
- 권장 라우트: `/projects/[id]/requirements`
- 목적: 추출된 요구사항을 필터/정렬/검색하며 검토
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
- 추가 검토/TODO: 서버 정렬/필터와 UI 배지 일관성 유지
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| FilterBar<br>테이블(ID/제목/분류/필수/증빙/분석상태/검토상태/FactLevel/질의필요/근거페이지)<br>행 클릭 상세 이동 | 검색<br>분류/상태 필터<br>상세 열기 | GET /projects/{projectId}/requirements?page&size&category&mandatory&evidenceRequired&analysisStatus&reviewStatus&factLevel&queryNeeded&keyword | 기본<br>결과 없음<br>권한 없음 |

### 8. 요구사항 상세
- 권장 라우트: `/projects/[id]/requirements/[requirementId]`
- 목적: 원문 근거·AI 분석·사람 검토를 한 화면에서 검토
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
- 추가 검토/TODO: SourceExcerpt 벌크 조회, review 이력 표시 강화
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 좌측 PDF/근거<br>중앙 구조화 분석<br>우측 검토/이력<br>근거 클릭 하이라이트<br>review 액션 | 분석 수정<br>검토 상태 변경<br>근거 보기<br>질의 필요 표시 | GET /projects/{projectId}/requirements/{requirementId}<br>GET /projects/{projectId}/requirements/{requirementId}/sources<br>GET /projects/{projectId}/requirements/{requirementId}/analysis<br>PATCH /projects/{projectId}/requirements/{requirementId}/analysis<br>GET /projects/{projectId}/requirements/{requirementId}/review<br>POST /projects/{projectId}/requirements/{requirementId}/review-status<br>GET /source-excerpts/{id} | 기본<br>근거 없음<br>review 없음<br>analysis 없음<br>권한 없음 |

### 9. 체크리스트
- 권장 라우트: `/projects/[id]/checklists`
- 목적: 제출서류/증빙/평가반영 누락 방지
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
- 추가 검토/TODO: keyword, ownerUserId 필터 프론트 UX 정교화 / 벌크 근거 조회 검토
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 유형 탭<br>항목 테이블<br>상태/위험도 배지<br>담당자<br>근거 링크<br>인라인 PDF/원문 보기 | 상태 변경<br>담당자 지정<br>근거 보기<br>자동 생성 | GET /projects/{projectId}/checklists?type&status&ownerUserId&mandatoryOnly<br>POST /projects/{projectId}/checklists<br>PATCH /projects/{projectId}/checklists/{checklistItemId}<br>POST /projects/{projectId}/checklists/generate<br>GET /source-excerpts/{id} | 기본<br>TODO<br>IN_PROGRESS<br>DONE<br>BLOCKED |

### 10. 질의응답
- 권장 라우트: `/projects/[id]/queries`
- 목적: 모호하거나 충돌하는 조항에 대한 질의 관리
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
- 추가 검토/TODO: MVP 포함 여부 최종 결정 및 화면 우선순위 조정 필요
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 질의 목록<br>상태 배지<br>관련 요구사항<br>초안 생성 버튼<br>답변 이력 | 질의 저장<br>질의 초안 생성<br>상태 변경 | GET /projects/{projectId}/queries<br>POST /projects/{projectId}/queries<br>POST /projects/{projectId}/queries/generate<br>PATCH /projects/{projectId}/queries/{queryId} | DRAFT<br>REVIEWING<br>SENT<br>ANSWERED<br>CLOSED |

### 11. 산출물
- 권장 라우트: `/projects/[id]/artifacts`
- 목적: 제안 산출물 목록, 상태, 버전 관리
- 주요 사용자: OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
- 추가 검토/TODO: MVP에서는 최소 목록/상태 중심으로 간소화 권장
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 산출물 목록<br>상태 배지<br>버전/업로드 시각<br>생성 버튼 | 산출물 생성<br>상태 변경<br>버전 보기 | GET /projects/{projectId}/artifacts<br>POST /projects/{projectId}/artifacts<br>PATCH /projects/{projectId}/artifacts/{artifactId}<br>GET /projects/{projectId}/artifacts/{artifactId}/versions | 기본<br>결과 없음 |

### 12. 관리자/설정
- 권장 라우트: `/admin, /settings`
- 목적: 사용자/권한/조직/시스템 설정 관리
- 주요 사용자: OWNER, ADMIN
- 추가 검토/TODO: 프로젝트 멤버 초대/권한 변경 API와 관리자 화면 상세 정의 필요
| 구성 요소 | 주요 기능/액션 | 필요 API | 상태 |
|---|---|---|---|
| 조직 정보<br>프로젝트 멤버/권한<br>감사 로그<br>시스템 설정<br>사용자 관리 | 권한 변경<br>멤버 초대<br>로그 확인 | GET /projects/{projectId}/audit-logs<br>프로젝트 멤버 초대/변경 API(후속)<br>조직/사용자 관리 API(추가 필요) | 권한 없음<br>기본 |

## 4. 메뉴 우선순위 제안
| 우선순위 | 메뉴 | 비고 |
|---|---|---|
| P0 | 로그인, 프로젝트 목록, 프로젝트 상세, 문서함, 분석 결과 대시보드 | MVP 메인 흐름 |
| P1 | 요구사항 목록, 요구사항 상세, 체크리스트 | 핵심 분석/검토 기능 |
| P2 | 질의응답, 산출물, 관리자/권한 | 운영 및 확장 기능 |

## 5. 설계 메모
- 프로젝트 목록 API는 반드시 조직/프로젝트 멤버 기준으로 자기 프로젝트만 반환해야 한다.
- 회원가입/조직 등록 플로우가 아직 고정되지 않았으므로 auth/register, organization 생성/선택 정책을 별도 문서로 확정해야 한다.
- 대시보드와 프로젝트 상세는 집계 전용 API가 있으면 프론트 성능과 단순성이 좋아진다.
- 체크리스트와 요구사항 상세에서 사용하는 SourceExcerpt/PDF 근거 UX는 공통 컴포넌트로 재사용하는 것이 좋다.