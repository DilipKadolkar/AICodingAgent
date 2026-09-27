import { useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble.jsx';
import ToolCallCard from './ToolCallCard.jsx';

const NEAR_BOTTOM_THRESHOLD_PX = 80;

function buildTimeline(messages, toolCalls) {
  const items = [
    ...messages.map((m) => ({ type: 'message', at: m.createdAt, key: `m-${m.id}`, data: m })),
    ...toolCalls.map((t) => ({ type: 'toolCall', at: t.startedAt, key: `t-${t.id}`, data: t })),
  ];
  items.sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());
  return items;
}

export default function MessageList({ messages, toolCalls, emptyHint }) {
  const containerRef = useRef(null);
  const wasNearBottomRef = useRef(true);
  const timeline = buildTimeline(messages || [], toolCalls || []);

  useEffect(() => {
    const el = containerRef.current;
    if (el && wasNearBottomRef.current) {
      el.scrollTop = el.scrollHeight;
    }
  }, [timeline.length]);

  const handleScroll = () => {
    const el = containerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    wasNearBottomRef.current = distanceFromBottom <= NEAR_BOTTOM_THRESHOLD_PX;
  };

  if (timeline.length === 0) {
    return <div className="message-list-empty">{emptyHint || 'No messages yet. Say hello to get started.'}</div>;
  }

  return (
    <div className="message-list" ref={containerRef} onScroll={handleScroll} data-testid="message-list">
      {timeline.map((item) =>
        item.type === 'message' ? (
          <MessageBubble key={item.key} message={item.data} />
        ) : (
          <ToolCallCard key={item.key} toolCall={item.data} />
        )
      )}
    </div>
  );
}
