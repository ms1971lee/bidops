"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { checklistApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

export default function ChecklistsPage() {
  const { id } = useParams() as { id: string };
  const [checklists, setChecklists] = useState<any[]>([]);
  const [selectedCl, setSelectedCl] = useState<string | null>(null);
  const [items, setItems] = useState<any[]>([]);
  const [newTitle, setNewTitle] = useState("");
  const [newItem, setNewItem] = useState("");

  const loadLists = () => checklistApi.list(id).then(setChecklists).catch(() => {});
  useEffect(() => { loadLists(); }, [id]);

  const loadItems = (clId: string) => {
    setSelectedCl(clId);
    checklistApi.items(id, clId).then(setItems).catch(() => {});
  };

  const handleCreateList = async () => {
    if (!newTitle) return;
    await checklistApi.create(id, { checklist_type: "SUBMISSION", title: newTitle });
    setNewTitle("");
    loadLists();
  };

  const handleAddItem = async () => {
    if (!selectedCl || !newItem) return;
    await checklistApi.createItem(id, selectedCl, { item_text: newItem, mandatory_flag: true, risk_level: "MEDIUM" });
    setNewItem("");
    loadItems(selectedCl);
    loadLists();
  };

  const handleToggle = async (itemId: string, currentStatus: string) => {
    if (!selectedCl) return;
    const next = currentStatus === "DONE" ? "TODO" : "DONE";
    await checklistApi.changeItemStatus(id, selectedCl, itemId, { status: next });
    loadItems(selectedCl);
    loadLists();
  };

  return (
    <div className="flex gap-4">
      {/* 체크리스트 목록 */}
      <div className="w-72">
        <div className="flex gap-2 mb-3">
          <input className="border px-2 py-1 rounded text-sm flex-1" placeholder="체크리스트 제목"
            value={newTitle} onChange={(e) => setNewTitle(e.target.value)} />
          <button onClick={handleCreateList} className="px-3 py-1 bg-blue-600 text-white rounded text-sm">추가</button>
        </div>

        <div className="space-y-2">
          {checklists.map((c) => (
            <div key={c.id}
              className={`bg-white border rounded p-3 cursor-pointer text-sm ${selectedCl === c.id ? "border-blue-500" : "hover:bg-gray-50"}`}
              onClick={() => loadItems(c.id)}>
              <div className="font-medium">{c.title}</div>
              <div className="text-xs text-gray-500 mt-1">
                <StatusBadge value={c.checklist_type} />
                <span className="ml-2">{c.done_count}/{c.total_count} 완료</span>
              </div>
              {c.total_count > 0 && (
                <div className="w-full bg-gray-200 rounded h-1.5 mt-2">
                  <div className="bg-green-500 h-1.5 rounded" style={{ width: `${(c.done_count / c.total_count) * 100}%` }} />
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* 항목 목록 */}
      <div className="flex-1">
        {selectedCl ? (
          <>
            <div className="flex gap-2 mb-3">
              <input className="border px-2 py-1 rounded text-sm flex-1" placeholder="새 항목 추가..."
                value={newItem} onChange={(e) => setNewItem(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleAddItem()} />
              <button onClick={handleAddItem} className="px-3 py-1 bg-blue-600 text-white rounded text-sm">추가</button>
            </div>

            <div className="space-y-1">
              {items.map((i) => (
                <div key={i.id} className="flex items-center gap-3 bg-white border rounded px-3 py-2 text-sm">
                  <input type="checkbox" checked={i.current_status === "DONE"}
                    onChange={() => handleToggle(i.id, i.current_status)}
                    className="w-4 h-4" />
                  <span className="font-mono text-xs text-gray-400">{i.item_code}</span>
                  <span className={i.current_status === "DONE" ? "line-through text-gray-400" : ""}>{i.item_text}</span>
                  <div className="ml-auto flex gap-1">
                    {i.mandatory_flag && <span className="text-xs text-red-600 font-medium">필수</span>}
                    <StatusBadge value={i.risk_level} />
                  </div>
                </div>
              ))}
              {items.length === 0 && <div className="text-center text-gray-400 py-8">항목이 없습니다</div>}
            </div>
          </>
        ) : (
          <div className="text-center text-gray-400 py-16">체크리스트를 선택하세요</div>
        )}
      </div>
    </div>
  );
}
