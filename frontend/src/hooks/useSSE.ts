"use client";

import { useRef, useCallback } from "react";

export interface SSEOptions {
  onMessage: (event: string, data: unknown) => void;
  onError?: (error: Event) => void;
  onOpen?: () => void;
}

/**
 * 封装 EventSource 的 SSE hook
 * 用于连接后端的 SSE 流式接口
 */
export function useSSE() {
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = useCallback((url: string, options: SSEOptions) => {
    // 关闭之前的连接
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }

    const es = new EventSource(url);
    eventSourceRef.current = es;

    es.onopen = () => {
      options.onOpen?.();
    };

    // 监听具名事件
    const eventTypes = ["stage", "data", "complete", "error", "message", "result"];
    eventTypes.forEach((eventType) => {
      es.addEventListener(eventType, (e: MessageEvent) => {
        try {
          const parsed = JSON.parse(e.data);
          options.onMessage(eventType, parsed);
        } catch {
          options.onMessage(eventType, e.data);
        }
      });
    });

    // 默认消息事件（无具名event时）
    es.onmessage = (e: MessageEvent) => {
      try {
        const parsed = JSON.parse(e.data);
        options.onMessage("message", parsed);
      } catch {
        options.onMessage("message", e.data);
      }
    };

    es.onerror = (e: Event) => {
      options.onError?.(e);
      es.close();
      eventSourceRef.current = null;
    };

    return es;
  }, []);

  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  return { connect, disconnect };
}
