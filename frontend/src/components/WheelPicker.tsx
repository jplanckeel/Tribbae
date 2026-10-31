import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import {
  bounds,
  clamp,
  dayOptions,
  describe,
  HOURS,
  minuteOptions,
  monthOptions,
  MONTHS,
  partsOf,
  shortcuts,
  toMs,
  yearOptions,
  type DateKind,
  type DateParts,
} from "../lib/dateWheel";
import Icon, { type IconName } from "./Icon";
import Sheet from "./Sheet";
import { Button } from "./ui";

const ITEM = 44; // hauteur d'un cran (px)
const VISIBLE = 5; // crans visibles, celui du milieu est choisi

interface Option {
  value: number;
  label: string;
}

const reducedMotion = () => typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

/**
 * Une colonne qui défile au doigt, à la molette ou au clavier et s'arrête sur un cran
 * (scroll-snap). Lue comme un `spinbutton` : flèches, Page ↑/↓ (5 crans), Début/Fin.
 */
export function Wheel({ label, options, value, onChange, className = "" }: {
  label: string;
  options: Option[];
  value: number;
  onChange: (v: number) => void;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const settle = useRef<number | undefined>(undefined);
  const shown = useRef(false);
  const lastIndex = useRef(-1);
  const index = Math.max(0, options.findIndex((o) => o.value === value));

  // Aligne la colonne sur la valeur : à l'ouverture, après un raccourci, ou quand
  // la liste change (mois plus court, bornes)
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const top = index * ITEM;
    if (Math.abs(el.scrollTop - top) > 1) el.scrollTo({ top, behavior: shown.current && !reducedMotion() ? "smooth" : "auto" });
    shown.current = true;
    lastIndex.current = index;
  }, [index, options.length]);

  useEffect(() => () => window.clearTimeout(settle.current), []);

  const pick = (i: number) => {
    const o = options[Math.min(options.length - 1, Math.max(0, i))];
    if (o && o.value !== value) onChange(o.value);
  };

  const onScroll = () => {
    const el = ref.current;
    if (!el) return;
    const i = Math.round(el.scrollTop / ITEM);
    // Un léger retour à chaque cran franchi, là où le navigateur sait vibrer
    if (i !== lastIndex.current) {
      lastIndex.current = i;
      navigator.vibrate?.(4);
    }
    window.clearTimeout(settle.current);
    settle.current = window.setTimeout(() => pick(i), 110);
  };

  const onKeyDown = (e: KeyboardEvent) => {
    const moves: Record<string, number> = { ArrowUp: -1, ArrowDown: 1, PageUp: -5, PageDown: 5, Home: -options.length, End: options.length };
    if (!(e.key in moves)) return;
    e.preventDefault();
    pick(index + moves[e.key]);
  };

  const current = options[index];
  return (
    <div className={`relative ${className}`}>
      <div
        ref={ref}
        role="spinbutton"
        tabIndex={0}
        aria-label={label}
        aria-valuenow={current?.value}
        aria-valuetext={current?.label}
        aria-valuemin={options[0]?.value}
        aria-valuemax={options[options.length - 1]?.value}
        onScroll={onScroll}
        onKeyDown={onKeyDown}
        className="wheel no-scrollbar relative snap-y snap-mandatory overflow-y-scroll overscroll-contain rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
        style={{ height: ITEM * VISIBLE, paddingBlock: ITEM * Math.floor(VISIBLE / 2) }}
      >
        {options.map((o, i) => {
          const distance = Math.abs(i - index);
          return (
            <div
              key={o.value}
              onClick={() => pick(i)}
              aria-hidden="true"
              className={`flex snap-center items-center justify-center text-[17px] tabular-nums transition-[opacity,color] duration-150 select-none ${distance === 0 ? "font-bold text-ecorce" : "font-medium text-sec"}`}
              style={{ height: ITEM, opacity: distance === 0 ? 1 : distance === 1 ? 0.7 : 0.4 }}
            >
              {o.label}
            </div>
          );
        })}
      </div>
    </div>
  );
}

const pad = (n: number) => String(n).padStart(2, "0");

const defaultValue = (kind: DateKind, now: number): DateParts => {
  const n = partsOf(now);
  // Un enfant : quelques années en arrière, pour moins faire tourner les roulettes
  if (kind === "birth") return { year: n.year - 4, month: n.month, day: n.day, hour: 0, minute: 0 };
  return { year: n.year, month: n.month, day: n.day, hour: 0, minute: 0 };
};

