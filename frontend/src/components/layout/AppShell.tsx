import { Sidebar } from "@/components/layout/Sidebar";

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen lg:flex">
      <a
        href="#main-content"
        className="sr-only fixed top-3 left-3 z-50 rounded-lg bg-white p-3 focus:not-sr-only"
      >
        跳转到主内容
      </a>
      <Sidebar />
      <main id="main-content" className="min-w-0 flex-1 lg:ml-60">
        <div className="mx-auto max-w-[1600px]">{children}</div>
      </main>
    </div>
  );
}
