import http from 'k6/http';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const VU_OPTIONS = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export function createGuest() {
  const guestId = uuidv4();
  const res = http.post(
    `${BASE_URL}/api/v1/guests`,
    JSON.stringify({ guestId }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  return { res, guestId };
}

export function authHeaders(accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  };
}

export function getCalendarParams() {
  const now = new Date();
  return {
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    selectedDate: now.toISOString().slice(0, 10),
  };
}
