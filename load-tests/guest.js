import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, VU_OPTIONS, createGuest } from './common.js';

export const options = VU_OPTIONS;

export function setup() {
  const res = http.get(`${BASE_URL}/health`);
  if (res.status !== 200) {
    throw new Error(
      `백엔드에 연결할 수 없습니다 (${BASE_URL}/health → status=${res.status}). ` +
        'Spring Boot가 실행 중인지 확인하세요.',
    );
  }
}

export default function () {
  const { res } = createGuest();

  check(res, {
    'guest created or reconnected': (r) => {
      if (r.status !== 201 && r.status !== 200) {
        console.error(`guest 실패: status=${r.status}, body=${r.body}`);
      }
      return r.status === 201 || r.status === 200;
    },
    'guest response has access token': (r) => !!r.json('data.auth.accessToken'),
  });

  sleep(1);
}
