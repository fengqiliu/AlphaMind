package com.alphamind.service;

import com.alphamind.agent.AgentRouter;
import com.alphamind.agent.BaseAgent;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.entity.ChatSessionEntity;
import com.alphamind.model.enums.AgentType;
import com.alphamind.repository.AnalysisReportRepository;
import com.alphamind.repository.ChatMessageRepository;
import com.alphamind.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private final MemoryService memory = mock(MemoryService.class);
    private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final AnalysisReportRepository reports = mock(AnalysisReportRepository.class);
    private final AgentRouter router = mock(AgentRouter.class);
    private final BaseAgent agent = mock(BaseAgent.class);
    private final ChatService service = new ChatService(memory, sessions, messages, reports,
            mock(AnalysisReportMapper.class), router);

    private void givenSession(String id) {
        when(sessions.findById(id)).thenReturn(Optional.of(
                ChatSessionEntity.builder().sessionId(id).stockCode("600519").stockName("贵州茅台").build()));
        when(reports.findFirstByStockCodeOrderByCreatedAtDesc("600519")).thenReturn(Optional.empty());
    }

    private void givenRoute(String content) {
        when(router.route(eq(content), any())).thenReturn(
                new AgentRouter.RouteResult(agent, AgentType.TECHNICAL, "怎么看", true));
    }

    @Test
    void replyReturnsEmptyWhenSessionMissing() {
        when(sessions.findById("missing")).thenReturn(Optional.empty());

        assertTrue(service.reply("missing", "你好", AgentType.PORTFOLIO).isEmpty());
        verifyNoInteractions(router, memory);
    }

    @Test
    void replyStripsMentionSavesBothMessagesAndClearsContextAfterChat() {
        givenSession("s1");
        givenRoute("@Technical 怎么看");
        ChatMessage answer = ChatMessage.builder().id("a1").role("assistant").content("偏多").build();
        when(agent.chat(any())).thenReturn(answer);

        ChatMessage reply = service.reply("s1", "@Technical 怎么看", AgentType.PORTFOLIO).orElseThrow();

        assertSame(answer, reply);
        verify(agent).chat(argThat(input -> "怎么看".equals(input.getContent())));
        verify(memory, times(2)).saveMessage(eq("s1"), any());
        InOrder order = inOrder(agent);
        order.verify(agent).setContext("sessionId", "s1");
        order.verify(agent).chat(any());
        order.verify(agent).clearContext();
        verify(sessions).touchSession(eq("s1"), any());
    }

    @Test
    void contextIsClearedEvenWhenAgentThrows() {
        givenSession("s1");
        givenRoute("问题");
        when(agent.chat(any())).thenThrow(new IllegalStateException("llm down"));

        assertThrows(IllegalStateException.class, () -> service.reply("s1", "问题", AgentType.PORTFOLIO));
        verify(agent, atLeast(2)).clearContext();
    }

    @Test
    void persistenceFailureDoesNotFailReply() {
        givenSession("s1");
        givenRoute("问题");
        when(agent.chat(any())).thenReturn(ChatMessage.builder().id("a1").content("答").build());
        when(messages.save(any())).thenThrow(new IllegalStateException("db down"));

        assertTrue(service.reply("s1", "问题", AgentType.PORTFOLIO).isPresent());
    }

    @Test
    void sessionContextCacheIsBoundedAndReloadsEvictedSessions() {
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String first = service.createSession("600519", "贵州茅台");
        for (int i = 0; i < ChatService.SESSION_CACHE_MAX; i++) service.createSession("000001", null);
        assertEquals(ChatService.SESSION_CACHE_MAX, service.cachedSessionCount(), "超过上限的最旧会话被淘汰");
        givenSession(first);
        givenRoute("问题");
        when(agent.chat(any())).thenReturn(ChatMessage.builder().id("a1").content("答").build());

        assertTrue(service.reply(first, "问题", AgentType.PORTFOLIO).isPresent());
        verify(sessions, atLeastOnce()).findById(first);
        assertTrue(service.cachedSessionCount() <= ChatService.SESSION_CACHE_MAX);
    }

    @Test
    void clearSessionRemovesMemoryCacheAndRow() {
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String id = service.createSession("600519", null);

        service.clearSession(id);

        verify(memory).clearSession(id);
        verify(sessions).deleteById(id);
        assertEquals(0, service.cachedSessionCount());
    }
}
