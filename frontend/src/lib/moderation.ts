/** Motifs de signalement et décisions : mêmes clés que le serveur (internal/moderation) et data/Moderation.kt. */
export type ReportTarget = "idea" | "comment" | "account";
export type ReportReason = "spam" | "inappropriate" | "harassment" | "other";

export const REPORT_REASONS: [ReportReason, string][] = [
  ["inappropriate", "Contenu inapproprié"],
  ["harassment", "Harcèlement ou propos blessants"],
  ["spam", "Spam ou publicité"],
  ["other", "Autre raison"],
];

export const REPORT_TITLES: Record<ReportTarget, string> = {
  idea: "Signaler cette idée",
  comment: "Signaler ce commentaire",
  account: "Signaler ce compte",
};

export const reasonLabel = (r: string) => REPORT_REASONS.find(([k]) => k === r)?.[1] ?? "Autre raison";
export const DETAILS_MAX = 500;

/** Signalements regroupés par contenu (plusieurs comptes peuvent signaler le même). */
export function groupReports<T extends { targetType: string; targetId: string }>(reports: T[]): T[][] {
  const groups = new Map<string, T[]>();
  for (const r of reports) {
    const key = `${r.targetType}:${r.targetId}`;
    groups.set(key, [...(groups.get(key) ?? []), r]);
  }
  return [...groups.values()];
}

