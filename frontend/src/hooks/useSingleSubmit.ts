import { useCallback, useRef, useState } from 'react';

/**
 * Runs one submit at a time. The ref blocks a second submit in the same tick, before React has
 * re-rendered with the disabled button.
 */
export function useSingleSubmit() {
  const running = useRef(false);
  const [submitting, setSubmitting] = useState(false);

  const run = useCallback(async (task: () => Promise<void>) => {
    if (running.current) {
      return;
    }
    running.current = true;
    setSubmitting(true);
    try {
      await task();
    } finally {
      running.current = false;
      setSubmitting(false);
    }
  }, []);

  return { submitting, run };
}
