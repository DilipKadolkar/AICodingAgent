export default function DiffView({ diff }) {
  if (!diff) {
    return null;
  }
  const lines = diff.split('\n');
  return (
    <pre className="diff-view" data-testid="diff-view">
      {lines.map((line, idx) => {
        let cls = 'diff-context';
        if (line.startsWith('+') && !line.startsWith('+++')) cls = 'diff-add';
        else if (line.startsWith('-') && !line.startsWith('---')) cls = 'diff-remove';
        else if (line.startsWith('---') || line.startsWith('+++')) cls = 'diff-header';
        return (
          <div key={idx} className={cls}>
            {line}
          </div>
        );
      })}
    </pre>
  );
}