/**
 * Feuille du sélecteur à roulettes (jour, mois, année, et heure pour une sortie).
 * À monter quand elle est ouverte. `value` 0 : rien de choisi encore.
 */
export function DateSheet({ title, kind, value, withTime = false, onConfirm, onClear, onClose }: {
  title: string;
  kind: DateKind;
  value: number;
  withTime?: boolean;
  onConfirm: (ms: number) => void;
  /** « Effacer la date » (absent quand la date est obligatoire). */
  onClear?: () => void;
  onClose: () => void;
}) {
  const [now] = useState(() => Date.now());
  const [{ min, max }] = useState(() => bounds(kind, now, value || undefined));
  const [parts, setParts] = useState<DateParts>(() => clamp(value ? partsOf(value) : defaultValue(kind, now), min, max));
  const set = (patch: Partial<DateParts>) => setParts((p) => clamp({ ...p, ...patch }, min, max));

  const days = dayOptions(parts.year, parts.month, min, max).map((d) => ({ value: d, label: String(d) }));
  const months = monthOptions(parts.year, min, max).map((m) => ({ value: m, label: MONTHS[m - 1] }));
  const years = yearOptions(min, max).map((y) => ({ value: y, label: String(y) }));
  const quick = shortcuts(kind, now);

  return (
    <Sheet
      open
      onClose={onClose}
      title={title}
      footer={
        <div className="flex gap-2 pb-4">
          {onClear && (
            <Button variant="secondary" className="flex-1" onClick={() => { onClear(); onClose(); }}>
              Effacer la date
            </Button>
          )}
          <Button className="flex-1" icon="check" onClick={() => { onConfirm(toMs(parts, kind)); onClose(); }}>
            Valider
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        {quick.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {quick.map((q) => {
              const on = q.date.year === parts.year && q.date.month === parts.month && q.date.day === parts.day;
              return (
                <button
                  key={q.label}
                  type="button"
                  aria-pressed={on}
                  onClick={() => set({ year: q.date.year, month: q.date.month, day: q.date.day })}
                  className={`h-10 rounded-full px-4 text-sm font-bold transition-colors ${on ? "bg-ecorce text-sable" : "bg-sable-2 text-ecorce hover:bg-line"}`}
                >
                  {q.label}
                </button>
              );
            })}
          </div>
        )}

        <p aria-live="polite" className="text-center font-display text-lg font-bold">{describe(parts, withTime)}</p>

        <div className="relative flex items-stretch gap-1">
          {/* Bande du cran choisi, derrière les colonnes */}
          <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-1/2 h-11 -translate-y-1/2 rounded-2xl bg-sable-2" />
          <Wheel label="Jour" options={days} value={parts.day} onChange={(day) => set({ day })} className="w-14" />
          <Wheel label="Mois" options={months} value={parts.month} onChange={(month) => set({ month })} className="min-w-0 flex-1" />
          <Wheel label="Année" options={years} value={parts.year} onChange={(year) => set({ year })} className="w-[72px]" />
          {withTime && (
            <>
              <Wheel label="Heure" options={HOURS.map((h) => ({ value: h, label: pad(h) }))} value={parts.hour} onChange={(hour) => set({ hour })} className="ml-2 w-12" />
              <span aria-hidden="true" className="relative self-center text-lg font-bold">:</span>
              <Wheel label="Minute" options={minuteOptions(parts.minute).map((m) => ({ value: m, label: pad(m) }))} value={parts.minute} onChange={(minute) => set({ minute })} className="w-12" />
            </>
          )}
        </div>
        {withTime && <p className="text-center text-xs text-muted">00:00 : sans heure précise</p>}
      </div>
    </Sheet>
  );
}

/** Champ qui ouvre le sélecteur : même allure qu'un `Field`. */
export function DateField({ label, icon = "calendar", text, placeholder, onOpen }: {
  label: string;
  icon?: IconName;
  text?: string;
  placeholder: string;
  onOpen: () => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-[13px] font-bold text-sec">{label}</span>
      <button
        type="button"
        onClick={onOpen}
        aria-label={`${label} : ${text ?? placeholder}`}
        className="flex h-[52px] items-center gap-2.5 rounded-medium border-[1.5px] border-line-2 bg-card px-4 text-left text-muted outline-none focus-visible:border-terracotta"
      >
        <Icon name={icon} size={19} />
        <span className={`min-w-0 flex-1 truncate text-[15px] font-medium ${text ? "text-ecorce" : "text-muted"}`}>{text ?? placeholder}</span>
        <Icon name="chevronDown" size={18} />
      </button>
    </div>
  );
}
