"use client";

import { useRef, useCallback, useEffect } from "react";

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

    const isCurrent = () => eventSourceRef.current === es;
    es.onopen = () => {
      if (isCurrent()) options.onOpen?.();
    };

    // 监听具名事件
    const eventTypes = [
      "stage",
      "data",
      "complete",
      "error",
      "message",
      "result",
    ];
    eventTypes.forEach((eventType) => {
      es.addEventListener(eventType, (e: MessageEvent) => {
        // 原生 error 是传输错误；具名 error 的 MessageEvent 携带业务错误。
        if (!isCurrent() || !(e instanceof MessageEvent)) return;
        let parsed: unknown = e.data;
        try {
          parsed = JSON.parse(e.data);
        } catch {
          /* 非 JSON 消息保留原文。 */
        }
        options.onMessage(eventType, parsed);
      });
    });

    es.onerror = (e: Event) => {
      if (!isCurrent() || e instanceof MessageEvent) return;
      es.close();
      eventSourceRef.current = null;
      options.onError?.(e);
    };

    return es;
  }, []);

  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  useEffect(() => disconnect, [disconnect]);

  return { connect, disconnect };
}
