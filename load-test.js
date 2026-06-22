import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    // Teste para 300 usuários
    { duration: '2m', target: 300 },
    { duration: '3m', target: 300 },
    { duration: '1m', target: 0 },
    
    // Teste para 10k usuários
    { duration: '2m', target: 10000 },
    { duration: '5m', target: 10000 },
    { duration: '2m', target: 0 },
    
    // Teste para 20k usuários
    { duration: '2m', target: 20000 },
    { duration: '5m', target: 20000 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% das requisições < 500ms
    http_req_failed: ['rate<0.01'],   // menos de 1% de falhas
  },
};

export default function () {
  // Teste GET /api/energy
  const res1 = http.get('http://localhost:8080/api/energy');
  check(res1, {
    'status is 200': (r) => r.status === 200,
    'response time < 200ms': (r) => r.timings.duration < 200,
  });
  
  // Teste POST /api/energy
  const payload = JSON.stringify({
    value: Math.random() * 1000,
    timestamp: new Date().toISOString(),
  });
  
  const res2 = http.post('http://localhost:8080/api/energy', payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  
  check(res2, {
    'status is 201': (r) => r.status === 201,
  });
  
  sleep(1);
}