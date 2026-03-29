-- ============================================================
-- V002: documents 테이블에 page_count 컬럼 추가
--
-- AI 워커가 PDF 파싱 완료 시 전체 페이지 수를 저장한다.
-- 프론트 PDF 뷰어의 페이지 이동 UI (p.N/M) 에 사용된다.
--
-- 적용 대상: documents
-- 작성일: 2026-03-25
-- ============================================================

-- ■ 1단계: 컬럼 추가 (nullable — 기존 레코드는 NULL 유지)
ALTER TABLE documents ADD COLUMN page_count INTEGER NULL;

-- ■ 2단계: 이미 PARSED 상태인 문서가 있다면
--          운영 환경에서 별도 배치로 채울 수 있음 (선택)
-- UPDATE documents SET page_count = ... WHERE parse_status = 'PARSED';

-- ■ 3단계: 적용 후 검증
-- 3-1. 컬럼 존재 확인
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'documents' AND column_name = 'page_count';

-- 3-2. NULL 분포 확인
SELECT
    COUNT(*) AS total,
    COUNT(page_count) AS has_page_count,
    COUNT(*) - COUNT(page_count) AS null_page_count
FROM documents;
