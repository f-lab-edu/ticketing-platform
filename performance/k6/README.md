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

```bash
curl -X POST http://localhost:8080/ticket-stocks/{ticketStockId} \
  -H "Content-Type: application/json" \
  -d '{
    "requestQuantity": 1
  }'
```

- ticketStockId : 차감 대상 티켓 재고 ID 
- requestQuantity : 차감할 재고 수량

### 기본 시나리오

- 여러 Virtual User(VU)가 동시에 재고 차감 요청
- 재고 수량 초과 요청 시 실패 처리
- 락 전략에 따라 처리 시간과 성공률 비교