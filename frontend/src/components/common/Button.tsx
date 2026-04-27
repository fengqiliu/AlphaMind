"use client";

import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-medium transition-all duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]/50 disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        default:
          "bg-gradient-to-r from-[var(--accent)] to-[#00ff88] text-[var(--bg-primary)] font-semibold hover:shadow-lg hover:shadow-[var(--accent-glow)]",
        destructive:
          "bg-gradient-to-r from-[var(--bearish)] to-[#ff6699] text-white font-semibold hover:shadow-lg hover:shadow-[var(--bearish-glow)]",
        outline:
          "border border-[var(--border)] bg-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)] hover:border-[var(--accent)]/30",
        secondary:
          "bg-[var(--bg-tertiary)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]",
        ghost:
          "bg-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]",
        link: "text-[var(--accent)] underline-offset-4 hover:underline",
        success:
          "bg-gradient-to-r from-[var(--bullish)] to-[#66ffaa] text-[var(--bg-primary)] font-semibold hover:shadow-lg hover:shadow-[var(--bullish-glow)]",
        warning:
          "bg-gradient-to-r from-[var(--neutral)] to-[#ffcc00] text-[var(--bg-primary)] font-semibold hover:shadow-lg hover:shadow-[var(--neutral-glow)]",
      },
      size: {
        default: "h-10 px-4 py-2",
        sm: "h-8 px-3 text-xs",
        lg: "h-12 px-6 text-base",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends
    ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => {
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  },
);

Button.displayName = "Button";
