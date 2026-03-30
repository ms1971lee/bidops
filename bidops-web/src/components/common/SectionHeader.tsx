"use client";

interface SectionHeaderProps {
  title: string;
  description?: string;
  action?: React.ReactNode;
}

export default function SectionHeader({ title, description, action }: SectionHeaderProps) {
  return (
    <div className="flex items-center justify-between mb-3">
      <div>
        <h3 className="text-xs font-semibold text-gray-600">{title}</h3>
        {description && <p className="text-[10px] text-gray-400 mt-0.5">{description}</p>}
      </div>
      {action && <div>{action}</div>}
    </div>
  );
}
