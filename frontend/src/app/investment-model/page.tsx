"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ArrowRight, BookOpen, Loader2, ShieldCheck } from "lucide-react";
import api from "@/api/client";
import { isAxiosError } from "axios";
import type { AnalysisReport } from "@/types";
import type {
  PersonalModelDefinition,
  PersonalModelReview,
  PersonalReviewDraft,
} from "@/types/investment";
import { Button } from "@/components/common/Button";
import { PersonalModelPanel } from "@/components/analysis/PersonalModelPanel";
import { useFundamentals } from "@/hooks/useFundamentals";
import { AssetSummary } from "@/components/investment/AssetSummary";
import { AutoFundamentals } from "@/components/investment/AutoFundamentals";
import { DraftNotice } from "@/components/investment/DraftNotice";
import { EvidenceFields } from "@/components/investment/EvidenceFields";
import { RiskBudgetSection } from "@/components/investment/RiskBudgetSection";
import { PriceAlertPanel } from "@/components/investment/PriceAlertPanel";
import { PortfolioEditor } from "@/components/investment/PortfolioEditor";
import { PortfolioHealthPanel } from "@/components/investment/PortfolioHealthPanel";
import { PortfolioSnapshotPanel } from "@/components/investment/PortfolioSnapshotPanel";
import {
  inputStyle,
  labelStyle,
  type ReviewSetter,
} from "@/components/investment/formControls";

export default function InvestmentModelPage() {
  const [model, setModel] = useState<PersonalModelDefinition | null>(null);
  const [stockCode, setStockCode] = useState("601138");
  const [review, setReview] = useState<PersonalModelReview>({});
  const [report, setReport] = useState<AnalysisReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [portfolioVersion, setPortfolioVersion] = useState(0);
  const [draft, setDraft] = useState<PersonalReviewDraft | null>(null);
  const [draftLoading, setDraftLoading] = useState(false);
  const [draftMessage, setDraftMessage] = useState<string | null>(null);
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
  const {
    fundamentals,
    message: fundamentalsMessage,
    loading: fundamentalsLoading,
  } = useFundamentals(assetCode);
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
  const selectStock = (code: string) => {
    setStockCode(code);
    setReview({});
    setReport(null);
    setError(null);
    draftRequest.current++;
    setDraft(null);
    setDraftMessage(null);
    setDraftLoading(false);
  };
  const reviewFromAlert = (code: string) => {
    if (code !== stockCode) selectStock(code);
    document
      .getElementById("review-form")
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  };
  const set: ReviewSetter = (key, value) =>
    setReview((previous) => ({ ...previous, [key]: value }));
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
          <PriceAlertPanel onReview={reviewFromAlert} />
          <PortfolioHealthPanel
            refreshKey={portfolioVersion}
            assetCodes={model.assets.map((item) => item.code)}
            onReview={reviewFromAlert}
          />
          <PortfolioSnapshotPanel />
          <PortfolioEditor
            assets={model.assets}
            onSaved={() => setPortfolioVersion((n) => n + 1)}
          />
          <form
            id="review-form"
            onSubmit={(event) => void analyze(event)}
            className="space-y-5"
          >
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
                    onChange={(event) => selectStock(event.target.value)}
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
                {asset && <AssetSummary asset={asset} />}
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
                <DraftNotice message={draftMessage} draft={draft} />
                <AutoFundamentals
                  loading={fundamentalsLoading}
                  message={fundamentalsMessage}
                  fundamentals={fundamentals}
                  onAppendSummary={appendEvidenceSummary}
                />
                <p className="mt-4 mb-5 text-xs leading-6 text-[var(--text-muted)]">
                  自动取数只作参考，不会改变任何决策门。以下结论仍是你的手工复核声明，报告会保存来源、日期和状态；留空时如实标记待复核。
                </p>
                <EvidenceFields review={review} set={set} asset={asset} />
              </section>
              <RiskBudgetSection review={review} set={set} asset={asset} />
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
            。原文件保持不变。没有自动下单；价格提醒只提示复核，每次复核以最新证据和组合数据为准。
          </footer>
        </>
      )}
    </div>
  );
}
