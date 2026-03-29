"use client";

export interface Column<T = any> {
  key: string;
  label: string;
  width?: string;
  align?: "left" | "center" | "right";
  render?: (row: T) => React.ReactNode;
}

interface Props<T = any> {
  columns: Column<T>[];
  data: T[];
  loading?: boolean;
  emptyMessage?: string;
  rowKey?: (row: T) => string;
  onRowClick?: (row: T) => void;
  activeRowKey?: string;
  rowClassName?: (row: T) => string;
}

export default function DataTable<T extends Record<string, any>>({
  columns, data, loading, emptyMessage = "데이터가 없습니다",
  rowKey, onRowClick, activeRowKey, rowClassName,
}: Props<T>) {
  return (
    <div className="bg-white rounded border overflow-hidden">
      <table className="w-full text-sm" role="grid">
        <thead className="bg-gray-50 sticky top-0">
          <tr>
            {columns.map((col) => (
              <th key={col.key}
                className={`text-${col.align || "left"} px-4 py-2 text-xs font-medium text-gray-600 ${col.width || ""}`}
                scope="col">
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading && (
            <tr><td colSpan={columns.length} className="px-4 py-8 text-center text-gray-400">
              로딩 중...
            </td></tr>
          )}
          {!loading && data.length === 0 && (
            <tr><td colSpan={columns.length} className="px-4 py-8 text-center text-gray-400">
              {emptyMessage}
            </td></tr>
          )}
          {!loading && data.map((row, i) => {
            const key = rowKey ? rowKey(row) : row.id || String(i);
            const isActive = activeRowKey === key;
            const extraCls = rowClassName ? rowClassName(row) : "";
            return (
              <tr key={key}
                className={`border-t transition-colors ${
                  onRowClick ? "cursor-pointer hover:bg-blue-50" : ""
                } ${isActive ? "bg-blue-50" : ""} ${extraCls}`}
                onClick={() => onRowClick?.(row)}
                tabIndex={onRowClick ? 0 : undefined}
                onKeyDown={(e) => { if (e.key === "Enter" && onRowClick) onRowClick(row); }}
                role={onRowClick ? "button" : undefined}>
                {columns.map((col) => (
                  <td key={col.key} className={`px-4 py-2 text-${col.align || "left"}`}>
                    {col.render ? col.render(row) : row[col.key]}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
