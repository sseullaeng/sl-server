# 쓸랭 프로덕션 배포 가이드

> Docker Compose 기반. 호스트 (GCP Compute Engine) 에 nginx 가 80/443 → app:8080 으로 reverse proxy.

---

## 1. 사전 준비

### 1.1 호스트 (GCP Compute Engine 기준)
- e2-medium (2 vCPU / 4GB RAM) — JVM heap + 3개 DB 컨테이너 빠듯하지만 동작
- Debian 12 / Ubuntu 22.04+
- 정적 외부 IP 예약 (인스턴스 재시작 시 IP 보존)
- 디스크 30GB 이상 권장 (10GB 는 Docker image + 로그로 빠르게 참)
- 방화벽: 80/443/22 만 허용 (GCP Network tags `http-server`, `https-server` 자동 적용)
- Docker + Docker Compose plugin 설치
  ```bash
  # Debian/Ubuntu
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker $USER   # 재로그인 필요
  ```
- nginx 설치 (호스트 패키지 — 컨테이너 외부)
  ```bash
  sudo apt install -y nginx
  ```

### 1.1a 도메인 — DuckDNS (무료) 또는 정식 도메인
- DuckDNS: https://www.duckdns.org/ 가입 → `sseulang.duckdns.org` 같은 sub 발급 → IP 매핑 → 즉시 사용
- 정식 도메인: 가비아/Namecheap 구입 후 DNS A 레코드 → 외부 IP
- ⚠️ Let's Encrypt SSL 은 도메인 필수 (IP 발급 X)

### 1.2 외부 서비스 키 발급 (.env.prod 채우기 전)
- **JWT_SECRET**: `openssl rand -hex 64`
- **카카오**: 콘솔 → 앱 키 → REST API 키
  - 사이트 도메인: 프론트 도메인 (예: `https://sseulang.vercel.app`)
  - Redirect URI: 프론트 라우팅 (예: `https://sseulang.vercel.app/auth/oauth2/kakao/callback`)
- **구글 OAuth**: Google Cloud Console → OAuth 2.0 클라이언트
  - 승인된 JavaScript 출처: `https://sseulang.vercel.app`
  - 승인된 redirect URI: `https://sseulang.vercel.app/auth/oauth2/google/callback`
- **AWS IAM**: S3 PutObject/GetObject/DeleteObject/CopyObject 권한 (특정 버킷 한정)
- **토스페이먼츠**: 운영 키 (테스트 키 X). webhook secret 도 콘솔에서 발급
  - webhook URL: `https://{백엔드 도메인}/api/v1/payments/webhook/toss`
- **SMTP**: Gmail App Password / AWS SES / Naver SMTP 중 택

---

## 2. 코드 + 환경 설정

```bash
# 1. 프로젝트 clone
git clone git@github.com:sseullaeng/sl-server.git
cd sl-server

# 2. 환경변수 파일 복사 + 채우기
cp .env.prod.example .env.prod
vim .env.prod   # 모든 [필수] 항목 채움

# 3. 권한 — 다른 사용자 읽기 차단 (비밀키 노출 방지)
chmod 600 .env.prod
```

`.env.prod` 미채움 항목 확인 — 필수 변수에 `:?required` 가드가 걸려 있어 빠지면 컨테이너가 즉시 실패합니다.

---

## 3. 빌드 + 기동

### 3.1 Image 빌드
```bash
docker build -t sseulang-backend:latest .
```
- Multi-stage build — Gradle 8 + JDK 21 → JRE 21 alpine
- Asia/Seoul TZ 강제 (`-Duser.timezone=Asia/Seoul`)
- 비특권 user (`sseulang`, uid 1000)
- HEALTHCHECK: `/actuator/health`

### 3.2 Compose 기동
```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
docker compose -f docker-compose.prod.yml ps
```
서비스:
- `sseulang-mysql`   :3306 (internal only)
- `sseulang-redis`   :6379 (internal only)
- `sseulang-mongo`   :27017 (internal only)
- `sseulang-app`     :8080 (host loopback 만 노출 — `127.0.0.1:8080`)

전부 healthy 확인:
```bash
docker compose -f docker-compose.prod.yml ps --format json | jq '.[].Health'
```

### 3.3 첫 기동 검증
```bash
# 직접 접근 (호스트에서)
curl -fsS http://127.0.0.1:8080/actuator/health
# → {"status":"UP"}

# 로그
docker logs -f sseulang-app
# Flyway 마이그레이션 V1 ~ V11 적용 + Tomcat 8080 LISTEN 확인
```

---

## 4. nginx reverse proxy

