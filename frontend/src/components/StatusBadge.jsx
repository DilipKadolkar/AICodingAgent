const STATUS_LABELS = {
  IDLE: 'Idle',
  PROCESSING: 'Processing',
  EXECUTING: 'Executing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

export default function StatusBadge({ status }) {
  const normalized = status || 'IDLE';
  const label = STATUS_LABELS[normalized] || normalized;
  return (
    <span
      data-testid="status-badge"
      className={`status-badge status-${normalized.toLowerCase()}`}
    >
      {label}
    </span>
  );
}
