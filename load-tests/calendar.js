import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  BASE_URL,
  VU_OPTIONS,
  authHeaders,
  createGuest,
  getCalendarParams,
} from './common.js';

export const options = {
  ...VU_OPTIONS,
  duration: '1m',
};

export default function () {
  const { res: guestRes } = createGuest();

  const guestOk = check(guestRes, {
    'guest created': (r) => r.status === 201 || r.status === 200,
    'guest has token': (r) => !!r.json('data.auth.accessToken'),
  });

  if (!guestOk) {
    return;
  }

  const headers = authHeaders(guestRes.json('data.auth.accessToken'));
  const { year, month, selectedDate } = getCalendarParams();

  const statusRes = http.get(`${BASE_URL}/api/v1/users/status`, { headers });
  check(statusRes, { 'user status 200': (r) => r.status === 200 });

  const calRes = http.get(
    `${BASE_URL}/api/v1/calendars/main?year=${year}&month=${month}&selectedDate=${selectedDate}`,
    { headers },
  );
  check(calRes, { 'calendar main 200': (r) => r.status === 200 });

  const dateEventsRes = http.get(
    `${BASE_URL}/api/v1/calendars/dates/${selectedDate}/events`,
    { headers },
  );
  check(dateEventsRes, { 'date events 200': (r) => r.status === 200 });

  const labelsRes = http.get(`${BASE_URL}/api/v1/labels`, { headers });
  check(labelsRes, { 'labels 200': (r) => r.status === 200 });

  sleep(1);
}
