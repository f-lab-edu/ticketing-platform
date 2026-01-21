import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        ticket_stock_queue_test: {
            executor: 'constant-vus',
            vus: 100,            // 동시 사용자 수
            duration: '10s',     // 테스트 지속 시간
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TICKET_STOCK_ID = __ENV.TICKET_STOCK_ID || 1;

export default function () {
    const userId = `user-${__VU}-${__ITER}`;

    // 1. 대기열 등록
    const queueUrl = `${BASE_URL}/queues/${TICKET_STOCK_ID}/enter`;
    const queuePayload = JSON.stringify({ userId });
    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const queueRes = http.post(queueUrl, queuePayload, params);
    check(queueRes, {
        'queue registration success': (r) => r.status === 200,
    });

    if (queueRes.status !== 200) {
        return;
    }

    const queueData = JSON.parse(queueRes.body).data;

    // 2. 입장 가능할 때까지 폴링 (최대 5회)
    let canEnter = queueData.canEnter;
    let attempts = 0;
    const maxAttempts = 5;

    while (!canEnter && attempts < maxAttempts) {
        sleep(0.5);
        const statusUrl = `${BASE_URL}/queues/${TICKET_STOCK_ID}/status?userId=${userId}`;
        const statusRes = http.get(statusUrl, params);

        if (statusRes.status === 200) {
            const statusData = JSON.parse(statusRes.body).data;
            canEnter = statusData.canEnter;
        }
        attempts++;
    }

    // 3. 입장 가능하면 티켓 구매
    if (canEnter) {
        const purchaseUrl = `${BASE_URL}/ticket-stocks/${TICKET_STOCK_ID}`;
        const purchasePayload = JSON.stringify({
            userId,
            requestQuantity: 1,
        });

        const purchaseRes = http.post(purchaseUrl, purchasePayload, params);
        check(purchaseRes, {
            'purchase success': (r) => r.status === 200,
        });
    }
}
