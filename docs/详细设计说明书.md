# AlphaMind - 智能股票分析系统

> **版本**: v2.0.0
> **日期**: 2026-05-01
> **作者**: AI Assistant
> **状态**: 已完成（Phase 1–7 全部交付）
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

| 层级              | 技术选型（实际落地）                                          |
| ----------------- | ------------------------------------------------------------- |
| **后端框架**      | Spring Boot 3.4 + Spring AI 1.0.0                             |
| **数据库**        | PostgreSQL 16 + Redis 7.x                                     |
| **数据迁移**      | Flyway（自动版本化迁移）                                      |
| **向量存储**      | Redis Vector Store（Spring AI 集成，Redis 内置）               |
| **前端框架**      | Next.js 16 App Router + TypeScript + ECharts                  |
| **状态管理**      | Zustand                                                       |
| **HTTP 客户端**   | Axios（REST） + EventSource（SSE 流式）                       |
| **数据源**        | 内置静态模拟数据（50+ 只股票），可通过 FETCH_REAL_DATA 切换    |
| **LLM Providers** | OpenAI、DeepSeek、Anthropic（三选一或叠加，热切换+熔断）      |
| **容器化**        | Docker + Docker Compose（含健康检查与有序启动）               |
| **健康监控**      | Spring Boot Actuator（/actuator/health、metrics、info）        |

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

### 3.2 包结构设计（实际实现）

