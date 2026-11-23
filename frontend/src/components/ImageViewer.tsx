import { useEffect, useRef, useState, type PointerEvent, type WheelEvent } from "react";
import { createPortal } from "react-dom";
import { clampPan, clampScale, toggleScale } from "../lib/zoom";
import Icon from "./Icon";

interface View {
  scale: number;
  x: number;
  y: number;
}

/**
 * Image d'une idée en plein écran : pincer, double-toucher (double-clic) ou
 * molette pour agrandir, glisser pour se déplacer. Échap, la croix ou un toucher
 * hors de l'image agrandie ferment. Même écran sur Android (ui/ImageViewer.kt).
 */
export default function ImageViewer({ src, alt, onClose }: { src: string; alt: string; onClose: () => void }) {
  const [view, setView] = useState<View>({ scale: 1, x: 0, y: 0 });
  const box = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  // Doigts posés (id → position) et geste en cours
  const pointers = useRef(new Map<number, { x: number; y: number }>());
  const gesture = useRef<{ dist: number; scale: number; x: number; y: number; px: number; py: number; moved: boolean } | null>(null);
  const lastTap = useRef(0);

  const size = () => ({ w: box.current?.clientWidth ?? window.innerWidth, h: box.current?.clientHeight ?? window.innerHeight });
  const apply = (scale: number, x: number, y: number) => {
    const s = clampScale(scale);
    const { w, h } = size();
    setView({ scale: s, ...clampPan(x, y, s, w, h) });
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
    };
  }, [onClose]);

  const start = () => {
    const pts = [...pointers.current.values()];
    const dist = pts.length > 1 ? Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y) : 0;
    gesture.current = { dist, scale: view.scale, x: view.x, y: view.y, px: pts[0]?.x ?? 0, py: pts[0]?.y ?? 0, moved: gesture.current?.moved ?? false };
  };

  const onPointerDown = (e: PointerEvent) => {
    try {
      // Le geste continue même si le doigt sort de l'image
      (e.target as Element).setPointerCapture(e.pointerId);
    } catch {
      // Pointeur déjà relâché : sans capture
    }
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.current.size === 1) gesture.current = null;
    start();
  };
  const onPointerMove = (e: PointerEvent) => {
    if (!pointers.current.has(e.pointerId) || !gesture.current) return;
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const g = gesture.current;
    const pts = [...pointers.current.values()];
    if (pts.length > 1 && g.dist > 0) {
      g.moved = true;
      apply(g.scale * (Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y) / g.dist), g.x, g.y);
    } else if (pts.length === 1) {
      const dx = pts[0].x - g.px;
      const dy = pts[0].y - g.py;
      if (Math.abs(dx) + Math.abs(dy) > 6) g.moved = true;
      if (view.scale > 1) apply(g.scale, g.x + dx, g.y + dy);
    }
  };
  const onPointerUp = (e: PointerEvent) => {
    pointers.current.delete(e.pointerId);
    const moved = gesture.current?.moved;
    if (pointers.current.size > 0) {
      start();
      return;
    }
    gesture.current = null;
    if (moved) return;
    // Double-toucher : agrandit là où l'on touche, ou revient à l'image entière
    const now = Date.now();
    if (now - lastTap.current < 300) {
      lastTap.current = 0;
      const next = toggleScale(view.scale);
      const rect = box.current?.getBoundingClientRect();
      const cx = rect ? e.clientX - rect.left - rect.width / 2 : 0;
      const cy = rect ? e.clientY - rect.top - rect.height / 2 : 0;
      apply(next, next > 1 ? -cx * (next - 1) : 0, next > 1 ? -cy * (next - 1) : 0);
    } else {
      lastTap.current = now;
    }
  };
  const onWheel = (e: WheelEvent) => {
    const next = clampScale(view.scale * (e.deltaY < 0 ? 1.15 : 1 / 1.15));
    apply(next, next === 1 ? 0 : view.x, next === 1 ? 0 : view.y);
  };

  return createPortal(
    <div role="dialog" aria-modal="true" aria-label={`Image : ${alt}`} className="fixed inset-0 z-[80] flex flex-col bg-black">
      <div className="flex justify-end p-4 pt-[max(16px,env(safe-area-inset-top))]">
        <button
          ref={closeRef}
          type="button"
          onClick={onClose}
          aria-label="Fermer l'image"
          className="flex h-11 w-11 items-center justify-center rounded-full bg-white/15 text-white hover:bg-white/25"
        >
          <Icon name="close" size={22} />
        </button>
      </div>
      <div
        ref={box}
        className="relative min-h-0 flex-1 touch-none overflow-hidden select-none"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onWheel={onWheel}
        onClick={(e) => {
          // Toucher le fond (pas l'image) : on ferme, si l'image n'est pas agrandie
          if (e.target === e.currentTarget && view.scale === 1) onClose();
        }}
      >
        <img
          src={src}
          alt={alt}
          referrerPolicy="no-referrer"
          draggable={false}
          className="absolute inset-0 m-auto max-h-full max-w-full object-contain transition-transform duration-75"
          style={{ transform: `translate(${view.x}px, ${view.y}px) scale(${view.scale})` }}
        />
      </div>
      <p className="px-4 pb-[max(16px,env(safe-area-inset-bottom))] text-center text-xs text-white/70">
        {view.scale > 1 ? "Glissez pour vous déplacer · double-touchez pour revenir" : "Pincez ou double-touchez pour agrandir"}
      </p>
    </div>,
    document.body,
  );
}
