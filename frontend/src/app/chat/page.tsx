"use client";

import { useEffect, useRef, useState } from "react";
import { useChatStore } from "@/stores/chat";
import { AgentMessage } from "@/components/agent/AgentMessage";
import { AgentSelector } from "@/components/agent/AgentSelector";
import { StockSearch } from "@/components/common/StockSearch";
import { Button } from "@/components/common/Button";
import { clearChatSession, createChatSession } from "@/api/client";
import type { StockSearchResult } from "@/types";
import { Send, Loader2, Trash2, Zap } from "lucide-react";

export default function ChatPage() {
  const [isCreatingSession, setIsCreatingSession] = useState(false);
  const [isClearingSession, setIsClearingSession] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);

  const {
    messages,
    inputMessage,
    selectedAgent,
    isLoading,
    loadingMessage,
    error,
    sessionId,
    setSessionId,
    setCurrentStockCode,
    setInputMessage,
    setSelectedAgent,
    setIsLoading,
    setLoadingMessage,
    setError,
    addMessage,
    reset,
  } = useChatStore();

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const stockSelected = Boolean(sessionId) || isCreatingSession;

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  // 组件卸载时关闭 EventSource，防止泄漏
  useEffect(() => {
    return () => {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
    };
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = `${textareaRef.current.scrollHeight}px`;
    }
  }, [inputMessage]);

  const handleStockSelect = async (stock: StockSearchResult) => {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    reset();
    setIsCreatingSession(true);

    try {
      const sid = await createChatSession(stock.code, stock.name);
      setCurrentStockCode(stock.code);
      setSessionId(sid);
    } catch {
      setError("创建会话失败，请稍后重试");
    } finally {
      setIsCreatingSession(false);
    }
  };

  const handleSend = () => {
    if (!inputMessage.trim() || isLoading) return;
    if (!sessionId) {
      setError("会话尚未就绪，请重新选择股票");
      return;
    }

    const content = inputMessage.trim();
    const sid = sessionId;

    setError(null);
    addMessage({
      id: crypto.randomUUID(),
      role: "user",
      content,
      agentType: selectedAgent,
      timestamp: new Date().toISOString(),
    });

    setIsLoading(true);
    setLoadingMessage("AI分析中...");
    setInputMessage("");

    // 关闭上一个连接
    eventSourceRef.current?.close();

    const url = `/api/v1/chat/stream/${sid}?message=${encodeURIComponent(content)}&agentType=${selectedAgent}`;
    const es = new EventSource(url);
    eventSourceRef.current = es;
    let settled = false;

    es.onmessage = (e: MessageEvent) => {
      try {
        const payload = JSON.parse(e.data);
        if (payload.event === "thinking") {
          setLoadingMessage(payload.message || "AI分析中...");
        } else if (payload.event === "message" && payload.data) {
          addMessage({
            id: payload.data.id || crypto.randomUUID(),
            role: payload.data.role || "assistant",
            content: payload.data.content || "",
            agentType: payload.data.agentType || selectedAgent,
            agentName: payload.data.agentName,
            timestamp: payload.data.timestamp || new Date().toISOString(),
          });
          settled = true;
          setIsLoading(false);
          es.close();
          if (eventSourceRef.current === es) {
            eventSourceRef.current = null;
          }
        } else if (payload.event === "error") {
          settled = true;
          setError(payload.message || "分析失败");
          es.close();
          if (eventSourceRef.current === es) {
            eventSourceRef.current = null;
          }
        }
      } catch {
        // ignore parse errors
      }
    };

    es.onerror = () => {
      if (settled) return;
      settled = true;
      setError("连接失败，请检查后端服务");
      es.close();
      if (eventSourceRef.current === es) {
        eventSourceRef.current = null;
      }
    };
  };

  const handleClearConversation = async () => {
    if (!sessionId) {
      reset();
      return;
    }

    setIsClearingSession(true);
    setError(null);
    try {
      await clearChatSession(sessionId);
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      reset();
    } catch {
      setError("清空会话失败，请稍后重试");
    } finally {
      setIsClearingSession(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="p-6 h-[calc(100vh-3rem)] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-6 animate-enter">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="gradient-text">AI对话</span>
          </h1>
          <p className="text-sm text-[var(--text-muted)] mt-1 font-mono">
            MULTI-AGENT CONVERSATION INTERFACE
          </p>
        </div>
      </div>

      {/* Stock Selection */}
      {!stockSelected && (
        <div className="glass-card-glow p-6 mb-6 animate-enter delay-100">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 bg-gradient-to-br from-[var(--accent)] to-[#00ff88] rounded-xl flex items-center justify-center">
              <Zap className="w-5 h-5 text-[var(--bg-primary)]" />
            </div>
            <div>
              <h2 className="text-lg font-semibold">选择要分析的股票</h2>
              <p className="text-sm text-[var(--text-muted)]">
                开启与AI分析师的实时对话
              </p>
            </div>
          </div>
          <StockSearch
            onSelect={handleStockSelect}
            placeholder="搜索股票代码或名称，开启AI对话..."
          />
        </div>
      )}

      {/* Chat Header */}
      {stockSelected && (
        <div className="glass-card p-4 mb-6 animate-enter">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="relative">
                <div className="w-12 h-12 bg-gradient-to-br from-[var(--accent)] to-[#00ff88] rounded-xl flex items-center justify-center">
                  <span className="text-lg font-bold text-[var(--bg-primary)]">
                    AI
                  </span>
                </div>
                <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-[var(--bullish)] rounded-full border-2 border-[var(--bg-secondary)] pulse-live" />
              </div>
              <div>
                <div className="font-semibold text-lg">AI分析师</div>
                <div className="flex items-center gap-2">
                  <div className="w-2 h-2 bg-[var(--bullish)] rounded-full pulse-live" />
                  <span className="text-xs text-[var(--text-muted)] font-mono">
                    {sessionId ? "在线" : "连接中"}
                  </span>
                </div>
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={handleClearConversation}
              disabled={isLoading || isCreatingSession || isClearingSession}
              className="text-[var(--text-muted)] hover:text-[var(--bearish)] hover:bg-[var(--bearish-glow)]"
            >
              <Trash2 className="w-4 h-4 mr-2" />
              清空对话
            </Button>
          </div>
        </div>
      )}

      {/* Messages Container */}
      <div className="flex-1 glass-card overflow-hidden flex flex-col animate-enter delay-200">
        {/* Agent Selector */}
        <div className="p-4 border-b border-[var(--border)]">
          <AgentSelector selected={selectedAgent} onChange={setSelectedAgent} />
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.length === 0 && (
            <div className="h-full flex items-center justify-center">
              <div className="text-center">
                <div className="w-16 h-16 mx-auto mb-4 bg-[var(--bg-tertiary)] rounded-2xl flex items-center justify-center">
                  <Zap className="w-8 h-8 text-[var(--accent)]" />
                </div>
                <p className="text-[var(--text-secondary)] mb-2">
                  开始对话吧！
                </p>
                <p className="text-sm text-[var(--text-muted)] font-mono max-w-md">
                  输入消息与AI分析师对话，或使用 @Agent名称 指定特定Agent
                </p>
              </div>
            </div>
          )}

          {messages.map((message) => (
            <AgentMessage key={message.id} message={message} />
          ))}

          {isLoading && (
            <div className="flex items-center gap-3 p-3 bg-[var(--bg-tertiary)] rounded-xl w-fit">
              <Loader2 className="w-5 h-5 text-[var(--accent)] animate-spin" />
              <span className="text-sm text-[var(--text-secondary)]">
                {loadingMessage || "正在分析..."}
              </span>
            </div>
          )}

          {error && (
            <div className="p-3 bg-[var(--bearish-glow)] border border-[var(--bearish)]/30 rounded-xl text-[var(--bearish)] text-sm">
              {error}
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Area */}
        <div className="p-4 border-t border-[var(--border)]">
          <div className="flex gap-3">
            <textarea
              ref={textareaRef}
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={
                sessionId
                  ? "输入消息... (使用 @Agent名称 指定特定Agent)"
                  : "正在创建会话..."
              }
              className="flex-1 min-h-[48px] max-h-32 px-4 py-3 rounded-xl bg-[var(--bg-tertiary)] border border-[var(--border)] text-sm resize-none focus:outline-none focus:border-[var(--accent)] transition-colors font-mono"
              rows={1}
              disabled={isLoading || !sessionId || isCreatingSession || isClearingSession}
            />
            <Button
              onClick={handleSend}
              disabled={
                isLoading ||
                !sessionId ||
                !inputMessage.trim() ||
                isCreatingSession ||
                isClearingSession
              }
              className="h-12 px-6 bg-gradient-to-r from-[var(--accent)] to-[#00ff88] text-[var(--bg-primary)] font-semibold btn-glow self-end"
            >
              {isLoading ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </Button>
          </div>
          <div className="mt-2 text-xs text-[var(--text-muted)] font-mono">
            按 Enter 发送，Shift + Enter 换行
          </div>
        </div>
      </div>
    </div>
  );
}
