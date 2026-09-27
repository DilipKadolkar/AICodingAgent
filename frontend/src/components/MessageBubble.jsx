const FENCE_RE = /```(\w*)\n([\s\S]*?)```/g;

function renderContent(content) {
  const parts = [];
  let lastIndex = 0;
  let match;
  let key = 0;
  FENCE_RE.lastIndex = 0;
  while ((match = FENCE_RE.exec(content)) !== null) {
    if (match.index > lastIndex) {
      parts.push(<span key={key++}>{content.slice(lastIndex, match.index)}</span>);
    }
    const [, lang, code] = match;
    parts.push(
      <pre className="code-block" key={key++}>
        {lang && <div className="code-lang">{lang}</div>}
        <code>{code}</code>
      </pre>
    );
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < content.length) {
    parts.push(<span key={key++}>{content.slice(lastIndex)}</span>);
  }
  return parts;
}

export default function MessageBubble({ message }) {
  const role = message.role;
  return (
    <div className={`message-bubble message-${role.toLowerCase()}`} data-testid="message-bubble">
      <div className="message-role">{role === 'USER' ? 'You' : role === 'AGENT' ? 'Agent' : 'System'}</div>
      <div className="message-content">{renderContent(message.content)}</div>
    </div>
  );
}
