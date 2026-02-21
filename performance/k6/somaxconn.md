# kern.ipc.somaxconn 정리

## 정의

**TCP 연결 대기 큐(backlog)의 최대 크기**를 결정하는 OS 커널 파라미터입니다.

```
클라이언트 → [TCP 연결 대기 큐] → 서버 애플리케이션
              (somaxconn 제한)
```

## 동작 방식

```
1. 클라이언트가 TCP 연결 요청 (SYN)
2. 서버 OS가 연결을 수락하고 backlog 큐에 추가
3. 애플리케이션(톰캣)이 큐에서 연결을 꺼내 처리
4. 큐가 가득 차면 → 새 연결 거부 (RST 패킷) → "connection reset by peer"
```

## OS별 기본값

| OS | 기본값 | 권장값 (고부하) |
|----|-------|---------------|
| **macOS** | **128** | 1024+ |
| Linux | 128 (구버전) / 4096 (최신) | 4096+ |
| Windows | 200 | - |

## 톰캣 설정과의 관계

```yaml
server:
  tomcat:
    accept-count: 200  # 톰캣이 원하는 backlog 크기
```

```
실제 적용값 = min(accept-count, somaxconn)
           = min(200, 128)
           = 128  ← OS가 제한
```

톰캣에서 아무리 높게 설정해도 **OS 제한을 넘을 수 없습니다**.

## 확인 방법

```bash
# macOS
sysctl kern.ipc.somaxconn

# Linux
sysctl net.core.somaxconn
```

## 변경 방법

### macOS

**임시 (재부팅 시 초기화)**
```bash
sudo sysctl -w kern.ipc.somaxconn=1024
```

**영구 적용**
```bash
echo "kern.ipc.somaxconn=1024" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

### Linux

**임시**
```bash
sudo sysctl -w net.core.somaxconn=4096
```

**영구 적용**
```bash
echo "net.core.somaxconn=4096" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

## 테스트에서 발생한 문제

### 문제 상황

```
k6: 200 VUs 동시 요청
         ↓
macOS backlog: 128개만 대기 가능
         ↓
초과 연결 거부 → "connection reset by peer"
```

### 동기 vs 비동기 엔드포인트 차이

| 엔드포인트 | 처리 방식 | 스레드 점유 시간 | 문제 발생 |
|-----------|----------|----------------|----------|
| `/purchase/direct` | 동기 (DB 트랜잭션) | 수십~수백 ms | O |
| `/subscribe` (SSE) | 비동기 (SseEmitter) | 수 ms | X |

**SSE에서 문제없던 이유**: 비동기 처리로 스레드가 빠르게 반환되어 큐가 빨리 비워짐

## 요약

| 항목 | 내용 |
|-----|------|
| 역할 | TCP 연결 대기 큐 최대 크기 |
| macOS 기본값 | 128 |
| 문제 상황 | 동시 연결 > somaxconn → 연결 거부 |
| 해결책 | OS 값 증가 또는 k6 ramp-up 사용 |
