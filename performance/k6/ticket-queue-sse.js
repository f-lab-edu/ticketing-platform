import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import sse from 'k6/x/sse';

// 설정
// const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:80';
const CONCERT_ID = __ENV.CONCERT_ID || 1;
const REQUEST_QUANTITY = __ENV.REQUEST_QUANTITY || 1;

// 커스텀 메트릭
const sseConnections = new Counter('sse_connections');
const enterEvents = new Counter('enter_events_received');
const purchaseSuccess = new Counter('purchase_success');
const purchaseFailed = new Counter('purchase_failed');
const timeToEnter = new Trend('time_to_enter', true);
const purchaseDuration = new Trend('purchase_duration', true);

// 사용자 행동 대기 시간 (enter 수신 후 구매 버튼 클릭까지, 초 단위)
const THINK_TIME = parseFloat(__ENV.THINK_TIME) || 5;

export const options = {
    scenarios: {
        ticket_purchase: {
            executor: 'constant-vus',
            vus: parseInt(__ENV.VUS) || 1000,        // 동시 접속 사용자 수
            duration: __ENV.DURATION || '30s',       // 테스트 지속 시간
        },
    },
    thresholds: {
        'purchase_success': ['count>0'],
        'time_to_enter': ['p(95)<30000'],  // 95%가 30초 이내에 enter
        'purchase_duration': ['p(95)<1000'], // 95%가 1초 이내에 구매 완료
    },
};

/**
 * 메인 테스트 함수
 * - 각 VU가 1회만 실행 (1인 1회 구매)
 * - userId는 VU 번호 기반으로 고유하게 생성
 */
export default function () {
    const userId = `user_${__VU}_${__ITER}`;
    const startTime = Date.now();

    const url = `${BASE_URL}/queues/${CONCERT_ID}/subscribe`;
    const params = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ userId: userId }),
    };

    const response = sse.open(url, params, function (client) {
        sseConnections.add(1);

        client.on('event', function (event) {
            // SSE 이벤트 처리
            if (event.name === 'enter' || (event.data && event.data.includes('enter'))) {
                enterEvents.add(1);
                timeToEnter.add(Date.now() - startTime);

                // 사용자 행동 시뮬레이션: enter 수신 후 구매까지 대기
                if (THINK_TIME > 0) {
                    sleep(THINK_TIME); // 구매 하는데 걸리는 시간 (고정)
                    // sleep(Math.random() * THINK_TIME); // 구매 하는데 걸리는 시간 (랜덤)
                }

                // enter 이벤트를 받으면 구매 API 호출
                const purchaseStart = Date.now();
                const purchaseRes = http.post(
                    `${BASE_URL}/concerts/${CONCERT_ID}/purchase`,
                    JSON.stringify({
                        userId: userId,
                        requestQuantity: parseInt(REQUEST_QUANTITY),
                    }),
                    {
                        headers: { 'Content-Type': 'application/json' },
                    }
                );

                purchaseDuration.add(Date.now() - purchaseStart);

                const success = check(purchaseRes, {
                    'purchase status is 200': (r) => r.status === 200,
                });

                if (success) {
                    purchaseSuccess.add(1);
                } else {
                    purchaseFailed.add(1);
                    console.log(`Purchase failed: ${purchaseRes.status} - ${purchaseRes.body}`);
                }

                // 서버가 enter 전송 후 SSE를 종료하므로 client.close()는 정리용
                client.close();
            }
        });

        client.on('error', function (e) {
            console.log(`SSE error: ${e.error()}`);
            client.close();
        });
    });

    check(response, {
        'SSE connection established': (r) => r.status === 200,
    });
}

export function handleSummary(data) {
    const summary = {
        'Total SSE Connections': data.metrics.sse_connections?.values?.count || 0,
        'Enter Events Received': data.metrics.enter_events_received?.values?.count || 0,
        'Purchase Success': data.metrics.purchase_success?.values?.count || 0,
        'Purchase Failed': data.metrics.purchase_failed?.values?.count || 0,
        'Avg Time to Enter (ms)': data.metrics.time_to_enter?.values?.avg?.toFixed(2) || 'N/A',
        'P95 Time to Enter (ms)': data.metrics.time_to_enter?.values?.['p(95)']?.toFixed(2) || 'N/A',
        'Avg Purchase Duration (ms)': data.metrics.purchase_duration?.values?.avg?.toFixed(2) || 'N/A',
        'P95 Purchase Duration (ms)': data.metrics.purchase_duration?.values?.['p(95)']?.toFixed(2) || 'N/A',
    };

    return {
        stdout: JSON.stringify(summary, null, 2),
    };
}
