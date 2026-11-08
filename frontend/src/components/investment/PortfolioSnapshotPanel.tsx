"use client";

import { useEffect, useState } from "react";
import { LineChart } from "lucide-react";
import { getPortfolioSnapshots } from "@/api/portfolio";
import type { PortfolioSnapshotPoint } from "@/types/portfolio";

const DAY_MS = 86_400_000;
/** 两个相邻快照相隔超过这么多天（周末 3 天已计入）就视为中间缺记录，用虚线连接。 */
const GAP_DAYS = 4;
const WIDTH = 640;
const PAD_X = 8;

function day(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / DAY_MS;
}

/** 把点映射成 SVG 坐标；x 按真实日期铺开，所以缺失的日子会留出空隙。 */
function layout(points: PortfolioSnapshotPoint[], height: number, pick: (p: PortfolioSnapshotPoint) => number, lo: number, hi: number) {
  const first = day(points[0].date);
  const span = Math.max(day(points[points.length - 1].date) - first, 1);
  const range = hi - lo || 1;
  return points.map((p) => ({
    x: PAD_X + ((day(p.date) - first) / span) * (WIDTH - PAD_X * 2),
    y: 6 + (1 - (pick(p) - lo) / range) * (height - 12),
  }));
}

/** 相邻点之间的线段；间隔过大的线段标记为虚线。 */
function Segments({ points, coords, stroke }: { points: PortfolioSnapshotPoint[]; coords: { x: number; y: number }[]; stroke: string }) {
  return (
    <>
      {coords.slice(1).map((c, i) => {
        const missing = day(points[i + 1].date) - day(points[i].date) > GAP_DAYS;
        return (
          <line
            key={points[i + 1].date}
            x1={coords[i].x}
            y1={coords[i].y}
            x2={c.x}
            y2={c.y}
            stroke={stroke}
            strokeWidth={1.5}
            strokeDasharray={missing ? "3 4" : undefined}
            opacity={missing ? 0.5 : 1}
          />
        );
      })}
      {coords.map((c, i) => (
        <circle key={points[i].date} cx={c.x} cy={c.y} r={2} fill={stroke} />
      ))}
    </>
  );
}

/** 组合净值与回撤曲线：数据来自收盘后的每日快照，缺行情的日子不补数。 */
export function PortfolioSnapshotPanel() {
  const [state, setState] = useState<{ points?: PortfolioSnapshotPoint[]; error?: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    getPortfolioSnapshots()
      .then((points) => {
        if (!cancelled) setState({ points });
      })
      .catch((failure) => {
        if (!cancelled) setState({ error: failure instanceof Error ? failure.message : "净值快照加载失败" });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const points = state?.points;
  const latest = points && points.length > 0 ? points[points.length - 1] : null;
  const worst = points && points.length > 0 ? Math.max(...points.map((p) => p.drawdownPct)) : 0;
  const values = points?.map((p) => p.totalValue) ?? [];
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const valueCoords = points && points.length > 1 ? layout(points, 120, (p) => p.totalValue, lo, hi) : [];
  const drawdownCoords = points && points.length > 1 ? layout(points, 56, (p) => p.drawdownPct, 0, Math.max(worst, 1)) : [];

  return (
    <section className="glass-card p-5 sm:p-6" aria-labelledby="portfolio-snapshot-title">
      <h2 id="portfolio-snapshot-title" className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <LineChart className="size-4 text-[var(--accent)]" />
        组合净值与回撤
      </h2>
      <p className="text-xs leading-6 text-[var(--text-muted)]">
        每个交易日收盘后（15:30）按当期行情记录一次组合总资产。缺任何一只持仓的当期行情时当天不记录，也不用过期缓存补数，曲线上以虚线跨过缺口。记录的是总资产而不是收益率：期间追加或取出资金会直接改变曲线，回撤只作参考。
      </p>
      {state === null ? (
        <p role="status" className="mt-3 text-xs text-[var(--text-muted)]">正在加载净值快照...</p>
      ) : state.error ? (
        <p role="alert" className="mt-3 rounded-xl bg-[var(--bearish-glow)] p-3 text-xs text-[var(--bearish)]">{state.error}</p>
      ) : !points || points.length === 0 ? (
        <p className="mt-3 text-xs text-[var(--text-muted)]">
          还没有快照。录入持仓后，下一个交易日收盘后会自动记录第一条。
        </p>
      ) : (
        <div className="mt-3 space-y-3">
          <dl className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
            <div>
              <dt className="text-[var(--text-muted)]">最新总资产</dt>
              <dd className="mt-1 font-mono text-sm">{latest!.totalValue.toLocaleString("zh-CN")} 元</dd>
            </div>
            <div>
              <dt className="text-[var(--text-muted)]">当前回撤</dt>
              <dd className="mt-1 font-mono text-sm">{latest!.drawdownPct.toFixed(2)}%</dd>
            </div>
            <div>
              <dt className="text-[var(--text-muted)]">区间最大回撤</dt>
              <dd className="mt-1 font-mono text-sm">{worst.toFixed(2)}%</dd>
            </div>
            <div>
              <dt className="text-[var(--text-muted)]">已记录</dt>
              <dd className="mt-1 font-mono text-sm">{points.length} 天</dd>
            </div>
          </dl>
          {points.length < 2 ? (
            <p className="text-xs text-[var(--text-muted)]">至少积累两天快照后才会画出曲线。</p>
          ) : (
            <figure className="space-y-1">
              <svg viewBox={`0 0 ${WIDTH} 120`} className="w-full" role="img" aria-label="组合总资产曲线">
                <Segments points={points} coords={valueCoords} stroke="var(--accent)" />
              </svg>
              <svg viewBox={`0 0 ${WIDTH} 56`} className="w-full" role="img" aria-label="组合回撤曲线（越低回撤越大）">
                <Segments points={points} coords={drawdownCoords} stroke="var(--bearish)" />
              </svg>
              <figcaption className="flex justify-between text-[10px] text-[var(--text-muted)]">
                <span>{points[0].date}</span>
                <span>上：总资产 {lo.toLocaleString("zh-CN")} – {hi.toLocaleString("zh-CN")} 元；下：回撤（越靠下越深）</span>
                <span>{points[points.length - 1].date}</span>
              </figcaption>
            </figure>
          )}
        </div>
      )}
    </section>
  );
}
