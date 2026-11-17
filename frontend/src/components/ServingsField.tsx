import Icon from "./Icon";

/** Recette : nombre de personnes prévu (0 = non précisé, les courses comptent alors 4). */
export default function ServingsField({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  const step = (d: number) => onChange(Math.min(50, Math.max(0, (value || 4) + d)));
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="flex flex-col">
        <span className="text-[13px] font-bold text-sec">Pour combien de personnes</span>
        <span className="text-xs text-muted">Les courses recalculent les quantités selon les parts.</span>
      </span>
      <div className="flex items-center gap-1 rounded-full bg-card p-1">
        <button type="button" onClick={() => step(-1)} disabled={value === 1} aria-label="Une personne de moins" className="flex h-9 w-9 items-center justify-center rounded-full bg-sable disabled:opacity-40">
          <Icon name="minus" size={15} strokeWidth={2.6} />
        </button>
        <span className="min-w-14 text-center text-sm font-bold" aria-live="polite">{value ? `${value} pers.` : "—"}</span>
        <button type="button" onClick={() => step(1)} disabled={value >= 50} aria-label="Une personne de plus" className="flex h-9 w-9 items-center justify-center rounded-full bg-sable disabled:opacity-40">
          <Icon name="plus" size={15} strokeWidth={2.6} />
        </button>
      </div>
    </div>
  );
}
