import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import Icon from "./Icon";
import Sheet from "./Sheet";
import { Button } from "./ui";

// ── Toasts ─────────────────────────────────────────────────────────────

interface Toast {
  id: number;
  text: string;
  tone: "ok" | "error";
}

interface ConfirmOptions {
  title: string;
  text?: string;
  confirmLabel?: string;
  danger?: boolean;
}

interface FeedbackApi {
  toast: (text: string, tone?: Toast["tone"]) => void;
  confirm: (opts: ConfirmOptions) => Promise<boolean>;
}

const FeedbackContext = createContext<FeedbackApi | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [pending, setPending] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<(v: boolean) => void>(undefined);
  const nextId = useRef(0);

  const toast = useCallback((text: string, tone: Toast["tone"] = "ok") => {
    const id = ++nextId.current;
    setToasts((t) => [...t, { id, text, tone }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3200);
  }, []);

  const confirm = useCallback((opts: ConfirmOptions) => {
    setPending(opts);
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const settle = (v: boolean) => {
    resolver.current?.(v);
    setPending(null);
  };

  return (
    <FeedbackContext.Provider value={{ toast, confirm }}>
      {children}
      {createPortal(
        <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-[calc(116px+env(safe-area-inset-bottom))] z-[70] flex flex-col items-center gap-2 px-4 md:bottom-8">
          {toasts.map((t) => (
            <div
              key={t.id}
              role={t.tone === "error" ? "alert" : "status"}
              className={`flex animate-pop items-center gap-2 rounded-m px-4 py-3 text-sm font-semibold shadow-nav ${t.tone === "error" ? "bg-sortie-ink text-white" : "bg-ecorce text-sable"}`}
            >
              <Icon name={t.tone === "error" ? "close" : "check"} size={18} />
              {t.text}
            </div>
          ))}
        </div>,
        document.body,
      )}
      <Sheet open={!!pending} onClose={() => settle(false)} title={pending?.title ?? ""}>
        {pending?.text && <p className="mb-5 text-[15px] text-ecorce-2">{pending.text}</p>}
        <div className="flex gap-2.5">
          <Button variant="ghost" block onClick={() => settle(false)}>Annuler</Button>
          <Button variant={pending?.danger ? "primary" : "dark"} block onClick={() => settle(true)}>
            {pending?.confirmLabel ?? "Confirmer"}
          </Button>
        </div>
      </Sheet>
    </FeedbackContext.Provider>
  );
}

export function useFeedback(): FeedbackApi {
  const ctx = useContext(FeedbackContext);
  if (!ctx) throw new Error("useFeedback must be used inside FeedbackProvider");
  return ctx;
}
