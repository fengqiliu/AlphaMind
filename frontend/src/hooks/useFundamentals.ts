import { useEffect, useState } from "react";
import { isAxiosError } from "axios";
import api from "@/api/client";
import type { Fundamentals } from "@/types/investment";

const FALLBACK = "财报/估值数据暂不可用，请手工填写证据";

/**
 * 读取个人模型标的的自动财报/估值快照。结果连同所属代码一起保存，
 * 切换标的后旧结果自动失效，避免在 effect 内同步重置状态。
 */
export function useFundamentals(assetCode: string | undefined) {
  const [result, setResult] = useState<{
    code: string;
    data: Fundamentals | null;
    message: string | null;
  } | null>(null);

  useEffect(() => {
    if (!assetCode) return;
    let cancelled = false;
    api
      .get(`/investment-model/fundamentals/${encodeURIComponent(assetCode)}`)
      .then(({ data }) => {
        if (cancelled) return;
        const ok = data.code === 200 && data.data?.stockCode === assetCode;
        setResult({
          code: assetCode,
          data: ok ? data.data : null,
          message: ok ? null : data.message || FALLBACK,
        });
      })
      .catch((caught) => {
        if (cancelled) return;
        setResult({
          code: assetCode,
          data: null,
          message:
            (isAxiosError(caught) && caught.response?.data?.message) || FALLBACK,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [assetCode]);

  const current = result?.code === assetCode ? result : null;
  return {
    fundamentals: current?.data ?? null,
    message: current?.message ?? null,
    loading: Boolean(assetCode) && !current,
  };
}
