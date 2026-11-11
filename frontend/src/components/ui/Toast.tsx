type ToastProps = {
  message: string;
  variant?: 'success' | 'error' | 'info';
  onDismiss?: () => void;
};

const variants = {
  success: 'border-emerald-500/40 bg-emerald-950 text-emerald-100',
  error: 'border-rose-500/40 bg-rose-950 text-rose-100',
  info: 'border-sky-500/40 bg-sky-950 text-sky-100',
};

export function Toast({ message, variant = 'info', onDismiss }: ToastProps) {
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={`flex items-center justify-between gap-4 rounded-lg border px-4 py-3 text-sm ${variants[variant]}`}
    >
      <span>{message}</span>
      {onDismiss && (
        <button
          type="button"
          aria-label="Dismiss notification"
          onClick={onDismiss}
          className="shrink-0 rounded px-1 text-current/70 hover:text-current focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current"
        >
          <span aria-hidden="true">×</span>
        </button>
      )}
    </div>
  );
}
