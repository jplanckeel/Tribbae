import { useMemo, useState, type ReactNode } from "react";
import { folderColor } from "../lib/categories";
import { plural } from "../lib/format";
import { canEditFolder } from "../lib/links";
import type { Folder, Link } from "../lib/types";
import { FolderGlyph } from "./FolderCover";
import Icon from "./Icon";
import Sheet from "./Sheet";

// Sélecteur de dossier des parcours « Ajouter une idée » et « Idées avec l'IA ».

/** « Choisir un dossier » : recherche, création à la volée, dossiers par groupe. */
export default function FolderPicker({ folders, links, current, userId, peers, onClose, onPick, onCreate }: {
  folders: Folder[];
  links: Link[];
  current?: string;
  userId?: string;
  peers: ReadonlySet<string>;
  onClose: () => void;
  onPick: (id: string) => void;
  onCreate: (name: string) => Promise<void>;
}) {
  const [q, setQ] = useState("");
  const [busy, setBusy] = useState(false);
  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const l of links) if (l.folderId) m.set(l.folderId, (m.get(l.folderId) ?? 0) + 1);
    return m;
  }, [links]);
  const norm = (t: string) => t.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
  const query = q.trim();
  const match = folders.filter((f) => !query || norm(f.name).includes(norm(query)));
  const groupOf = (f: Folder) =>
    f.visibility === "VISIBILITY_PUBLIC" ? "public" : f.collaborators?.length || (f.ownerId !== userId && !peers.has(f.ownerId)) ? "shared" : "private";
  const groups = ([["shared", "Partagés"], ["private", "Privés"], ["public", "Publics"]] as const)
    .map(([k, title]) => ({ title, items: match.filter((f) => groupOf(f) === k) }))
    .filter((g) => g.items.length);
  const canCreate = query.length > 1 && !folders.some((f) => norm(f.name) === norm(query));

  return (
    <Sheet open onClose={onClose} title="Choisir un dossier" tall>
      <div className="flex flex-col gap-3.5">
        <label className="flex h-[50px] items-center gap-2.5 rounded-2xl border-2 border-ecorce bg-card px-3.5 text-muted">
          <Icon name="search" size={20} />
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Rechercher ou créer un dossier"
            aria-label="Rechercher un dossier"
            className="min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ecorce outline-none placeholder:text-muted"
          />
        </label>
        {canCreate && (
          <button
            type="button"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try { await onCreate(query); } finally { setBusy(false); }
            }}
            className="add-up flex items-center gap-3 rounded-2xl border-2 border-dashed border-terracotta bg-blush p-3 text-left text-[15px] font-bold text-terracotta-dark"
          >
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-terracotta text-white"><Icon name="plus" size={18} strokeWidth={2.6} /></span>
            Créer « {query} »
          </button>
        )}
        {!query && (
          <PickRow label="Sans dossier" sub="Dans « Non rangées »" on={current === ""} onClick={() => onPick("")} icon={<Icon name="bookmark" size={17} />} bg="var(--color-sable-2)" ink="var(--color-sec)" />
        )}
        {groups.map((g) => (
          <div key={g.title} className="flex flex-col gap-1.5">
            <span className="text-xs font-extrabold tracking-[0.8px] text-sec uppercase">{g.title} · {g.items.length}</span>
            <div className="overflow-hidden rounded-[18px] bg-card">
              {g.items.map((f) => {
                const ro = !canEditFolder(f, userId, peers);
                const c = folderColor(f.color);
                const sub = ro
                  ? `${f.ownerDisplayName ? `par ${f.ownerDisplayName} · ` : ""}lecture seule`
                  : f.collaborators?.length
                    ? `avec ${f.collaborators[0].displayName || f.collaborators[0].email}${f.collaborators.length > 1 ? ` +${f.collaborators.length - 1}` : ""}`
                    : plural(counts.get(f.id) ?? f.linkCount ?? 0, "idée", "idées");
                return (
                  <PickRow key={f.id} label={f.name} sub={sub} on={current === f.id} disabled={ro} onClick={() => onPick(f.id)} icon={<FolderGlyph icon={f.icon} size={17} />} bg={c.bg} ink={c.solid} />
                );
              })}
            </div>
          </div>
        ))}
        {query && !groups.length && !canCreate && (
          <span className="py-2.5 text-center text-sm text-sec">Aucun dossier ne s'appelle comme ça… pour l'instant !</span>
        )}
      </div>
    </Sheet>
  );
}

function PickRow({ label, sub, on, disabled, onClick, icon, bg, ink }: {
  label: string; sub: string; on: boolean; disabled?: boolean; onClick: () => void; icon: ReactNode; bg: string; ink: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={on}
      disabled={disabled}
      onClick={onClick}
      className={`flex w-full items-center gap-3 border-b border-sable-2 px-3 py-2.5 text-left transition-colors last:border-b-0 disabled:opacity-45 ${on ? "bg-peach" : "bg-card hover:bg-sable"}`}
    >
      <span className="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-[11px]" style={{ background: bg, color: ink }}>{icon}</span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-sm font-bold">{label}</span>
        <span className="truncate text-xs text-sec">{sub}</span>
      </span>
      {on && (
        <span className="add-pop flex h-[22px] w-[22px] items-center justify-center rounded-full bg-ecorce text-sable"><Icon name="check" size={12} strokeWidth={3.2} /></span>
      )}
    </button>
  );
}
