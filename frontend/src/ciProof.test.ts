import { describe, it, expect } from 'vitest';

describe('CI proof', () => {
  it('fails on purpose', () => {
    expect(1 + 1).toBe(3);
  });
});
