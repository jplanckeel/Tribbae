import * as api from "../lib/api";
import { useAsync } from "../lib/useAsync";
import { useFeedback } from "./feedback";
import Sheet from "./Sheet";
import { Avatar, Button, EmptyState, ErrorNote, Spinner } from "./ui";

/** Comptes bloqués : leurs idées et commentaires sont cachés ; on peut les débloquer. */
export default function BlockedSheet({ onClose }: { onClose: () => void }) {
  const { toast } = useFeedback();
  const list = useAsync(() => api.moderation.blocked(), []);
  const unblock = async (userId: string, name: string) => {
    try {
      await api.moderation.unblock(userId);
      list.setData((l) => (l ?? []).filter((u) => u.userId !== userId));
      toast(`${name || "Ce compte"} est débloqué`);
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };
  return (
    <Sheet open onClose={onClose} title="Comptes bloqués">
      {list.loading ? (
        <Spinner className="text-terracotta" />
      ) : list.error ? (
        <ErrorNote>{list.error.message}</ErrorNote>
      ) : !list.data?.length ? (
        <EmptyState icon="people" title="Aucun compte bloqué" text="Depuis une idée, le menu « … » permet de bloquer son auteur." />
      ) : (
        <ul className="flex flex-col gap-2">
          {list.data.map((u) => (
            <li key={u.userId} className="flex items-center gap-3 rounded-medium bg-card px-3.5 py-3">
              <Avatar name={u.displayName} size={36} rounded="rounded-small" />
              <span className="flex-1 font-semibold">{u.displayName || "Compte"}</span>
              <Button size="sm" variant="ghost" onClick={() => void unblock(u.userId, u.displayName)}>Débloquer</Button>
            </li>
          ))}
        </ul>
      )}
    </Sheet>
  );
}
