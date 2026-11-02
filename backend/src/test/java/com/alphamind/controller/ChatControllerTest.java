package com.alphamind.controller;

import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.enums.AgentType;
import com.alphamind.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatControllerTest {
    private final ChatService chat = mock(ChatService.class);
    private final ChatController controller = new ChatController(chat, new ObjectMapper().findAndRegisterModules());

    @Test
    void sendMessageReturns404ForUnknownSession() {
        when(chat.reply("missing", "你好", AgentType.PORTFOLIO)).thenReturn(Optional.empty());

        var response = controller.sendMessage("missing", "你好", AgentType.PORTFOLIO);

        assertEquals(404, response.getCode());
    }

    @Test
    void streamEmitsThinkingThenMessage() {
        when(chat.sessionExists("s1")).thenReturn(true);
        when(chat.reply("s1", "怎么看", AgentType.TECHNICAL))
                .thenReturn(Optional.of(ChatMessage.builder().id("a1").content("偏多").build()));

        List<String> frames = controller.streamChat("s1", "怎么看", AgentType.TECHNICAL).collectList().block();

        assertEquals(2, frames.size());
        assertTrue(frames.get(0).startsWith("data: {\"event\":\"thinking\""));
        assertTrue(frames.get(1).startsWith("data: {\"event\":\"message\",\"data\":"));
        assertTrue(frames.get(1).contains("\"content\":\"偏多\""));
        assertTrue(frames.get(1).endsWith("}\n\n"));
    }

    @Test
    void streamReportsUnknownSessionWithoutCallingAgent() {
        when(chat.sessionExists("missing")).thenReturn(false);

        List<String> frames = controller.streamChat("missing", "你好", AgentType.PORTFOLIO).collectList().block();

        assertEquals(List.of("data: {\"error\": \"会话不存在\"}\n\n"), frames);
        verify(chat, never()).reply(any(), any(), any());
    }

    @Test
    void streamSerializesAgentErrorsSafely() {
        when(chat.sessionExists("s1")).thenReturn(true);
        when(chat.reply("s1", "问题", AgentType.PORTFOLIO)).thenThrow(new IllegalStateException("含\"引号\"的错误"));

        List<String> frames = controller.streamChat("s1", "问题", AgentType.PORTFOLIO).collectList().block();

        assertEquals(2, frames.size());
        assertTrue(frames.get(1).contains("\"event\":\"error\""));
        assertTrue(frames.get(1).contains("含\\\"引号\\\"的错误"), "错误信息经 JSON 转义");
    }
}
