// تاهييه — load test with k6 (https://k6.io). Simulates ~5000 users browsing, plus a trickle of logins.
//
//   k6 run -e BASE_URL=https://localhost:8443 -e PASSWORD='<demo password>' loadtest/k6-tahiya.js
//
// Accounts used (they must exist, e.g. the demo accounts): see USERS below.
// Use only against your own test deployment — never against a system holding real data.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'https://localhost:8443';
const PASSWORD = __ENV.PASSWORD;
const USERS = (__ENV.USERS || 'senior1,leader1,agent,general').split(',');
const TARGET = Number(__ENV.TARGET_VUS || 5000);

export const options = {
  insecureSkipTLSVerify: true, // self-signed certificate on a test server
  scenarios: {
    // Read traffic of signed-in users: open a page every 10–30 seconds (a realistic think time).
    browse: {
      executor: 'ramping-vus', exec: 'browse', startVUs: 0,
      stages: [
        { duration: '2m', target: Math.round(TARGET / 5) },
        { duration: '3m', target: TARGET },
        { duration: '5m', target: TARGET },   // hold
        { duration: '1m', target: 0 },
      ],
    },
    // BCrypt makes logins the most CPU-hungry request; keep a constant trickle of them.
    logins: { executor: 'constant-arrival-rate', exec: 'login', rate: 5, timeUnit: '1s',
      duration: '11m', preAllocatedVUs: 30, maxVUs: 200 },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{scenario:browse}': ['p(95)<800'],
    'http_req_duration{scenario:logins}': ['p(95)<3000'],
  },
};

function signIn(username) {
  const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  return r.status === 200 ? r.json('accessToken') : null;
}

export function setup() {
  if (!PASSWORD) throw new Error('Pass the account password: -e PASSWORD=...');
  const tokens = USERS.map(u => ({ user: u, token: signIn(u) })).filter(t => t.token);
  if (!tokens.length) throw new Error('No account could sign in — check BASE_URL, USERS and PASSWORD');
  return { tokens };
}

export function browse(data) {
  const t = data.tokens[__VU % data.tokens.length];
  const params = { headers: { Authorization: `Bearer ${t.token}` } };
  const pages = ['/api/auth/me', '/api/sessions?size=20', '/api/soldiers?size=25'];
  if (t.user === 'agent' || t.user === 'general' || t.user === 'leader1') pages.push('/api/dashboard/live');
  const url = pages[Math.floor(Math.random() * pages.length)];
  const res = http.get(`${BASE}${url}`, params);
  check(res, { 'status 200': r => r.status === 200 });
  sleep(10 + Math.random() * 20);
}

export function login() {
  const u = USERS[Math.floor(Math.random() * USERS.length)];
  const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username: u, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  check(r, { 'login 200': x => x.status === 200 });
}
