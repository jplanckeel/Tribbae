import { useMemo, useState } from "react";
import { Navigate } from "react-router";
import { useFeedback } from "../components/feedback";
import { AVATAR_COLORS } from "../lib/categories";
import Icon from "../components/Icon";
import { Page } from "../components/Shell";
import { Avatar, ErrorNote, PageSpinner, Toggle } from "../components/ui";
import * as api from "../lib/api";
import { formatLongDate } from "../lib/format";
import { useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";
import Seo from "../components/Seo";

export default function Admin() {
  const session = useSession();
  const { toast } = useFeedback();
  const users = useAsync(() => api.admin.listUsers(), []);
  const [q, setQ] = useState("");
  const [busy, setBusy] = useState<string>();

  const list = useMemo(() => {
    const s = q.trim().toLowerCase();
    return (users.data ?? []).filter((u) => !s || u.email.toLowerCase().includes(s) || u.displayName?.toLowerCase().includes(s));
  }, [users.data, q]);

  // Le serveur vérifie aussi le rôle : ceci n'est qu'un confort d'affichage
  if (!session?.isAdmin) return <Navigate to="/" replace />;

  const setPremium = async (id: string, v: boolean) => {
    setBusy(id);
    try {
      await api.admin.setPremium(id, v);
      users.setData((l) => (l ?? []).map((u) => (u.id === id ? { ...u, isPremium: v } : u)));
      toast(v ? "Premium activé" : "Premium retiré");
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(undefined);
    }
  };

  const premium = users.data?.filter((u) => u.isPremium).length ?? 0;

  return (
    <Page className="max-w-4xl">
      <Seo title="Administration · Tribbae" noindex />
      <div className="flex flex-col gap-0.5">
        <h1 className="font-display text-[32px] font-extrabold tracking-[-0.8px]">Administration</h1>
        {users.data && <span className="text-sm text-sec">{users.data.length} comptes · {premium} premium</span>}
      </div>
      <label className="flex h-[52px] items-center gap-2.5 rounded-m bg-white px-4 text-muted shadow-card focus-within:ring-2 focus-within:ring-terracotta">
        <Icon name="search" size={20} />
        <input
          type="search"
          aria-label="Rechercher un compte"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Email ou nom"
          className="min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ecorce outline-none placeholder:text-muted"
        />
      </label>
      {users.loading ? (
        <PageSpinner />
      ) : users.error ? (
        <ErrorNote>{users.error.message}</ErrorNote>
      ) : (
        <ul className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-l bg-white">
          {list.map((u, i) => {
            const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
            return (
              <li key={u.id} className="flex items-center gap-3 px-4 py-3">
                <Avatar name={u.displayName || u.email} color={bg} ink={ink} size={40} rounded="rounded-s" />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="flex items-center gap-1.5 truncate font-bold">
                    {u.displayName || "—"}
                    {u.isAdmin && <Icon name="crown" size={15} className="text-braise" />}
                  </span>
                  <span className="truncate text-[13px] text-sec">
                    {u.email}
                    {Number(u.createdAt) > 0 ? ` · inscrit le ${formatLongDate(Number(u.createdAt) * 1000)}` : ""}
                  </span>
                </span>
                <span className="hidden text-xs font-semibold text-sec sm:block">Premium</span>
                <Toggle label={`Premium pour ${u.email}`} checked={u.isPremium} disabled={busy === u.id} onChange={(v) => setPremium(u.id, v)} />
              </li>
            );
          })}
        </ul>
      )}
    </Page>
  );
}
