import type { AnalysisReport, DebatePosition } from "@/types";
import { SignalType } from "@/types";

function sanitizeFileName(input: string): string {
  return input
    .replace(/[\\/:*?"<>|]/g, "-")
    .replace(/\s+/g, "_")
    .replace(/-+/g, "-")
    .trim();
}

function signalLabel(signal: SignalType): string {
  if (signal === SignalType.BUY) return "买入";
  if (signal === SignalType.SELL) return "卖出";
  return "持有";
}

function debateLabel(position?: DebatePosition): string {
  if (position === "BULLISH") return "看多";
  if (position === "BEARISH") return "看空";
  if (position === "NEUTRAL") return "中性";
  return "-";
}

function getBaseName(report: AnalysisReport): string {
  const raw = `${report.stockCode}_${report.stockName}_${new Date(report.createdAt).toISOString().slice(0, 19)}`;
  return sanitizeFileName(raw);
}

function triggerDownload(content: string, mimeType: string, fileName: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

export function reportToMarkdown(report: AnalysisReport): string {
  const confidencePercent = `${(report.confidence.value * 100).toFixed(0)}%`;
  const createdAt = new Date(report.createdAt).toLocaleString("zh-CN");
  const lines = [
    `# ${report.stockName}（${report.stockCode}）分析报告`,
    "",
    `- 生成时间：${createdAt}`,
    `- 最终信号：${signalLabel(report.finalSignal)}`,
    `- 置信度：${confidencePercent}（${report.confidence.level}）`,
    "",
    "## 交易建议",
    "",
    `- 入场价：¥${report.tradeSignal.entryPrice.toFixed(2)}`,
    `- 目标价：¥${report.tradeSignal.targetPrice.toFixed(2)}`,
    `- 止损价：¥${report.tradeSignal.stopLoss.toFixed(2)}`,
    `- 持仓周期：${report.tradeSignal.holdingPeriodDays} 个交易日`,
    `- 建议理由：${report.tradeSignal.rationale}`,
    "",
    "## 市场数据",
    "",
    `- 当前价：¥${report.marketData.currentPrice.toFixed(2)}`,
    `- 涨跌幅：${report.marketData.changePercent.toFixed(2)}%`,
    `- 成交量：${report.marketData.volume.toFixed(2)}`,
    `- 换手率：${report.marketData.turnoverRate.toFixed(2)}%`,
    "",
    "## 技术面",
    "",
    `- 技术评分：${report.technicalIndicators.technicalScore.toFixed(2)}`,
    `- MACD(DIF/DEA/Hist)：${report.technicalIndicators.macd.dif.toFixed(2)} / ${report.technicalIndicators.macd.dea.toFixed(2)} / ${report.technicalIndicators.macd.histogram.toFixed(2)}`,
    `- RSI(6/12/24)：${report.technicalIndicators.rsi.rsi6.toFixed(2)} / ${report.technicalIndicators.rsi.rsi12.toFixed(2)} / ${report.technicalIndicators.rsi.rsi24.toFixed(2)}`,
    `- KDJ(K/D/J)：${report.technicalIndicators.kdj.k.toFixed(2)} / ${report.technicalIndicators.kdj.d.toFixed(2)} / ${report.technicalIndicators.kdj.j.toFixed(2)}`,
    "",
    "## 舆情面",
    "",
    `- 舆情评分：${(report.sentimentData.sentimentScore * 100).toFixed(0)} / 100`,
    `- 趋势判断：${report.sentimentData.sentimentTrend}`,
    `- 摘要：${report.sentimentData.analysisSummary}`,
    "",
    "### 利好因素",
    ...report.sentimentData.positiveFactors.map((item) => `- ${item}`),
    "",
    "### 风险因素",
    ...report.sentimentData.negativeFactors.map((item) => `- ${item}`),
  ];

  if (report.judgment) {
    lines.push(
      "",
      "## 辩论裁决",
      "",
      `- 最终立场：${debateLabel(report.judgment.finalPosition)}`,
      `- 裁决说明：${report.judgment.reasoning}`,
    );

    if (report.judgment.riskWarnings?.length) {
      lines.push("", "### 风险提示", ...report.judgment.riskWarnings.map((r) => `- ${r}`));
    }
  }

  return lines.join("\n");
}

export function downloadReportJson(report: AnalysisReport) {
  const fileName = `${getBaseName(report)}.json`;
  triggerDownload(JSON.stringify(report, null, 2), "application/json", fileName);
}

export function downloadReportMarkdown(report: AnalysisReport) {
  const fileName = `${getBaseName(report)}.md`;
  triggerDownload(reportToMarkdown(report), "text/markdown;charset=utf-8", fileName);
}

// ─── PDF Export ────────────────────────────────────────────────────────────

function buildReportHtml(report: AnalysisReport): string {
  const signal = signalLabel(report.finalSignal);
  const signalColor =
    report.finalSignal === SignalType.BUY
      ? "#22c55e"
      : report.finalSignal === SignalType.SELL
        ? "#ef4444"
        : "#f59e0b";
  const confidencePercent = `${(report.confidence.value * 100).toFixed(0)}%`;
  const createdAt = new Date(report.createdAt).toLocaleString("zh-CN");
  const t = report.technicalIndicators;
  const m = report.marketData;
  const s = report.sentimentData;

  const sectionStyle =
    "margin-bottom:20px;background:#f8fafc;border-radius:8px;padding:16px;border:1px solid #e2e8f0;";
  const labelStyle =
    "font-size:11px;color:#64748b;font-family:monospace;margin-bottom:3px;";
  const valueStyle = "font-size:14px;font-weight:600;color:#0f172a;";
  const headingStyle =
    "font-size:13px;font-weight:700;color:#0f172a;margin:0 0 12px;padding-bottom:8px;border-bottom:2px solid #e2e8f0;";

  const header = `
    <div style="margin-bottom:20px;padding-bottom:16px;border-bottom:3px solid #0ea5e9;">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;">
        <div>
          <div style="font-size:22px;font-weight:800;color:#0f172a;">${report.stockName}</div>
          <div style="font-size:13px;color:#64748b;font-family:monospace;margin-top:4px;">
            ${report.stockCode} · AlphaMind 智能分析报告
          </div>
        </div>
        <div style="text-align:right;">
          <div style="display:inline-block;padding:5px 14px;background:${signalColor};color:#fff;border-radius:20px;font-weight:700;font-size:15px;">${signal}</div>
          <div style="font-size:11px;color:#64748b;margin-top:5px;">${createdAt}</div>
        </div>
      </div>
    </div>`;

  const trade = `
    <div style="${sectionStyle}">
      <p style="${headingStyle}">📊 交易建议</p>
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:10px;">
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <div style="${labelStyle}">入场价</div>
          <div style="${valueStyle}">¥${report.tradeSignal.entryPrice.toFixed(2)}</div>
        </div>
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #22c55e;">
          <div style="${labelStyle}">目标价</div>
          <div style="font-size:14px;font-weight:600;color:#22c55e;">¥${report.tradeSignal.targetPrice.toFixed(2)}</div>
        </div>
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #ef4444;">
          <div style="${labelStyle}">止损价</div>
          <div style="font-size:14px;font-weight:600;color:#ef4444;">¥${report.tradeSignal.stopLoss.toFixed(2)}</div>
        </div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:10px;">
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <span style="${labelStyle}">持仓周期：</span>
          <span style="font-weight:600;">${report.tradeSignal.holdingPeriodDays} 个交易日</span>
        </div>
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <span style="${labelStyle}">综合置信度：</span>
          <span style="font-weight:600;">${confidencePercent}（${report.confidence.level}）</span>
        </div>
      </div>
      <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
        <div style="${labelStyle}">投资理由</div>
        <div style="font-size:13px;color:#374151;line-height:1.6;">${report.tradeSignal.rationale}</div>
      </div>
    </div>`;

  const changeColor = m.changePercent >= 0 ? "#22c55e" : "#ef4444";
  const changeSign = m.changePercent >= 0 ? "+" : "";
  const marketCells = [
    { label: "当前价", value: `¥${m.currentPrice.toFixed(2)}` },
    { label: "涨跌幅", value: `${changeSign}${m.changePercent.toFixed(2)}%`, color: changeColor },
    { label: "成交量（手）", value: m.volume.toFixed(0) },
    { label: "换手率", value: `${m.turnoverRate.toFixed(2)}%` },
    { label: "市值（亿）", value: (m.marketCap / 1e8).toFixed(2) },
    { label: "市盈率", value: m.pe.toFixed(2) },
    { label: "市净率", value: m.pb.toFixed(2) },
    { label: "52周高", value: `¥${m.high.toFixed(2)}` },
  ]
    .map(
      (item) =>
        `<div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <div style="${labelStyle}">${item.label}</div>
          <div style="font-size:13px;font-weight:600;color:${item.color ?? "#0f172a"};">${item.value}</div>
        </div>`,
    )
    .join("");

  const market = `
    <div style="${sectionStyle}">
      <p style="${headingStyle}">📈 市场行情</p>
      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:10px;">${marketCells}</div>
    </div>`;

  const technical = `
    <div style="${sectionStyle}">
      <p style="${headingStyle}">📉 技术指标</p>
      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:10px;">
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <div style="${labelStyle}">技术评分</div>
          <div style="font-size:18px;font-weight:700;color:#0ea5e9;">${t.technicalScore.toFixed(1)}</div>
        </div>
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <div style="${labelStyle}">MACD（DIF / DEA）</div>
          <div style="font-size:12px;font-weight:600;">${t.macd.dif.toFixed(2)} / ${t.macd.dea.toFixed(2)}</div>
        </div>
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <div style="${labelStyle}">RSI（6 / 12 / 24）</div>
          <div style="font-size:12px;font-weight:600;">${t.rsi.rsi6.toFixed(1)} / ${t.rsi.rsi12.toFixed(1)} / ${t.rsi.rsi24.toFixed(1)}</div>
        </div>
        <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;">
          <div style="${labelStyle}">KDJ（K / D / J）</div>
          <div style="font-size:12px;font-weight:600;">${t.kdj.k.toFixed(1)} / ${t.kdj.d.toFixed(1)} / ${t.kdj.j.toFixed(1)}</div>
        </div>
      </div>
    </div>`;

  const sentColor =
    s.sentimentScore >= 0.6 ? "#22c55e" : s.sentimentScore >= 0.4 ? "#f59e0b" : "#ef4444";
  const posRows = (s.positiveFactors ?? [])
    .map((f) => `<div style="font-size:12px;color:#374151;margin-bottom:2px;padding-left:8px;">· ${f}</div>`)
    .join("");
  const negRows = (s.negativeFactors ?? [])
    .map((f) => `<div style="font-size:12px;color:#374151;margin-bottom:2px;padding-left:8px;">· ${f}</div>`)
    .join("");
  const sentiment = `
    <div style="${sectionStyle}">
      <p style="${headingStyle}">💬 舆情分析</p>
      <div style="display:flex;align-items:center;gap:16px;margin-bottom:10px;">
        <div style="font-size:28px;font-weight:800;color:${sentColor};">
          ${(s.sentimentScore * 100).toFixed(0)}<span style="font-size:13px;color:#64748b;">/100</span>
        </div>
        <div>
          <div style="font-size:13px;font-weight:600;">${s.sentimentTrend}</div>
          <div style="font-size:11px;color:#64748b;">媒体关注度 ${(s.mediaAttention * 100).toFixed(0)}%</div>
        </div>
      </div>
      ${s.analysisSummary ? `<div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;font-size:12px;color:#374151;line-height:1.6;margin-bottom:10px;">${s.analysisSummary}</div>` : ""}
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;">
        ${posRows ? `<div><div style="font-size:11px;color:#22c55e;font-weight:700;margin-bottom:5px;">▲ 利好因素</div>${posRows}</div>` : ""}
        ${negRows ? `<div><div style="font-size:11px;color:#ef4444;font-weight:700;margin-bottom:5px;">▼ 风险因素</div>${negRows}</div>` : ""}
      </div>
    </div>`;

  let judgment = "";
  if (report.judgment?.finalPosition) {
    const j = report.judgment;
    const warnings = (j.riskWarnings ?? [])
      .map((w) => `<div style="font-size:12px;color:#374151;margin-bottom:2px;">· ${w}</div>`)
      .join("");
    judgment = `
      <div style="${sectionStyle}">
        <p style="${headingStyle}">⚖️ 辩论裁决</p>
        <div style="display:flex;gap:10px;margin-bottom:10px;">
          <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;min-width:80px;">
            <div style="${labelStyle}">最终立场</div>
            <div style="font-size:14px;font-weight:700;">${debateLabel(j.finalPosition)}</div>
          </div>
          <div style="background:#fff;padding:10px;border-radius:6px;border:1px solid #e2e8f0;flex:1;">
            <div style="${labelStyle}">裁决说明</div>
            <div style="font-size:12px;color:#374151;line-height:1.5;">${j.reasoning}</div>
          </div>
        </div>
        ${warnings ? `<div style="background:#fef2f2;padding:10px;border-radius:6px;border:1px solid #fecaca;"><div style="font-size:11px;color:#ef4444;font-weight:700;margin-bottom:5px;">⚠️ 风险提示</div>${warnings}</div>` : ""}
      </div>`;
  }

  let debateViews = "";
  if (report.debateViews?.length) {
    const rows = report.debateViews
      .map((dv) => {
        const c = dv.position === "BULLISH" ? "#22c55e" : dv.position === "BEARISH" ? "#ef4444" : "#f59e0b";
        return `<div style="background:#fff;padding:10px;border-radius:6px;border-left:3px solid ${c};margin-bottom:8px;">
          <div style="font-size:11px;font-weight:700;color:${c};margin-bottom:4px;">${debateLabel(dv.position)}</div>
          <div style="font-size:12px;color:#374151;line-height:1.5;">${dv.view}</div>
        </div>`;
      })
      .join("");
    debateViews = `
      <div style="${sectionStyle}">
        <p style="${headingStyle}">🗣️ 辩论观点</p>
        ${rows}
      </div>`;
  }

  const footer = `
    <div style="margin-top:20px;padding-top:10px;border-top:1px solid #e2e8f0;display:flex;justify-content:space-between;align-items:center;">
      <div style="font-size:10px;color:#94a3b8;">AlphaMind 智能股票分析系统 · 本报告由 AI 生成，仅供参考，不构成投资建议</div>
      <div style="font-size:10px;color:#94a3b8;font-family:monospace;">${createdAt}</div>
    </div>`;

  return `<div style="width:714px;font-family:'PingFang SC','Microsoft YaHei','Helvetica Neue',Arial,sans-serif;font-size:14px;line-height:1.5;color:#0f172a;background:#ffffff;padding:40px;">
    ${header}${trade}${market}${technical}${sentiment}${judgment}${debateViews}${footer}
  </div>`;
}

/**
 * 生成 PDF 报告并触发下载。
 * 使用 html2canvas 将报告 HTML 渲染为图片，再通过 jsPDF 输出多页 A4 PDF。
 * jspdf 和 html2canvas 均为动态导入，不影响首屏包体积。
 */
export async function downloadReportPdf(report: AnalysisReport): Promise<void> {
  // Dynamic imports — keep initial bundle lean
  const [{ default: jsPDF }, { default: html2canvas }] = await Promise.all([
    import("jspdf"),
    import("html2canvas"),
  ]);

  const wrapper = document.createElement("div");
  wrapper.style.cssText = "position:absolute;left:-9999px;top:0;z-index:-1;overflow:visible;";
  wrapper.innerHTML = buildReportHtml(report);
  document.body.appendChild(wrapper);

  try {
    const el = wrapper.firstElementChild as HTMLElement;
    const canvas = await html2canvas(el, {
      scale: 2,
      useCORS: true,
      backgroundColor: "#ffffff",
      logging: false,
    });

    const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
    const pageW = pdf.internal.pageSize.getWidth();
    const pageH = pdf.internal.pageSize.getHeight();
    const imgH = (canvas.height / canvas.width) * pageW;
    const imgData = canvas.toDataURL("image/jpeg", 0.95);

    let remaining = imgH;
    let yOffset = 0;
    while (remaining > 0) {
      if (yOffset > 0) pdf.addPage();
      pdf.addImage(imgData, "JPEG", 0, -yOffset, pageW, imgH);
      yOffset += pageH;
      remaining -= pageH;
    }

    pdf.save(`${getBaseName(report)}.pdf`);
  } finally {
    document.body.removeChild(wrapper);
  }
}
