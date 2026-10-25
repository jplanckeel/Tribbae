// Logo Tribbae « D · Grand jour » (maquette « Le feu de camp ») : tipi au
// soleil, porte prune, fanion braise, ligne de sol.
export function TipiMark({ size = 40, stroke = 4.5, className = "" }: { size?: number; stroke?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" className={className}>
      <circle cx="76" cy="24" r="8" fill="#FFC857" />
      <path d="M50 24L81 80H19Z" fill="#D45A43" stroke="var(--color-ecorce)" strokeWidth={stroke} strokeLinejoin="round" />
      <path d="M43 13L54 30M57 13L46 30" stroke="var(--color-ecorce)" strokeWidth={stroke} strokeLinecap="round" />
      <path d="M50 50L64 80H36Z" fill="var(--tipi-door)" stroke="var(--color-ecorce)" strokeWidth={stroke} strokeLinejoin="round" />
      <path d="M34 52H50L42 68Z" fill="#F28C38" stroke="var(--color-ecorce)" strokeWidth={stroke * 0.8} strokeLinejoin="round" />
      <path d="M10 80H90" stroke="var(--color-ecorce)" strokeWidth={stroke} strokeLinecap="round" />
    </svg>
  );
}

/** Logo complet : symbole + nom. `size` règle les deux (mobile 34, en-tête web 40). */
export default function Logo({ size = 34, className = "" }: { size?: number; className?: string }) {
  return (
    <span className={`flex items-center gap-2 ${className}`}>
      <TipiMark size={size} />
      <span className="font-display font-extrabold tracking-[-0.6px]" style={{ fontSize: Math.round(size * 0.65) }}>
        Tribbae
      </span>
    </span>
  );
}
