import Link from "next/link";
import type { PersonalReviewDraft } from "@/types/investment";

/** 载入上次复核的结果提示：失败信息或已载入草稿的说明。 */
export function DraftNotice({
  message,
  draft,
}: {
  message: string | null;
  draft: PersonalReviewDraft | null;
}) {
  return (
    <>
      {message && (
        <p
          role="status"
          className="mt-4 rounded-xl bg-[var(--neutral-glow)] p-3 text-xs leading-6 text-[var(--text-secondary)]"
        >
          {message}
        </p>
      )}
      {draft && (
        <div
          role="status"
          className="mt-4 rounded-xl bg-[var(--accent-subtle)] p-4 text-xs leading-6 text-[var(--text-secondary)]"
        >
          <p className="font-medium">已载入 v{draft.modelVersion} 历史复核草稿</p>
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
    </>
  );
}
