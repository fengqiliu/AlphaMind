# AlphaMind - 智能股票分析系统

> **版本**: v1.0.0
> **日期**: 2026-04-27
> **作者**: AI Assistant
> **状态**: 初稿
>
> **项目名称**: AlphaMind
> **项目定位**: 基于Spring AI的多Agent智能股票分析系统，通过专业Agent协同与多模型辩论机制，实现更全面、更客观的投资决策支持

---

## 目录

1. [项目概述](#1-项目概述)
2. [系统架构](#2-系统架构)
3. [核心模块设计](#3-核心模块设计)
4. [Agent详细设计](#4-agent详细设计)
5. [辩论系统设计](#5-辩论系统设计)
6. [记忆系统设计](#6-记忆系统设计)
7. [工程能力设计](#7-工程能力设计)
8. [前端设计](#8-前端设计)
9. [数据库设计](#9-数据库设计)
10. [API接口设计](#10-api接口设计)
11. [数据源集成](#11-数据源集成)
12. [部署方案](#12-部署方案)
13. [开发计划](#13-开发计划)

---

## 1. 项目概述

### 1.1 项目背景

随着量化投资和智能投顾的快速发展，传统的单一AI模型分析已难以满足复杂多变的市场需求。**AlphaMind** 旨在构建一个基于Spring AI的多Agent智能股票分析系统，通过多个专业Agent协同工作，结合多模型辩论机制，实现更全面、更客观的股票分析。

### 1.2 核心特性

| 特性           | 描述                                                               |
| -------------- | ------------------------------------------------------------------ |
| **流水线模式** | 四个专业Agent串并行协作：行情采集 → 技术分析 → 舆情分析 → 综合决策 |
| **辩论模式**   | 多LLM多空对抗辩论，仲裁官综合裁决，减少单模型偏见                  |
| **记忆系统**   | 自动召回历史分析结论，保持上下文连贯                               |
| **流式输出**   | SSE实时推送分析进度，提升用户体验                                  |
| **弹性降级**   | 多LLM热切换与熔断机制，保障系统稳定性                              |
| **提示词管理** | 版本化管理，支持快速回滚与A/B测试                                  |

### 1.3 技术栈

| 层级              | 技术选型                              |
| ----------------- | ------------------------------------- |
| **后端框架**      | Spring Boot 3.x + Spring AI           |
| **数据库**        | MySQL 8.0 + Redis 7.x                 |
| **向量存储**      | Milvus / Qdrant / MySQL Vector        |
| **消息队列**      | Redis Pub/Sub (轻量级) / Kafka (可选) |
| **前端框架**      | Vue 3 + Element Plus + ECharts        |
| **数据源**        | Tushare Pro、AKShare                  |
| **LLM Providers** | OpenAI GPT-4、DeepSeek、智谱GLM-4     |
| **容器化**        | Docker + Docker Compose               |
| **CI/CD**         | GitHub Actions                        |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Frontend (Web/Mobile)                          │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐          │
│  │   K线图    │ │ Agent对话   │ │  自选股    │ │  历史报告  │          │
│  │  (ECharts) │ │  (@路由)    │ │   管理     │ │   导出     │          │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘          │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTP/SSE/WebSocket
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                              API Gateway Layer                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│  │  SSE流式端点    │  │   REST API      │  │   WebSocket     │            │
│  │ /stream/chat   │  │   /api/v1/*     │  │   /ws/analysis  │            │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘            │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                          Spring Boot Application                             │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────┐    │
│  │                      Agent Orchestration Layer                      │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │    │
│  │  │  @Router     │  │  Pipeline    │  │  Debate      │            │    │
│  │  │  消息路由    │  │  Orchestrator│  │  Orchestrator│            │    │
│  │  └──────────────┘  └──────────────┘  └──────────────┘            │    │
│  └───────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────┐    │
│  │                           Agent Layer                              │    │
│  │                                                                        │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐          │    │
│  │  │  MarketAgent  │  │ TechnicalAgent│  │ SentimentAgent│          │    │
│  │  │  行情采集Agent │  │  技术分析Agent │  │  舆情分析Agent │          │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘          │    │
│  │                       ┌────────────────┐                              │    │
│  │                       │ PortfolioAgent │                              │    │
│  │                       │  综合决策Agent │                              │    │
│  │                       └────────────────┘                              │    │
│  │                                                                        │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐          │    │
│  │  │   BullAgent   │  │   BearAgent   │  │  NeutralAgent │          │    │
│  │  │   多头辩论    │  │   空头辩论    │  │   中立辩论    │          │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘          │    │
│  │                       ┌────────────────┐                              │    │
│  │                       │ ArbitratorAgent│                              │    │
│  │                       │   仲裁官Agent  │                              │    │
│  │                       └────────────────┘                              │    │
│  └───────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │  Memory System  │  │   LLM Manager   │  │    Prompt Manager       │  │
│  │     记忆系统     │  │   多LLM管理    │  │     提示词版本管理       │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────┘  │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │  Strategy Engine│  │  Rate Limiter   │  │    Circuit Breaker      │  │
│  │      策略系统    │  │     限流熔断    │  │       弹性降级           │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                           External Services                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  Tushare    │  │   AKShare   │  │   东方财富   │  │   OpenAI/DeepSeek│  │
│  │  股票数据   │  │   免费数据   │  │   股吧资讯   │  │   LLM Providers  │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流图

```
用户请求
    │
    ▼
┌─────────────┐
│  消息路由   │ ─── @Agent语法解析
│  Router     │
└─────────────┘
    │
    ├── 无@前缀 ──▶ 投资经理综合处理
    │
    └── 有@前缀 ──▶ 指定Agent处理
                        │
                        ▼
              ┌─────────────────┐
              │   记忆系统      │
              │  召回历史上下文  │
              └─────────────────┘
                        │
                        ▼
              ┌─────────────────┐
              │  Pipeline/辩论  │ ◀── 两种执行模式
              └─────────────────┘
              │               │
              ▼               ▼
    ┌─────────────────┐ ┌─────────────────┐
    │    流水线模式    │ │     辩论模式    │
    │ Market→Tech→    │ │ Bull/Bear/     │
    │ Sentiment→Portfolio│ │ Neutral→Arbitrator │
    └─────────────────┘ └─────────────────┘
              │               │
              └───────┬───────┘
                      ▼
              ┌─────────────────┐
              │   报告生成器     │
              │  JSON+Signal+PDF │
              └─────────────────┘
                      │
                      ▼
              ┌─────────────────┐
              │   SSE流式推送   │
              └─────────────────┘
                      │
                      ▼
              ┌─────────────────┐
              │   记忆存储      │
              │  更新向量数据库 │
              └─────────────────┘
```

---

## 3. 核心模块设计

### 3.1 模块依赖关系

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Application                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Controller Layer                             │   │
│  │  StockAnalysisController | ChatController | ReportController      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                    │                                     │
│  ┌──────────────────────────────────▼─────────────────────────────────┐ │
│  │                        Service Layer                               │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │ │
│  │  │ Stock       │ │ Chat        │ │ Report      │ │ Prompt     │  │ │
│  │  │ Analysis    │ │ Session     │ │ Generation  │ │ Manager    │  │ │
│  │  │ Service     │ │ Service     │ │ Service     │ │ Service    │  │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                    │                                     │
│  ┌──────────────────────────────────▼─────────────────────────────────┐ │
│  │                      Orchestration Layer                          │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                  │ │
│  │  │ Agent       │ │ Pipeline    │ │ Debate      │                  │ │
│  │  │ Router      │ │ Orchestrator│ │ Orchestrator│                  │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘                  │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                    │                                     │
│  ┌──────────────────────────────────▼─────────────────────────────────┐ │
│  │                          Agent Layer                               │ │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                  │ │
│  │  │ Market  │ │Technical│ │Sentiment│ │Portfolio│                  │ │
│  │  │ Agent   │ │ Agent   │ │ Agent   │ │ Agent   │                  │ │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘                  │ │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                  │ │
│  │  │ Bull    │ │ Bear    │ │Neutral  │ │Arbitrator│                  │ │
│  │  │ Agent   │ │ Agent   │ │ Agent   │ │ Agent   │                  │ │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘                  │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                    │                                     │
│  ┌──────────────────────────────────▼─────────────────────────────────┐ │
│  │                       Infrastructure Layer                          │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │ │
│  │  │ LLM Manager │ │ Memory      │ │ Prompt      │ │ Strategy   │ │ │
│  │  │             │ │ System      │ │ Templates   │ │ Engine     │ │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                  │ │
│  │  │ Data Source │ │ Circuit     │ │ Rate        │                  │ │
│  │  │ Adapters    │ │ Breaker     │ │ Limiter     │                  │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘                  │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 包结构设计

```
com.stock.analysis
├── StockAnalysisApplication.java
│
├── config/                              # 配置层
│   ├── SpringAIConfig.java             # ChatClient配置
│   ├── LLMConfig.java                  # 多LLM配置
│   ├── RedisConfig.java                # Redis配置
│   ├── WebSocketConfig.java            # WebSocket配置
│   ├── CorsConfig.java                 # 跨域配置
│   └── SSEConfig.java                  # SSE配置
│
├── controller/                          # 控制器层
│   ├── StockAnalysisController.java    # 股票分析API
│   ├── ChatController.java             # 对话API
│   ├── ReportController.java           # 报告API
│   ├── WatchlistController.java        # 自选股API
│   └── PromptController.java           # 提示词管理API
│
├── service/                             # 服务层
│   ├── StockAnalysisService.java       # 股票分析主服务
│   ├── ChatSessionService.java         # 对话会话服务
│   ├── ReportGenerationService.java    # 报告生成服务
│   ├── WatchlistService.java           # 自选股服务
│   └── PromptManageService.java        # 提示词管理服务
│
├── orchestrator/                        # 编排层
│   ├── AgentRouter.java                # 消息路由器
│   ├── PipelineOrchestrator.java       # 流水线编排器
│   └── DebateOrchestrator.java          # 辩论编排器
│
├── agent/                               # Agent层
│   ├── base/
│   │   ├── AbstractAgent.java          # Agent基类
│   │   ├── AgentType.java              # Agent类型枚举
│   │   ├── AgentContext.java           # Agent上下文
│   │   └── AgentResponse.java          # Agent响应
│   │
│   ├── pipeline/                        # 流水线Agent
│   │   ├── MarketAgent.java            # 行情采集Agent
│   │   ├── TechnicalAgent.java         # 技术分析Agent
│   │   ├── SentimentAgent.java          # 舆情分析Agent
│   │   └── PortfolioAgent.java         # 综合决策Agent
│   │
│   └── debate/                          # 辩论Agent
│       ├── BullAgent.java               # 多头Agent
│       ├── BearAgent.java               # 空头Agent
│       ├── NeutralAgent.java           # 中立Agent
│       └── ArbitratorAgent.java        # 仲裁官Agent
│
├── memory/                              # 记忆系统
│   ├── AgentMemory.java                # Agent记忆接口
│   ├── ChatMemoryImpl.java             # 对话记忆实现
│   ├── VectorMemory.java               # 向量记忆
│   └── MemoryContext.java              # 记忆上下文
│
├── llm/                                 # LLM管理
│   ├── LLMManager.java                  # LLM管理器
│   ├── ChatModelWrapper.java           # 模型包装器
│   ├── CircuitBreakerRegistry.java     # 熔断器注册表
│   └── FallbackStrategy.java           # 降级策略
│
├── prompt/                              # 提示词管理
│   ├── PromptTemplate.java             # 提示词模板
│   ├── PromptVersion.java              # 提示词版本
│   └── PromptManager.java              # 提示词管理器
│
├── datasource/                          # 数据源层
│   ├── DataSourceAdapter.java          # 抽象适配器
│   ├── TushareAdapter.java            # Tushare实现
│   ├── AkShareAdapter.java             # AKShare实现
│   └── DataSourceFactory.java          # 工厂类
│
├── strategy/                            # 策略系统
│   ├── Strategy.java                   # 策略接口
│   ├── StrategyRegistry.java          # 策略注册表
│   ├── ConservativeStrategy.java       # 保守策略
│   ├── AggressiveStrategy.java         # 激进策略
│   └── BalancedStrategy.java           # 平衡策略
│
├── model/                               # 数据模型
│   ├── entity/                          # JPA实体
│   │   ├── Stock.java
│   │   ├── Watchlist.java
│   │   ├── ChatSession.java
│   │   ├── ChatMessage.java
│   │   ├── AnalysisReport.java
│   │   ├── PromptVersion.java
│   │   └── AnalysisHistory.java
│   │
│   ├── dto/                            # DTO
│   │   ├── StockQueryDTO.java
│   │   ├── AnalysisResultDTO.java
│   │   ├── TradeSignalDTO.java
│   │   ├── ConfidenceIntervalDTO.java
│   │   └── DebateResultDTO.java
│   │
│   ├── enums/                          # 枚举
│   │   ├── AgentType.java
│   │   ├── SignalType.java
│   │   ├── ConfidenceLevel.java
│   │   ├── DebatePosition.java
│   │   └── PromptStatus.java
│   │
│   └── vo/                             # Value Object
│       ├── MarketData.java
│       ├── TechnicalIndicators.java
│       ├── SentimentData.java
│       ├── Judgment.java
│       └── PipelineResult.java
│
├── repository/                          # 数据访问层
│   ├── StockRepository.java
│   ├── WatchlistRepository.java
│   ├── ChatSessionRepository.java
│   ├── AnalysisReportRepository.java
│   └── PromptVersionRepository.java
│
├── exception/                           # 异常处理
│   ├── AgentException.java
│   ├── LLMException.java
│   ├── DataSourceException.java
│   └── GlobalExceptionHandler.java
│
└── util/                               # 工具类
    ├── JsonUtil.java
    ├── DateUtil.java
    └── FormatUtil.java
```

---

## 4. Agent详细设计

### 4.1 Agent类型定义

```java
/**
 * Agent类型枚举
 */
public enum AgentType {

    // ========== 流水线Agent ==========
    MARKET("行情采集Agent", "负责采集实时行情、历史K线、股票基本信息"),
    TECHNICAL("技术分析Agent", "负责K线形态分析、MACD/RSI/KDJ指标计算"),
    SENTIMENT("舆情分析Agent", "负责采集和分析股吧、新闻、公告等舆情信息"),
    PORTFOLIO("综合决策Agent", "负责整合各方分析，生成投资建议和风险评估"),

    // ========== 辩论Agent ==========
    BULL("多头Agent", "从看多角度分析股票，提供买入理由"),
    BEAR("空头Agent", "从看空角度分析股票，提示风险因素"),
    NEUTRAL("中立Agent", "客观中立分析，不偏多空"),
    ARBITRATOR("仲裁官Agent", "综合多方观点，做出最终裁决");

    private final String name;
    private final String description;

    AgentType(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
```

### 4.2 Agent基类设计

```java
/**
 * Agent基类 - 所有分析师的父类
 */
public abstract class AbstractAgent {

    protected final String agentId;
    protected final AgentType agentType;
    protected final String agentName;
    protected SystemPrompt prompt;
    protected ChatClient chatClient;

    public AbstractAgent(AgentType agentType) {
        this.agentId = UUID.randomUUID().toString();
        this.agentType = agentType;
        this.agentName = agentType.getName();
    }

    /**
     * 执行Agent分析
     */
    public abstract AgentResponse execute(AgentContext context);

    /**
     * 判断是否能够处理该消息
     */
    public boolean canHandle(String message) {
        // 默认实现，可被子类重写
        return true;
    }

    /**
     * 获取该Agent的Prompt模板
     */
    protected String getPromptTemplate() {
        return promptManager.getActivePrompt(agentType);
    }

    /**
     * 渲染Prompt模板
     */
    protected String renderPrompt(String template, Map<String, Object> params) {
        // 使用Thymeleaf或StringTemplate渲染
        return templateEngine.render(template, params);
    }

    /**
     * 调用LLM
     */
    protected LLMResponse callLLM(String prompt, LLMConfig config) {
        return llmManager.call(prompt, config);
    }
}
```

### 4.3 Agent消息路由 (@语法)

```java
/**
 * 消息路由器 - 支持 @AgentName 语法
 */
@Component
@RequiredArgsConstructor
public class AgentRouter {

    private final Map<AgentType, AbstractAgent> agentMap;

    // @AgentName 消息正则
    private static final Pattern AT_PATTERN = Pattern.compile(
        "@(\\w+Agent)\\s+(.+)", Pattern.CASE_INSENSITIVE
    );

    /**
     * 路由消息到对应的Agent
     */
    public RouteResult route(String message, ChatSession session) {
        Matcher matcher = AT_PATTERN.matcher(message);

        if (matcher.matches()) {
            String agentName = matcher.group(1);
            String content = matcher.group(2);
            AgentType agentType = resolveAgentType(agentName);

            if (agentType != null) {
                AbstractAgent agent = agentMap.get(agentType);
                return RouteResult.builder()
                    .agent(agent)
                    .content(content)
                    .isAtMention(true)
                    .build();
            }
        }

        // 无@前缀 → 默认路由到投资经理(Pipeline入口)
        return RouteResult.builder()
            .agent(agentMap.get(AgentType.PORTFOLIO))
            .content(message)
            .isAtMention(false)
            .build();
    }

    /**
     * 解析Agent名称到AgentType
     */
    private AgentType resolveAgentType(String name) {
        String normalized = name.replace("Agent", "").toUpperCase();
        try {
            return AgentType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

/**
 * 路由结果
 */
@Data
@Builder
public class RouteResult {
    private AbstractAgent agent;
    private String content;
    private boolean isAtMention;
}
```

### 4.4 各Agent职责说明

#### 4.4.1 行情采集Agent (MarketAgent)

```java
/**
 * 行情采集Agent
 * 职责: 采集股票的实时行情、历史K线、基本信息等
 */
@Service
@RequiredArgsConstructor
public class MarketAgent extends AbstractAgent {

    private final DataSourceFactory dataSourceFactory;

    public MarketAgent() {
        super(AgentType.MARKET);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        String stockCode = context.getStockCode();

        // 采集实时行情
        MarketData realtime = fetchRealtimeQuote(stockCode);

        // 采集历史K线
        KLineHistory kline = fetchKLineHistory(stockCode,
            context.getKlineDays() != null ? context.getKlineDays() : 250);

        // 采集基本信息
        StockBasicInfo basicInfo = fetchBasicInfo(stockCode);

        return AgentResponse.builder()
            .agentType(AgentType.MARKET)
            .success(true)
            .data(MarketDataVO.builder()
                .realtime(realtime)
                .klineHistory(kline)
                .basicInfo(basicInfo)
                .fetchTime(LocalDateTime.now())
                .build())
            .build();
    }

    /**
     * 获取实时行情
     */
    private MarketData fetchRealtimeQuote(String stockCode) {
        DataSourceAdapter adapter = dataSourceFactory.getAdapter("tushare");
        return adapter.getRealtimeQuote(stockCode);
    }

    /**
     * 获取历史K线
     */
    private KLineHistory fetchKLineHistory(String stockCode, int days) {
        DataSourceAdapter adapter = dataSourceFactory.getAdapter("akshare");
        return adapter.getKLineHistory(stockCode, days);
    }

    /**
     * 获取股票基本信息
     */
    private StockBasicInfo fetchBasicInfo(String stockCode) {
        DataSourceAdapter adapter = dataSourceFactory.getAdapter("tushare");
        return adapter.getBasicInfo(stockCode);
    }
}
```

#### 4.4.2 技术分析Agent (TechnicalAgent)

```java
/**
 * 技术分析Agent
 * 职责: K线形态分析、MACD/RSI/KDJ/MA等指标计算
 */
@Service
@RequiredArgsConstructor
public class TechnicalAgent extends AbstractAgent {

    private final TechnicalIndicatorCalculator calculator;

    public TechnicalAgent() {
        super(AgentType.TECHNICAL);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        KLineHistory kline = context.getMarketData().getKlineHistory();

        // 计算各技术指标
        MACDResult macd = calculator.calculateMACD(kline);
        RSIResult rsi = calculator.calculateRSI(kline, 14);
        KDJResult kdj = calculator.calculateKDJ(kline, 9, 3, 3);
        MAResult ma = calculator.calculateMA(kline, Arrays.asList(5, 10, 20, 60));

        // K线形态识别
        PatternResult patterns = patternRecognizer.recognize(kline);

        // 综合评分
        double technicalScore = calculateTechnicalScore(macd, rsi, kdj, patterns);

        return AgentResponse.builder()
            .agentType(AgentType.TECHNICAL)
            .success(true)
            .data(TechnicalResultVO.builder()
                .macd(macd)
                .rsi(rsi)
                .kdj(kdj)
                .ma(ma)
                .patterns(patterns)
                .technicalScore(technicalScore)
                .summary(generateSummary(macd, rsi, kdj, patterns))
                .confidence(calculateConfidence(macd, rsi, kdj))
                .build())
            .build();
    }

    /**
     * 计算综合技术评分 (0-100)
     */
    private double calculateTechnicalScore(MACDResult macd, RSIResult rsi,
                                          KDJResult kdj, PatternResult patterns) {
        double score = 50; // 基准分

        // MACD评分 (权重: 30%)
        if (macd.getHistogram() > 0) score += 15;
        if (macd.getDif() > macd.getDea()) score += 15;

        // RSI评分 (权重: 25%)
        if (rsi.getValue() > 70) score -= 10; // 超买
        else if (rsi.getValue() < 30) score += 10; // 超卖
        else if (rsi.getValue() > 50) score += 5;

        // KDJ评分 (权重: 25%)
        if (kdj.getJ() > 80) score -= 5;
        else if (kdj.getJ() < 20) score += 10;

        // 形态评分 (权重: 20%)
        score += patterns.getScoreBonus();

        return Math.max(0, Math.min(100, score));
    }
}
```

#### 4.4.3 舆情分析Agent (SentimentAgent)

```java
/**
 * 舆情分析Agent
 * 职责: 采集和分析股吧、新闻、公告等舆情信息
 */
@Service
@RequiredArgsConstructor
public class SentimentAgent extends AbstractAgent {

    private final NewsCollector newsCollector;
    private final SentimentAnalyzer sentimentAnalyzer;

    public SentimentAgent() {
        super(AgentType.SENTIMENT);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        String stockCode = context.getStockCode();
        String stockName = context.getMarketData().getBasicInfo().getName();

        // 采集舆情数据
        List<NewsItem> news = newsCollector.collectNews(stockCode, stockName, 30);
        List<ForumPost> posts = newsCollector.collectForumPosts(stockName, 100);
        List<Announcement> announcements = newsCollector.collectAnnouncements(stockCode);

        // 情感分析
        SentimentResult newsSentiment = sentimentAnalyzer.analyze(news);
        SentimentResult forumSentiment = sentimentAnalyzer.analyze(posts);

        // 热点事件提取
        List<HotEvent> hotEvents = extractHotEvents(news, posts);

        // 综合舆情评分
        double sentimentScore = calculateSentimentScore(newsSentiment, forumSentiment);

        return AgentResponse.builder()
            .agentType(AgentType.SENTIMENT)
            .success(true)
            .data(SentimentResultVO.builder()
                .news(news)
                .forumPosts(posts)
                .announcements(announcements)
                .newsSentiment(newsSentiment)
                .forumSentiment(forumSentiment)
                .hotEvents(hotEvents)
                .sentimentScore(sentimentScore)
                .summary(generateSentimentSummary(newsSentiment, forumSentiment, hotEvents))
                .confidence(calculateConfidence(news, posts))
                .build())
            .build();
    }

    /**
     * 计算舆情综合评分 (-100 到 +100)
     */
    private double calculateSentimentScore(SentimentResult newsSentiment,
                                           SentimentResult forumSentiment) {
        // 新闻权重60%，股吧权重40%
        return newsSentiment.getScore() * 0.6 + forumSentiment.getScore() * 0.4;
    }
}
```

#### 4.4.4 综合决策Agent (PortfolioAgent)

```java
/**
 * 综合决策Agent
 * 职责: 整合所有分析结果，生成投资建议和风险评估
 */
@Service
@RequiredArgsConstructor
public class PortfolioAgent extends AbstractAgent {

    private final RiskCalculator riskCalculator;
    private final SignalGenerator signalGenerator;

    public PortfolioAgent() {
        super(AgentType.PORTFOLIO);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        PipelineResult pipeline = context.getPipelineResult();

        // 整合分析结果
        AnalysisSummary summary = synthesizeAnalysis(pipeline);

        // 生成交易信号
        TradeSignal signal = signalGenerator.generate(pipeline);

        // 风险评估
        RiskAssessment risk = riskCalculator.assess(pipeline);

        // 置信区间
        ConfidenceInterval confidence = calculateOverallConfidence(pipeline);

        return AgentResponse.builder()
            .agentType(AgentType.PORTFOLIO)
            .success(true)
            .data(PortfolioResultVO.builder()
                .summary(summary)
                .tradeSignal(signal)
                .riskAssessment(risk)
                .confidence(confidence)
                .targetPrice(signal.getTargetPrice())
                .stopLoss(signal.getStopLoss())
                .entryPrice(signal.getEntryPrice())
                .holdingPeriodDays(signal.getHoldingPeriodDays())
                .build())
            .build();
    }
}
```

---

## 5. 辩论系统设计

### 5.1 辩论系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         辩论系统架构图                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐             │
│    │   Pipeline │────▶│  Debate     │────▶│   Report    │             │
│    │   Result   │     │  Input      │     │   Generator │             │
│    └─────────────┘     └─────────────┘     └─────────────┘             │
│                               │                                          │
│                               ▼                                          │
│    ┌──────────────────────────────────────────────────────────────┐    │
│    │                      辩论阶段 (并行执行)                       │    │
│    │                                                                    │    │
│    │    ┌──────────┐    ┌──────────┐    ┌──────────┐               │    │
│    │    │ BullAgent│    │BearAgent │    │Neutral   │               │    │
│    │    │  多头    │    │  空头    │    │  中立    │               │    │
│    │    └──────────┘    └──────────┘    └──────────┘               │    │
│    │         │               │               │                       │    │
│    └─────────┼───────────────┼───────────────┼───────────────────────┘    │
│              │               │               │                              │
│              ▼               ▼               ▼                              │
│    ┌──────────────────────────────────────────────────────────────┐    │
│    │                   交叉质疑阶段 (可选)                          │    │
│    │                                                                    │    │
│    │    Bull质疑Bear ──▶ Bear反驳Bull ──▶ Neutral调解               │    │
│    │                                                                    │    │
│    └──────────────────────────────────────────────────────────────┘    │
│              │               │               │                              │
│              └───────────────┼───────────────┘                              │
│                              ▼                                              │
│                    ┌──────────────────┐                                    │
│                    │  ArbitratorAgent │                                    │
│                    │      仲裁官       │                                    │
│                    └──────────────────┘                                    │
│                              │                                              │
│                              ▼                                              │
│                    ┌──────────────────┐                                    │
│                    │    Judgment      │                                    │
│                    │    最终裁决       │                                    │
│                    └──────────────────┘                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.2 辩论Agent实现

#### 5.2.1 多头Agent (BullAgent)

```java
/**
 * 多头辩论Agent
 * 职责: 从看多角度分析股票，提供买入理由
 */
@Service
@RequiredArgsConstructor
public class BullAgent extends AbstractAgent {

    private static final String BULL_SYSTEM_PROMPT = """
        你是一位资深看多派股票分析师，专注于发掘股票的上涨潜力。

        你的分析风格:
        1. 乐观但不盲目，关注支撑多头逻辑的核心因素
        2. 善于发现被市场低估的正面信息
        3. 重点关注: 趋势转强、基本面改善、催化剂事件、资金流入等

        请基于提供的研究数据、技术分析和舆情信息，从多角度给出看多理由。
        """;

    public BullAgent() {
        super(AgentType.BULL);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        PipelineResult pipeline = context.getPipelineResult();

        // 构建看多Prompt
        String prompt = buildBullPrompt(pipeline);

        // 调用LLM
        LLMResponse response = callLLM(prompt, LLMConfig.builder()
            .model("deepseek")
            .temperature(0.7)
            .maxTokens(2000)
            .build());

        // 解析响应
        BullView bullView = parseBullView(response.getContent());

        return AgentResponse.builder()
            .agentType(AgentType.BULL)
            .success(true)
            .data(DebateResultVO.builder()
                .position(DebatePosition.BULLISH)
                .view(bullView.getView())
                .reasons(bullView.getReasons())
                .targetPrice(bullView.getTargetPrice())
                .upsidePotential(bullView.getUpsidePotential())
                .confidence(bullView.getConfidence())
                .keyPoints(bullView.getKeyPoints())
                .attackPoints(bullView.getAttackPoints()) // 攻击空头的论点
                .build())
            .build();
    }

    private String buildBullPrompt(PipelineResult pipeline) {
        return String.format("""
            基于以下分析数据，请给出看多该股票的理由:

            === 基本面 ===
            %s

            === 技术分析 ===
            %s

            === 舆情分析 ===
            %s

            请以JSON格式输出:
            {
                "view": "总体看多观点(200字以内)",
                "reasons": ["理由1", "理由2", "理由3"],
                "targetPrice": 目标价(数字),
                "upsidePotential": "上涨空间(百分比)",
                "confidence": {"value": 0.0-1.0, "level": "HIGH/MEDIUM/LOW"},
                "keyPoints": ["核心看点1", "核心看点2"],
                "attackPoints": ["反驳空头的论点1", "论点2"]
            }
            """,
            pipeline.getMarketAnalysis(),
            pipeline.getTechnicalAnalysis(),
            pipeline.getSentimentAnalysis()
        );
    }
}
```

#### 5.2.2 空头Agent (BearAgent)

```java
/**
 * 空头辩论Agent
 * 职责: 从看空角度分析股票，提示风险因素
 */
@Service
@RequiredArgsConstructor
public class BearAgent extends AbstractAgent {

    private static final String BEAR_SYSTEM_PROMPT = """
        你是一位资深看空派股票分析师，专注于识别股票的风险和问题。

        你的分析风格:
        1. 谨慎但不悲观，关注可能导致下跌的风险因素
        2. 善于发现市场忽视的负面信号
        3. 重点关注: 趋势破位、基本面恶化、估值过高、负面催化剂等

        请基于提供的研究数据、技术分析和舆情信息，从多角度给出看空理由。
        """;

    public BearAgent() {
        super(AgentType.BEAR);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        // 实现类似BullAgent，但生成看空观点
        // ...
    }
}
```

#### 5.2.3 中立Agent (NeutralAgent)

```java
/**
 * 中立辩论Agent
 * 职责: 客观中立分析，不偏多空
 */
@Service
@RequiredArgsConstructor
public class NeutralAgent extends AbstractAgent {

    private static final String NEUTRAL_SYSTEM_PROMPT = """
        你是一位客观中立的股票分析师，不偏多空，关注事实和数据。

        你的分析风格:
        1. 完全基于数据和分析，不带主观倾向
        2. 同时列出利多和利空因素
        3. 关注不确定性因素和关键观察点

        请基于提供的研究数据、技术分析和舆情信息，给出客观分析。
        """;

    public NeutralAgent() {
        super(AgentType.NEUTRAL);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        // 实现中立分析
        // ...
    }
}
```

#### 5.2.4 仲裁官Agent (ArbitratorAgent)

```java
/**
 * 仲裁官Agent
 * 职责: 综合多方观点，做出最终裁决
 */
@Service
@RequiredArgsConstructor
public class ArbitratorAgent extends AbstractAgent {

    private static final String ARBITRATOR_SYSTEM_PROMPT = """
        你是一位资深的股票投资仲裁官，负责综合多方分析师的观点，
        做出最终客观的投资决策。

        你的职责:
        1. 权衡多空双方的论点和证据
        2. 评估各方观点的可信度
        3. 综合考虑基本面、技术面、舆情等多维因素
        4. 给出最终裁决，包括: 投资建议、置信度、风险提示

        仲裁原则:
        - 证据权重 > 论点数量
        - 逻辑一致性 > 表面相似
        - 不被极端观点带偏
        """;

    public ArbitratorAgent() {
        super(AgentType.ARBITRATOR);
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        List<DebateResultVO> debateResults = context.getDebateResults();

        // 构建仲裁Prompt
        String prompt = buildArbitrationPrompt(debateResults);

        // 调用LLM
        LLMResponse response = callLLM(prompt, LLMConfig.builder()
            .model("gpt-4") // 使用最强模型
            .temperature(0.3) // 降低随机性
            .maxTokens(3000)
            .build());

        // 解析裁决
        Judgment judgment = parseJudgment(response.getContent());

        return AgentResponse.builder()
            .agentType(AgentType.ARBITRATOR)
            .success(true)
            .data(JudgmentVO.builder()
                .finalPosition(judgment.getPosition())
                .confidence(judgment.getConfidence())
                .reasoning(judgment.getReasoning())
                .voteBreakdown(judgment.getVoteBreakdown())
                .riskWarnings(judgment.getRiskWarnings())
                .finalSignal(judgment.getFinalSignal())
                .build())
            .build();
    }

    private String buildArbitrationPrompt(List<DebateResultVO> results) {
        StringBuilder prompt = new StringBuilder("""
            作为仲裁官，请综合以下多方分析师的观点，做出最终裁决。

            === 多头观点 ===
            """);

        results.stream()
            .filter(r -> r.getPosition() == DebatePosition.BULLISH)
            .findFirst()
            .ifPresent(r -> prompt.append(r.getView()));

        prompt.append("\n\n=== 空头观点 ===\n");
        results.stream()
            .filter(r -> r.getPosition() == DebatePosition.BEARISH)
            .findFirst()
            .ifPresent(r -> prompt.append(r.getView()));

        prompt.append("\n\n=== 中立观点 ===\n");
        results.stream()
            .filter(r -> r.getPosition() == DebatePosition.NEUTRAL)
            .findFirst()
            .ifPresent(r -> prompt.append(r.getView()));

        prompt.append("""
            请以JSON格式输出最终裁决:
            {
                "finalPosition": "BULLISH/BEARISH/NEUTRAL",
                "confidence": {"value": 0.0-1.0, "level": "HIGH/MEDIUM/LOW"},
                "reasoning": "裁决理由(300字以内)",
                "voteBreakdown": {"bull": 0-10, "bear": 0-10, "neutral": 0-10},
                "riskWarnings": ["风险提示1", "风险提示2"],
                "finalSignal": {"type": "BUY/SELL/HOLD", "entryPrice": 0.0, "targetPrice": 0.0, "stopLoss": 0.0}
            }
            """);

        return prompt.toString();
    }
}
```

### 5.3 辩论编排器

```java
/**
 * 辩论编排器
 * 负责管理辩论流程
 */
@Component
@RequiredArgsConstructor
public class DebateOrchestrator {

    private final BullAgent bullAgent;
    private final BearAgent bearAgent;
    private final NeutralAgent neutralAgent;
    private final ArbitratorAgent arbitratorAgent;

    /**
     * 执行完整辩论流程
     */
    public DebateResult execute(PipelineResult pipeline) {
        log.info("启动辩论系统");

        // 阶段1: 并行执行三方辩论
        DebateResultVO[] debateResults = parallelDebate(pipeline);

        // 阶段2: 交叉质疑 (可选)
        List<DebateResultVO> crossExamined = crossExamination(debateResults, pipeline);

        // 阶段3: 仲裁官裁决
        JudgmentVO judgment = arbitratorAgent.execute(AgentContext.builder()
            .pipelineResult(pipeline)
            .debateResults(crossExamined)
            .build());

        return DebateResult.builder()
            .bullView(debateResults[0])
            .bearView(debateResults[1])
            .neutralView(debateResults[2])
            .judgment(judgment)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * 并行执行三方辩论
     */
    private DebateResultVO[] parallelDebate(PipelineResult pipeline) {
        AgentContext context = AgentContext.builder()
            .pipelineResult(pipeline)
            .build();

        CompletableFuture<DebateResultVO> bullFuture =
            CompletableFuture.supplyAsync(() -> bullAgent.execute(context));
        CompletableFuture<DebateResultVO> bearFuture =
            CompletableFuture.supplyAsync(() -> bearAgent.execute(context));
        CompletableFuture<DebateResultVO> neutralFuture =
            CompletableFuture.supplyAsync(() -> neutralAgent.execute(context));

        try {
            return CompletableFuture.allOf(bullFuture, bearFuture, neutralFuture)
                .thenApply(v -> new DebateResultVO[]{
                    bullFuture.join(),
                    bearFuture.join(),
                    neutralFuture.join()
                })
                .get(60, TimeUnit.SECONDS); // 60秒超时
        } catch (Exception e) {
            log.error("辩论执行异常", e);
            throw new DebateException("辩论执行失败", e);
        }
    }

    /**
     * 交叉质疑轮次
     */
    private List<DebateResultVO> crossExamination(
            DebateResultVO[] results, PipelineResult pipeline) {

        AgentContext context = AgentContext.builder()
            .pipelineResult(pipeline)
            .build();

        // Bull质疑Bear
        context.setChallengerView(results[0]);
        context.setTargetView(results[1]);
        DebateResultVO bullChallenge = bullAgent.challenge(context);

        // Bear反驳Bull
        context.setChallengerView(results[1]);
        context.setTargetView(results[0]);
        DebateResultVO bearDefense = bearAgent.defend(context);

        return Arrays.asList(
            bullChallenge,
            bearDefense,
            results[2] // Neutral保持不变
        );
    }
}
```

### 5.4 辩论数据结构

```java
/**
 * 辩论立场枚举
 */
public enum DebatePosition {
    BULLISH("多头", "看涨"),
    BEARISH("空头", "看跌"),
    NEUTRAL("中立", "观望");

    private final String name;
    private final String description;
}

/**
 * 辩论结果
 */
@Data
@Builder
public class DebateResultVO {

    private DebatePosition position;
    private String view;                    // 核心观点
    private List<String> reasons;          // 理由列表
    private BigDecimal targetPrice;        // 目标价
    private String upsidePotential;        // 上涨空间
    private ConfidenceInterval confidence; // 置信度
    private List<String> keyPoints;        // 关键看点
    private List<String> attackPoints;     // 攻击对方的论点
}

/**
 * 仲裁裁决
 */
@Data
@Builder
public class JudgmentVO {

    private DebatePosition finalPosition;  // 最终立场
    private ConfidenceInterval confidence; // 置信度
    private String reasoning;              // 裁决理由

    @Builder.Default
    private Map<DebatePosition, Integer> voteBreakdown = new HashMap<>();

    private List<String> riskWarnings;    // 风险提示
    private TradeSignalVO finalSignal;     // 最终交易信号
}

/**
 * 完整辩论结果
 */
@Data
@Builder
public class DebateResult {

    private DebateResultVO bullView;
    private DebateResultVO bearView;
    private DebateResultVO neutralView;
    private JudgmentVO judgment;
    private LocalDateTime timestamp;
}
```

---

## 6. 记忆系统设计

### 6.1 记忆系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           记忆系统架构                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                       Agent Memory Layer                          │   │
│  │                                                                      │   │
│  │    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │   │
│  │    │ 短期记忆    │  │ 中期记忆    │  │ 长期记忆    │             │   │
│  │    │ ChatMemory  │  │ Summary     │  │ VectorStore │             │   │
│  │    │ (会话级)    │  │ (日/周级)   │  │ (永久)      │             │   │
│  │    └─────────────┘  └─────────────┘  └─────────────┘             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                    │                                     │
│                                    ▼                                     │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Memory Manager                              │   │
│  │                                                                      │   │
│  │    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │   │
│  │    │   Store     │  │   Recall    │  │   Evict     │             │   │
│  │    │   存储      │  │   召回      │  │   淘汰      │             │   │
│  │    └─────────────┘  └─────────────┘  └─────────────┘             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                    │                                     │
│                                    ▼                                     │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Storage Layer                                │   │
│  │                                                                      │   │
│  │    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │   │
│  │    │    Redis    │  │    MySQL    │  │   VectorDB  │             │   │
│  │    │  (高速缓存)  │  │  (持久化)    │  │  (语义检索) │             │   │
│  │    └─────────────┘  └─────────────┘  └─────────────┘             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.2 记忆系统实现

```java
/**
 * Agent记忆接口
 */
public interface AgentMemory {

    /**
     * 存储记忆
     */
    void remember(AnalysisRecord record);

    /**
     * 召回相关记忆
     */
    List<AnalysisRecord> recall(String stockCode, String query, int limit);

    /**
     * 构建带记忆的上下文
     */
    AgentContext buildContext(String stockCode, String currentQuery);

    /**
     * 清理过期记忆
     */
    void evictExpired();
}

/**
 * 记忆实现
 */
@Service
@RequiredArgsConstructor
public class AgentMemoryImpl implements AgentMemory {

    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;
    private final AnalysisHistoryRepository historyRepository;

    @Override
    public void remember(AnalysisRecord record) {
        // 1. 存储到对话记忆 (短期)
        chatMemory.add(record.toChatMessage());

        // 2. 持久化到数据库 (中期)
        historyRepository.save(record.toEntity());

        // 3. 存储到向量数据库 (长期)
        vectorStore.store(VectorDocument.builder()
            .id(record.getId())
            .content(record.toText())
            .metadata(Map.of(
                "stockCode", record.getStockCode(),
                "agentType", record.getAgentType().name(),
                "signalType", record.getSignalType() != null ?
                    record.getSignalType().name() : "",
                "timestamp", record.getTimestamp().toString()
            ))
            .embedding(embeddingModel.embed(record.toText()))
            .build());

        log.info("记忆已存储: stockCode={}, agentType={}",
            record.getStockCode(), record.getAgentType());
    }

    @Override
    public List<AnalysisRecord> recall(String stockCode, String query, int limit) {
        // 1. 向量相似度检索
        List<AnalysisRecord> semantic = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(limit * 2) // 多取一些用于过滤
                .filterExpression("stockCode == '" + stockCode + "'")
                .build()
        ).stream()
            .map(this::toRecord)
            .collect(toList());

        // 2. 获取该股票最近的记录
        List<AnalysisRecord> recent = historyRepository
            .findTopNByStockCodeOrderByTimestampDesc(stockCode, limit)
            .stream()
            .map(this::toRecord)
            .collect(toList());

        // 3. 合并去重，按时间+相关性排序
        Map<String, AnalysisRecord> merged = new LinkedHashMap<>();
        semantic.forEach(r -> merged.put(r.getId(), r));
        recent.forEach(r -> merged.put(r.getId(), r));

        return merged.values().stream()
            .sorted(Comparator
                .comparing(AnalysisRecord::getTimestamp).reversed())
            .limit(limit)
            .collect(toList());
    }

    @Override
    public AgentContext buildContext(String stockCode, String currentQuery) {
        // 召回相关历史
        List<AnalysisRecord> history = recall(stockCode, currentQuery, 5);

        // 构建历史摘要
        String historySummary = buildHistorySummary(history);

        return AgentContext.builder()
            .stockCode(stockCode)
            .currentQuery(currentQuery)
            .historicalAnalyses(history)
            .historySummary(historySummary)
            .chatHistory(chatMemory.getRecent(stockCode, 10))
            .build();
    }

    /**
     * 构建历史摘要
     */
    private String buildHistorySummary(List<AnalysisRecord> history) {
        if (history.isEmpty()) {
            return "暂无该股票的历史分析记录。";
        }

        StringBuilder summary = new StringBuilder("=== 历史分析摘要 ===\n\n");

        for (int i = 0; i < history.size(); i++) {
            AnalysisRecord record = history.get(i);
            summary.append(String.format("[%d] %s (%s)\n",
                i + 1,
                record.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE),
                record.getAgentType().getName()));
            summary.append(String.format("    信号: %s, 置信度: %s\n",
                record.getSignalType(),
                record.getConfidence()));
            summary.append(String.format("    摘要: %s\n\n",
                record.getSummary()));
        }

        return summary.toString();
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void evictExpired() {
        // 清理超过6个月的历史记录
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);
        int deleted = historyRepository.deleteByTimestampBefore(cutoff);
        log.info("清理过期记忆: {} 条", deleted);
    }
}
```

### 6.3 记忆上下文构建

```java
/**
 * Agent上下文
 */
@Data
@Builder
public class AgentContext {

    private String stockCode;
    private String currentQuery;

    // 来自流水线的分析结果
    private PipelineResult pipelineResult;

    // 辩论结果
    private List<DebateResultVO> debateResults;

    // 记忆相关
    private List<AnalysisRecord> historicalAnalyses;
    private String historySummary;
    private List<ChatMessage> chatHistory;

    // 配置
    private LLMConfig llmConfig;
}

/**
 * 分析记录
 */
@Entity
@Table(name = "analysis_history")
@Data
public class AnalysisHistory {

    @Id
    private String id;

    private String stockCode;
    private String stockName;

    @Enumerated(EnumType.STRING)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    private SignalType signalType;

    private Double confidence;
    private String confidenceLevel;

    private String summary;
    private String fullContent;       // 完整分析内容(JSON)

    private String modelUsed;          // 使用的模型

    @Column(columnDefinition = "TEXT")
    private String vectorEmbedding;    // 向量(序列化存储)

    private LocalDateTime timestamp;
    private LocalDateTime createdAt;

    // 转换为Record
    public AnalysisRecord toRecord() {
        return AnalysisRecord.builder()
            .id(id)
            .stockCode(stockCode)
            .stockName(stockName)
            .agentType(agentType)
            .signalType(signalType)
            .confidence(confidence)
            .confidenceLevel(confidenceLevel)
            .summary(summary)
            .fullContent(fullContent)
            .timestamp(timestamp)
            .build();
    }
}
```

---

## 7. 工程能力设计

### 7.1 SSE流式输出

```java
/**
 * SSE流式输出服务
 */
@Service
@RequiredArgsConstructor
public class StreamAnalysisService {

    private final PipelineOrchestrator pipelineOrchestrator;
    private final DebateOrchestrator debateOrchestrator;

    /**
     * SSE流式分析
     */
    public Flux<SseEvent> streamAnalysis(String stockCode) {
        return Flux.create(sink -> {
            try {
                // 阶段1: 开始
                sink.next(SseEvent.stage("START", "开始分析..."));

                // 阶段2: 行情采集
                sink.next(SseEvent.stage("MARKET", "正在采集行情数据..."));
                MarketData marketData = marketAgent.execute(context);
                sink.next(SseEvent.data("MARKET", marketData));

                // 阶段3: 技术分析
                sink.next(SseEvent.stage("TECHNICAL", "正在进行技术分析..."));
                TechnicalResult technical = technicalAgent.execute(context);
                sink.next(SseEvent.data("TECHNICAL", technical));

                // 阶段4: 舆情分析
                sink.next(SseEvent.stage("SENTIMENT", "正在分析舆情..."));
                SentimentResult sentiment = sentimentAgent.execute(context);
                sink.next(SseEvent.data("SENTIMENT", sentiment));

                // 阶段5: 综合决策
                sink.next(SseEvent.stage("PORTFOLIO", "正在生成投资建议..."));
                PortfolioResult portfolio = portfolioAgent.execute(context);
                sink.next(SseEvent.data("PORTFOLIO", portfolio));

                // 阶段6: 辩论裁决
                sink.next(SseEvent.stage("DEBATE", "正在进行多空辩论..."));
                DebateResult debate = debateOrchestrator.execute(pipeline);
                sink.next(SseEvent.data("DEBATE", debate));

                // 完成
                sink.next(SseEvent.complete());

            } catch (Exception e) {
                log.error("SSE分析异常", e);
                sink.error(e);
            }
        });
    }
}

/**
 * SSE事件
 */
@Data
@Builder
public class SseEvent {
    private String event;  // START, STAGE, DATA, ERROR, COMPLETE
    private String stage; // 当前阶段
    private String message; // 消息
    private Object data;  // 数据

    public static SseEvent stage(String stage, String message) {
        return SseEvent.builder()
            .event("stage")
            .stage(stage)
            .message(message)
            .build();
    }

    public static SseEvent data(String stage, Object data) {
        return SseEvent.builder()
            .event("data")
            .stage(stage)
            .data(data)
            .build();
    }

    public static SseEvent complete() {
        return SseEvent.builder()
            .event("complete")
            .message("分析完成")
            .build();
    }
}

/**
 * SSE控制器
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StreamAnalysisController {

    private final StreamAnalysisService streamService;

    @GetMapping(value = "/stream/analysis/{stockCode}",
               produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamAnalysis(@PathVariable String stockCode) {
        return streamService.streamAnalysis(stockCode)
            .map(event -> ServerSentEvent.<String>builder()
                .id(UUID.randomUUID().toString())
                .event(event.getEvent())
                .data(JsonUtil.toJson(event))
                .build());
    }
}
```

### 7.2 多LLM热切换与弹性降级

```java
/**
 * LLM管理器
 */
@Service
@RequiredArgsConstructor
public class LLMManager {

    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, ModelConfig> modelConfigs = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    /**
     * 注册模型
     */
    public void registerModel(String name, ChatModel model, ModelConfig config) {
        models.put(name, model);
        modelConfigs.put(name, config);

        // 注册熔断器
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

        circuitBreakerRegistry.circuitBreaker(name, cbConfig);

        // 注册重试
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();

        retryRegistry.retryRegistry(name, retryConfig);

        log.info("模型已注册: name={}, config={}", name, config);
    }

    /**
     * 智能选择最佳可用模型
     */
    public ChatModel getAvailableModel(String preferred) {
        // 1. 检查首选模型
        ChatModel model = models.get(preferred);
        if (model == null) {
            throw new ModelNotFoundException(preferred);
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(preferred);

        try {
            // 检查熔断器状态和模型健康状态
            return cb.executeSupplier(() -> {
                if (!model.isHealthy()) {
                    throw new ModelUnavailableException(preferred);
                }
                return model;
            });
        } catch (Exception e) {
            log.warn("模型 {} 不可用: {}, 开始降级", preferred, e.getMessage());
            return fallbackToAvailable(preferred);
        }
    }

    /**
     * 降级到可用模型
     */
    private ChatModel fallbackToAvailable(String failedModel) {
        // 按优先级尝试备选模型
        List<String> priority = Arrays.asList(
            "deepseek", "zhipu", "openai", "local"
        );

        // 移除失败的模型
        priority = priority.stream()
            .filter(m -> !m.equals(failedModel))
            .collect(toList());

        for (String candidate : priority) {
            ChatModel model = models.get(candidate);
            if (model != null && model.isHealthy()) {
                CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(candidate);
                try {
                    return cb.executeSupplier(() -> model);
                } catch (Exception e) {
                    log.warn("备选模型 {} 也不可用", candidate);
                }
            }
        }

        throw new AllModelsUnavailableException(
            "所有模型都不可用，请稍后重试");
    }

    /**
     * 调用LLM (带重试和熔断)
     */
    public LLMResponse call(String prompt, LLMConfig config) {
        ChatModel model = getAvailableModel(config.getModel());

        Retry retry = retryRegistry.retry(config.getModel());
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(config.getModel());

        Supplier<LLMResponse> supplier = () -> {
            long start = System.currentTimeMillis();

            ChatResponse response = model.call(new Prompt(prompt,
                ChatOptions.builder()
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .build()));

            long latency = System.currentTimeMillis() - start;

            return LLMResponse.builder()
                .content(response.getResult().getOutput().getContent())
                .model(config.getModel())
                .tokensUsed(estimateTokens(prompt, response))
                .latency(latency)
                .timestamp(LocalDateTime.now())
                .build();
        };

        // 组合重试和熔断
        return cb.execute(retry.execute(supplier));
    }
}

/**
 * 模型配置
 */
@Data
@Builder
public class ModelConfig {
    private String name;
    private String endpoint;           // API端点
    private String apiKey;             // API密钥
    private double temperature;         // 默认温度
    private int maxTokens;             // 最大Token数
    private int priority;              // 优先级(越小越高)
    private boolean enabled;           // 是否启用
    private Map<String, String> extraParams; // 额外参数
}
```

### 7.3 提示词版本管理

```java
/**
 * 提示词版本实体
 */
@Entity
@Table(name = "prompt_versions")
@Data
public class PromptVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AgentType agentType;

    private Integer version;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String variables;  // JSON Schema

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PromptStatus status = PromptStatus.DRAFT;

    private String changeLog;
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime activatedAt;

    // A/B测试配置
    private Double trafficAllocation;  // 流量分配比例
}

/**
 * 提示词状态枚举
 */
public enum PromptStatus {
    DRAFT("草稿"),
    ACTIVE("生效中"),
    DEPRECATED("已废弃");

    private final String name;
}

/**
 * 提示词管理器
 */
@Service
@RequiredArgsConstructor
public class PromptManager {

    private final PromptVersionRepository repository;
    private final PromptTemplateEngine templateEngine;

    /**
     * 获取当前生效的Prompt
     */
    public String getActivePrompt(AgentType agentType) {
        return repository.findByAgentTypeAndStatus(agentType, PromptStatus.ACTIVE)
            .orElseThrow(() -> new PromptNotFoundException(agentType))
            .getContent();
    }

    /**
     * 创建新版本
     */
    @Transactional
    public PromptVersion createVersion(AgentType agentType, String content,
                                       String changeLog, String createdBy) {
        // 将当前版本改为DEPRECATED
        repository.findByAgentTypeAndStatus(agentType, PromptStatus.ACTIVE)
            .ifPresent(current -> {
                current.setStatus(PromptStatus.DEPRECATED);
                current.setUpdatedAt(LocalDateTime.now());
                repository.save(current);
            });

        // 获取最新版本号
        Integer maxVersion = repository.findMaxVersionByAgentType(agentType);
        int newVersion = (maxVersion != null ? maxVersion : 0) + 1;

        // 创建新版本
        PromptVersion version = PromptVersion.builder()
            .agentType(agentType)
            .version(newVersion)
            .content(content)
            .status(PromptStatus.ACTIVE)
            .changeLog(changeLog)
            .createdBy(createdBy)
            .createdAt(LocalDateTime.now())
            .activatedAt(LocalDateTime.now())
            .build();

        return repository.save(version);
    }

    /**
     * 回滚到指定版本
     */
    @Transactional
    public void rollback(AgentType agentType, Integer targetVersion) {
        PromptVersion target = repository
            .findByAgentTypeAndVersion(agentType, targetVersion)
            .orElseThrow(() -> new PromptVersionNotFoundException(
                agentType, targetVersion));

        // 将当前ACTIVE改为DEPRECATED
        repository.findByAgentTypeAndStatus(agentType, PromptStatus.ACTIVE)
            .ifPresent(current -> {
                current.setStatus(PromptStatus.DEPRECATED);
                repository.save(current);
            });

        // 激活目标版本
        target.setStatus(PromptStatus.ACTIVE);
        target.setActivatedAt(LocalDateTime.now());
        repository.save(target);

        log.info("Prompt回滚: agentType={}, version={}", agentType, targetVersion);
    }

    /**
     * A/B测试流量分配
     */
    public String getPromptForABTest(AgentType agentType) {
        List<PromptVersion> candidates = repository
            .findActiveVersionsForABTest(agentType);

        if (candidates.size() <= 1) {
            return getActivePrompt(agentType);
        }

        // 按流量分配随机选择
        double rand = Math.random();
        double cumulative = 0;

        for (PromptVersion v : candidates) {
            cumulative += v.getTrafficAllocation();
            if (rand < cumulative) {
                return v.getContent();
            }
        }

        return candidates.get(0).getContent();
    }
}
```

### 7.4 策略系统

```java
/**
 * 策略接口
 */
public interface Strategy {

    /**
     * 策略名称
     */
    String getName();

    /**
     * 获取置信度阈值
     */
    double getConfidenceThreshold();

    /**
     * 是否启用辩论模式
     */
    boolean isDebateEnabled();

    /**
     * 获取分析深度
     */
    AnalysisDepth getDepth();

    /**
     * 生成交易信号
     */
    TradeSignal generateSignal(PipelineResult pipeline, Judgment judgment);
}

/**
 * 保守策略
 */
@Service("conservativeStrategy")
public class ConservativeStrategy implements Strategy {

    @Override
    public String getName() {
        return "保守策略";
    }

    @Override
    public double getConfidenceThreshold() {
        return 0.8; // 高置信度要求
    }

    @Override
    public boolean isDebateEnabled() {
        return true; // 必须辩论
    }

    @Override
    public AnalysisDepth getDepth() {
        return AnalysisDepth.FULL; // 全量分析
    }

    @Override
    public TradeSignal generateSignal(PipelineResult pipeline, Judgment judgment) {
        // 保守策略: 高置信度 + 多方一致才买入
        if (judgment.getConfidence().getValue() < 0.8) {
            return TradeSignal.builder()
                .type(SignalType.HOLD)
                .reason("置信度不足")
                .build();
        }

        if (!isConsensus(judgment)) {
            return TradeSignal.builder()
                .type(SignalType.HOLD)
                .reason("缺乏共识")
                .build();
        }

        // 执行买入信号
        return buildBuySignal(pipeline);
    }
}

/**
 * 激进策略
 */
@Service("aggressiveStrategy")
public class AggressiveStrategy implements Strategy {

    @Override
    public String getName() {
        return "激进策略";
    }

    @Override
    public double getConfidenceThreshold() {
        return 0.5; // 低置信度要求
    }

    @Override
    public boolean isDebateEnabled() {
        return false; // 跳过辩论加速响应
    }

    @Override
    public AnalysisDepth getDepth() {
        return AnalysisDepth.QUICK; // 快速分析
    }
}

/**
 * 策略注册表
 */
@Service
public class StrategyRegistry {

    private final Map<String, Strategy> strategies = new HashMap<>();

    public StrategyRegistry(
            @Qualifier("conservativeStrategy") Strategy conservative,
            @Qualifier("aggressiveStrategy") Strategy aggressive,
            @Qualifier("balancedStrategy") Strategy balanced) {

        strategies.put("conservative", conservative);
        strategies.put("aggressive", aggressive);
        strategies.put("balanced", balanced);
    }

    public Strategy getStrategy(String name) {
        Strategy strategy = strategies.get(name.toLowerCase());
        if (strategy == null) {
            return strategies.get("balanced"); // 默认平衡策略
        }
        return strategy;
    }
}
```

---

## 8. 前端设计

### 8.1 前端技术栈

| 类别            | 技术选型                             |
| --------------- | ------------------------------------ |
| **框架**        | Vue 3 + Composition API + TypeScript |
| **状态管理**    | Pinia                                |
| **UI组件库**    | Element Plus                         |
| **图表**        | ECharts (K线图)                      |
| **HTTP客户端**  | Axios                                |
| **构建工具**    | Vite                                 |
| **CSS预处理器** | SCSS                                 |

### 8.2 前端页面结构

```
src/
├── views/
│   ├── Dashboard.vue              # 仪表盘
│   ├── StockAnalysis.vue          # 股票分析主页面
│   ├── AgentChat.vue              # Agent对话页面
│   ├── Watchlist.vue              # 自选股管理
│   ├── History.vue                # 分析历史
│   └── ReportDetail.vue           # 报告详情
│
├── components/
│   ├── chart/
│   │   ├── KLineChart.vue         # K线图组件
│   │   ├── MACDChart.vue           # MACD指标
│   │   └── IndicatorChart.vue      # 技术指标
│   │
│   ├── agent/
│   │   ├── AgentPanel.vue          # Agent面板
│   │   ├── AgentMessage.vue        # 消息气泡
│   │   └── AgentSelector.vue       # Agent选择器
│   │
│   ├── analysis/
│   │   ├── AnalysisResult.vue      # 分析结果卡片
│   │   ├── TradeSignal.vue         # 交易信号展示
│   │   └── ConfidenceBar.vue       # 置信度条
│   │
│   └── common/
│       ├── Header.vue
│       ├── Sidebar.vue
│       └── StockSearch.vue
│
├── stores/
│   ├── analysis.ts                # 分析状态
│   ├── chat.ts                    # 对话状态
│   ├── watchlist.ts               # 自选股状态
│   └── user.ts                    # 用户状态
│
├── api/
│   ├── analysis.ts                # 分析API
│   ├── chat.ts                    # 对话API
│   ├── watchlist.ts               # 自选股API
│   └── report.ts                  # 报告API
│
├── composables/
│   ├── useSSE.ts                  # SSE流式请求
│   ├── useAgent.ts                # Agent交互
│   └── useChart.ts                # 图表配置
│
└── utils/
    ├── format.ts                  # 格式化工具
    └── stock.ts                   # 股票相关工具
```

### 8.3 核心组件设计

#### 8.3.1 Agent对话面板

```vue
<!-- AgentChat.vue -->
<template>
  <div class="agent-chat">
    <!-- Agent选择器 -->
    <div class="agent-selector">
      <el-select v-model="selectedAgent" placeholder="选择Agent">
        <el-option-group label="流水线">
          <el-option value="MARKET" label="行情Agent" />
          <el-option value="TECHNICAL" label="技术Agent" />
          <el-option value="SENTIMENT" label="舆情Agent" />
          <el-option value="PORTFOLIO" label="投资经理" />
        </el-option-group>
        <el-option-group label="辩论">
          <el-option value="BULL" label="多头" />
          <el-option value="BEAR" label="空头" />
          <el-option value="NEUTRAL" label="中立" />
          <el-option value="ARBITRATOR" label="仲裁官" />
        </el-option-group>
      </el-select>
    </div>

    <!-- 消息列表 -->
    <div class="message-list" ref="messageListRef">
      <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
        <div class="message-avatar">
          <el-avatar :icon="getAgentIcon(msg.agentType)" />
        </div>
        <div class="message-content">
          <div class="message-header">
            <span class="agent-name">{{ msg.agentName }}</span>
            <span class="timestamp">{{ formatTime(msg.timestamp) }}</span>
          </div>
          <div class="message-body" v-html="renderMarkdown(msg.content)" />
          <div class="message-meta" v-if="msg.modelUsed">
            <el-tag size="small">{{ msg.modelUsed }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-indicator">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ loadingStage }}</span>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-area">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :rows="3"
        placeholder="输入消息... (使用 @AgentName 指定特定Agent)"
        @keydown.enter.ctrl="sendMessage"
      />
      <el-button type="primary" @click="sendMessage" :loading="loading">
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from "vue";
import { useChatStore } from "@/stores/chat";
import { useSSE } from "@/composables/useSSE";

const chatStore = useChatStore();
const { messages, loading, loadingStage, send } = useSSE();

const inputMessage = ref("");
const selectedAgent = ref("PORTFOLIO");
const messageListRef = ref<HTMLElement>();

const sendMessage = async () => {
  if (!inputMessage.value.trim()) return;

  // 添加用户消息
  chatStore.addMessage({
    role: "user",
    content: inputMessage.value,
    agentType: selectedAgent.value,
  });

  // SSE请求
  await send({
    stockCode: chatStore.currentStockCode,
    message: inputMessage.value,
    agentType: selectedAgent.value,
  });

  inputMessage.value = "";
  scrollToBottom();
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
    }
  });
};
</script>
```

#### 8.3.2 K线图组件

```vue
<!-- KLineChart.vue -->
<template>
  <div class="kline-chart" ref="chartRef" />
</template>

<script setup lang="ts">
import * as echarts from "echarts";
import { onMounted, watch, ref } from "vue";

const props = defineProps<{
  data: KLineData;
  indicators?: string[];
}>();

const chartRef = ref<HTMLElement>();
let chart: echarts.ECharts;

onMounted(() => {
  chart = echarts.init(chartRef.value!);
  chart.setOption(buildOption());
});

watch(
  () => props.data,
  () => {
    chart.setOption(buildOption());
  },
  { deep: true },
);

const buildOption = (): echarts.EChartsOption => ({
  tooltip: {
    trigger: "axis",
    axisPointer: { type: "cross" },
  },
  legend: {
    data: ["K线", "MA5", "MA10", "MA20", "MA60"],
  },
  grid: [
    { left: "10%", right: "8%", height: "60%" },
    { left: "10%", right: "8%", top: "73%", height: "15%" },
  ],
  xAxis: [
    { type: "category", data: props.data.dates, gridIndex: 0 },
    { type: "category", data: props.data.dates, gridIndex: 1 },
  ],
  yAxis: [
    { scale: true, gridIndex: 0 },
    { scale: true, gridIndex: 1 },
  ],
  series: [
    {
      name: "K线",
      type: "candlestick",
      data: props.data.klines,
      xAxisIndex: 0,
      yAxisIndex: 0,
    },
    {
      name: "MA5",
      type: "line",
      data: props.data.ma5,
      smooth: true,
      xAxisIndex: 0,
      yAxisIndex: 0,
    },
    // ... 其他MA线
    {
      name: "MACD",
      type: "bar",
      data: props.data.macd,
      xAxisIndex: 1,
      yAxisIndex: 1,
    },
  ],
});
</script>
```

#### 8.3.3 SSE流式请求Composable

```typescript
// useSSE.ts
import { ref, Ref } from "vue";
import { chatAPI } from "@/api/chat";

export interface UseSSEReturn {
  messages: Ref<ChatMessage[]>;
  loading: Ref<boolean>;
  loadingStage: Ref<string>;
  send: (params: SendParams) => Promise<void>;
  abort: () => void;
}

export function useSSE(): UseSSEReturn {
  const messages = ref<ChatMessage[]>([]);
  const loading = ref(false);
  const loadingStage = ref("");

  let eventSource: EventSource | null = null;
  let abortController: AbortController | null = null;

  const send = async (params: SendParams) => {
    // 清理之前的连接
    abort();

    loading.value = true;
    loadingStage.value = "连接中...";

    try {
      const response = await fetch(chatAPI.streamUrl(params), {
        method: "GET",
        headers: { Accept: "text/event-stream" },
      });

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value);
        const lines = chunk.split("\n");

        for (const line of lines) {
          if (line.startsWith("data: ")) {
            const event = JSON.parse(line.slice(6));
            handleSSEEvent(event);
          }
        }
      }
    } catch (e) {
      console.error("SSE Error:", e);
    } finally {
      loading.value = false;
      loadingStage.value = "";
    }
  };

  const handleSSEEvent = (event: SSEEvent) => {
    switch (event.event) {
      case "stage":
        loadingStage.value = event.message;
        break;

      case "data":
        // 根据stage更新消息
        const existingMsg = messages.value.find(
          (m) => m.agentType === event.stage,
        );

        if (existingMsg) {
          existingMsg.content = JSON.stringify(event.data);
        } else {
          messages.value.push({
            id: crypto.randomUUID(),
            role: "assistant",
            agentType: event.stage,
            agentName: getAgentName(event.stage),
            content: JSON.stringify(event.data),
            timestamp: new Date(),
          });
        }
        break;

      case "complete":
        loading.value = false;
        break;
    }
  };

  const abort = () => {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
    if (abortController) {
      abortController.abort();
      abortController = null;
    }
  };

  return {
    messages,
    loading,
    loadingStage,
    send,
    abort,
  };
}
```

---

## 9. 数据库设计

### 9.1 ER图

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│    USER         │       │    WATCHLIST    │       │   STOCK         │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id (PK)         │──┐   │ id (PK)         │       │ code (PK)       │
│ username        │  │   │ user_id (FK)    │◀──┐   │ name            │
│ email           │  └──▶│ stock_code (FK) │   │   │ market          │
│ password_hash   │       │ added_at        │   │   │ industry        │
│ created_at      │       └─────────────────┘   │   └─────────────────┘
└─────────────────┘                             │           │
                                               │           │
┌─────────────────┐       ┌─────────────────┐ │           │
│  CHAT_SESSION    │       │  CHAT_MESSAGE   │ │           │
├─────────────────┤       ├─────────────────┤ │           │
│ id (PK)         │──────▶│ id (PK)         │ │           │
│ user_id (FK)    │◀─┐    │ session_id (FK)│◀─┘           │
│ stock_code (FK) │  │    │ role            │             │
│ strategy        │  │    │ content         │             │
│ created_at      │  │    │ agent_type      │             │
│ updated_at      │  └───▶│ model_used      │             │
└─────────────────┘       │ tokens_used     │             │
                          │ timestamp       │             │
┌─────────────────┐       └─────────────────┘             │
│ANALYSIS_REPORT  │                                        │
├─────────────────┤       ┌─────────────────┐               │
│ id (PK)         │       │ANALYSIS_HISTORY │               │
│ session_id (FK) │──────▶│ id (PK)         │◀──────────────┘
│ stock_code      │       │ stock_code      │
│ final_signal    │       │ agent_type      │
│ confidence_val  │       │ signal_type     │
│ report_content  │       │ confidence      │
│ created_at      │       │ summary         │
└─────────────────┘       │ full_content    │
                          │ timestamp       │
┌─────────────────┐       └─────────────────┘
│ PROMPT_VERSION  │
├─────────────────┤
│ id (PK)         │
│ agent_type      │
│ version         │
│ content         │
│ status          │
│ traffic_alloc   │
│ change_log      │
│ created_by      │
│ created_at      │
└─────────────────┘
```

### 9.2 表结构

```sql
-- 用户表
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 股票基本信息表
CREATE TABLE `stock` (
    `code` VARCHAR(20) PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `market` VARCHAR(20) NOT NULL COMMENT 'SH/SZ/BJ',
    `industry` VARCHAR(50),
    `listing_date` DATE,
    `total_shares` BIGINT,
    `circulating_shares` BIGINT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_industry (industry),
    INDEX idx_market (market)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 自选股表
CREATE TABLE `watchlist` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `stock_code` VARCHAR(20) NOT NULL,
    `added_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `note` VARCHAR(255),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`stock_code`) REFERENCES `stock`(`code`),
    UNIQUE KEY `uk_user_stock` (`user_id`, `stock_code`),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对话会话表
CREATE TABLE `chat_session` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `stock_code` VARCHAR(20),
    `strategy` VARCHAR(50) DEFAULT 'balanced',
    `status` VARCHAR(20) DEFAULT 'ACTIVE',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_stock (stock_code),
    INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 聊天消息表
CREATE TABLE `chat_message` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL COMMENT 'user/assistant/system',
    `content` TEXT,
    `agent_type` VARCHAR(50),
    `model_used` VARCHAR(50),
    `tokens_used` INT,
    `latency_ms` INT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`session_id`) REFERENCES `chat_session`(`id`) ON DELETE CASCADE,
    INDEX idx_session (session_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分析报告表
CREATE TABLE `analysis_report` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL,
    `stock_code` VARCHAR(20) NOT NULL,
    `stock_name` VARCHAR(100),
    `final_signal` VARCHAR(20) COMMENT 'BUY/SELL/HOLD',
    `confidence_value` DECIMAL(5,4),
    `confidence_level` VARCHAR(20),
    `target_price` DECIMAL(10,2),
    `stop_loss` DECIMAL(10,2),
    `entry_price` DECIMAL(10,2),

    `market_analysis` JSON COMMENT '行情分析结果',
    `technical_analysis` JSON COMMENT '技术分析结果',
    `sentiment_analysis` JSON COMMENT '舆情分析结果',
    `debate_judgment` JSON COMMENT '辩论裁决结果',

    `report_content` LONGTEXT COMMENT 'Markdown格式报告',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (`session_id`) REFERENCES `chat_session`(`id`) ON DELETE CASCADE,
    INDEX idx_stock (stock_code),
    INDEX idx_signal (final_signal),
    INDEX idx_confidence (confidence_value),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分析历史表
CREATE TABLE `analysis_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `stock_code` VARCHAR(20) NOT NULL,
    `stock_name` VARCHAR(100),
    `agent_type` VARCHAR(50) NOT NULL,
    `signal_type` VARCHAR(20),
    `confidence` DECIMAL(5,4),
    `confidence_level` VARCHAR(20),
    `summary` VARCHAR(500),
    `full_content` LONGTEXT,
    `model_used` VARCHAR(50),
    `embedding` VECTOR(1536) COMMENT '向量存储(可选)',
    `timestamp` DATETIME NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_stock (stock_code),
    INDEX idx_agent (agent_type),
    INDEX idx_signal (signal_type),
    INDEX idx_timestamp (timestamp),
    INDEX idx_stock_timestamp (stock_code, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 提示词版本表
CREATE TABLE `prompt_version` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `agent_type` VARCHAR(50) NOT NULL,
    `version` INT NOT NULL,
    `content` LONGTEXT NOT NULL,
    `variables` JSON COMMENT '变量定义',
    `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DEPRECATED',
    `traffic_allocation` DECIMAL(5,4) DEFAULT 1.0,
    `change_log` VARCHAR(500),
    `created_by` VARCHAR(50),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `activated_at` DATETIME,

    UNIQUE KEY `uk_agent_version` (`agent_type`, `version`),
    INDEX idx_agent_status (agent_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 10. API接口设计

### 10.1 REST API

| 方法           | 路径                                        | 描述           |
| -------------- | ------------------------------------------- | -------------- |
| **股票分析**   |                                             |                |
| POST           | `/api/v1/analysis/stock`                    | 发起股票分析   |
| GET            | `/api/v1/analysis/{reportId}`               | 获取分析报告   |
| GET            | `/api/v1/analysis/history`                  | 获取分析历史   |
| GET            | `/api/v1/analysis/export/{reportId}`        | 导出报告       |
| **对话**       |                                             |                |
| POST           | `/api/v1/chat/session`                      | 创建会话       |
| GET            | `/api/v1/chat/session/{sessionId}`          | 获取会话       |
| GET            | `/api/v1/chat/session/{sessionId}/messages` | 获取消息列表   |
| **自选股**     |                                             |                |
| GET            | `/api/v1/watchlist`                         | 获取自选股列表 |
| POST           | `/api/v1/watchlist`                         | 添加自选股     |
| DELETE         | `/api/v1/watchlist/{stockCode}`             | 删除自选股     |
| **提示词管理** |                                             |                |
| GET            | `/api/v1/prompts/{agentType}`               | 获取当前Prompt |
| POST           | `/api/v1/prompts/{agentType}/version`       | 创建新版本     |
| PUT            | `/api/v1/prompts/{agentType}/rollback`      | 回滚版本       |
| GET            | `/api/v1/prompts/{agentType}/versions`      | 获取版本历史   |

### 10.2 SSE流式API

| 方法 | 路径                                  | 描述         |
| ---- | ------------------------------------- | ------------ |
| GET  | `/api/v1/stream/analysis/{stockCode}` | 流式股票分析 |
| GET  | `/api/v1/stream/chat/{sessionId}`     | 流式对话     |

### 10.3 API请求/响应示例

```json
// POST /api/v1/analysis/stock
// 请求
{
  "stockCode": "000858",
  "strategy": "balanced",
  "includeDebate": true,
  "indicators": ["MACD", "RSI", "KDJ", "MA"]
}

// 响应
{
  "code": 200,
  "message": "success",
  "data": {
    "reportId": "rpt_20240427_001",
    "stockCode": "000858",
    "stockName": "五粮液",
    "status": "PROCESSING",
    "streamUrl": "/api/v1/stream/analysis/000858"
  }
}

// GET /api/v1/analysis/{reportId}
// 响应
{
  "code": 200,
  "data": {
    "id": "rpt_20240427_001",
    "stockCode": "000858",
    "stockName": "五粮液",
    "finalSignal": "BUY",
    "confidence": {
      "value": 0.75,
      "level": "MEDIUM"
    },
    "tradeSignal": {
      "type": "BUY",
      "entryPrice": 165.50,
      "targetPrice": 180.00,
      "stopLoss": 158.00,
      "holdingPeriodDays": 30
    },
    "marketAnalysis": { ... },
    "technicalAnalysis": { ... },
    "sentimentAnalysis": { ... },
    "debateJudgment": {
      "finalPosition": "BULLISH",
      "reasoning": "技术面MACD金叉，基本面业绩稳健...",
      "voteBreakdown": { "bull": 7, "bear": 2, "neutral": 1 }
    },
    "createdAt": "2026-04-27T10:30:00"
  }
}
```

---

## 11. 数据源集成

### 11.1 数据源适配器设计

```java
/**
 * 数据源适配器接口
 */
public interface DataSourceAdapter {

    /**
     * 获取适配器名称
     */
    String getName();

    /**
     * 获取实时行情
     */
    MarketData getRealtimeQuote(String stockCode);

    /**
     * 获取历史K线
     */
    KLineHistory getKLineHistory(String stockCode, int days);

    /**
     * 获取股票基本信息
     */
    StockBasicInfo getBasicInfo(String stockCode);

    /**
     * 获取财务数据
     */
    FinancialData getFinancialData(String stockCode);

    /**
     * 健康检查
     */
    boolean isHealthy();
}

/**
 * 数据源工厂
 */
@Service
@RequiredArgsConstructor
public class DataSourceFactory {

    private final Map<String, DataSourceAdapter> adapters = new HashMap<>();

    @PostConstruct
    public void init() {
        // 自动注册所有实现
        adapters.put("tushare", new TushareAdapter());
        adapters.put("akshare", new AkShareAdapter());
    }

    public DataSourceAdapter getAdapter(String name) {
        DataSourceAdapter adapter = adapters.get(name.toLowerCase());
        if (adapter == null) {
            throw new DataSourceException("未找到数据源适配器: " + name);
        }
        return adapter;
    }

    /**
     * 获取最佳可用适配器
     */
    public DataSourceAdapter getBestAvailable() {
        for (DataSourceAdapter adapter : adapters.values()) {
            if (adapter.isHealthy()) {
                return adapter;
            }
        }
        throw new DataSourceException("所有数据源均不可用");
    }
}
```

### 11.2 Tushare适配器

```java
/**
 * Tushare数据源适配器
 */
@Service
@RequiredArgsConstructor
public class TushareAdapter implements DataSourceAdapter {

    private static final String NAME = "tushare";
    private final RestTemplate restTemplate;

    @Value("${datasource.tushare.token}")
    private String token;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public MarketData getRealtimeQuote(String stockCode) {
        String api = "http://api.tushare.pro";
        String params = buildParams(Map.of(
            "api_name", "quotes",
            "token", token,
            "params", Map.of("ts_code", normalizeStockCode(stockCode)),
            "fields", "ts_code,name,open,high,low,close,vol,amount,turnoverrate"
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(params, headers);

        ResponseEntity<TushareResponse> response = restTemplate.postForEntity(
            api, request, TushareResponse.class);

        return parseRealtimeQuote(response.getBody());
    }

    @Override
    public KLineHistory getKLineHistory(String stockCode, int days) {
        String api = "http://api.tushare.pro";
        String endDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String startDate = LocalDate.now().minusDays(days)
            .format(DateTimeFormatter.BASIC_ISO_DATE);

        String params = buildParams(Map.of(
            "api_name", "daily",
            "token", token,
            "params", Map.of(
                "ts_code", normalizeStockCode(stockCode),
                "start_date", startDate,
                "end_date", endDate
            ),
            "fields", "ts_code,trade_date,open,high,low,close,vol"
        ));

        // 调用API并解析...
        return parseKLineHistory(response.getBody());
    }

    private String normalizeStockCode(String stockCode) {
        // 000858 -> 000858.SZ
        if (stockCode.matches("\\d{6}")) {
            if (stockCode.startsWith("6")) {
                return stockCode + ".SH";
            } else {
                return stockCode + ".SZ";
            }
        }
        return stockCode;
    }

    @Override
    public boolean isHealthy() {
        try {
            // 简单健康检查
            getRealtimeQuote("000001");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 11.3 AKShare适配器

```java
/**
 * AKShare数据源适配器
 */
@Service
@RequiredArgsConstructor
public class AkShareAdapter implements DataSourceAdapter {

    private static final String NAME = "akshare";

    private final RestTemplate restTemplate;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public MarketData getRealtimeQuote(String stockCode) {
        String url = "http://push2.eastmoney.com/api/qt/stock/get";
        String params = String.format(
            "?secid=%s&fields=f43,f44,f45,f46,f47,f48,f57,f58,f107,f169,f170",
            getMarketCode(stockCode));

        ResponseEntity<String> response = restTemplate.getForEntity(url + params, String.class);
        return parseRealtimeQuote(response.getBody());
    }

    @Override
    public KLineHistory getKLineHistory(String stockCode, int days) {
        String url = "http://push2his.eastmoney.com/api/qt/stock/kline/get";
        String params = String.format(
            "?secid=%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58",
            getMarketCode(stockCode));

        ResponseEntity<String> response = restTemplate.getForEntity(url + params, String.class);
        return parseKLineHistory(response.getBody());
    }

    @Override
    public boolean isHealthy() {
        try {
            getRealtimeQuote("000001");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## 12. 部署方案

### 12.1 Docker Compose部署

```yaml
# docker-compose.yml
version: "3.8"

services:
  # 后端应用
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/stock_analysis
      - SPRING_DATASOURCE_USERNAME=${DB_USER}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
    depends_on:
      - mysql
      - redis
    networks:
      - stock-network

  # 前端
  frontend:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - backend
    networks:
      - stock-network

  # MySQL数据库
  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=stock_analysis
      - MYSQL_USER=${DB_USER}
      - MYSQL_PASSWORD=${DB_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "3306:3306"
    networks:
      - stock-network

  # Redis缓存
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - stock-network

  # 向量数据库 (可选: Milvus/Qdrant)
  # qdrant:
  #   image: qdrant/qdrant:latest
  #   ports:
  #     - "6333:6333"
  #   volumes:
  #     - qdrant-data:/qdrant/storage

networks:
  stock-network:
    driver: bridge

volumes:
  mysql-data:
  redis-data:
  # qdrant-data:
```

### 12.2 环境变量配置

```bash
# .env
# 数据库
MYSQL_ROOT_PASSWORD=your_root_password
DB_USER=stock_user
DB_PASSWORD=your_secure_password

# LLM API Keys
OPENAI_API_KEY=sk-xxxxx
DEEPSEEK_API_KEY=sk-xxxxx
ZHIPU_API_KEY=xxxxx

# Tushare Token
TUSHARE_TOKEN=xxxxx
```

### 12.3 应用配置

```yaml
# application-prod.yml
spring:
  application:
    name: stock-analysis-system

  datasource:
    url: jdbc:mysql://${DB_HOST}:3306/stock_analysis?useSSL=false&serverTimezone=Asia/Shanghai
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  redis:
    host: ${REDIS_HOST}
    port: 6379
    timeout: 5000

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com

datasource:
  tushare:
    token: ${TUSHARE_TOKEN}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

---

## 13. 开发计划

### 13.1 里程碑规划

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         开发里程碑                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Phase 1: 基础架构 (2周)                                                │
│  ├── 项目脚手架搭建                                                     │
│  ├── 包结构和基础配置                                                   │
│  ├── 数据模型设计                                                       │
│  └── 数据库初始化                                                       │
│                           ▼                                             │
│  Phase 2: 数据源层 (1周)                                                │
│  ├── Tushare适配器实现                                                  │
│  ├── AKShare适配器实现                                                  │
│  └── 技术指标计算                                                       │
│                           ▼                                             │
│  Phase 3: Pipeline Agent (2周)                                          │
│  ├── Agent基类开发                                                      │
│  ├── 行情Agent实现                                                      │
│  ├── 技术Agent实现                                                      │
│  ├── 舆情Agent实现                                                      │
│  ├── 投资经理Agent实现                                                  │
│  └── Pipeline编排器                                                      │
│                           ▼                                             │
│  Phase 4: 辩论系统 (2周)                                                │
│  ├── 多头/空头/中立Agent                                                │
│  ├── 仲裁官Agent                                                        │
│  └── 辩论编排器                                                         │
│                           ▼                                             │
│  Phase 5: 工程能力 (2周)                                                │
│  ├── LLM管理器                                                          │
│  ├── 熔断降级机制                                                       │
│  ├── 提示词版本管理                                                     │
│  ├── SSE流式输出                                                       │
│  └── 记忆系统                                                          │
│                           ▼                                             │
│  Phase 6: 前端开发 (3周)                                                │
│  ├── 项目搭建                                                           │
│  ├── K线图组件                                                          │
│  ├── Agent对话面板                                                      │
│  ├── 自选股管理                                                         │
│  └── 分析报告导出                                                       │
│                           ▼                                             │
│  Phase 7: 策略与优化 (1周)                                              │
│  ├── 策略系统实现                                                       │
│  ├── 系统优化                                                           │
│  └── 部署上线                                                           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 13.2 详细任务分解

| 阶段        | 任务               | 预估工时 | 负责人 |
| ----------- | ------------------ | -------- | ------ |
| **Phase 1** | 项目脚手架         | 4h       |        |
|             | 依赖配置           | 2h       |        |
|             | 包结构             | 1h       |        |
|             | 数据模型           | 4h       |        |
|             | 数据库脚本         | 2h       |        |
| **Phase 2** | 数据源接口         | 2h       |        |
|             | Tushare适配器      | 8h       |        |
|             | AKShare适配器      | 6h       |        |
|             | 技术指标计算       | 8h       |        |
| **Phase 3** | Agent基类          | 4h       |        |
|             | 消息路由器         | 4h       |        |
|             | MarketAgent        | 6h       |        |
|             | TechnicalAgent     | 8h       |        |
|             | SentimentAgent     | 6h       |        |
|             | PortfolioAgent     | 6h       |        |
|             | Pipeline编排器     | 4h       |        |
| **Phase 4** | BullAgent          | 6h       |        |
|             | BearAgent          | 6h       |        |
|             | NeutralAgent       | 4h       |        |
|             | ArbitratorAgent    | 8h       |        |
|             | DebateOrchestrator | 6h       |        |
| **Phase 5** | LLMManager         | 8h       |        |
|             | 熔断降级           | 4h       |        |
|             | 提示词管理         | 6h       |        |
|             | SSE流式            | 6h       |        |
|             | 记忆系统           | 8h       |        |
| **Phase 6** | 前端脚手架         | 4h       |        |
|             | K线图组件          | 12h      |        |
|             | Agent对话          | 16h      |        |
|             | 自选股管理         | 8h       |        |
|             | 报告导出           | 6h       |        |
| **Phase 7** | 策略系统           | 8h       |        |
|             | 优化测试           | 8h       |        |
|             | 部署上线           | 4h       |        |

**总计预估**: ~200工时

---

## 附录

### A. 技术指标计算公式

#### MACD

```
EMA(12) = 12日指数移动平均
EMA(26) = 26日指数移动平均
DIF = EMA(12) - EMA(26)
DEA = DIF的9日指数移动平均
MACD柱 = (DIF - DEA) * 2
```

#### RSI

```
RS = 平均涨幅 / 平均跌幅
RSI = 100 - (100 / (1 + RS))
```

#### KDJ

```
RSV = (Close - LLV(L, N)) / (HHV(H, N) - LLV(L, N)) * 100
K = 2/3 * K(-1) + 1/3 * RSV
D = 2/3 * D(-1) + 1/3 * K
J = 3*K - 2*D
```

### B. 置信度等级定义

| 等级   | 数值范围  | 说明                     |
| ------ | --------- | ------------------------ |
| HIGH   | 0.8 - 1.0 | 信号非常可靠，多指标共振 |
| MEDIUM | 0.5 - 0.8 | 信号较可靠，少数指标存疑 |
| LOW    | 0.0 - 0.5 | 信号可靠性一般，需谨慎   |

### C. 参考资源

- Spring AI官方文档: https://docs.spring.io/spring-ai/
- AKShare文档: https://akshare.akfamily.xyz/
- Tushare文档: https://tushare.pro/document
- ECharts文档: https://echarts.apache.org/

---

_文档版本: v1.0.0_
_最后更新: 2026-04-27_
