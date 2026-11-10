import { useState, type FormEvent } from "react";
import * as api from "../lib/api";
import { DETAILS_MAX, REPORT_REASONS, REPORT_TITLES, type ReportReason, type ReportTarget } from "../lib/moderation";
import { useFeedback } from "./feedback";
import Sheet from "./Sheet";
import { Button } from "./ui";

/** Signaler une idée, un commentaire ou un compte : un motif, des précisions facultatives. */
export default function ReportSheet({ type, id, onClose }: { type: ReportTarget; id: string; onClose: () => void }) {
  const { toast } = useFeedback();
  const [reason, setReason] = useState<ReportReason>();
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);

  const send = async (e: FormEvent) => {
    e.preventDefault();
    if (!reason) return;
    setBusy(true);
    try {
      await api.moderation.report(type, id, reason, details.trim());
      toast("Merci, nous allons regarder ce signalement.");
      onClose();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet open onClose={onClose} title={REPORT_TITLES[type]}>
      <form onSubmit={send} className="flex flex-col gap-4">
        <p className="text-sm text-sec">Votre signalement reste anonyme : la personne concernée ne saura pas qu'il vient de vous.</p>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-[13px] font-bold text-sec">Pourquoi ?</legend>
          {REPORT_REASONS.map(([k, label]) => (
            <label key={k} className={`flex cursor-pointer items-center gap-3 rounded-medium bg-card px-4 py-3.5 text-[15px] font-semibold ${reason === k ? "ring-2 ring-ecorce" : ""}`}>
              <input type="radio" name="reason" value={k} checked={reason === k} onChange={() => setReason(k)} className="h-4 w-4 accent-[var(--color-ecorce)]" />
              {label}
            </label>
          ))}
        </fieldset>
        <label className="flex flex-col gap-1.5">
          <span className="text-[13px] font-bold text-sec">Précisions (facultatif)</span>
          <textarea
            value={details}
            maxLength={DETAILS_MAX}
            rows={3}
            onChange={(e) => setDetails(e.target.value)}
            className="min-h-24 resize-y rounded-medium border-[1.5px] border-line-2 bg-card px-4 py-3 text-[15px] outline-none focus:border-terracotta"
          />
        </label>
        <Button type="submit" block loading={busy} disabled={!reason}>Envoyer le signalement</Button>
      </form>
    </Sheet>
  );
}