`/etc/nginx/conf.d/sseulang.conf`:
```nginx
# webhook 전용 IP 별 rate limit zone — 결제 webhook DoS 방어 (Codex 게이트 2 W5/W10).
# zone 크기 10MB ≈ 16만 IP 정도 추적 가능. burst 5 + nodelay 로 정상 retry 흐름은 통과.
limit_req_zone $binary_remote_addr zone=toss_webhook:10m rate=10r/s;

server {
    listen 80;
    server_name sseulang.com www.sseulang.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name sseulang.com www.sseulang.com;

    # Let's Encrypt 인증서 (#91 작업)
    ssl_certificate     /etc/letsencrypt/live/sseulang.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sseulang.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    # WebSocket 업그레이드
    location /ws-stomp {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade           $http_upgrade;
        proxy_set_header   Connection        "upgrade";
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_read_timeout 3600s;
    }
    location /ws-stomp-native {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade           $http_upgrade;
        proxy_set_header   Connection        "upgrade";
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_read_timeout 3600s;
    }

    # 토스 결제 webhook 전용 — body 크기 16KB 제한 + IP 별 rate limit + 짧은 timeout.
    # 토스 retry 정상 흐름(같은 transmission-id)은 burst 안에서 통과, 폭주 공격은 zone 한도에서 차단.
    location = /api/v1/payments/webhook/toss {
        limit_req            zone=toss_webhook burst=5 nodelay;
        client_max_body_size 16k;
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 30s;
    }

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 30s;
        client_max_body_size 10m;   # S3 presigned 업로드 X — 백엔드 직접 업로드 없음. 여유 설정.
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

---

## 5. 갱신 / 롤백

### 갱신
```bash
git pull
docker build -t sseulang-backend:$(git rev-parse --short HEAD) .
APP_IMAGE_TAG=$(git rev-parse --short HEAD) \
  docker compose -f docker-compose.prod.yml --env-file .env.prod up -d app
# DB 컨테이너는 영향 X — app 만 새 image 로 교체
```

### 롤백
```bash
APP_IMAGE_TAG=<이전-sha> \
  docker compose -f docker-compose.prod.yml --env-file .env.prod up -d app
```

### Flyway 롤백 시 주의
Flyway 는 forward-only — 마이그레이션 거꾸로 돌리는 자동 도구 없음. 스키마 변경 동반 롤백은 사전 백업 + 수동 SQL.

---

## 6. 백업 / 운영

### MySQL dump (cron 권장)
```bash
docker exec sseulang-mysql mysqldump \
  -u root -p$MYSQL_ROOT_PASSWORD \
  --single-transaction --quick sseulang > backup-$(date +%F).sql
```

### Mongo dump
```bash
docker exec sseulang-mongo mongodump \
  --username sseulang --password $MONGO_PASSWORD \
  --authenticationDatabase admin --db sseulang \
  --archive | gzip > mongo-$(date +%F).gz
```

### Redis 는 RDB 자동 (`--appendonly yes`)
- 컨테이너 volume `redis-data` 가 호스트에 영속.
- AOF 만으로도 RT/blacklist 복원 가능.

---

## 7. 흔한 이슈

| 증상 | 해결 |
|---|---|
| `mysql: Driver loading was attempted ...` | DB_URL 의 timezone parameter 누락. `serverTimezone=Asia/Seoul` 포함 |
| 컨테이너 즉시 종료 + `required` 메시지 | `.env.prod` 의 `:?required` 변수 누락 |
| Flyway `Validate failed` | local 에서 미적용 마이그 prod 에 먼저 들어가 hash mismatch — 보통 `baseline-on-migrate: true` 로 자동 해결 |
| `503 from nginx` | app 컨테이너 healthy 안 됐거나 포트 차이. `docker logs sseulang-app` 확인 |
| `Cookie SameSite=Strict 인데 OAuth 쿠키 안 박힘` | 외부 redirect 후 첫 요청 — Lax 가 아니면 누락. SDK redirect URI 도메인이 동일 site 인지 확인 |
| Toss webhook 타임아웃 | nginx `proxy_read_timeout` ≥ 30s. 토스 default 30s 재시도 |

---

## 8. Swagger prod 노출 정책

기본값: **비활성** (`APP_SWAGGER_ENABLED=false`).

- `/swagger-ui.html`, `/v3/api-docs/**` 모두 404 (springdoc 자체 차단)
- 외부 API 검수 / 응급 디버그 시에만 일시 켜기:
  ```bash
  # .env.prod 에서 일시 변경 후 재기동
  sed -i 's/APP_SWAGGER_ENABLED=false/APP_SWAGGER_ENABLED=true/' .env.prod
  docker compose -f docker-compose.prod.yml --env-file .env.prod up -d app
  # 사용 끝나면 즉시 false 로 되돌리기
  ```
- IP 제한이 필요하면 nginx `location = /swagger-ui.html { allow <admin-ip>; deny all; }` 추가 권장.

---

## 9. CORS 도메인 갱신 (Vercel 호스팅)

프론트가 Vercel 사용 (2026-05-05). 도메인 확정되면 `.env.prod` 의 `CORS_ALLOWED_ORIGINS` 콤마 추가:

```bash
# 예시
CORS_ALLOWED_ORIGINS=https://sseulang.com,https://www.sseulang.com,https://sseulang.vercel.app
```

⚠️ Vercel **preview 배포** 도메인은 PR 별로 다름 (`sseulang-git-{branch}-{team}.vercel.app`). 모든 preview 허용하려면:
- 옵션 a) 정확 매칭만 → 프론트가 preview 환경에서 백엔드 호출 X (mock API 사용)
- 옵션 b) `CorsConfigurationSource` 에 패턴 매칭 추가 (코드 변경, follow-up)

현재 옵션 (a) — preview 는 prod API 호출 X 가정. 합의 필요 시 옵션 (b) 추가.

재기동:
```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d app
```

---

## 10. 다음 단계 (이 문서 외)

- [ ] **#91** EC2 인스턴스 생성 + Let's Encrypt (`certbot --nginx`)
- [ ] CI/CD — GitHub Actions 로 image build → ECR/Docker Hub push → SSH 갱신 (선택)
- [ ] CloudWatch 또는 Loki 로 로그 수집 (선택)
- [ ] CORS preview 도메인 패턴 매칭 (Vercel 결정 후, 선택)
