import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, VU_OPTIONS } from './common.js';

export const options = VU_OPTIONS;

export default function () {
  const res = http.get(`${BASE_URL}/health`);

  check(res, {
    'health status is 200': (r) => r.status === 200,
    'health overall UP': (r) => r.json('status') === 'UP',
  });

  sleep(1);
}
