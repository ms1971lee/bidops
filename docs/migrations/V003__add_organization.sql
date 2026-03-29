-- Organization 테이블 생성
CREATE TABLE IF NOT EXISTS organizations (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    business_number VARCHAR(20),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    created_by   VARCHAR(36),
    updated_by   VARCHAR(36)
);
