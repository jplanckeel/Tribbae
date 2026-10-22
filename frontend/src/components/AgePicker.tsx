import { useState } from "react";
import { AGE_BANDS, AGE_STOPS, OPEN_AGE, ageLabel, covers, formatAgeRange, hull, parseAgeRange, snapAge, type AgeRange } from "../lib/age";
import { ageInMonths } from "../lib/format";
import type { Child } from "../lib/types";
import Icon from "./Icon";
import { Chip } from "./ui";

const ALL: AgeRange = [0, OPEN_AGE];
const same = (a: AgeRange | null, b: AgeRange) => !!a && a[0] === b[0] && a[1] === b[1];
const BOUNDS = new Set([0, 3, 6, 10, OPEN_AGE]);

/**
 * Tranche d'âge d'une idée, sans rien taper : tranches en un toucher (même
 * découpage que les filtres), raccourcis vers l'âge des enfants de la tribu,
 * et réglage précis au besoin (par 6 mois jusqu'à 2 ans). Même comportement
 * que AgePicker de l'app Android.
 */
export default function AgePicker({ value, onChange, kids }: { value: string; onChange: (v: string) => void; kids: Child[] }) {
  const range = parseAgeRange(value);
  const all = same(range, ALL);
  const children = kids
    .map((k) => ({ id: k.id, name: k.name, age: snapAge(ageInMonths(k.birthDate) / 12) }))
    .filter((k) => k.name.trim());
  const set = (r: AgeRange | null) => onChange(r ? formatAgeRange(r) : "");
  // Réglage précis ouvert d'office si la tranche ne tombe pas sur les tranches proposées
  const [precise, setPrecise] = useState(
    () => !!range && !all && !(BOUNDS.has(range[0]) && BOUNDS.has(range[1])) && !children.some((k) => same(range, [k.age, k.age])),
  );

  // Toucher une tranche : l'ajouter (la tranche s'élargit), ou la retirer d'un bord
  const toggleBand = (b: AgeRange) => {
    if (!range || all) return set(b);
    if (!covers(range, b)) return set(hull(range, b));
    if (same(range, b)) return set(null);
    if (b[0] === range[0]) return set([b[1], range[1]]);
    if (b[1] === range[1]) return set([range[0], b[0]]);
    set(b);
  };
  const toggleAge = (a: number) => {
    const r: AgeRange = [a, a];
    if (!range || all) return set(r);
    if (!covers(range, r)) return set(hull(range, r));
    set(same(range, r) ? null : r);
  };

  const shown = range && !all ? range : ([3, 6] as AgeRange);
  const step = (end: 0 | 1, dir: 1 | -1) => {
    const stops = end === 0 ? AGE_STOPS.filter((s) => s < OPEN_AGE) : AGE_STOPS.filter((s) => s > 0);
    const i = stops.indexOf(shown[end]);
    const next = stops[Math.min(stops.length - 1, Math.max(0, (i < 0 ? 0 : i) + dir))];
    const r: AgeRange = end === 0 ? [next, Math.max(next, shown[1])] : [Math.min(shown[0], next), next];
    set(r);
  };

  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-baseline justify-between gap-2">
        <span id="age-label" className="text-[13px] font-bold text-sec">Âge</span>
        {value ? (
          <span className="flex items-center gap-2 text-[13px]">
            <span className="font-semibold text-ecorce">{range ? formatAgeRange(range) : value}</span>
            <button type="button" onClick={() => set(null)} className="font-semibold text-terracotta hover:text-terracotta-dark">Effacer</button>
          </span>
        ) : (
          <span className="text-[13px] text-muted">Non précisé</span>
        )}
      </div>
      {value && !range && <p className="text-xs text-sec">Saisie actuelle : « {value} ». Choisissez une tranche pour la remplacer.</p>}

      <div role="group" aria-labelledby="age-label" className="flex flex-wrap gap-2">
        <Chip active={all} onClick={() => set(all ? null : ALL)}>Tous âges</Chip>
        {AGE_BANDS.map((b) => (
          <Chip key={b.key} active={!!range && !all && covers(range, b.range)} onClick={() => toggleBand(b.range)}>
            {b.label}
          </Chip>
        ))}
      </div>

      {children.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-sec">Pour :</span>
          {children.map((k) => (
            <Chip
              key={k.id}
              active={!!range && !all && covers(range, [k.age, k.age])}
              onClick={() => toggleAge(k.age)}
              activeClass="bg-terracotta text-white"
              className="!h-[34px] !px-3 !text-[13px]"
            >
              {k.name} · {ageLabel(k.age)}
            </Chip>
          ))}
        </div>
      )}

      {precise ? (
        <div className="grid grid-cols-[auto_1fr] items-center gap-x-3 gap-y-2 rounded-medium bg-white px-3 py-2.5 text-sm">
          <span className="text-sec">De</span>
          <Stepper label="Âge minimum" text={shown[0] === 0 ? "Naissance" : ageLabel(shown[0])} onMinus={() => step(0, -1)} onPlus={() => step(0, 1)} />
          <span className="text-sec">à</span>
          <Stepper label="Âge maximum" text={shown[1] >= OPEN_AGE ? "et +" : ageLabel(shown[1])} onMinus={() => step(1, -1)} onPlus={() => step(1, 1)} />
        </div>
      ) : (
        <button type="button" onClick={() => setPrecise(true)} className="self-start text-[13px] font-semibold text-terracotta hover:text-terracotta-dark">
          Plus précis (mois, âges exacts)…
        </button>
      )}
    </div>
  );
}

function Stepper({ label, text, onMinus, onPlus }: { label: string; text: string; onMinus: () => void; onPlus: () => void }) {
  const btn = "flex h-9 w-9 items-center justify-center rounded-full bg-sable-2 text-ecorce hover:bg-line";
  return (
    <span role="group" aria-label={label} className="flex items-center gap-1.5">
      <button type="button" aria-label={`${label} : moins`} onClick={onMinus} className={btn}>
        <span aria-hidden="true" className="text-lg leading-none font-bold">−</span>
      </button>
      <span aria-live="polite" className="min-w-[84px] text-center font-semibold text-ecorce">{text}</span>
      <button type="button" aria-label={`${label} : plus`} onClick={onPlus} className={btn}>
        <Icon name="plus" size={16} />
      </button>
    </span>
  );
}
