"use client";

const VARIANTS = {
  primary: "bg-indigo-600 text-white hover:bg-indigo-700 shadow-sm",
  secondary: "bg-white border border-gray-200 text-gray-600 hover:border-indigo-200 hover:text-indigo-600",
  subtle: "bg-gray-50 text-gray-500 hover:bg-gray-100 hover:text-gray-700",
  danger: "bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-100",
};

const SIZES = {
  sm: "px-3 py-1 text-[11px]",
  md: "px-4 py-1.5 text-sm",
  lg: "px-5 py-2 text-sm",
};

interface AppButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: keyof typeof VARIANTS;
  size?: keyof typeof SIZES;
}

export default function AppButton({
  variant = "primary",
  size = "md",
  className = "",
  children,
  ...props
}: AppButtonProps) {
  return (
    <button
      className={`rounded-lg font-medium transition-colors disabled:opacity-50 ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
