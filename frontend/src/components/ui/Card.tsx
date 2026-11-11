import type { HTMLAttributes } from 'react';

type CardProps = HTMLAttributes<HTMLDivElement>;

export function Card({ className = '', ...props }: CardProps) {
  return (
    <div
      className={`rounded-ui-xl border border-ui-border bg-ui-surface p-ui-5 shadow-sm ${className}`}
      {...props}
    />
  );
}
