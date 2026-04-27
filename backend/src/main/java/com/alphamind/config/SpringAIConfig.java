package com.alphamind.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI 配置 - 创建 ChatClient Bean
 */
@Configuration
public class SpringAIConfig {

    /**
     * 创建 ChatClient 实例
     * Spring AI 自动配置会根据环境变量创建 ChatModel（OpenAI/DeepSeek/Anthropic）
     * 此处统一包装为 ChatClient 供各 Agent 使用
     */
    @Bean
    @ConditionalOnBean(ChatModel.class)
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个专业的股票分析AI助手，请用中文回答，分析要客观专业。")
                .build();
    }
}
