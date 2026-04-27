"use client";

import { useEffect, useRef } from "react";
import * as echarts from "echarts";
import type { KLineData } from "@/types";
import { cn } from "@/utils";

interface KLineChartProps {
  data: KLineData;
  className?: string;
}

export function KLineChart({ data, className }: KLineChartProps) {
  const chartRef = useRef<HTMLDivElement>(null);
  const chartInstance = useRef<echarts.ECharts | undefined>(undefined);

  useEffect(() => {
    if (!chartRef.current) return;

    chartInstance.current = echarts.init(chartRef.current);
    const chart = chartInstance.current;

    // 主题颜色
    const bullish = "#00ff88";
    const bearish = "#ff3366";
    const accent = "#00f0ff";
    const textColor = "#a1a1aa";
    const bgColor = "#12141a";

    const option: echarts.EChartsOption = {
      backgroundColor: "transparent",
      tooltip: {
        trigger: "axis",
        axisPointer: {
          type: "cross",
          crossStyle: { color: accent },
          lineStyle: { color: accent, opacity: 0.3 },
        },
        formatter: (params: unknown) => {
          const p = (
            params as Array<{
              data: [number, number, number, number];
              axisValue: string;
            }>
          )[0];
          const [open, close, low, high] = p.data;
          const isUp = close >= open;
          const color = isUp ? bullish : bearish;
          return `
            <div style="font-family: 'JetBrains Mono', monospace; font-size: 12px; background: ${bgColor}; border: 1px solid ${accent}40; border-radius: 8px; padding: 12px;">
              <div style="font-weight: 600; margin-bottom: 8px; color: ${textColor};">${p.axisValue}</div>
              <div style="display: grid; grid-template-columns: auto auto; gap: 4px 12px;">
                <span style="color: #52525b;">开盘</span><span style="color:${color}; font-weight: 500;">${open.toFixed(2)}</span>
                <span style="color: #52525b;">收盘</span><span style="color:${color}; font-weight: 500;">${close.toFixed(2)}</span>
                <span style="color: #52525b;">最低</span><span style="color:${bullish};">${low.toFixed(2)}</span>
                <span style="color: #52525b;">最高</span><span style="color:${bearish};">${high.toFixed(2)}</span>
              </div>
            </div>
          `;
        },
      },
      legend: {
        data: ["MA5", "MA10", "MA20", "MA60"],
        top: 5,
        right: 10,
        textStyle: {
          fontSize: 10,
          color: textColor,
          fontFamily: "'JetBrains Mono', monospace",
        },
        itemGap: 16,
      },
      grid: [
        { left: "8%", right: "5%", top: 45, height: "55%" },
        { left: "8%", right: "5%", top: "75%", height: "15%" },
      ],
      xAxis: [
        {
          type: "category",
          data: data.dates,
          gridIndex: 0,
          axisLabel: { show: false },
          axisLine: { lineStyle: { color: "#ffffff10" } },
        },
        {
          type: "category",
          data: data.dates,
          gridIndex: 1,
          axisLabel: {
            fontSize: 10,
            color: textColor,
            fontFamily: "'JetBrains Mono', monospace",
          },
          axisLine: { lineStyle: { color: "#ffffff10" } },
          splitLine: { show: false },
        },
      ],
      yAxis: [
        {
          scale: true,
          gridIndex: 0,
          axisLabel: {
            fontSize: 10,
            color: textColor,
            fontFamily: "'JetBrains Mono', monospace",
            formatter: (value: number) => value.toFixed(0),
          },
          axisLine: { show: false },
          splitLine: { lineStyle: { color: "#ffffff08" } },
        },
        {
          scale: true,
          gridIndex: 1,
          axisLabel: {
            fontSize: 10,
            color: textColor,
            fontFamily: "'JetBrains Mono', monospace",
            formatter: (value: number) => (value / 1000).toFixed(0) + "k",
          },
          axisLine: { show: false },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: "K线",
          type: "candlestick",
          data: data.klines,
          xAxisIndex: 0,
          yAxisIndex: 0,
          itemStyle: {
            color: bullish,
            color0: bearish,
            borderColor: bullish,
            borderColor0: bearish,
          },
        },
        {
          name: "MA5",
          type: "line",
          data: data.ma5,
          smooth: true,
          xAxisIndex: 0,
          yAxisIndex: 0,
          lineStyle: { width: 1.5, color: "#ffaa00" },
          symbol: "none",
        },
        {
          name: "MA10",
          type: "line",
          data: data.ma10,
          smooth: true,
          xAxisIndex: 0,
          yAxisIndex: 0,
          lineStyle: { width: 1.5, color: accent },
          symbol: "none",
        },
        {
          name: "MA20",
          type: "line",
          data: data.ma20,
          smooth: true,
          xAxisIndex: 0,
          yAxisIndex: 0,
          lineStyle: { width: 1.5, color: "#8b5cf6" },
          symbol: "none",
        },
        {
          name: "MA60",
          type: "line",
          data: data.ma60,
          smooth: true,
          xAxisIndex: 0,
          yAxisIndex: 0,
          lineStyle: { width: 1.5, color: "#ec4899" },
          symbol: "none",
        },
        {
          name: "成交量",
          type: "bar",
          data: data.volumes,
          xAxisIndex: 1,
          yAxisIndex: 1,
          itemStyle: {
            color: (params: unknown) => {
              const p = params as { dataIndex: number };
              return data.klines[p.dataIndex][1] >= data.klines[p.dataIndex][0]
                ? bullish + "60"
                : bearish + "60";
            },
            borderRadius: [2, 2, 0, 0],
          },
        },
      ],
      dataZoom: [
        { type: "inside", xAxisIndex: 0, start: 70, end: 100 },
        { type: "inside", xAxisIndex: 1, start: 70, end: 100 },
      ],
    };

    chart.setOption(option);

    const handleResize = () => chart.resize();
    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
      chart.dispose();
    };
  }, [data]);

  return (
    <div
      ref={chartRef}
      className={cn("w-full h-full min-h-[400px]", className)}
    />
  );
}