```
com.alphamind
├── AlphaMindApplication.java
│
├── config/                              # 配置层
│   ├── AlphaMindConfig.java            # 应用配置属性（含LLM/分析/辩论/SSE/记忆子配置）
│   ├── SpringAIConfig.java             # Spring AI ChatClient 配置
│   ├── RedisConfig.java                # Redis + RedisTemplate 配置
│   ├── CorsConfig.java                 # 跨域配置（支持环境变量）
│   └── StrategyTypeConverter.java      # 策略类型字符串转换器（大小写兼容）
│
├── controller/                          # 控制器层
│   ├── AnalysisController.java         # 分析API（SSE流式 + 同步 + 历史记录）
│   ├── ChatController.java             # 对话API（会话创建、消息、流式）
│   ├── StockController.java            # 股票搜索与自选股管理
│   └── AdminController.java            # 工程能力管理（提示词版本、LLM健康）
│
├── service/                             # 服务层
│   ├── PipelineOrchestrator.java       # 流水线编排器（SSE驱动，Market→Tech→Sentiment→Portfolio）
│   ├── DebateOrchestrator.java         # 辩论编排器（Bull→Bear→Neutral→Arbitrator顺序执行）
│   ├── LlmManager.java                 # 多模型管理+轻量级熔断降级（CLOSED/OPEN/HALF_OPEN）
│   ├── MemoryService.java              # 会话记忆（Redis优先+内存fallback）
│   ├── PromptManager.java              # 提示词版本管理（纯内存，支持回滚）
│   └── StockService.java               # 股票搜索（内置50+只A股数据）+自选股管理
│
├── agent/                               # Agent层
│   ├── BaseAgent.java                  # Agent基类（ThreadLocal上下文隔离，LLM可选，fallback模板）
│   ├── AgentRouter.java                # 消息路由器（@AgentName语法解析）
│   ├── MarketAgent.java                # 行情采集Agent
│   ├── TechnicalAgent.java             # 技术分析Agent（MACD/RSI/KDJ/布林带/MA）
│   ├── SentimentAgent.java             # 舆情分析Agent
│   ├── PortfolioAgent.java             # 综合决策Agent（接入StrategyRegistry+StrategySignalPlanner）
│   ├── BullAgent.java                  # 多头辩论Agent
│   ├── BearAgent.java                  # 空头辩论Agent
│   ├── NeutralAgent.java               # 中立辩论Agent
│   └── ArbitratorAgent.java            # 仲裁官Agent
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
├── strategy/                            # 策略系统（实际实现）
│   ├── StrategyProfile.java            # 策略配置接口
│   ├── StrategyRegistry.java           # Spring Bean 注册表
│   ├── StrategyModeResolver.java       # mode/enableDebate/strategy 优先级解析
│   ├── StrategySignalPlanner.java      # 基于技术分+舆情分生成交易信号
│   ├── ConservativeStrategyProfile.java # 保守策略（30%仓位，-5%止损，45天，阈值75%）
│   ├── BalancedStrategyProfile.java    # 平衡策略（50%仓位，-7%止损，30天，阈值65%）
│   └── AggressiveStrategyProfile.java  # 激进策略（80%仓位，-10%止损，15天，阈值55%）
│
├── model/                               # 数据模型
│   ├── entity/                          # JPA 实体（对应 PostgreSQL 表）
│   │   ├── AnalysisReportEntity.java   # analysis_reports 表
│   │   ├── WatchlistItemEntity.java    # watchlist_items 表
│   │   ├── ChatSessionEntity.java      # chat_sessions 表
│   │   └── ChatMessageEntity.java      # chat_messages 表
│   │
│   ├── dto/                            # 传输对象
│   │   ├── AnalysisReportDTO.java      # 完整分析报告
│   │   ├── AnalysisRequest.java        # 同步分析请求体
│   │   ├── ApiResponse.java            # 统一响应包装（code/message/data）
│   │   ├── ChatMessage.java            # 聊天消息
│   │   ├── ConfidenceDTO.java          # 置信度（value+level+explanation）
│   │   ├── DebateViewDTO.java          # 辩论观点（position/view/reasons/targetPrice）
│   │   ├── JudgmentDTO.java            # 仲裁裁决（finalPosition/reasoning/riskWarnings）
│   │   ├── MarketDataDTO.java          # 行情数据（价格/涨跌/PE/PB等）
│   │   ├── SSEEvent.java               # SSE事件（stage/data/complete/error）
│   │   ├── SentimentDataDTO.java       # 舆情数据（sentimentScore/news/aiSummary）
│   │   ├── StockSearchResult.java      # 股票搜索结果
│   │   ├── TechnicalIndicatorsDTO.java # 技术指标（MACD/RSI/KDJ/布林带/MA/评分）
│   │   ├── TradeSignalDTO.java         # 交易信号（type/entryPrice/targetPrice/stopLoss）
│   │   └── WatchlistItem.java          # 自选股条目
│   │
│   └── enums/                          # 枚举
│       ├── AgentType.java              # MARKET/TECHNICAL/SENTIMENT/PORTFOLIO/BULL/BEAR/NEUTRAL/ARBITRATOR
│       ├── AnalysisMode.java           # PIPELINE/DEBATE
│       ├── AnalysisStage.java          # MARKET/TECHNICAL/SENTIMENT/PORTFOLIO/DEBATE
│       ├── ConfidenceLevel.java        # HIGH/MEDIUM/LOW
│       ├── DebatePosition.java         # BULLISH/BEARISH/NEUTRAL
│       ├── SignalType.java             # BUY/SELL/HOLD
│       └── StrategyType.java           # CONSERVATIVE/BALANCED/AGGRESSIVE
│
└── repository/                          # JPA 数据访问层
    ├── AnalysisReportRepository.java   # 分析报告（按code查询、Top50）
    ├── WatchlistItemRepository.java    # 自选股（按userId查询）
    ├── ChatSessionRepository.java      # 聊天会话
    └── ChatMessageRepository.java      # 聊天消息
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

### 4.2 BaseAgent 基类（实际实现）

所有 Agent 继承 `BaseAgent`，核心特性：

- **ThreadLocal 上下文隔离**：每个请求线程拥有独立 Context Map，并发安全，执行后调用 `clearContext()` 释放资源。
- **LLM 可选**：`llmCall()` 失败时返回 `null`，子类降级为模板输出，不中断流水线。
- **统一 Context Key**：`stockCode`、`stockName`、`strategy`、`marketData`、`technicalIndicators`、`sentimentData`、`tradeSignal`、`confidence`、`sessionId`、`contextSummary`。

```java
// com.alphamind.agent.BaseAgent（核心结构）
@Component
public abstract class BaseAgent {

    @Autowired(required = false)
    protected ChatClient chatClient;

    private final ThreadLocal<Map<String, Object>> contextHolder =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    protected void setContext(String key, Object value) {
        contextHolder.get().put(key, value);
    }

    protected Object getContext(String key) {
        return contextHolder.get().get(key);
    }

    /** 每次任务结束必须调用，防止 ThreadLocal 内存泄漏 */
    protected void clearContext() {
        contextHolder.remove();
    }

    public abstract String execute(String input);

