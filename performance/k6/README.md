## Ticket Service k6 부하 테스트

티켓 예매 서비스의 부하 테스트를 위한 k6 스크립트입니다.

## 1. k6 설치

### macOS (Homebrew)

```bash
brew install k6
```

### SSE 확장 빌드 (ticket-queue-sse.js 사용 시 필수)

k6 기본 바이너리에는 SSE 모듈이 포함되어 있지 않으므로, xk6로 빌드해야 합니다.

```bash
# Go 설치
brew install go

# xk6 설치
go install go.k6.io/xk6/cmd/xk6@latest

# SSE 확장 포함된 k6 빌드
~/go/bin/xk6 build --with github.com/phymbert/xk6-sse

# 빌드된 k6 확인 (현재 디렉토리에 생성됨)
./k6 version
```

## 2. 테스트 실행 전 준비사항

1. Docker Compose로 인프라 실행 (Redis, MySQL 등)
2. Spring Boot 애플리케이션 실행 (profile: performance-test)
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=performance-test'
   ```

## 3. 테스트 스크립트

### ticket-stock-direct.js

대기열 없이 직접 재고 차감 API를 호출하는 테스트입니다.

```bash
k6 run ticket-stock-direct.js
```

**용도:**
- 락 전략(pessimistic/optimistic/distributed) 성능 비교
- 단일 인스턴스 환경에서의 동시성 제어 검증

### ticket-queue-sse.js

SSE 기반 대기열 + 티켓 구매 플로우 테스트입니다.

```bash
# SSE 확장이 포함된 k6로 실행
./k6 run ticket-queue-sse.js
```

**테스트 플로우:**
1. `POST /queues/{concertId}/subscribe` - 대기열 등록 + SSE 연결
2. SSE에서 `enter` 이벤트 수신 대기
3. `enter` 수신 시 `POST /concerts/{concertId}/purchase` - 티켓 구매

## 4. 환경변수 옵션

### ticket-queue-sse.js

| 옵션 | 설명 | 기본값 |
|-----|------|-------|
| `VUS` | 동시 접속 사용자 수 | 300 |
| `DURATION` | 테스트 지속 시간 | 30s |
| `THINK_TIME` | enter 후 구매까지 대기 시간(초) | 10 |
| `CONCERT_ID` | 테스트할 콘서트 ID | 1 |
| `BASE_URL` | 서버 주소 | http://localhost:8080 |

**실행 예시:**
```bash
./k6 run ticket-queue-sse.js \
  -e VUS=500 \
  -e DURATION=1m \
  -e THINK_TIME=5 \
  -e CONCERT_ID=1
```

## 5. 커스텀 메트릭

### ticket-queue-sse.js

| 메트릭 | 설명 |
|-------|------|
| `sse_connections` | SSE 연결 수 |
| `enter_events_received` | enter 이벤트 수신 횟수 |
| `purchase_success` | 구매 성공 횟수 |
| `purchase_failed` | 구매 실패 횟수 |
| `time_to_enter` | SSE 연결부터 enter 이벤트까지 소요 시간 |
| `purchase_duration` | 구매 API 응답 시간 |

## 6. 테스트 API

### 직접 API (대기열 없음)
```bash
curl -X POST http://localhost:8080/concerts/{concertId}/purchase/direct \
  -H "Content-Type: application/json" \
  -d '{"requestQuantity": 1}'
```

### 대기열 기반 API
```bash
# 대기열 등록 + SSE 구독
curl -X POST http://localhost:8080/queues/{concertId}/subscribe \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"userId": "user123"}'

# 티켓 구매
curl -X POST http://localhost:8080/concerts/{concertId}/purchase \
  -H "Content-Type: application/json" \
  -d '{"userId": "user123", "requestQuantity": 1}'

# 대기열 취소
curl -X DELETE "http://localhost:8080/queues/{concertId}?userId=user123"
```
