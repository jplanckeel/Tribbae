// Logo Tribbae « D · Grand jour » (maquette « Le feu de camp ») : tipi au
// soleil, porte prune, fanion braise, ligne de sol.
export function TipiMark({ size = 40, stroke = 4.5, className = "" }: { size?: number; stroke?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" className={className}>
      <circle cx="76" cy="24" r="8" fill="#FFC857" />
      <path d="M50 24L81 80H19Z" fill="#D45A43" stroke="#3A211B" strokeWidth={stroke} strokeLinejoin="round" />
      <path d="M43 13L54 30M57 13L46 30" stroke="#3A211B" strokeWidth={stroke} strokeLinecap="round" />
      <path d="M50 50L64 80H36Z" fill="#5E4558" stroke="#3A211B" strokeWidth={stroke} strokeLinejoin="round" />
      <path d="M34 52H50L42 68Z" fill="#F28C38" stroke="#3A211B" strokeWidth={stroke * 0.8} strokeLinejoin="round" />
      <path d="M10 80H90" stroke="#3A211B" strokeWidth={stroke} strokeLinecap="round" />
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

/** Petits motifs décoratifs du logo : rond et croix. */
export function Sparkles() {
  return (
    <>
      <span aria-hidden="true" className="absolute top-[118px] right-10 h-[9px] w-[9px] rounded-full border-2 border-terracotta" />
      <svg aria-hidden="true" width="12" height="12" viewBox="0 0 12 12" className="absolute top-[70px] right-[150px]">
        <path d="M2 2l8 8M10 2l-8 8" stroke="#3A211B" strokeWidth="2" strokeLinecap="round" />
      </svg>
    </>
  );
}
