import type { ButtonHTMLAttributes } from 'react';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
};

const variants = {
  primary:
    'bg-ui-primary text-ui-primary-foreground hover:bg-ui-primary-hover focus-visible:ring-ui-primary-focus',
  secondary:
    'border border-ui-border bg-ui-surface-raised text-ui-text hover:bg-ui-surface-hover focus-visible:ring-ui-muted',
  danger:
    'bg-ui-danger text-ui-danger-foreground hover:bg-ui-danger-hover focus-visible:ring-ui-danger-focus',
  ghost:
    'text-ui-subtle hover:bg-ui-surface-raised hover:text-ui-text focus-visible:ring-ui-muted',
};

const sizes = {
  sm: 'px-ui-3 py-ui-2 text-ui-sm',
  md: 'px-ui-4 py-ui-2 text-ui-sm',
  lg: 'px-ui-5 py-ui-3 text-ui-base',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  type = 'button',
  className = '',
  disabled,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      aria-busy={loading || undefined}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-ui-2 rounded-ui-lg font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-ui-surface disabled:pointer-events-none disabled:opacity-50 ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="size-ui-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}
