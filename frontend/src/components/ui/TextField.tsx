import { useId, type InputHTMLAttributes } from 'react';

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  hint?: string;
  error?: string;
};

export function TextField({
  label,
  hint,
  error,
  id,
  className = '',
  'aria-describedby': describedBy,
  ...props
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const descriptionId = `${inputId}-description`;
  const helpText = error ?? hint;
  const ariaDescribedBy =
    [describedBy, helpText ? descriptionId : undefined]
      .filter(Boolean)
      .join(' ') || undefined;

  return (
    <div className="space-y-ui-2">
      {label && (
        <label
          htmlFor={inputId}
          className="block text-ui-sm font-medium text-ui-text"
        >
          {label}
        </label>
      )}
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={ariaDescribedBy}
        className={`block w-full rounded-ui-lg border border-ui-border bg-ui-surface px-ui-3 py-ui-2 text-ui-sm text-ui-text placeholder:text-ui-muted focus:border-ui-primary-focus focus:outline-none focus:ring-2 focus:ring-ui-primary/30 disabled:cursor-not-allowed disabled:opacity-50 ${error ? 'border-ui-error focus:border-ui-error focus:ring-ui-error/30' : ''} ${className}`}
        {...props}
      />
      {helpText && (
        <p
          id={descriptionId}
          className={`text-ui-sm ${error ? 'text-ui-error' : 'text-ui-muted'}`}
        >
          {helpText}
        </p>
      )}
    </div>
  );
}
