import { useState } from "react";
import { Link as RouterLink } from "react-router";
import * as api from "../lib/api";
import { groupReports, reasonLabel } from "../lib/moderation";
import type { AdminReport } from "../lib/types";
import { useAsync } from "../lib/useAsync";
import { useFeedback } from "./feedback";
import { Button, Chip, EmptyState, ErrorNote, SectionTitle, Spinner } from "./ui";

const KIND: Record<string, string> = { idea: "Idée", comment: "Commentaire", account: "Compte" };
const ACTION: Record<string, string> = { remove: "retiré", suspend: "compte suspendu", dismiss: "classé sans suite" };

/** File des signalements : retirer le contenu, suspendre le compte ou classer sans suite. */
export default function AdminReports() {
  const { toast, confirm } = useFeedback();
  const [closed, setClosed] = useState(false);
  const reports = useAsync(() => api.admin.reports(closed), [closed]);
  const [busy, setBusy] = useState<string>();

  const resolve = async (group: AdminReport[], action: "dismiss" | "remove" | "suspend") => {
    const r = group[0];
    if (action !== "dismiss") {
      const ok = await confirm({
        title: action === "suspend" ? `Suspendre ${r.targetOwnerName || "ce compte"}\u00a0?` : "Retirer ce contenu\u00a0?",
        text: action === "suspend"
          ? "La personne ne pourra plus se connecter, et ses idées publiques seront dépubliées et masquées."
          : r.targetType === "comment" ? "Le commentaire sera effacé." : "L'idée sera dépubliée et masquée ; son auteur la garde sans pouvoir la republier.",
        confirmLabel: action === "suspend" ? "Suspendre" : "Retirer",
        danger: true,
      });
      if (!ok) return;
    }
    setBusy(r.id);
    try {
      await api.admin.resolve(r.id, action);
      reports.setData((l) => (l ?? []).filter((x) => !(x.targetType === r.targetType && x.targetId === r.targetId)));
      toast(action === "dismiss" ? "Signalement classé" : action === "suspend" ? "Compte suspendu" : "Contenu retiré");
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(undefined);
    }
  };

  const groups = groupReports(reports.data ?? []);
  return (
    <section className="flex flex-col gap-3" aria-label="Signalements">
      <SectionTitle title="Signalements" />
      <div className="flex gap-2">
        <Chip active={!closed} onClick={() => setClosed(false)}>À traiter</Chip>
        <Chip active={closed} onClick={() => setClosed(true)}>Traités</Chip>
      </div>
      {reports.loading ? (
        <Spinner className="text-terracotta" />
      ) : reports.error ? (
        <ErrorNote>{reports.error.message}</ErrorNote>
      ) : !groups.length ? (
        <EmptyState icon="flag" title={closed ? "Aucun signalement traité" : "Rien à traiter"} text={closed ? undefined : "Les nouveaux signalements arrivent aussi par email."} />
      ) : (
        <ul className="flex flex-col gap-2.5">
          {groups.map((g) => {
            const r = g[0];
            const href = r.targetType === "idea" ? `/links/${r.targetId}` : r.targetType === "comment" && r.linkId ? `/links/${r.linkId}` : undefined;
            return (
              <li key={`${r.targetType}:${r.targetId}`} className="flex flex-col gap-2 rounded-large bg-card p-4">
                <div className="flex flex-wrap items-baseline gap-2 text-[13px] text-sec">
                  <strong className="text-ecorce">{KIND[r.targetType] ?? r.targetType}</strong>
                  <span>de {r.targetOwnerName || "compte supprimé"}</span>
                  <span>· {g.length} signalement{g.length > 1 ? "s" : ""}</span>
                  {closed && r.action && <span>· {ACTION[r.action] ?? r.action}</span>}
                </div>
                {r.targetSummary && <p className="line-clamp-3 text-[15px] font-semibold break-words">{r.targetSummary}</p>}
                <ul className="flex flex-col gap-1 text-sm text-sec">
                  {g.map((x) => (
                    <li key={x.id}>
                      <strong className="text-ecorce-2">{reasonLabel(x.reason)}</strong> par {x.reporterName || "un compte"}
                      {x.details && <> : « {x.details} »</>}
                    </li>
                  ))}
                </ul>
                <div className="flex flex-wrap gap-2 pt-1">
                  {href && <RouterLink to={href} className="text-sm font-semibold text-link hover:underline">Voir</RouterLink>}
                  {!closed && (
                    <span className="ml-auto flex flex-wrap gap-2">
                      <Button size="sm" variant="ghost" loading={busy === r.id} onClick={() => void resolve(g, "dismiss")}>Classer</Button>
                      {r.targetType !== "account" && <Button size="sm" variant="danger" onClick={() => void resolve(g, "remove")}>Retirer</Button>}
                      <Button size="sm" variant="danger" onClick={() => void resolve(g, "suspend")}>Suspendre le compte</Button>
                    </span>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
