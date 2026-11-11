import { useEffect, useRef, useState } from 'react';

type ToastProps = {
  message: string;
  variant?: 'success' | 'error' | 'info';
  onDismiss?: () => void;
  duration?: number;
};

const variants = {
  success:
    'border-ui-success-border bg-ui-success-surface text-ui-success-text',
  error: 'border-ui-error-border bg-ui-error-surface text-ui-error-text',
  info: 'border-ui-info-border bg-ui-info-surface text-ui-info-text',
};

export function Toast({
  message,
  variant = 'info',
  onDismiss,
  duration = 3000,
}: ToastProps) {
  const [visible, setVisible] = useState(true);
  const onDismissRef = useRef(onDismiss);
  const timeoutRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    onDismissRef.current = onDismiss;
  }, [onDismiss]);

  useEffect(() => {
    timeoutRef.current = window.setTimeout(() => {
      setVisible(false);
      onDismissRef.current?.();
    }, duration);
    return () => window.clearTimeout(timeoutRef.current);
  }, [duration]);

  if (!visible) return null;

  const dismiss = () => {
    window.clearTimeout(timeoutRef.current);
    setVisible(false);
    onDismissRef.current?.();
  };

  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={`flex items-center justify-between gap-ui-4 rounded-ui-lg border px-ui-4 py-ui-3 text-ui-sm ${variants[variant]}`}
    >
      <span>{message}</span>
      {onDismiss && (
        <button
          type="button"
          aria-label="Dismiss notification"
          onClick={dismiss}
          className="shrink-0 rounded-ui-md px-ui-1 text-current/70 hover:text-current focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current"
        >
          <span aria-hidden="true">×</span>
        </button>
      )}
    </div>
  );
}
