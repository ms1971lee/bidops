#!/bin/bash
# MinIO Docker 기동 + S3 provider 테스트 스크립트
#
# 사전 조건: Docker Desktop 실행 중
#
# 사용법:
#   chmod +x docs/examples/test-s3-minio.sh
#   ./docs/examples/test-s3-minio.sh

set -e

echo "=== 1. MinIO 컨테이너 기동 ==="
docker run -d --name bidops-minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"

echo "MinIO 기동 대기 (5s)..."
sleep 5

echo ""
echo "=== 2. API 서버 기동 (S3 provider) ==="
echo "별도 터미널에서 실행:"
echo "  cd bidops-api"
echo "  STORAGE_PROVIDER=s3 \\"
echo "  STORAGE_BASE_URL=http://localhost:9000 \\"
echo "  STORAGE_BUCKET=bidops-dev \\"
echo "  STORAGE_ACCESS_KEY=minioadmin \\"
echo "  STORAGE_SECRET_KEY=minioadmin \\"
echo "  DB_PASSWORD=bidops1234 \\"
echo "  ./gradlew bootRun --args='--spring.profiles.active=local --spring.jpa.hibernate.ddl-auto=update'"
echo ""
echo "서버 기동 후 아래 테스트 실행:"
echo ""

echo "=== 3. 테스트 명령 ==="
cat << 'TESTEOF'
# 프로젝트 생성
P=$(curl -s -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"S3Test","client_name":"C","business_name":"B"}')
PID=$(echo "$P" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# PDF 업로드
echo "test pdf" > /tmp/s3-test.pdf
D=$(curl -s -X POST "http://localhost:8080/api/v1/projects/$PID/documents" \
  -F "file=@/tmp/s3-test.pdf" -F "type=RFP")
echo "Upload: $(echo $D | grep -o '"viewer_url":"[^"]*"')"

# viewer_url이 presigned URL인지 확인
VURL=$(echo "$D" | grep -o '"viewer_url":"[^"]*"' | cut -d'"' -f4)
echo "Presigned URL: $VURL"

# 파일 조회
curl -s -o /dev/null -w "Serve: HTTP %{http_code}\n" "$VURL"

# proxy 경로로도 조회
DID=$(echo "$D" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
SPATH=$(echo "$D" | grep -o '"storage_path":"[^"]*"' | cut -d'"' -f4)
curl -s -o /dev/null -w "Proxy: HTTP %{http_code}\n" "http://localhost:8080/api/v1/files/$SPATH"
TESTEOF

echo ""
echo "=== 정리 ==="
echo "  docker stop bidops-minio && docker rm bidops-minio"
