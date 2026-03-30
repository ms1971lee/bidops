"use client";

const PADDING = {
  sm: "p-3",
  md: "p-4",
  lg: "p-5",
};

interface AppCardProps {
  children: React.ReactNode;
  padding?: "sm" | "md" | "lg";
  className?: string;
  onClick?: () => void;
}

export default function AppCard({ children, padding = "md", className = "", onClick }: AppCardProps) {
  const base = `bg-white rounded-xl border border-gray-100 shadow-sm ${PADDING[padding]}`;
  const interactive = onClick ? "cursor-pointer hover:shadow-md hover:border-indigo-200 transition-all" : "";
  return (
    <div className={`${base} ${interactive} ${className}`} onClick={onClick}>
      {children}
    </div>
  );
}
