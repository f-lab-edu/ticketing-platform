import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        ticket_stock_direct_test: {
            executor: 'constant-vus',
            vus: 100,            // 동시 사용자 수
            duration: '10s',     // 테스트 지속 시간
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TICKET_STOCK_ID = __ENV.TICKET_STOCK_ID || 1;

export default function () {
    const url = `${BASE_URL}/ticket-stocks/${TICKET_STOCK_ID}/direct`;

    const payload = JSON.stringify({
        requestQuantity: 1,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(0.1);
}
