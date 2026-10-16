// Logo tipi Tribbae (repris de la maquette « Le feu de camp »).
export function TipiMark({ size = 34, stroke = 2.6, className = "" }: { size?: number; stroke?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" aria-hidden="true" className={className}>
      <path d="M20 3l6 9M28 3l-6 9" stroke="#3A211B" strokeWidth={stroke} strokeLinecap="round" />
      <path d="M24 9L44 43H4z" fill="#D45A43" stroke="#3A211B" strokeWidth={stroke} strokeLinejoin="round" />
      <path d="M24 24l11 19H13z" fill="#5E4558" stroke="#3A211B" strokeWidth={stroke} strokeLinejoin="round" />
      <path d="M12 26h12.5L18 39z" fill="#F28C38" stroke="#3A211B" strokeWidth={stroke * 0.85} strokeLinejoin="round" />
    </svg>
  );
}

export default function Logo({ className = "" }: { className?: string }) {
  return (
    <span className={`flex items-center gap-2 ${className}`}>
      <TipiMark />
      <span className="font-display text-[22px] font-extrabold tracking-[-0.4px]">Tribbae</span>
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
