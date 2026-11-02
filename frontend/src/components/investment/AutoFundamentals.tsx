import { Loader2 } from "lucide-react";
import type { Fundamentals } from "@/types/investment";
import { Button } from "@/components/common/Button";
import { FundamentalsCard } from "@/components/analysis/FundamentalsCard";

/** 自动财报/估值参考：加载中、不可用提示，或数据卡片与“填入证据来源”按钮。 */
export function AutoFundamentals({
  loading,
  message,
  fundamentals,
  onAppendSummary,
}: {
  loading: boolean;
  message: string | null;
  fundamentals: Fundamentals | null;
  onAppendSummary: () => void;
}) {
  return (
    <div className="mt-4">
      {loading && (
        <p className="flex items-center gap-2 text-xs text-[var(--text-muted)]">
          <Loader2 className="size-3.5 animate-spin" />
          正在读取财报与估值数据…
        </p>
      )}
      {message && (
        <p
          role="status"
          className="rounded-xl bg-[var(--neutral-glow)] p-3 text-xs leading-6 text-[var(--text-secondary)]"
        >
          {message}
        </p>
      )}
      {fundamentals && (
        <>
          <FundamentalsCard data={fundamentals} />
          <Button type="button" variant="outline" size="sm" className="mt-3" onClick={onAppendSummary}>
            将取数摘要填入证据来源
          </Button>
        </>
      )}
    </div>
  );
}
