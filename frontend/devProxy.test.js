import { devProxy } from './devProxy.js';

describe('devProxy', () => {
  it('sends REST and WebSocket traffic to the given backend', () => {
    const proxy = devProxy('http://backend:8080');

    expect(proxy['/api']).toEqual({ target: 'http://backend:8080' });
    expect(proxy['/ws']).toEqual({ target: 'http://backend:8080', ws: true });
  });

  it('defaults to the backend on localhost for manual runs', () => {
    const proxy = devProxy(undefined);

    expect(proxy['/api'].target).toBe('http://localhost:8080');
    expect(proxy['/ws'].target).toBe('http://localhost:8080');
  });
});
