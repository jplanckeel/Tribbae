import { useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { TooltipContentProps } from "recharts";
import * as api from "../lib/api";
import type { AdminStats as Stats, StatsPeriod, StatsPoint } from "../lib/types";
import { useAsync } from "../lib/useAsync";
import { Button, Chip, EmptyState, ErrorNote, SectionTitle, Spinner } from "./ui";

/*
 * Statistiques admin : nouvelles idées (publiques / privées), nouveaux comptes
 * et comptes actifs, par semaine ou par mois. Chargé à part (lazy) depuis
 * pages/Admin.tsx pour garder Recharts hors du paquet principal.
 */

const COUNT = 12;
const PERIODS: { id: StatsPeriod; label: string; unit: string; current: string }[] = [
  { id: "week", label: "Semaine", unit: "12 semaines", current: "Semaine dernière" },
  { id: "month", label: "Mois", unit: "12 mois", current: "Mois dernier" },
];

// Périodes découpées à l'heure de Paris côté serveur : on les affiche de même
const tz = "Europe/Paris";
const dayFmt = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short", timeZone: tz });
const weekFmt = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "long", year: "numeric", timeZone: tz });
const monthShortFmt = new Intl.DateTimeFormat("fr-FR", { month: "short", year: "2-digit", timeZone: tz });
const monthFmt = new Intl.DateTimeFormat("fr-FR", { month: "long", year: "numeric", timeZone: tz });
const numFmt = new Intl.NumberFormat("fr-FR");

interface Row {
  start: string;
  publicIdeas: number;
  privateIdeas: number;
  ideas: number;
  newUsers: number;
  activeUsers: number;
}

type Metric = Exclude<keyof Row, "start">;

const LABELS: Record<Metric, string> = {
  ideas: "Idées",
  publicIdeas: "Publiques",
  privateIdeas: "Privées",
  newUsers: "Nouveaux comptes",
  activeUsers: "Comptes actifs",
};

const n = (p: StatsPoint | undefined) => Number(p?.count ?? 0);

function rows(s: Stats): Row[] {
  return (s.ideas ?? []).map((p, i) => ({
    start: p.bucketStart,
    ideas: n(p),
    publicIdeas: n(s.publicIdeas?.[i]),
    privateIdeas: n(s.privateIdeas?.[i]),
    newUsers: n(s.newUsers?.[i]),
    activeUsers: n(s.activeUsers?.[i]),
  }));
}

const tick = (period: StatsPeriod) => (v: string) => (period === "week" ? dayFmt : monthShortFmt).format(new Date(v));
const periodLabel = (period: StatsPeriod, v: string) =>
  period === "week" ? `Semaine du ${weekFmt.format(new Date(v))}` : monthFmt.format(new Date(v));

/** Variation d'une période par rapport à la précédente. */
function variation(last: number, prev: number): string {
  if (prev === 0) return last > 0 ? "nouveau" : "stable";
  const pct = Math.round(((last - prev) / prev) * 100);
  return pct === 0 ? "stable" : `${pct > 0 ? "+" : "−"}${Math.abs(pct)} %`;
}

