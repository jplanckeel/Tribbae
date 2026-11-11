import type { ReactNode } from "react";
import { isPlaceholder } from "../lib/legal";
import Seo from "./Seo";
import { Page } from "./Shell";

/** Mise en page commune des textes légaux (CGU, mentions légales). */
export default function LegalPage({ title, description, path, updated, children }: {
  title: string; description: string; path: string; updated?: string; children: ReactNode;
}) {
  return (
    <Page className="max-w-3xl">
      <Seo title={`${title} · Tribbae`} description={description} path={path} />
      <div className="flex flex-col gap-1">
        <h1 className="font-display text-[32px] leading-tight font-extrabold tracking-[-0.8px]">{title}</h1>
        {updated && <span className="text-sm text-sec">Mise à jour le {updated}</span>}
      </div>
      <div className="flex flex-col gap-3 pb-10">{children}</div>
    </Page>
  );
}

export function LegalSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2 rounded-large bg-card px-5 py-4 text-[15px] leading-relaxed text-ecorce-2">
      <h2 className="font-display text-lg font-bold text-ecorce">{title}</h2>
      {children}
    </section>
  );
}

/** Valeur légale : mise en évidence tant qu'elle reste à compléter. */
export function LegalValue({ children }: { children: string }) {
  return isPlaceholder(children) ? <mark className="rounded bg-soleil px-1 text-ecorce">{children}</mark> : <>{children}</>;
}
