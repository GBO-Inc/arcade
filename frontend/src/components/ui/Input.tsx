import { useId, type InputHTMLAttributes } from 'react';

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  hint?: string;
  error?: string;
};

export function Input({
  label,
  hint,
  error,
  id,
  className = '',
  'aria-describedby': describedBy,
  ...props
}: InputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const descriptionId = `${inputId}-description`;
  const helpText = error ?? hint;
  const ariaDescribedBy =
    [describedBy, helpText ? descriptionId : undefined]
      .filter(Boolean)
      .join(' ') || undefined;

  return (
    <div className="space-y-1.5">
      {label && (
        <label
          htmlFor={inputId}
          className="block text-sm font-medium text-slate-200"
        >
          {label}
        </label>
      )}
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={ariaDescribedBy}
        className={`block w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-400/30 disabled:cursor-not-allowed disabled:opacity-50 ${error ? 'border-rose-500 focus:border-rose-400 focus:ring-rose-400/30' : ''} ${className}`}
        {...props}
      />
      {helpText && (
        <p
          id={descriptionId}
          className={`text-sm ${error ? 'text-rose-400' : 'text-slate-400'}`}
        >
          {helpText}
        </p>
      )}
    </div>
  );
}
