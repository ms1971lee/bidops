# BidOps 문서 업로드 정책

- 작성일: 2026-03-18
- 적용 범위: MVP

---

## 1. 허용 파일 형식

| 형식 | 허용 여부 | 비고 |
|---|---|---|
| PDF (.pdf) | **허용** | 유일한 업로드 형식 |
| HWP (.hwp) | 불허 | 사용자가 PDF로 변환 후 업로드 |
| HWPX (.hwpx) | 불허 | 사용자가 PDF로 변환 후 업로드 |
| DOC/DOCX | 불허 | MVP 범위 밖 |
| 이미지 (PNG/JPG) | 불허 | MVP 범위 밖 |

---

## 2. 정책 근거

1. **OCR/파싱 파이프라인 단순화**: Azure Document Intelligence가 PDF를 기본 지원하므로, 파이프라인을 PDF 단일 경로로 유지한다.
2. **페이지 기반 구조 일관성**: SourceExcerpt의 pageNo, DocumentPage 구조가 PDF 페이지와 1:1 매핑된다.
3. **HWP 파서 복잡도 회피**: HWP/HWPX 파싱은 별도 라이브러리가 필요하고 레이아웃 추출 품질이 불안정하다.
4. **운영 현실성**: 공공기관 RFP도 PDF 배포가 일반적이며, HWP 원본만 있는 경우 한컴오피스/웹 변환으로 PDF 생성이 용이하다.

---

## 3. 검증 위치

| 계층 | 검증 방식 |
|---|---|
| 프론트엔드 | `<input accept=".pdf">` + 파일명 확장자 체크 + 안내 문구 |
| 백엔드 | `DocumentServiceImpl.uploadDocument()` — 확장자 `.pdf` 체크, 위반 시 400 BAD_REQUEST |

---

## 4. 사용자 안내 문구

> PDF 파일만 업로드할 수 있습니다. HWP/HWPX 문서는 PDF로 변환 후 업로드해 주세요.

이 문구는 문서 업로드 화면에 상시 노출한다.

---

## 5. Future Scope (MVP 이후)

- [ ] HWP/HWPX → PDF 서버사이드 자동 변환 (libreoffice headless 또는 한컴 변환 API)
- [ ] DOC/DOCX 지원
- [ ] 이미지 기반 RFP 스캔본 직접 업로드 (OCR 전용 경로)
- [ ] 파일 크기/페이지 수 제한 정책 추가
