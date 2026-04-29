package com.alphamind.model.dto;

import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.DebatePosition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 辩论观点 DTO - 代表 Bull/Bear/Neutral 三方各自的分析视角
 *
 * <p>字段命名与前端 {@code DebateView} 接口保持一致：
 * <ul>
 *   <li>{@code view}        - 主要论述文本</li>
 *   <li>{@code reasons}     - 支撑论点列表（3-5 条）</li>
 *   <li>{@code keyPoints}   - 核心观察点（最多 3 条）</li>
 *   <li>{@code attackPoints}- 针对对立方的反驳论点（可选）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateViewDTO {

    /** 辩论立场：BULLISH / BEARISH / NEUTRAL */
    private DebatePosition position;

    /** 产生此观点的 Agent 类型 */
    private AgentType agentType;

    /** 主要论述全文（对应前端 view 字段） */
    private String view;

    /** 支撑论点列表 */
    private List<String> reasons;

    /** 核心观察点 */
    private List<String> keyPoints;

    /** 目标价（多头给出上涨目标；空头可给出下行目标；中立可为 null） */
    private Double targetPrice;

    /** 涨跌幅描述，如 "+15.0%" / "-8.0%" */
    private String upsidePotential;

    /** 置信度 */
    private ConfidenceDTO confidence;

    /** 针对对立方的反驳论点（可选，增强辩论互动感） */
    private List<String> attackPoints;
}
