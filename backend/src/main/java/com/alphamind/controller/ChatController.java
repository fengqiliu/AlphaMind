package com.alphamind.controller;

import com.alphamind.model.dto.ApiResponse;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.enums.AgentType;
import com.alphamind.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 对话接口：只负责 HTTP 参数与 SSE 帧格式，业务逻辑在 {@link ChatService}。
 *
 * <p>对话流的帧格式与分析流不同，为 {@code data: {"event": ..., ...}}（无具名事件），前端按此解析，保持不变。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /** 创建新会话 */
    @PostMapping("/session")
    public ApiResponse<Map<String, String>> createSession(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String stockName) {
        return ApiResponse.success(Map.of("sessionId", chatService.createSession(stockCode, stockName)));
    }

    /** 发送消息 */
    @PostMapping("/message")
    public ApiResponse<ChatMessage> sendMessage(
            @RequestParam String sessionId,
            @RequestParam @NotBlank String content,
            @RequestParam(required = false, defaultValue = "PORTFOLIO") AgentType agentType) {
        return chatService.reply(sessionId, content, agentType)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "会话不存在"));
    }

    /** SSE 流式对话 */
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @PathVariable String sessionId,
            @RequestParam @NotBlank String message,
            @RequestParam(required = false, defaultValue = "PORTFOLIO") AgentType agentType) {
        if (!chatService.sessionExists(sessionId)) {
            return Flux.just("data: {\"error\": \"会话不存在\"}\n\n");
        }
        return Flux.create(emitter -> {
            emitter.next("data: {\"event\":\"thinking\",\"message\":\"Agent正在分析...\"}\n\n");
            try {
                ChatMessage response = chatService.reply(sessionId, message, agentType)
                        .orElseThrow(() -> new IllegalStateException("会话不存在"));
                emitter.next("data: {\"event\":\"message\",\"data\":"
                        + objectMapper.writeValueAsString(response) + "}\n\n");
            } catch (Exception e) {
                log.error("流式对话失败: sessionId={}", sessionId, e);
                emitter.next(errorFrame(e));
            }
            emitter.complete();
        });
    }

    /** 获取会话历史 */
    @GetMapping("/history/{sessionId}")
    public ApiResponse<List<ChatMessage>> getHistory(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ApiResponse.success(chatService.history(sessionId, limit));
    }

    /** 清除会话 */
    @DeleteMapping("/session/{sessionId}")
    public ApiResponse<Void> clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return ApiResponse.success(null);
    }

    /** 用 ObjectMapper 序列化，防止错误信息中的特殊字符破坏 JSON。 */
    private String errorFrame(Exception e) {
        try {
            return "data: " + objectMapper.writeValueAsString(Map.of(
                    "event", "error", "message", e.getMessage() != null ? e.getMessage() : "处理失败")) + "\n\n";
        } catch (Exception serialization) {
            return "data: {\"event\":\"error\",\"message\":\"处理失败\"}\n\n";
        }
    }
}
