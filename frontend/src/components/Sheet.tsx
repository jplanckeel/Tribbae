import { useEffect, useEffectEvent, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { IconButton } from "./ui";

interface SheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  /** Ton prune pour les écrans IA. */
  tone?: "sable" | "prune";
  wide?: boolean;
  footer?: ReactNode;
}

// Pile des feuilles ouvertes : seule celle du dessus réagit au clavier.
const stack: object[] = [];

/**
 * Feuille modale : glisse du bas sur mobile, fenêtre centrée sur grand écran.
 * Gère Échap, le clic sur le fond, le blocage du défilement et le focus.
 */
export default function Sheet({ open, onClose, title, children, tone = "sable", wide, footer }: SheetProps) {
  const ref = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const close = useEffectEvent(() => onClose());

  useEffect(() => {
    if (!open) return;
    const token = {};
    stack.push(token);
    const previous = document.activeElement as HTMLElement | null;
    const { overflow } = document.body.style;
    document.body.style.overflow = "hidden";
    const first = ref.current?.querySelector<HTMLElement>("input, textarea, select, button:not([data-close])");
    (first ?? ref.current)?.focus();

    const onKey = (e: KeyboardEvent) => {
      if (stack[stack.length - 1] !== token) return;
      if (e.key === "Escape") close();
      if (e.key !== "Tab" || !ref.current) return;
      // Garde le focus dans la feuille
      const items = ref.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), textarea, select, [tabindex]:not([tabindex="-1"])',
      );
      if (items.length === 0) return;
      const firstEl = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === firstEl) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        firstEl.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      stack.splice(stack.indexOf(token), 1);
      if (stack.length === 0) document.body.style.overflow = overflow;
      previous?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  const surface = tone === "prune" ? "bg-prune text-sable" : "bg-sable text-ecorce";
  return createPortal(
    <div className="fixed inset-0 z-[60] flex items-end justify-center sm:items-center sm:p-6">
      <div aria-hidden="true" className="absolute inset-0 animate-fade-in bg-[rgba(42,22,18,0.45)]" onClick={onClose} />
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`relative flex max-h-[92dvh] w-full flex-col rounded-t-[30px] shadow-sheet outline-none animate-sheet-up sm:rounded-[30px] ${wide ? "sm:max-w-2xl" : "sm:max-w-lg"} ${surface}`}
      >
        <span aria-hidden="true" className={`mx-auto mt-2.5 h-[5px] w-11 rounded-full sm:hidden ${tone === "prune" ? "bg-white/25" : "bg-dash"}`} />
        <div className="flex items-center justify-between gap-3 px-5 pt-3 pb-1 sm:px-6 sm:pt-5">
          <h2 id={titleId} className="font-display text-2xl font-extrabold tracking-[-0.4px]">{title}</h2>
          <IconButton
            data-close
            icon="close"
            label="Fermer"
            onClick={onClose}
            size={40}
            iconSize={20}
            className={tone === "prune" ? "bg-white/10 hover:bg-white/20" : "bg-sable-2 hover:bg-line"}
          />
        </div>
        <div className="overflow-y-auto px-5 pt-3 pb-6 sm:px-6">{children}</div>
        {footer && <div className="border-t border-line px-5 py-4 pb-safe sm:px-6">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
