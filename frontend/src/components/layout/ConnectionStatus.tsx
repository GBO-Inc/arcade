import { useSocket } from '../../hooks/socket';

const indicators = {
  connected: { label: 'Connected', dot: 'bg-emerald-500' },
  connecting: { label: 'Connecting', dot: 'bg-sky-400' },
  reconnecting: { label: 'Reconnecting', dot: 'bg-amber-500' },
  offline: { label: 'Offline', dot: 'bg-red-500' },
};

export function ConnectionStatus() {
  const { status } = useSocket();
  const { label, dot } = indicators[status];

  return (
    <span
      data-testid="connection-status"
      data-status={status}
      className="flex items-center gap-2 px-2 text-xs text-slate-400"
    >
      <span aria-hidden="true" className={`size-2.5 rounded-full ${dot}`} />
      {label}
    </span>
  );
}
