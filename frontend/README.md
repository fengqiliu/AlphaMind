# AlphaMind Frontend（Phase 6 脚手架）

基于 Next.js 16 + React 19 + TypeScript + Zustand + ECharts 的前端应用。

本阶段目标是提供可扩展、可维护的前端工程骨架：

- 统一应用壳层（`RootLayout` + `AppShell` + `Sidebar`）
- 页面路由入口（`/`、`/chat`、`/history`、`/watchlist`）
- API 访问层与类型定义（`src/api` + `src/types`）
- 状态管理基线（`src/stores`）
- 统一导航配置（`src/config/navigation.ts`）

## 本地开发

```bash
npm install
npm run dev
```

打开 `http://localhost:3000`。

## 构建与检查

```bash
npm run lint
npm run build
```

## 目录结构（脚手架层）

```text
src/
├── app/                    # App Router 页面入口
├── api/                    # REST API 客户端与请求封装
├── components/
│   ├── layout/             # 应用壳层（Sidebar / AppShell）
│   ├── common/             # 通用组件
│   ├── chart/              # 图表组件
│   ├── analysis/           # 分析结果相关组件
│   └── agent/              # Agent 会话相关组件
├── config/                 # 全局配置（导航、常量等）
├── hooks/                  # 自定义 hooks
├── stores/                 # Zustand 状态管理
├── types/                  # TS 类型定义
└── utils/                  # 工具函数
```

## 扩展约定

1. 新页面优先放在 `src/app/<route>/page.tsx`。
2. 新导航项统一在 `src/config/navigation.ts` 维护。
3. 新业务 API 先补 `src/types` 类型，再补 `src/api` 调用。
4. 全局状态优先分域建 store，避免单一超大 store。

