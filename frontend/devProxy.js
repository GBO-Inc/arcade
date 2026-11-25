/**
 * Forwards REST and STOMP traffic to the backend, so the browser only talks to the dev server
 * and needs no CORS. The Host and Origin headers stay on the dev server, which the backend's
 * WebSocket origin check (WS_ALLOWED_ORIGINS) already allows.
 */
export function devProxy(target = 'http://localhost:8080') {
  return {
    '/api': { target },
    '/ws': { target, ws: true },
  };
}
