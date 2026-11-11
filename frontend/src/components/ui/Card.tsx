import type { HTMLAttributes } from 'react';

type CardProps = HTMLAttributes<HTMLDivElement>;

export function Card({ className = '', ...props }: CardProps) {
  return (
    <div
      className={`rounded-xl border border-slate-800 bg-slate-900 p-5 shadow-sm ${className}`}
      {...props}
    />
  );
}
