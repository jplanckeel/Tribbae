// Généré depuis la maquette « Bannières illustrées » (viewBox 350×110) — même dessin que
// composeApp/.../ui/campfire/FolderBannerShapes.kt. Les dessins sur fond pastel prennent
// le fond (bg) et la teinte secondaire (mid) de la couleur du dossier.
import type { JSX } from "react";

export type IllustrationKey = "anniv" | "cadeaux" | "noel" | "jouets" | "montagne" | "plage" | "gouter" | "pluie" | "automne";

type Art = (c: { bg: string; mid: string }) => JSX.Element;

export const ILLUSTRATION_ART: Record<IllustrationKey, { label: string; tinted: boolean; art: Art }> = {
  anniv: {
    label: "Anniversaire : gâteau, bougies, ballons et guirlande",
    tinted: true,
    art: ({ bg, mid }) => (
      <>
      <rect width="350" height="110" fill={bg} />
      <circle cx="300" cy="18" r="46" fill={mid} />
      <path d="M0 6Q175 50 350 6" fill="none" stroke="#A04812" strokeWidth="1.5" />
      <path d="M16 12h18l-9 16z" fill="#C8492C" />
      <path d="M51 18h18l-9 16z" fill="#FFC857" />
      <path d="M86 23h18l-9 16z" fill="#5E4558" />
      <path d="M121 26h18l-9 16z" fill="#F28C38" />
      <path d="M156 28h18l-9 16z" fill="#C8492C" />
      <path d="M191 28h18l-9 16z" fill="#FFC857" />
      <path d="M226 25h18l-9 16z" fill="#5E4558" />
      <path d="M261 21h18l-9 16z" fill="#F28C38" />
      <path d="M296 16h18l-9 16z" fill="#C8492C" />
      <path d="M326 10h18l-9 16z" fill="#FFC857" />
      <path d="M40 72q-6 16 4 32" fill="none" stroke="#A04812" strokeWidth="1.2" />
      <ellipse cx="40" cy="56" rx="13" ry="16" fill="#C8492C" />
      <ellipse cx="35" cy="50" rx="3" ry="5" fill="#FFFFFF" opacity=".45" />
      <path d="M66 63q6 20-4 41" fill="none" stroke="#A04812" strokeWidth="1.2" />
      <ellipse cx="66" cy="48" rx="12" ry="15" fill="#FFC857" />
      <path d="M90 76q-4 14 2 28" fill="none" stroke="#A04812" strokeWidth="1.2" />
      <ellipse cx="90" cy="62" rx="11" ry="14" fill="#5E4558" />
      <ellipse cx="250" cy="104" rx="62" ry="6" fill={mid} />
      <rect x="205" y="72" width="90" height="31" rx="6" fill="#FFFFFF" />
      <path d="M205 80q0-8 8-8h74q8 0 8 8v3q-5.5 6-11 0q-5.5 6-11 0q-5.5 6-11 0q-5.5 6-11 0q-5.5 6-11 0q-5.5 6-11 0q-5.5 6-11 0q-5.5 6-11 0z" fill="#F28C38" />
      <circle cx="222" cy="94" r="3" fill="#FFC857" />
      <circle cx="242" cy="96" r="3" fill="#C8492C" />
      <circle cx="262" cy="94" r="3" fill="#5E4558" />
      <circle cx="282" cy="96" r="3" fill="#FFC857" />
      <rect x="222" y="50" width="56" height="23" rx="5" fill="#C8492C" />
      <rect x="234" y="34" width="4" height="16" rx="2" fill="#5E4558" />
      <rect x="248" y="32" width="4" height="18" rx="2" fill="#FFFFFF" />
      <rect x="262" y="34" width="4" height="16" rx="2" fill="#5E4558" />
      <ellipse cx="236" cy="29" rx="3" ry="5" fill="#FFC857" />
      <ellipse cx="250" cy="27" rx="3" ry="5" fill="#FFC857" />
      <ellipse cx="264" cy="29" rx="3" ry="5" fill="#FFC857" />
      <rect x="130" y="60" width="6" height="3" fill="#C8492C" transform="rotate(30 133 61)" />
      <rect x="160" y="84" width="6" height="3" fill="#5E4558" transform="rotate(-20 163 85)" />
      <circle cx="146" cy="74" r="2.5" fill="#F28C38" />
      <circle cx="318" cy="70" r="2.5" fill="#C8492C" />
      <rect x="320" y="90" width="6" height="3" fill="#FFC857" transform="rotate(40 323 91)" />
      <circle cx="176" cy="56" r="2" fill="#FFC857" />
      </>
    ),
  },
  cadeaux: {
    label: "Cadeaux : trois paquets avec rubans",
    tinted: true,
    art: ({ bg, mid }) => (
      <>
      <rect width="350" height="110" fill={bg} />
      <circle cx="60" cy="10" r="50" fill={mid} />
      <rect y="102" width="350" height="8" fill={mid} />
      <rect x="95" y="72" width="48" height="31" rx="3" fill="#C8492C" />
      <rect x="91" y="64" width="56" height="10" rx="3" fill="#D45A43" />
      <rect x="115" y="64" width="8" height="39" fill="#FBF3E7" />
      <ellipse cx="113" cy="60" rx="8" ry="5" fill="#FBF3E7" transform="rotate(-25 113 60)" />
      <ellipse cx="125" cy="60" rx="8" ry="5" fill="#FBF3E7" transform="rotate(25 125 60)" />
      <rect x="150" y="50" width="80" height="53" rx="4" fill="#5E4558" />
      <rect x="144" y="40" width="92" height="14" rx="4" fill="#7A5B73" />
      <rect x="184" y="40" width="12" height="63" fill="#FFC857" />
      <ellipse cx="180" cy="33" rx="13" ry="7" fill="#FFC857" transform="rotate(-25 180 33)" />
      <ellipse cx="200" cy="33" rx="13" ry="7" fill="#FFC857" transform="rotate(25 200 33)" />
      <circle cx="190" cy="38" r="4" fill="#F28C38" />
      <rect x="238" y="62" width="58" height="41" rx="4" fill="#F28C38" />
      <rect x="234" y="54" width="66" height="10" rx="3" fill="#F5A66E" />
      <rect x="262" y="54" width="10" height="49" fill="#FFFFFF" />
      <rect x="238" y="78" width="58" height="8" fill="#FFFFFF" />
      <path d="M60 40l2.5 6 6 2.5-6 2.5-2.5 6-2.5-6-6-2.5 6-2.5z" fill="#FFC857" />
      <path d="M310 26l2 5 5 2-5 2-2 5-2-5-5-2 5-2z" fill="#C8492C" />
      <path d="M270 22l1.5 3.5 3.5 1.5-3.5 1.5-1.5 3.5-1.5-3.5-3.5-1.5 3.5-1.5z" fill="#5E4558" />
      <circle cx="40" cy="80" r="3" fill="#F28C38" />
      <circle cx="325" cy="70" r="3" fill="#5E4558" />
      </>
    ),
  },
  noel: {
    label: "Noël : sapin décoré sous la neige",
    tinted: false,
    art: () => (
      <>
      <rect width="350" height="110" fill="#235C77" />
      <circle cx="305" cy="24" r="12" fill="#FDE6BD" />
      <circle cx="30" cy="20" r="1.8" fill="#FFFFFF" />
      <circle cx="80" cy="12" r="1.5" fill="#FFFFFF" />
      <circle cx="140" cy="26" r="1.8" fill="#FFFFFF" />
      <circle cx="180" cy="10" r="1.4" fill="#FFFFFF" />
      <circle cx="250" cy="30" r="1.6" fill="#FFFFFF" />
      <circle cx="330" cy="52" r="1.6" fill="#FFFFFF" />
      <circle cx="110" cy="48" r="1.4" fill="#FFFFFF" />
      <circle cx="20" cy="56" r="1.6" fill="#FFFFFF" />
      <path d="M0 88Q80 70 170 86T350 82V110H0Z" fill="#DCEDF3" />
      <path d="M0 98Q120 86 230 98T350 96V110H0Z" fill="#F6EBDD" />
      <path d="M60 52l14 24H46z" fill="#2F5A2A" />
      <rect x="58" y="76" width="4" height="8" fill="#6B5047" />
      <path d="M96 60l11 20H85z" fill="#2F5A2A" />
      <rect x="94" y="80" width="4" height="6" fill="#6B5047" />
      <path d="M240 18l26 32h-52z" fill="#3E6630" />
      <path d="M240 34l32 38h-64z" fill="#4C7A3C" />
      <path d="M240 52l38 38h-76z" fill="#3E6630" />
      <rect x="235" y="90" width="10" height="8" fill="#6B5047" />
      <path d="M240 8l3 6 6.5 1-4.7 4.4 1.2 6.4-6-3.2-6 3.2 1.2-6.4-4.7-4.4 6.5-1z" fill="#FFC857" />
      <path d="M222 50q18 8 36-2M214 72q26 10 52-2" fill="none" stroke="#FFC857" strokeWidth="1.6" />
      <circle cx="232" cy="58" r="3" fill="#C8492C" />
      <circle cx="252" cy="62" r="3" fill="#F28C38" />
      <circle cx="226" cy="80" r="3" fill="#FFC857" />
      <circle cx="248" cy="84" r="3" fill="#C8492C" />
      <circle cx="262" cy="78" r="3" fill="#FDE6BD" />
      <rect x="268" y="88" width="20" height="14" rx="2" fill="#C8492C" />
      <rect x="276" y="88" width="4" height="14" fill="#FFC857" />
      <rect x="196" y="92" width="16" height="11" rx="2" fill="#F28C38" />
      <rect x="202" y="92" width="3" height="11" fill="#FFFFFF" />
      </>
    ),
  },
  jouets: {
    label: "Jouets : cubes, ballon et petit train",
    tinted: true,
    art: ({ bg, mid }) => (
      <>
      <rect width="350" height="110" fill={bg} />
      <circle cx="170" cy="0" r="56" fill={mid} />
      <rect y="104" width="350" height="6" fill={mid} />
      <rect x="34" y="68" width="36" height="36" rx="5" fill="#C8492C" />
      <text x="52" y="93" textAnchor="middle" fontFamily="Bricolage Grotesque, sans-serif" fontWeight="800" fontSize="20" fill="#FFFFFF">A</text>
      <rect x="74" y="68" width="36" height="36" rx="5" fill="#2F7393" />
      <text x="92" y="93" textAnchor="middle" fontFamily="Bricolage Grotesque, sans-serif" fontWeight="800" fontSize="20" fill="#FFFFFF">B</text>
      <rect x="54" y="32" width="36" height="36" rx="5" fill="#3E6630" transform="rotate(-6 72 50)" />
      <text x="72" y="57" textAnchor="middle" fontFamily="Bricolage Grotesque, sans-serif" fontWeight="800" fontSize="20" fill="#FFFFFF" transform="rotate(-6 72 50)">C</text>
      <circle cx="160" cy="82" r="22" fill="#F28C38" />
      <path d="M138 82h44M160 60q12 22 0 44M160 60q-12 22 0 44" fill="none" stroke="#FFFFFF" strokeWidth="3" />
      <rect x="216" y="104" width="120" height="3" fill="#A38C7C" />
      <rect x="222" y="60" width="44" height="38" rx="5" fill="#5E4558" />
      <rect x="246" y="42" width="22" height="22" rx="3" fill="#7A5B73" />
      <rect x="250" y="46" width="14" height="10" rx="2" fill="#FDE6BD" />
      <rect x="228" y="44" width="9" height="18" rx="2" fill="#3A211B" />
      <circle cx="232" cy="38" r="5" fill="#FFFFFF" opacity=".7" />
      <circle cx="226" cy="28" r="7" fill="#FFFFFF" opacity=".5" />
      <rect x="272" y="70" width="44" height="28" rx="4" fill="#C8492C" />
      <rect x="278" y="76" width="32" height="8" rx="2" fill="#FFC857" />
      <rect x="266" y="82" width="8" height="3" fill="#3A211B" />
      <circle cx="233" cy="99" r="7" fill="#3A211B" />
      <circle cx="257" cy="99" r="7" fill="#3A211B" />
      <circle cx="283" cy="99" r="6" fill="#3A211B" />
      <circle cx="305" cy="99" r="6" fill="#3A211B" />
      <circle cx="233" cy="99" r="2.5" fill="#FFC857" />
      <circle cx="257" cy="99" r="2.5" fill="#FFC857" />
      <circle cx="283" cy="99" r="2" fill="#FFC857" />
      <circle cx="305" cy="99" r="2" fill="#FFC857" />
      </>
    ),
  },
  montagne: {
    label: "Montagne : sommets, rivière et tipi",
    tinted: false,
    art: () => (
      <>
      <rect width="350" height="110" fill="#9FD3E6" />
      <circle cx="292" cy="28" r="17" fill="#FFC857" />
      <path d="M18 24a8 8 0 0 1 14-5a6 6 0 0 1 10 5z" fill="#FFFFFF" />
      <path d="M-10 110L70 38l80 72z" fill="#2F7393" />
      <path d="M90 110l80-82 80 82z" fill="#235C77" />
      <path d="M170 28l15 16-9-3-6 6-6-6-9 3z" fill="#F6EBDD" />
      <path d="M200 110l80-54 80 54z" fill="#2F7393" />
      <path d="M0 92Q90 80 180 90T350 88V110H0Z" fill="#A7D08F" />
      <path d="M120 110q30-14 70-12t70 12z" fill="#DCEDF3" />
      <path d="M64 72l14 24H50z" fill="#D45A43" stroke="#3A211B" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M64 84l6 12h-12z" fill="#5E4558" />
      <path d="M60 68l8 8M68 68l-8 8" stroke="#3A211B" strokeWidth="1.5" />
      <path d="M30 76l8 14H22z" fill="#3E6630" />
      <path d="M300 78l9 16h-18z" fill="#3E6630" />
      <path d="M318 84l7 12h-14z" fill="#4C7A3C" />
      </>
    ),
  },
  plage: {
    label: "Plage : parasol, mer et soleil",
    tinted: false,
    art: () => (
      <>
      <rect width="350" height="110" fill="#BFE0EC" />
      <circle cx="60" cy="30" r="17" fill="#FFC857" />
      <rect y="58" width="350" height="26" fill="#4C9DBF" />
      <path d="M0 64q15-5 30 0t30 0 30 0 30 0 30 0 30 0 30 0 30 0 30 0 30 0 30 0 30 0" fill="none" stroke="#FFFFFF" strokeWidth="2" opacity=".7" />
      <path d="M0 80Q100 70 200 78T350 76V110H0Z" fill="#FBEACB" />
      <path d="M250 46v58" stroke="#6B5047" strokeWidth="3" />
      <path d="M206 50Q250 14 294 50Z" fill="#C8492C" />
      <path d="M228 50Q240 22 250 22Q238 34 236 50Z" fill="#FFFFFF" />
      <path d="M264 50Q262 34 250 22Q262 24 278 50Z" fill="#FFFFFF" />
      <circle cx="150" cy="94" r="10" fill="#F28C38" />
      <path d="M140 94h20M150 84q6 10 0 20" fill="none" stroke="#FFFFFF" strokeWidth="2" />
      <path d="M300 96l4 12h14l4-12z" fill="#5E4558" />
      <path d="M302 96q9-10 18 0" fill="none" stroke="#5E4558" strokeWidth="2" />
      <path d="M100 40q4-4 8 0q4-4 8 0" fill="none" stroke="#3A211B" strokeWidth="1.5" />
      </>
    ),
  },
  gouter: {
    label: "Goûter : pile de crêpes, tasse et confiture",
    tinted: true,
    art: ({ bg, mid }) => (
      <>
      <rect width="350" height="110" fill={bg} />
      <circle cx="300" cy="10" r="48" fill={mid} />
      <rect y="90" width="350" height="20" fill={mid} />
      <rect x="58" y="54" width="30" height="36" rx="6" fill="#C8492C" />
      <rect x="56" y="48" width="34" height="9" rx="3" fill="#3A211B" />
      <rect x="64" y="66" width="18" height="12" rx="2" fill="#FBF3E7" />
      <ellipse cx="160" cy="90" rx="52" ry="7" fill="#FFFFFF" />
      <rect x="120" y="78" width="80" height="8" rx="4" fill="#F6DAA3" />
      <rect x="122" y="70" width="76" height="8" rx="4" fill="#F2C76B" />
      <rect x="118" y="62" width="82" height="8" rx="4" fill="#F6DAA3" />
      <rect x="122" y="54" width="76" height="8" rx="4" fill="#F2C76B" />
      <path d="M132 54q4 10 8 0q4 14 10 0q4 8 8 0q4 12 10 0" fill="#C8492C" />
      <path d="M152 46q4-6 8 0q-4 4-8 0z" fill="#C8492C" />
      <path d="M156 44q0-4 3-6" stroke="#3E6630" strokeWidth="2" fill="none" />
      <rect x="232" y="58" width="36" height="32" rx="7" fill="#F28C38" />
      <path d="M268 66q12 0 10 10t-10 8" fill="none" stroke="#F28C38" strokeWidth="5" />
      <path d="M240 50q-4-8 2-14M252 50q-4-8 2-14" fill="none" stroke="#FFFFFF" strokeWidth="2.4" strokeLinecap="round" opacity=".85" />
      <circle cx="300" cy="84" r="5" fill="#C8492C" />
      <circle cx="312" cy="86" r="5" fill="#C8492C" />
      </>
    ),
  },
  pluie: {
    label: "Jour de pluie : fenêtre mouillée et cabane de couvertures",
    tinted: true,
    art: ({ bg, mid }) => (
      <>
      <rect width="350" height="110" fill={bg} />
      <rect y="96" width="350" height="14" fill={mid} />
      <rect x="40" y="14" width="100" height="78" rx="8" fill="#9FD3E6" stroke="#FFFFFF" strokeWidth="6" />
      <path d="M90 14v78M40 53h100" stroke="#FFFFFF" strokeWidth="4" />
      <path d="M52 22l-6 14M70 26l-6 14M110 20l-6 14M126 30l-6 14M60 60l-6 14M100 62l-6 14M120 66l-6 14" stroke="#FFFFFF" strokeWidth="2" strokeLinecap="round" opacity=".8" />
      <path d="M162 12q60 14 170 0" fill="none" stroke="#6B5047" strokeWidth="1.2" />
      <circle cx="180" cy="15" r="3" fill="#FFC857" />
      <circle cx="210" cy="18" r="3" fill="#F28C38" />
      <circle cx="240" cy="19" r="3" fill="#FFC857" />
      <circle cx="270" cy="18" r="3" fill="#C8492C" />
      <circle cx="300" cy="16" r="3" fill="#FFC857" />
      <path d="M240 30l58 70H182z" fill="#F28C38" />
      <path d="M240 30l-20 70h-14zM240 30l20 70h14z" fill="#F5A66E" />
      <path d="M240 62l18 38h-36z" fill="#5E4558" />
      <path d="M240 30l-4-8M240 30l4-8" stroke="#6B5047" strokeWidth="2.4" strokeLinecap="round" />
      <ellipse cx="320" cy="96" rx="16" ry="7" fill="#C8492C" />
      <ellipse cx="168" cy="98" rx="14" ry="6" fill="#2F7393" />
      </>
    ),
  },
  automne: {
    label: "Automne : courges et feuilles qui tombent",
    tinted: true,
    art: ({ bg, mid }) => (
      <>
      <rect width="350" height="110" fill={bg} />
      <circle cx="40" cy="14" r="44" fill={mid} />
      <rect y="94" width="350" height="16" fill={mid} />
      <ellipse cx="120" cy="82" rx="28" ry="20" fill="#F28C38" />
      <path d="M108 64q-6 18 0 36M132 64q6 18 0 36M120 62v40" fill="none" stroke="#E07428" strokeWidth="2" />
      <rect x="117" y="56" width="6" height="9" rx="2" fill="#3E6630" />
      <ellipse cx="168" cy="90" rx="17" ry="12" fill="#FFC857" />
      <path d="M168 78v24" stroke="#E0A93C" strokeWidth="2" />
      <rect x="166" y="74" width="4" height="6" rx="1.5" fill="#3E6630" />
      <ellipse cx="250" cy="78" rx="34" ry="25" fill="#C8492C" />
      <path d="M234 56q-8 22 0 44M266 56q8 22 0 44M250 53v50" fill="none" stroke="#A03A22" strokeWidth="2" />
      <path d="M248 54q-2-10 6-14" fill="none" stroke="#3E6630" strokeWidth="4" strokeLinecap="round" />
      <path d="M256 46q10-4 12 4q-8 2-12-4z" fill="#4C7A3C" />
      <path d="M60 30q8-8 14 0q-6 8-14 0z" fill="#A04812" transform="rotate(20 67 30)" />
      <path d="M200 24q8-8 14 0q-6 8-14 0z" fill="#FFC857" transform="rotate(-30 207 24)" />
      <path d="M300 40q8-8 14 0q-6 8-14 0z" fill="#9A3420" transform="rotate(40 307 40)" />
      <path d="M150 36q6-6 11 0q-5 6-11 0z" fill="#F28C38" transform="rotate(-10 155 36)" />
      <path d="M320 14q6-6 11 0q-5 6-11 0z" fill="#A04812" />
      </>
    ),
  },
};
