"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ArrowRight, BookOpen, Loader2, ShieldCheck } from "lucide-react";
import api from "@/api/client";
import { isAxiosError } from "axios";
import type { AnalysisReport } from "@/types";
import type {
  Decision,
  Fundamentals,
  PersonalModelDefinition,
  PersonalModelReview,
  PersonalReviewDraft,
  Valuation,
} from "@/types/investment";
import { Button } from "@/components/common/Button";
import { PersonalModelPanel } from "@/components/analysis/PersonalModelPanel";
import { FundamentalsCard } from "@/components/analysis/FundamentalsCard";

const inputStyle =
  "mt-2 w-full min-w-0 rounded-xl border border-[var(--border)] bg-white px-3 py-2.5 text-sm text-[var(--text-primary)]";
const labelStyle =
  "block min-w-0 text-xs leading-5 text-[var(--text-secondary)]";

export default function InvestmentModelPage() {
  const [model, setModel] = useState<PersonalModelDefinition | null>(null);
  const [stockCode, setStockCode] = useState("601138");
  const [review, setReview] = useState<PersonalModelReview>({});
  const [report, setReport] = useState<AnalysisReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<PersonalReviewDraft | null>(null);
  const [draftLoading, setDraftLoading] = useState(false);
  const [draftMessage, setDraftMessage] = useState<string | null>(null);
  const [fundamentalsResult, setFundamentalsResult] = useState<{
    code: string;
    data: Fundamentals | null;
    message: string | null;
  } | null>(null);
  const draftRequest = useRef(0);
  useEffect(
    () => () => {
      draftRequest.current++;
    },
    [],
  );
  useEffect(() => {
    let cancelled = false;
    api
      .get("/investment-model")
      .then(({ data }) => {
        if (data.code !== 200 || !data.data) throw new Error("模型加载失败");
        if (!cancelled) {
          setModel(data.data);
          const requested = new URL(window.location.href).searchParams.get(
            "stock",
          );
          setError(null);
          if (requested) {
            const supported = data.data.assets.some(
              (item: { code: string }) => item.code === requested,
            );
            setStockCode(supported ? requested : "");
            if (!supported)
              setError(
                `代码 ${requested} 不在本模型的 13 个观察标的内，请重新选择。`,
              );
          }
        }
      })
      .catch(() => {
        if (!cancelled) setError("投资模型加载失败，请重新加载");
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);
  const asset = model?.assets.find((item) => item.code === stockCode);
  const assetCode = asset?.code;
  useEffect(() => {
    if (!assetCode) return;
    let cancelled = false;
    const fallback = "财报/估值数据暂不可用，请手工填写证据";
    api
      .get(`/investment-model/fundamentals/${encodeURIComponent(assetCode)}`)
      .then(({ data }) => {
        if (cancelled) return;
        const ok = data.code === 200 && data.data?.stockCode === assetCode;
        setFundamentalsResult({
          code: assetCode,
          data: ok ? data.data : null,
          message: ok ? null : data.message || fallback,
        });
      })
      .catch((caught) => {
        if (cancelled) return;
        setFundamentalsResult({
          code: assetCode,
          data: null,
          message:
            (isAxiosError(caught) && caught.response?.data?.message) ||
            fallback,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [assetCode]);
  const current =
    fundamentalsResult?.code === assetCode ? fundamentalsResult : null;
  const fundamentals = current?.data ?? null;
  const fundamentalsMessage = current?.message ?? null;
  const fundamentalsLoading = Boolean(assetCode) && !current;
  const appendEvidenceSummary = () => {
    if (!fundamentals) return;
    const existing = review.evidenceSource?.trim();
    if (existing?.includes(fundamentals.evidenceSummary)) return;
    set(
      "evidenceSource",
      (existing ? `${existing}\n` : "") + fundamentals.evidenceSummary,
    );
  };
  const loadDraft = async () => {
    if (!asset) return;
    const request = ++draftRequest.current;
    setDraftLoading(true);
    setDraftMessage(null);
    try {
      const { data } = await api.get(
        `/investment-model/reviews/${encodeURIComponent(stockCode)}/latest`,
      );
      if (request !== draftRequest.current) return;
      if (data.code !== 200 || !data.data) {
        setDraftMessage(data.message || "载入历史复核失败，请稍后重试");
        return;
      }
      const loaded = data.data as PersonalReviewDraft;
      if (loaded.stockCode !== stockCode) {
        setDraftMessage("历史复核与当前标的不匹配，未更改表单");
        return;
      }
      setDraft(loaded);
      setReview(loaded.review);
      setReport(null);
    } catch {
      if (request === draftRequest.current)
        setDraftMessage("载入历史复核失败，请稍后重试；当前表单已保留");
    } finally {
      if (request === draftRequest.current) setDraftLoading(false);
    }
  };
  const set = <K extends keyof PersonalModelReview>(
    key: K,
    value: PersonalModelReview[K],
  ) => setReview((previous) => ({ ...previous, [key]: value }));
  const decision = (key: keyof PersonalModelReview, label: string) => (
    <label key={key} className={labelStyle}>
      {label}
      <select
        aria-label={label}
        value={String(review[key] ?? "UNKNOWN")}
        onChange={(event) => set(key, event.target.value as Decision)}
        className={inputStyle}
      >
        <option value="UNKNOWN">未确认</option>
        <option value="PASS">已复核通过</option>
        <option value="FAIL">未通过</option>
      </select>
    </label>
  );
  const percent = (key: keyof PersonalModelReview, label: string) => (
    <label key={key} className={labelStyle}>
      {label}
      <input
        aria-label={label}
        type="number"
        min="0"
        max="100"
        step="0.01"
        value={String(review[key] ?? "")}
        onChange={(event) =>
          set(
            key,
            event.target.value === "" ? null : Number(event.target.value),
          )
        }
        className={inputStyle}
        placeholder="未提供"
      />
    </label>
  );
  const analyze = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setReport(null);
    try {
      const { data } = await api.post(
        "/investment-model/analyze",
        { stockCode, review },
        { timeout: 150000 },
      );
      if (data.code !== 200 || !data.data?.personalModel)
        throw new Error(data.message || "模型复核失败");
      setReport(data.data);
    } catch (failure) {
      if (isAxiosError(failure))
        setError("模型复核请求失败，请检查输入和后端服务后重试");
      else
        setError(
          failure instanceof Error
            ? failure.message
            : "模型复核失败，请稍后重试",
        );
    } finally {
      setLoading(false);
    }
  };
  return (
    <div className="space-y-6 px-4 py-6 sm:px-7 lg:px-9 lg:py-8">
      <header>
        <p className="mb-2 flex items-center gap-2 text-xs text-[var(--accent)]">
          <ShieldCheck className="size-4" />
          个人长期投资框架
        </p>
        <h1 className="text-3xl font-semibold">我的投资模型</h1>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-[var(--text-secondary)]">
          价格触发 · 估值确认 · 基本面验证 · RSI / ATR 择时 ·
          风险预算。先证明价值，再讨论价格；先确认基本面，再使用技术指标。
        </p>
      </header>
      {error && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[var(--bearish-glow)] p-4 text-sm text-[var(--bearish)]"
        >
          {error}
          {!model && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setAttempt((value) => value + 1)}
            >
              重新加载
            </Button>
          )}
        </div>
      )}
      {!model ? (
        !error && (
          <p role="status" className="py-8 text-sm text-[var(--text-muted)]">
            正在读取模型...
          </p>
        )
      ) : (
        <>
          <section className="glass-card p-5 sm:p-6">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                <BookOpen className="size-4 text-[var(--accent)]" />v
                {model.version} · {model.versionDate} · {model.assets.length}{" "}
                个观察标的
              </h2>
              <p className="text-xs text-[var(--text-muted)]">
                原文长期目标年化约 5%–8%，不是收益预测
              </p>
            </div>
            <p className="text-sm leading-7 text-[var(--text-secondary)]">
              价格区间只负责触发复核，不是机械买入信号。估值、盈利、现金流、核心逻辑或组合风险未确认时，不会生成
              S3/S4。文档旧持仓不作为实时账户。
            </p>
          </section>
          <form onSubmit={(event) => void analyze(event)} className="space-y-5">
            <fieldset
              disabled={loading || draftLoading}
              className="space-y-5 disabled:opacity-70"
            >
              <section className="glass-card p-5 sm:p-6">
                <label className={labelStyle}>
                  观察标的
                  <select
                    aria-label="观察标的"
                    value={stockCode}
                    onChange={(event) => {
                      setStockCode(event.target.value);
                      setReview({});
                      setReport(null);
                      setError(null);
                      draftRequest.current++;
                      setDraft(null);
                      setDraftMessage(null);
                      setDraftLoading(false);
                    }}
                    className={inputStyle}
                  >
                    {!stockCode && (
                      <option value="" disabled>
                        请选择模型内标的
                      </option>
                    )}
                    {model.assets.map((item) => (
                      <option key={item.code} value={item.code}>
                        {item.name} · {item.code}
                      </option>
                    ))}
                  </select>
                </label>
                {asset && (
                  <div className="mt-5 space-y-4">
                    <div className="flex flex-wrap gap-2 text-xs text-[var(--accent)]">
                      <span>{asset.category}</span>
                      <span>· {asset.theme}</span>
                      {asset.provisional && <span>· 暂定观察区</span>}
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                      {asset.bands.map((band) => (
                        <div
                          key={band.tier}
                          className="rounded-xl bg-[var(--bg-tertiary)] p-3"
                        >
                          <p className="text-xs text-[var(--text-muted)]">
                            {["首档", "第二档", "第三档"][band.tier - 1]}
                          </p>
                          <p className="mt-2 font-mono text-sm">
                            {band.low}–{band.high}
                          </p>
                        </div>
                      ))}
                    </div>
                    <p className="text-xs leading-6 text-[var(--text-secondary)]">
                      估值：{asset.valuationFocus}
                      <br />
                      基本面：{asset.fundamentalFocus}
                      <br />
                      重点风险：{asset.vetoRisk}
                    </p>
                  </div>
                )}
              </section>
              <section className="glass-card p-5 sm:p-6">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <h2 className="text-base font-semibold">估值与基本面证据</h2>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={!asset || draftLoading}
                    onClick={() => void loadDraft()}
                  >
                    {draftLoading && (
                      <Loader2 className="size-3.5 animate-spin" />
                    )}
                    载入上次复核
                  </Button>
                </div>
                {draftMessage && (
                  <p
                    role="status"
                    className="mt-4 rounded-xl bg-[var(--neutral-glow)] p-3 text-xs leading-6 text-[var(--text-secondary)]"
                  >
                    {draftMessage}
                  </p>
                )}
                {draft && (
                  <div
                    role="status"
                    className="mt-4 rounded-xl bg-[var(--accent-subtle)] p-4 text-xs leading-6 text-[var(--text-secondary)]"
                  >
                    <p className="font-medium">
                      已载入 v{draft.modelVersion} 历史复核草稿
                    </p>
                    {draft.expired && (
                      <p className="text-[var(--bearish)]">
                        源证据已过期或日期不完整，请重新核实，日期不会自动延长。
                      </p>
                    )}
                    <ul className="mt-2 list-disc pl-4">
                      {draft.notices.map((notice) => (
                        <li key={notice}>{notice}</li>
                      ))}
                    </ul>
                    <Link
                      href={`/history?report=${encodeURIComponent(draft.sourceReportId)}`}
                      className="mt-2 inline-block font-medium text-[var(--accent)]"
                    >
                      查看来源报告
                    </Link>
                  </div>
                )}
                <div className="mt-4">
                  {fundamentalsLoading && (
                    <p className="flex items-center gap-2 text-xs text-[var(--text-muted)]">
                      <Loader2 className="size-3.5 animate-spin" />
                      正在读取财报与估值数据…
                    </p>
                  )}
                  {fundamentalsMessage && (
                    <p
                      role="status"
                      className="rounded-xl bg-[var(--neutral-glow)] p-3 text-xs leading-6 text-[var(--text-secondary)]"
                    >
                      {fundamentalsMessage}
                    </p>
                  )}
                  {fundamentals && (
                    <>
                      <FundamentalsCard data={fundamentals} />
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="mt-3"
                        onClick={appendEvidenceSummary}
                      >
                        将取数摘要填入证据来源
                      </Button>
                    </>
                  )}
                </div>
                <p className="mt-4 mb-5 text-xs leading-6 text-[var(--text-muted)]">
                  自动取数只作参考，不会改变任何决策门。以下结论仍是你的手工复核声明，报告会保存来源、日期和状态；留空时如实标记待复核。
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className={labelStyle}>
                    复核日期
                    <input
                      aria-label="复核日期"
                      type="date"
                      value={review.reviewedAt ?? ""}
                      onChange={(event) =>
                        set("reviewedAt", event.target.value || undefined)
                      }
                      className={inputStyle}
                    />
                  </label>
                  <label className={labelStyle}>
                    证据有效至
                    <input
                      aria-label="证据有效至"
                      type="date"
                      value={review.validUntil ?? ""}
                      onChange={(event) =>
                        set("validUntil", event.target.value || undefined)
                      }
                      className={inputStyle}
                    />
                  </label>
                  <label className={labelStyle}>
                    估值结论
                    <select
                      aria-label="估值结论"
                      value={review.valuation ?? "UNKNOWN"}
                      onChange={(event) =>
                        set("valuation", event.target.value as Valuation)
                      }
                      className={inputStyle}
                    >
                      <option value="UNKNOWN">未确认</option>
                      <option value="REASONABLE">合理</option>
                      <option value="UNDERVALUED">低估</option>
                      <option value="OVERVALUED">未通过 / 仍高估</option>
                    </select>
                  </label>
                  {decision("earnings", "盈利与扣非利润稳定")}
                  {decision("cashFlow", "经营现金流支持盈利")}
                  {decision("businessLogic", "核心业务逻辑仍成立")}
                  {decision("valueTrapClear", "已排除价值陷阱与重大风险")}
                  {asset &&
                    Object.entries(asset.specialChecks).map(([key, label]) => (
                      <label key={key} className={labelStyle}>
                        {label}
                        <select
                          aria-label={label}
                          value={review.specialChecks?.[key] ?? "UNKNOWN"}
                          onChange={(event) =>
                            set("specialChecks", {
                              ...review.specialChecks,
                              [key]: event.target.value as Decision,
                            })
                          }
                          className={inputStyle}
                        >
                          <option value="UNKNOWN">未确认</option>
                          <option value="PASS">已复核通过</option>
                          <option value="FAIL">未通过</option>
                        </select>
                      </label>
                    ))}
                </div>
                <label className={`${labelStyle} mt-5`}>
                  证据来源与报告期
                  <textarea
                    aria-label="证据来源与报告期"
                    maxLength={4000}
                    value={review.evidenceSource ?? ""}
                    onChange={(event) =>
                      set("evidenceSource", event.target.value)
                    }
                    rows={3}
                    className={inputStyle}
                    placeholder="填写财报/公告链接、报告期及估值依据；不以新闻标题代替财务证据"
                  />
                </label>
                <label className={`${labelStyle} mt-4`}>
                  为什么现在不应该买
                  <textarea
                    aria-label="为什么现在不应该买"
                    maxLength={4000}
                    value={review.counterArgument ?? ""}
                    onChange={(event) =>
                      set("counterArgument", event.target.value)
                    }
                    rows={2}
                    className={inputStyle}
                    placeholder="记录反方证据和需要持续跟踪的风险"
                  />
                </label>
              </section>
              <details className="glass-card p-5 sm:p-6">
                <summary className="cursor-pointer text-base font-semibold">
                  择时确认与交易后风险预算
                </summary>
                <p className="mt-4 mb-5 text-xs leading-6 text-[var(--text-muted)]">
                  日 RSI14 与 ATR20 来自真实日 K；周 RSI
                  使用已结束周，数据不足则留空。周线、底背离和波动收敛需对应最新交易日人工复核。权重按整个组合计算，主题应合并相关持仓。
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                  {decision("weeklyTiming", "当期日线与周线联合复核")}
                  {decision("bottomDivergence", "已确认超卖后底背离")}
                  {decision("volatilityContraction", "已确认波动收敛")}
                  {decision("noLeverage", "确认不使用杠杆")}
                  {percent("projectedPositionPct", "交易后单标的权重（%）")}
                  {percent(
                    "projectedThemePct",
                    `交易后${asset?.theme ?? "主题"}合计权重（%）`,
                  )}
                  {percent("cashAfterTradePct", "交易后现金及低波动比例（%）")}
                  {percent("portfolioDrawdownPct", "当前组合回撤幅度（%）")}
                  {asset?.category === "黄金矿股ETF" &&
                    percent(
                      "goldPositionLimitPct",
                      "黄金矿股ETF人工仓位上限（%）",
                    )}
                </div>
                <p className="mt-5 text-xs leading-6 text-[var(--text-muted)]">
                  执行采用原文范围的保守端：普通单股 5%、成长科技起步
                  3%、主题合计 20%、现金底线 15%、组合回撤 20%。10%–20%
                  的试仓比例指单标的目标仓位内部，不是总资产比例。
                </p>
              </details>
            </fieldset>
            <Button
              type="submit"
              disabled={loading || draftLoading || !asset}
              className="h-12 w-full sm:w-auto"
            >
              {loading ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <ShieldCheck className="size-4" />
              )}
              {loading ? "正在获取行情并复核..." : "按最新行情复核并保存"}
            </Button>
          </form>
          {report?.personalModel && (
            <section className="glass-card p-5 sm:p-6">
              <PersonalModelPanel
                assessment={report.personalModel}
                marketData={report.marketData}
              />
              <Link
                href={`/history?report=${encodeURIComponent(report.id)}`}
                className="mt-6 inline-flex items-center gap-2 text-sm font-medium text-[var(--accent)]"
              >
                查看已保存的报告与导出
                <ArrowRight className="size-4" />
              </Link>
            </section>
          )}
          <footer className="border-t border-[var(--border)] pt-4 text-xs leading-6 text-[var(--text-muted)]">
            规则来源：{model.sourceFile}
            。原文件保持不变。没有自动下单或自动提醒；每次复核以最新证据和组合数据为准。
          </footer>
        </>
      )}
    </div>
  );
}
