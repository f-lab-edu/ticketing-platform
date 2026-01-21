## Ticket Stock API k6 Test

이 디렉토리는 티켓 예매 서비스의 **락 전략별 성능 테스트**를 위해  
k6를 사용한 부하 테스트 스크립트를 관리합니다.

## 1. k6 설치

### macOS (Homebrew)

```bash
brew install k6
```

### 설치 확인

```bash
k6 version
```


## 2. 테스트 실행 전 준비사항

1. Spring Boot 애플리케이션 실행 (profile: performance-test)
2. Docker Compose로 인프라 실행
3. 락 전략 설정 (application-performance-test.yml)
```yaml
ticket:
  lock:
    strategy: distributed
```

## 3. 테스트 실행 방법

### 기본 실행
```bash
k6 run ticket-stock.js
```

### 테스트 대상 API

#### 1. 직접 API (대기열 없음)
```bash
curl -X POST http://localhost:8080/ticket-stocks/{ticketStockId}/direct \
  -H "Content-Type: application/json" \
  -d '{
    "requestQuantity": 1
  }'
```

- 대기열 검증 없이 직접 재고 차감
- 락 전략(pessimistic/optimistic/distributed)별 성능 비교용
- 성능 테스트 및 개발 환경에서 사용

#### 2. 대기열 기반 API (프로덕션)
```bash
# 대기열 등록
curl -X POST http://localhost:8080/queues/{ticketStockId}/enter \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123"
  }'

# 대기열 상태 조회
curl -X GET "http://localhost:8080/queues/{ticketStockId}/status?userId=user123"

# 티켓 구매 (입장 가능한 경우)
curl -X POST http://localhost:8080/ticket-stocks/{ticketStockId} \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "requestQuantity": 1
  }'
```

## 4. 테스트 시나리오

### 시나리오 1: 직접 API 부하 테스트 (대기열 없음)

단일 인스턴스 기반 락 전략 성능 테스트

```bash
k6 run ticket-stock-direct.js
```

**테스트 내용:**
- 100명의 동시 사용자가 10초간 재고 차감 요청
- 대기열 검증 없이 직접 `TicketStockService` 호출
- 락 전략별 처리 시간과 성공률 비교

**사용 사례:**
- 락 전략(pessimistic/optimistic/distributed) 성능 비교
- 단일 인스턴스 환경에서의 동시성 제어 검증

### 시나리오 2: 대기열 기반 API 부하 테스트

프로덕션 환경과 동일한 대기열 플로우 테스트

```bash
k6 run ticket-stock-queue.js
```

**테스트 내용:**
- 대기열 등록 → 상태 폴링 → 구매 전체 플로우
- Redis 대기열 동작 및 성능 검증
- 입장 가능한 사용자만 구매 진행

**사용 사례:**
- Redis 대기열 시스템 성능 측정
- 프로덕션 환경 시뮬레이션

### 기본 시나리오

- 여러 Virtual User(VU)가 동시에 재고 차감 요청
- 재고 수량 초과 요청 시 실패 처리
- 락 전략에 따라 처리 시간과 성공률 비교