export default function AdminStats() {
  const [period, setPeriod] = useState<StatsPeriod>("week");
  const stats = useAsync(() => api.admin.stats(period, COUNT), [period]);
  const meta = PERIODS.find((p) => p.id === period)!;
  const data = stats.data ? rows(stats.data) : [];
  const empty = data.every((r) => r.ideas + r.newUsers + r.activeUsers === 0);

  return (
    <section aria-labelledby="stats-title" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="stats-title" className="font-display text-xl font-bold">Statistiques</h2>
        <div role="group" aria-label="Période" className="flex gap-2">
          {PERIODS.map((p) => (
            <Chip key={p.id} active={period === p.id} onClick={() => setPeriod(p.id)}>
              {p.label}
            </Chip>
          ))}
        </div>
      </div>

      {stats.loading ? (
        <div className="flex h-40 items-center justify-center text-terracotta">
          <Spinner />
        </div>
      ) : stats.error ? (
        <div className="flex flex-col items-start gap-3">
          <ErrorNote>Impossible de charger les statistiques : {stats.error.message}</ErrorNote>
          <Button variant="secondary" onClick={stats.reload}>Réessayer</Button>
        </div>
      ) : empty ? (
        <EmptyState icon="idee" title="Rien sur la période" text={`Aucune idée ni aucun compte sur les ${meta.unit}.`} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {(["ideas", "newUsers", "activeUsers"] as const).map((m) => (
              <Tile key={m} label={LABELS[m]} data={data} metric={m} unit={meta.unit} current={meta.current} />
            ))}
          </div>

          <ChartCard title="Nouvelles idées" hint="Publiques et privées, empilées">
            <BarChart data={data} margin={{ top: 8, right: 4, bottom: 0, left: -20 }} barCategoryGap="22%">
              <CartesianGrid vertical={false} stroke="var(--color-line)" />
              <XAxis dataKey="start" tickFormatter={tick(period)} {...axis} minTickGap={8} />
              <YAxis allowDecimals={false} {...axis} />
              <Tooltip content={(p) => <ChartTooltip {...p} period={period} />} cursor={{ fill: "var(--color-sable-2)", opacity: 0.6 }} />
              <Legend content={() => <ChartLegend items={["publicIdeas", "privateIdeas"]} />} />
              <Bar dataKey="publicIdeas" name={LABELS.publicIdeas} stackId="ideas" fill="var(--chart-1)" stroke="var(--color-card)" strokeWidth={2} />
              <Bar dataKey="privateIdeas" name={LABELS.privateIdeas} stackId="ideas" fill="var(--chart-2)" stroke="var(--color-card)" strokeWidth={2} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ChartCard>

          <ChartCard title="Comptes" hint="Nouveaux comptes et comptes actifs">
            <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -20 }}>
              <CartesianGrid vertical={false} stroke="var(--color-line)" />
              <XAxis dataKey="start" tickFormatter={tick(period)} {...axis} minTickGap={8} padding={{ left: 12, right: 12 }} />
              <YAxis allowDecimals={false} {...axis} />
              <Tooltip content={(p) => <ChartTooltip {...p} period={period} />} cursor={{ stroke: "var(--color-dash)", strokeWidth: 1 }} />
              <Legend content={() => <ChartLegend items={["newUsers", "activeUsers"]} />} />
              {(["newUsers", "activeUsers"] as const).map((m, i) => (
                <Line
                  key={m}
                  dataKey={m}
                  name={LABELS[m]}
                  type="monotone"
                  stroke={`var(--chart-${i + 1})`}
                  strokeWidth={2}
                  dot={{ r: 3, fill: `var(--chart-${i + 1})`, strokeWidth: 0 }}
                  activeDot={{ r: 5, stroke: "var(--color-card)", strokeWidth: 2 }}
                />
              ))}
            </LineChart>
          </ChartCard>

          <p className="text-[13px] text-sec">
            Comptes actifs : connexion ou renouvellement de session dans la période. Seule la dernière activité de chaque
            compte est gardée, donc les périodes passées sont sous-estimées (un compte revenu depuis n'y compte plus).
          </p>

          <details className="rounded-large bg-card px-4 py-3 text-sm">
            <summary className="cursor-pointer font-semibold">Voir les données</summary>
            <div className="mt-3 overflow-x-auto">
              <table className="w-full min-w-[480px] text-right tabular-nums">
                <thead className="text-xs text-sec">
                  <tr>
                    <th scope="col" className="py-1.5 text-left font-semibold">Période</th>
                    {(Object.keys(LABELS) as Metric[]).map((m) => (
                      <th key={m} scope="col" className="py-1.5 pl-3 font-semibold">{LABELS[m]}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {[...data].reverse().map((r) => (
                    <tr key={r.start}>
                      <th scope="row" className="py-1.5 text-left font-medium">{periodLabel(period, r.start)}</th>
                      {(Object.keys(LABELS) as Metric[]).map((m) => (
                        <td key={m} className="py-1.5 pl-3">{numFmt.format(r[m])}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </>
      )}
    </section>
  );
}

const axis = {
  tick: { fill: "var(--color-sec)", fontSize: 12 },
  tickLine: false,
  axisLine: { stroke: "var(--color-line)" },
};

function Tile({ label, data, metric, unit, current }: { label: string; data: Row[]; metric: Metric; unit: string; current: string }) {
  const total = data.reduce((sum, r) => sum + r[metric], 0);
  // Dernière période complète comparée à celle d'avant (la période en cours
  // n'est pas finie : la comparer donnerait toujours une baisse)
  const last = data.at(-2)?.[metric] ?? 0;
  const prev = data.at(-3)?.[metric] ?? 0;
  return (
    <div className="flex flex-col gap-1 rounded-large bg-card p-4 shadow-card">
      <span className="text-[13px] font-semibold text-sec">{label}</span>
      <span className="font-display text-[32px] font-extrabold leading-none tracking-[-0.8px] tabular-nums">{numFmt.format(total)}</span>
      <span className="text-xs text-sec">sur {unit}</span>
      <span className="mt-1 text-[13px] text-ecorce-2">
        {current} : <strong className="tabular-nums">{numFmt.format(last)}</strong>{" "}
        <span className="text-sec">({variation(last, prev)} vs la précédente)</span>
      </span>
    </div>
  );
}

function ChartCard({ title, hint, children }: { title: string; hint: string; children: React.ReactElement }) {
  return (
    <figure className="flex flex-col gap-2 rounded-large bg-card p-4 shadow-card">
      <figcaption className="flex flex-col">
        <SectionTitle title={title} />
        <span className="text-[13px] text-sec">{hint}</span>
      </figcaption>
      <div className="h-[220px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          {children}
        </ResponsiveContainer>
      </div>
    </figure>
  );
}

function ChartLegend({ items }: { items: Metric[] }) {
  return (
    <ul className="mt-1 flex flex-wrap justify-center gap-4 text-xs font-semibold text-sec">
      {items.map((m, i) => (
        <li key={m} className="flex items-center gap-1.5">
          <span aria-hidden className="h-2.5 w-2.5 rounded-[3px]" style={{ background: `var(--chart-${i + 1})` }} />
          {LABELS[m]}
        </li>
      ))}
    </ul>
  );
}

function ChartTooltip({ active, payload, label, period }: TooltipContentProps & { period: StatsPeriod }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="surface-raised rounded-small bg-card border border-line px-3 py-2 text-[13px] shadow-card">
      <p className="mb-1 font-semibold">{periodLabel(period, String(label))}</p>
      {payload.map((p) => (
        <p key={String(p.dataKey)} className="flex items-center gap-2 text-sec">
          <span aria-hidden className="h-2 w-2 rounded-full" style={{ background: p.color }} />
          {p.name} : <strong className="tabular-nums text-ecorce">{numFmt.format(Number(p.value))}</strong>
        </p>
      ))}
    </div>
  );
}
