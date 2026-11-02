/**
 * ECharts 按需引入：只注册 K 线图实际用到的系列与组件，避免把整个 echarts 打进包里。
 * 新增图表类型或组件时，在这里补充注册，并同步扩展 ChartOption 类型。
 */
import * as echarts from "echarts/core";
import { BarChart, CandlestickChart, LineChart } from "echarts/charts";
import type { BarSeriesOption, CandlestickSeriesOption, LineSeriesOption } from "echarts/charts";
import {
  DataZoomInsideComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import type {
  DataZoomComponentOption,
  GridComponentOption,
  LegendComponentOption,
  TooltipComponentOption,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([
  CandlestickChart,
  LineChart,
  BarChart,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DataZoomInsideComponent,
  CanvasRenderer,
]);

export type ChartOption = echarts.ComposeOption<
  | CandlestickSeriesOption
  | LineSeriesOption
  | BarSeriesOption
  | TooltipComponentOption
  | LegendComponentOption
  | GridComponentOption
  | DataZoomComponentOption
>;

export { echarts };