    protected String llmCall(String systemPrompt, String userMessage) {
        if (chatClient == null) return null;
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
        } catch (Exception e) {
            return null;
        }
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

### 7.2 多LLM热切换与弹性降级（实际实现）

后端通过 `LlmManager` 实现自轻量级熔断机制，**未引入 Resilience4j**。

#### 熔断状态机

| 状态         | 含义                                            |
| ------------ | ----------------------------------------------- |
| `CLOSED`     | 正常调用                                        |
| `OPEN`       | 达到失败阈值（连续3次），拒绝调用，等待30秒冷却 |
| `HALF_OPEN`  | 冷却结束，放行单次试探请求                      |

```java
// com.alphamind.service.LlmManager（核心结构）
@Service
public class LlmManager {

    private static final int FAILURE_THRESHOLD = 3;       // 连续失败次数阈值
    private static final long OPEN_DURATION_MS = 30_000L; // OPEN 状态持续时长

    enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private static class ModelState {
        volatile CircuitState state = CircuitState.CLOSED;
        volatile int failureCount = 0;
        volatile long openedAt = 0L;
    }

    // 每个 ChatClient 独立维护一套熔断状态
    private final Map<String, ModelState> states = new ConcurrentHashMap<>();

    /**
     * 通过可用的 ChatClient 调用 LLM，失败自动熔断并降级
     */
    public Optional<String> call(String modelKey, ChatClient client,
                                  String systemPrompt, String userMessage) {
        ModelState ms = states.computeIfAbsent(modelKey, k -> new ModelState());

        // 检查是否允许调用
        if (ms.state == CircuitState.OPEN) {
            if (System.currentTimeMillis() - ms.openedAt > OPEN_DURATION_MS) {
                ms.state = CircuitState.HALF_OPEN;
            } else {
                return Optional.empty(); // 熔断中，直接返回空
            }
        }

        try {
            String result = client.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
            onSuccess(ms);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            onFailure(ms);
            return Optional.empty();
        }
    }

    private void onSuccess(ModelState ms) {
        ms.failureCount = 0;
        ms.state = CircuitState.CLOSED;
    }

    private void onFailure(ModelState ms) {
        ms.failureCount++;
        if (ms.failureCount >= FAILURE_THRESHOLD) {
            ms.state = CircuitState.OPEN;
            ms.openedAt = System.currentTimeMillis();
        }
    }
}
```

**Spring AI 集成方式**：`SpringAIConfig` 根据环境变量分别构建 `ChatClient` Bean（OpenAI / DeepSeek / Anthropic），并注入 `LlmManager` 的 model registry。`BaseAgent.llmCall()` 委托 `LlmManager.call()` 并在返回 `Optional.empty()` 时降级到模板输出。



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

### 7.4 策略系统（实际实现）

策略系统位于 `com.alphamind.strategy` 包，由 `StrategyProfile` 接口、3 个具体策略、`StrategyRegistry`、`StrategySignalPlanner` 和 `StrategyModeResolver` 五部分组成。

```java
// 策略配置接口
public interface StrategyProfile {
    double getPositionRatio();          // 建仓比例
    double getStopLossRatio();          // 止损比例
    int    getHoldingPeriodDays();      // 持仓天数
    double getConfidenceThreshold();    // 置信度阈值（买入门槛）
    AnalysisMode getDefaultMode();      // 默认分析模式
    double getBuyTargetBaseRatio();     // 目标价计算基础比例
}
```

**三种策略参数对比**：

| 策略       | 仓位  | 止损   | 持仓期 | 置信度阈值 | 默认模式  |
| ---------- | ----- | ------ | ------ | ---------- | --------- |
| 保守       | 30%   | -5%    | 45天   | 75%        | DEBATE    |
| 平衡       | 50%   | -7%    | 30天   | 65%        | DEBATE    |
| 激进       | 80%   | -10%   | 15天   | 55%        | PIPELINE  |

**StrategySignalPlanner（信号生成逻辑）**：

```java
// 综合评分 = 技术分 × 0.5 + 舆情分×100 × 0.5
double compositeScore = technicalScore * 0.5 + sentimentScore * 100 * 0.5;

SignalType signal;
if (compositeScore >= 70)      signal = SignalType.BUY;
else if (compositeScore >= 50) signal = SignalType.HOLD;
else                           signal = SignalType.SELL;

// 再叠加置信度阈值检查：未达阈值强制 HOLD
if (confidence < profile.getConfidenceThreshold() / 100.0)
    signal = SignalType.HOLD;
```

**StrategyModeResolver（模式解析优先级）**：

```
显式 mode 参数 > enableDebate=true > strategy.getDefaultMode()
```

**StrategyRegistry**：Spring `@Service`，注入三个 Profile Bean，通过 `StrategyType` 枚举查询对应配置。

`PortfolioAgent` 在生成投资建议时从 `StrategyRegistry` 获取当前策略参数，并将 `positionRatio`、`stopLossRatio`、`holdingPeriodDays` 注入 LLM Prompt，保持策略参数与 AI 输出的一致性。

---

## 8. 前端设计

### 8.1 前端技术栈（实际实现）

| 类别              | 技术选型                                |
| ----------------- | --------------------------------------- |
| **框架**          | Next.js 16 (App Router) + TypeScript    |
| **状态管理**      | Zustand                                 |
| **图表**          | ECharts（K线图 + 技术指标）             |
| **HTTP 客户端**   | Axios（REST）+ EventSource（SSE 流式）  |
| **CSS**           | Tailwind CSS                            |
| **构建工具**      | Next.js 内置（Turbopack/Webpack）       |
| **代码风格**      | ESLint (Next.js 官方配置)               |

### 8.2 前端页面结构（实际实现）

```
frontend/src/
├── app/                              # Next.js App Router 路由层
│   ├── layout.tsx                   # 全局布局（含 AppShell/Sidebar）
│   ├── page.tsx                     # / 股票分析主页
│   ├── chat/page.tsx                # /chat Agent 对话页
│   ├── history/page.tsx             # /history 分析历史页
│   └── watchlist/page.tsx           # /watchlist 自选股管理页
│
├── components/
│   ├── agent/
│   │   ├── AgentMessage.tsx         # Agent 消息气泡（含 AgentType 图标/名称）
│   │   └── AgentSelector.tsx        # Agent 类型选择器
│   ├── analysis/
│   │   ├── AnalysisResult.tsx       # 流水线分析结果展示
│   │   └── DebateResult.tsx         # 辩论模式结果与裁决展示
│   ├── chart/
│   │   ├── KLineChart.tsx           # ECharts K线图（含MA5/10/20/60）
│   │   └── TechnicalIndicators.tsx  # MACD/RSI/KDJ 指标图
│   ├── common/
│   │   ├── Button.tsx               # 通用按钮
│   │   ├── ConfidenceBar.tsx        # 置信度进度条（HIGH/MEDIUM/LOW 色彩）
│   │   └── StockSearch.tsx          # 股票搜索框（调用 /api/v1/stocks/search）
│   └── layout/
│       ├── AppShell.tsx             # 整体布局外壳
│       └── Sidebar.tsx              # 侧边导航栏
│
├── stores/                          # Zustand 状态
│   ├── analysis.ts                  # 分析状态（SSE进度、报告数据、历史列表）
│   ├── chat.ts                      # 对话状态（会话、消息列表、loading）
│   └── watchlist.ts                 # 自选股状态
│
├── api/
│   └── client.ts                    # Axios 实例（baseURL=/api/v1）
│
├── hooks/
│   └── useSSE.ts                    # EventSource 封装（处理 stage/data/result/error 事件）
│
├── utils/
│   ├── index.ts                     # 通用工具函数
│   └── reportExport.ts              # 分析报告导出（JSON + Markdown 格式）
│
├── types/
│   └── index.ts                     # 全局 TypeScript 类型定义
│
└── config/
    └── navigation.ts                # 导航菜单配置
```
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

#### 8.3.1 Agent 对话面板

实际实现在 `frontend/src/app/chat/page.tsx`，使用 Zustand `useChatStore` + `useSSE` hook 驱动。

```tsx
// 核心结构（简化）
const ChatPage = () => {
  const { messages, sendMessage, loading } = useChatStore();
  const [agentType, setAgentType] = useState("PORTFOLIO");

  return (
    <div className="flex flex-col h-full">
      <AgentSelector value={agentType} onChange={setAgentType} />
      <div className="flex-1 overflow-y-auto">
        {messages.map((msg) => (
          <AgentMessage key={msg.id} message={msg} />
        ))}
      </div>
      <ChatInput onSend={(text) => sendMessage(text, agentType)} loading={loading} />
    </div>
  );
};
```

#### 8.3.2 K线图组件

```tsx
// frontend/src/components/chart/KLineChart.tsx
// ECharts candlestick + MA 多线叠加，MACD 子图在独立 grid
import * as echarts from "echarts";
import { useEffect, useRef } from "react";

export function KLineChart({ data }: { data: MarketDataDTO }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const chart = echarts.init(ref.current!);
    chart.setOption(buildOption(data));
    return () => chart.dispose();
  }, [data]);
  return <div ref={ref} style={{ width: "100%", height: 480 }} />;
}
```

#### 8.3.3 SSE 流式请求 Hook

```tsx
// frontend/src/hooks/useSSE.ts
export function useSSE(url: string) {
  const [stages, setStages] = useState<Record<string, unknown>>({});
  const [finalReport, setFinalReport] = useState<AnalysisReportDTO | null>(null);
  const [status, setStatus] = useState<"idle" | "loading" | "done" | "error">("idle");

  const start = useCallback(() => {
    setStatus("loading");
    const es = new EventSource(url);

    es.addEventListener("stage", (e) => {
      const data = JSON.parse(e.data);
      setStages((prev) => ({ ...prev, [data.stage]: data }));
    });

    es.addEventListener("result", (e) => {
      const resp = JSON.parse(e.data);
      setFinalReport(resp.data);
      setStatus("done");
      es.close();
    });

    es.onerror = () => { setStatus("error"); es.close(); };
  }, [url]);

  return { stages, finalReport, status, start };
}
```



---

## 9. 数据库设计

> 实际使用 **PostgreSQL 16**，通过 **Flyway** 管理版本化迁移脚本（`backend/src/main/resources/db/migration/V1__init_schema.sql`）。

### 9.1 ER图（实际表结构）

```
analysis_reports          watchlist_items          chat_sessions
─────────────────         ───────────────          ─────────────
id (VARCHAR PK)           id (BIGSERIAL PK)        session_id (VARCHAR PK)
stock_code                user_id                  user_id
stock_name                stock_code               stock_code
strategy                  stock_name               stock_name
enable_debate             notes                    strategy
signal_type               created_at               message_count
entry_price               updated_at               last_active_at
target_price                                       created_at
stop_loss
holding_days              chat_messages            stock_info
rationale                 ─────────────            ──────────
confidence_value          id (VARCHAR PK)          stock_code (PK)
confidence_level          session_id (FK)          stock_name
market_data (JSONB)       role                     market
technical_indicators (J)  content                  industry
sentiment_data (JSONB)    agent_type               current_price
judgment (JSONB)          agent_name               change_percent
created_at                model_used               pe_ratio
updated_at                token_count              last_updated
                          created_at
```

### 9.2 表结构（PostgreSQL DDL）

```sql
-- ==========================================
-- 1. 分析报告表
-- ==========================================
CREATE TABLE IF NOT EXISTS analysis_reports (
    id              VARCHAR(36)  PRIMARY KEY,
    stock_code      VARCHAR(20)  NOT NULL,
    stock_name      VARCHAR(100) NOT NULL,
    strategy        VARCHAR(20)  NOT NULL DEFAULT 'BALANCED',
    enable_debate   BOOLEAN      NOT NULL DEFAULT TRUE,
    signal_type     VARCHAR(10),                    -- BUY / SELL / HOLD
    entry_price     NUMERIC(12, 4),
    target_price    NUMERIC(12, 4),
    stop_loss       NUMERIC(12, 4),
    holding_days    INTEGER,
    rationale       TEXT,
    confidence_value   NUMERIC(5, 4),
    confidence_level   VARCHAR(10),                 -- HIGH / MEDIUM / LOW
    market_data          JSONB,
    technical_indicators JSONB,
    sentiment_data       JSONB,
    judgment             JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_stock_code ON analysis_reports (stock_code);
CREATE INDEX idx_analysis_created_at ON analysis_reports (created_at DESC);
CREATE INDEX idx_analysis_signal_type ON analysis_reports (signal_type);
CREATE INDEX idx_analysis_market_data ON analysis_reports USING GIN (market_data);

-- ==========================================
-- 2. 自选股表
-- ==========================================
CREATE TABLE IF NOT EXISTS watchlist_items (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     VARCHAR(100) NOT NULL DEFAULT 'default',
    stock_code  VARCHAR(20)  NOT NULL,
    stock_name  VARCHAR(100) NOT NULL,
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, stock_code)
);

-- ==========================================
-- 3. 聊天会话表
-- ==========================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    session_id      VARCHAR(36)  PRIMARY KEY,
    user_id         VARCHAR(100) NOT NULL DEFAULT 'default',
    stock_code      VARCHAR(20),
    stock_name      VARCHAR(100),
    strategy        VARCHAR(20),
    message_count   INTEGER      NOT NULL DEFAULT 0,
    last_active_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ==========================================
-- 4. 聊天消息表
-- ==========================================
CREATE TABLE IF NOT EXISTS chat_messages (
    id          VARCHAR(36)  PRIMARY KEY,
    session_id  VARCHAR(36)  NOT NULL REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    role        VARCHAR(20)  NOT NULL,   -- user / assistant / system
    content     TEXT         NOT NULL,
    agent_type  VARCHAR(20),
    agent_name  VARCHAR(50),
    model_used  VARCHAR(50),
    token_count INTEGER,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ==========================================
-- 5. 股票基础信息缓存表
-- ==========================================
CREATE TABLE IF NOT EXISTS stock_info (
    stock_code      VARCHAR(20)  PRIMARY KEY,
    stock_name      VARCHAR(100) NOT NULL,
    market          VARCHAR(20),
    industry        VARCHAR(50),
    current_price   NUMERIC(12, 4),
    change_percent  NUMERIC(8, 4),
    market_cap      BIGINT,
    pe_ratio        NUMERIC(10, 4),
    last_updated    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ==========================================
-- 触发器：自动更新 updated_at
-- ==========================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_analysis_reports_updated
    BEFORE UPDATE ON analysis_reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_watchlist_items_updated
    BEFORE UPDATE ON watchlist_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```



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
---

## 10. API接口设计（实际实现）

### 10.1 REST API

| 方法   | 路径                                      | 描述                         |
| ------ | ----------------------------------------- | ---------------------------- |
| **股票分析**                                                                  |
| GET    | `/api/v1/analysis/stream`                 | SSE 流式分析（主入口）       |
| POST   | `/api/v1/analysis/analyze`                | 同步分析（非流式）           |
| GET    | `/api/v1/analysis/history`                | 获取最近50条分析记录         |
| **对话**                                                                      |
| POST   | `/api/v1/chat/session`                    | 创建/复用对话会话            |
| POST   | `/api/v1/chat/message`                    | 发送对话消息（同步响应）     |
| GET    | `/api/v1/chat/stream/{sessionId}`         | SSE 流式对话                 |
| **股票**                                                                      |
| GET    | `/api/v1/stocks/search?query=`            | 股票搜索（关键词匹配）       |
| GET    | `/api/v1/stocks/watchlist`                | 获取自选股列表               |
| POST   | `/api/v1/stocks/watchlist`                | 添加自选股                   |
| DELETE | `/api/v1/stocks/watchlist/{stockCode}`    | 删除自选股                   |
| **工程管理（Admin）**                                                         |
| GET    | `/api/v1/admin/prompts/{agentType}`       | 获取当前激活 Prompt          |
| POST   | `/api/v1/admin/prompts/{agentType}`       | 创建新 Prompt 版本           |
| PUT    | `/api/v1/admin/prompts/{agentType}/rollback` | 回滚到上一版本            |
| GET    | `/api/v1/admin/prompts/{agentType}/versions` | 获取版本历史列表          |
| GET    | `/api/v1/admin/llm/health`               | LLM 健康检查                 |
| **健康监控**                                                                  |
| GET    | `/actuator/health`                        | Spring Boot Actuator 健康    |
| GET    | `/actuator/metrics`                       | 指标端点                     |

### 10.2 SSE 事件格式

分析流（`/api/v1/analysis/stream`）的 SSE 事件类型：

| event 名称  | 触发时机             | data 内容                           |
| ----------- | -------------------- | ----------------------------------- |
| `stage`     | 每个 Agent 开始时    | `{stage, agentType, message}`       |
| `result`    | 流水线全部完成时     | `ApiResponse<AnalysisReportDTO>`    |
| `error`     | 异常时               | `{message}`                         |

### 10.3 请求/响应示例

```
# 分析请求（SSE）
GET /api/v1/analysis/stream?stockCode=600519&strategy=balanced&enableDebate=true

# SSE 事件流：
event: stage
data: {"stage":"MARKET","agentType":"MARKET","message":"正在采集行情数据..."}

event: stage
data: {"stage":"TECHNICAL","agentType":"TECHNICAL","message":"正在进行技术分析..."}

event: result
data: {"code":200,"message":"success","data":{...AnalysisReportDTO...}}
```

```json
// POST /api/v1/chat/message
// 请求
{
  "sessionId": "sess-abc123",
  "content": "这只股票的技术面怎么样？",
  "agentType": "TECHNICAL"
}

// 响应
{
  "code": 200,
  "message": "success",
  "data": {
    "role": "assistant",
    "content": "从技术面来看，该股 MACD 金叉信号...",
    "agentType": "TECHNICAL",
    "agentName": "技术分析Agent"
  }
}
```

---

## 11. 数据源集成（实际实现）

当前版本使用**内置静态数据**模式，`MarketAgent` 内置约 50 只常见 A 股的模拟行情数据（`STOCK_DATA` 静态 Map）。通过环境变量 `FETCH_REAL_DATA=true` 可切换到真实数据拉取模式。

**已规划但未实现的真实数据源**（保留为扩展方向）：
- Tushare Pro API（需 Token）
- AKShare（东方财富接口代理）

**扩展方式**：实现 `DataSourceAdapter` 接口并注册到 `DataSourceFactory` 即可热插拔，不需要修改 Agent 逻辑。

---

## 12. 部署方案（实际实现）

### 12.1 Docker Compose 部署（含健康检查）

```yaml
# docker-compose.yml（实际落地版）
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: alphamind
      POSTGRES_USER: ${DB_USERNAME:-alphamind}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-alphamind123}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-alphamind}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD:-redis123}
    volumes:
      - redis-data:/data
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-redis123}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_USERNAME: ${DB_USERNAME:-alphamind}
      DB_PASSWORD: ${DB_PASSWORD:-alphamind123}
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:-}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    depends_on:
      backend:
        condition: service_healthy

volumes:
  postgres-data:
  redis-data:
```

### 12.2 环境变量配置（.env.example）

```bash
# LLM API Keys（至少配置一个）
OPENAI_API_KEY=sk-xxxxx
DEEPSEEK_API_KEY=sk-xxxxx
ANTHROPIC_API_KEY=sk-xxxxx

# 数据库（生产环境必须修改）
DB_USERNAME=alphamind
DB_PASSWORD=your_secure_password

# Redis
REDIS_PASSWORD=your_redis_password

# 跨域（前端地址）
CORS_ALLOWED_ORIGINS=http://localhost:3000

# 数据源模式（true=真实数据，false=模拟数据）
FETCH_REAL_DATA=false
```

### 12.3 Dockerfile（后端，含 JVM 调优）

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:17-jre-alpine

# 安装 curl 用于健康检查
RUN apk add --no-cache curl

WORKDIR /app
COPY target/*.jar app.jar

# JVM 调优：容器感知内存分配 + G1GC + OOM 快速失败
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT sh -c "java $JAVA_OPTS -jar /app/app.jar"
```

### 12.4 Spring Profile 说明

| Profile | 数据库配置                         | Redis    | 用途          |
| ------- | ---------------------------------- | -------- | ------------- |
| `dev`   | H2 内存数据库（JPA 自动建表）      | 可选     | 本地开发      |
| `prod`  | PostgreSQL（Flyway 迁移）          | 必须     | 生产/容器部署 |

本地开发启动命令：
```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### 12.5 健康监控端点

| 端点                      | 说明                                   |
| ------------------------- | -------------------------------------- |
| `GET /actuator/health`    | 整体健康状态（DB/Redis/LLM）           |
| `GET /actuator/metrics`   | JVM / HTTP / 自定义指标                |
| `GET /actuator/info`      | 应用版本信息                           |

---

## 13. 开发计划（实际完成情况）

### 13.1 阶段完成状态

| 阶段        | 名称                   | 状态         | 主要交付物                                                                          |
| ----------- | ---------------------- | ------------ | ----------------------------------------------------------------------------------- |
| **Phase 1** | 基础架构               | ✅ 已完成    | Spring Boot 脚手架、包结构、配置层、数据库 Schema（PostgreSQL + Flyway）           |
| **Phase 2** | 数据源层               | ✅ 已完成    | StockService（内置50+只股票静态数据）、MarketAgent 数据采集                        |
| **Phase 3** | 流水线 Agent            | ✅ 已完成    | MarketAgent、TechnicalAgent（MACD/RSI/KDJ/布林带）、SentimentAgent、PortfolioAgent + PipelineOrchestrator |
| **Phase 4** | 辩论系统               | ✅ 已完成    | BullAgent、BearAgent、NeutralAgent、ArbitratorAgent + DebateOrchestrator            |
| **Phase 5** | 工程能力               | ✅ 已完成    | LlmManager（自实现熔断）、PromptManager（内存版本管理）、MemoryService（Redis+内存 fallback）、SSE 流式 |
| **Phase 6** | 前端开发               | ✅ 已完成    | Next.js 16 脚手架、K线图（ECharts）、Agent 对话、自选股管理、报告导出（JSON+Markdown） |
| **Phase 7** | 策略与系统优化         | ✅ 已完成    | StrategyProfile 体系、ThreadLocal 并发修复、Docker 健康检查、Actuator、JVM 调优、.env.example |

### 13.2 详细任务分解

| 阶段        | 任务                   | 状态      |
| ----------- | ---------------------- | --------- |
| **Phase 1** | 项目脚手架             | ✅ 完成   |
|             | 依赖配置（pom.xml）    | ✅ 完成   |
|             | 包结构 com.alphamind   | ✅ 完成   |
|             | 数据模型（实体+DTO+枚举）| ✅ 完成  |
|             | PostgreSQL 迁移脚本    | ✅ 完成   |
| **Phase 2** | StockService 静态数据  | ✅ 完成   |
|             | MarketAgent 行情采集   | ✅ 完成   |
| **Phase 3** | BaseAgent（ThreadLocal）| ✅ 完成  |
|             | AgentRouter            | ✅ 完成   |
|             | MarketAgent            | ✅ 完成   |
|             | TechnicalAgent         | ✅ 完成   |
|             | SentimentAgent         | ✅ 完成   |
|             | PortfolioAgent         | ✅ 完成   |
|             | PipelineOrchestrator   | ✅ 完成   |
| **Phase 4** | BullAgent              | ✅ 完成   |
|             | BearAgent              | ✅ 完成   |
|             | NeutralAgent           | ✅ 完成   |
|             | ArbitratorAgent        | ✅ 完成   |
|             | DebateOrchestrator     | ✅ 完成   |
| **Phase 5** | LlmManager（自实现熔断）| ✅ 完成  |
|             | PromptManager          | ✅ 完成   |
|             | MemoryService          | ✅ 完成   |
|             | AnalysisController SSE | ✅ 完成   |
|             | AdminController        | ✅ 完成   |
| **Phase 6** | Next.js 16 脚手架      | ✅ 完成   |
|             | KLineChart（ECharts）  | ✅ 完成   |
|             | AgentMessage/Selector  | ✅ 完成   |
|             | 自选股管理页面         | ✅ 完成   |
|             | reportExport 工具      | ✅ 完成   |
|             | useSSE hook            | ✅ 完成   |
| **Phase 7** | StrategyProfile 接口   | ✅ 完成   |
|             | 三策略具体实现         | ✅ 完成   |
|             | StrategySignalPlanner  | ✅ 完成   |
|             | StrategyModeResolver   | ✅ 完成   |
|             | ThreadLocal 并发修复   | ✅ 完成   |
|             | Docker 健康检查        | ✅ 完成   |
|             | Spring Boot Actuator   | ✅ 完成   |
|             | JVM 调优参数           | ✅ 完成   |
|             | .env.example           | ✅ 完成   |

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

- Spring AI 官方文档: https://docs.spring.io/spring-ai/
- Next.js 16 文档: https://nextjs.org/docs
- ECharts 文档: https://echarts.apache.org/
- PostgreSQL 16 文档: https://www.postgresql.org/docs/

---

_文档版本: v2.0.0_
_最后更新: 2026-05-01_
_所有 Phase 1-7 已交付完成_

