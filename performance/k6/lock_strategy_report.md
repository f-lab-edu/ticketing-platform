# 티켓 재고 차감 락 전략별 성능 테스트 보고서

## 1. 테스트 목적
동일한 티켓 재고(Single Row)에 대한 동시성 제어 전략별 처리량(TPS) 및 안정성 비교.

## 2. 테스트 환경
- **Application**: Spring Boot 3.5.9 (Embedded Tomcat)
- **Database**: MySQL 8.0 (Docker)
- **Cache**: Redis 7.2 (Redisson 사용)
- **Infrastructure 기본값**:
    - HikariCP Max Pool Size: 10
    - Tomcat Max Threads: 200
- **Test Tool**: k6 v1.5.0

## 3. 테스트 시나리오
- **VUs**: 100
- **Duration**: 10s
- **Think Time**: 0.1s sleep
- **Target**: 단일 티켓(ID: 1) 재고 1개 차감 API

## 4. 테스트 결과 (Summary)

| 전략 | TPS | P95 Latency | 성공률 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **Pessimistic** | **364.9** | **256.8ms** | 100% | 최상의 성능 |
| **Synchronized** | 242.0 | 599.0ms | 100% | JVM 모니터 락 병목 |
| **Optimistic** | 243.3 | 597.3ms | 97.2% | **충돌 시 실패 발생** |
| **Distributed** | 165.0 | 1030.0ms | 100% | Redis 통신 비용 발생 |

## 5. 분석 의견
1. **Pessimistic Lock**: 단일 레코드 경합 상황에서 가장 빠르고 안정적임. DB 커넥션 점유 시간이 길어질 수 있으나, 현재 시나리오에서는 최적임.
2. **Optimistic Lock**: 5회 재시도 설정에도 불구하고 2.79%의 요청이 실패함. 고경합 상황에서는 부적합함을 확인.
3. **Distributed Lock**: 네트워크 I/O 오버헤드로 인해 TPS가 가장 낮음. 단, 분산 서버 환경으로 확장 시 정합성을 위한 필수 선택지임.