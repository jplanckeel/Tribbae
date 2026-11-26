import { useRef, useState, type ReactNode } from "react";
import Icon, { type IconName } from "./Icon";
import { Avatar } from "./ui";
import { categoryMeta } from "../lib/categories";
import { DEMO_TRIBE, EXPLORER, FOLDERS, IDEAS, NEW_IDEA, type Idea, type Ingredient, type Member } from "../lib/demo";
import { plural } from "../lib/format";

/*
 * Aperçu interactif de l'appli sur l'accueil visiteur : un téléphone où l'on
 * navigue (dossiers, fiches, Explorer, courses, tribu) avec une famille
 * fictive. Tout est local : aucune donnée n'est lue ni envoyée au serveur.
 */

type Tab = "home" | "explorer" | "shopping" | "tribe";
type Screen = { kind: "folder"; id: string } | { kind: "idea"; id: string };

export default function DemoPhone() {
  const [tab, setTab] = useState<Tab>("home");
  const [stack, setStack] = useState<Screen[]>([]);
  const [done, setDone] = useState<Record<string, string>>({});
  const [liked, setLiked] = useState<Set<string>>(new Set());
  const [menu, setMenu] = useState<Set<string>>(new Set(["crepes", "gratin"]));
  const [checked, setChecked] = useState<Set<string>>(new Set(["Lait"]));
  const [added, setAdded] = useState(false);
  const [adding, setAdding] = useState(false);
  const [toast, setToast] = useState<{ text: string; id: number } | null>(null);
  const [touched, setTouched] = useState(false);

  const ideas = added ? [NEW_IDEA, ...IDEAS] : IDEAS;
  const idea = (id: string) => ideas.find((i) => i.id === id)!;
  const top = stack[stack.length - 1];
  const open = (s: Screen) => { setTouched(true); setToast(null); setStack([...stack, s]); };
  const back = () => { setToast(null); setStack(stack.slice(0, -1)); };
  const go = (t: Tab) => { setTouched(true); setToast(null); setTab(t); setStack([]); setAdding(false); };
  const toastId = useRef(0);
  const say = (text: string) => { toastId.current += 1; setToast({ text, id: toastId.current }); };
  const toggle = <T,>(set: Set<T>, v: T) => { const n = new Set(set); if (n.has(v)) n.delete(v); else n.add(v); return n; };

  let screen: ReactNode;
  let title = "";
  if (top?.kind === "idea") {
    const i = idea(top.id);
    title = categoryMeta(i.category).label;
    screen = (
      <IdeaScreen
        idea={i}
        done={i.done ?? done[i.id]}
        liked={liked.has(i.id)}
        inMenu={menu.has(i.id)}
        onDone={() => { setDone({ ...done, [i.id]: done[i.id] ? "" : "Fait aujourd'hui" }); if (!done[i.id]) say("Bravo ! Gardé en souvenir"); }}
        onLike={() => setLiked(toggle(liked, i.id))}
        onMenu={() => { setMenu(toggle(menu, i.id)); say(menu.has(i.id) ? "Retirée du menu" : "Au menu : ingrédients ajoutés aux courses"); }}
        onReserve={() => say("Paul l'a réservé, sans compte : la surprise reste entière")}
      />
    );
  } else if (top?.kind === "folder") {
    const f = FOLDERS.find((x) => x.id === top.id)!;
    title = f.name;
    screen = <FolderScreen folder={f} ideas={ideas.filter((i) => i.folder === f.id)} done={done} onOpen={(id) => open({ kind: "idea", id })} />;
  } else if (tab === "home") {
    screen = <HomeScreen ideas={ideas} onFolder={(id) => open({ kind: "folder", id })} onIdea={(id) => open({ kind: "idea", id })} />;
  } else if (tab === "explorer") {
    screen = <ExplorerScreen ideas={EXPLORER.map(idea)} liked={liked} onLike={(id) => setLiked(toggle(liked, id))} onOpen={(id) => open({ kind: "idea", id })} />;
  } else if (tab === "shopping") {
    screen = <ShoppingScreen recipes={[...menu].map(idea)} checked={checked} onCheck={(n) => setChecked(toggle(checked, n))} onRecipe={(id) => open({ kind: "idea", id })} onExplore={() => go("home")} />;
  } else {
    screen = <TribeScreen onInvite={() => say("Créez votre tribu pour inviter vos proches")} />;
  }

  return (
    <div className="relative mx-auto flex w-full max-w-[340px] flex-col items-center gap-4">
      <span aria-hidden="true" className="absolute -inset-8 rounded-full bg-[radial-gradient(closest-side,var(--color-soleil),transparent)]" />
      <section
        aria-label="Aperçu de l'application Tribbae, avec une famille fictive"
        className="relative flex aspect-[9/18.5] max-h-[680px] w-full flex-col overflow-hidden rounded-[46px] border-[10px] border-(--phone-frame) bg-sable shadow-[0_24px_50px_rgba(58,33,27,0.25)]"
      >
        {/* Barre d'état */}
        <div aria-hidden="true" className="relative flex h-9 shrink-0 items-center justify-between px-6 text-[11px] font-bold">
          <span>9:41</span>
          <span className="absolute top-2 left-1/2 h-5 w-24 -translate-x-1/2 rounded-full bg-(--phone-frame)" />
          <span className="flex items-center gap-1"><Icon name="cloud" size={12} />100 %</span>
        </div>
        {top && (
          <div className="flex h-10 shrink-0 items-center gap-1 px-2">
            <button type="button" onClick={back} aria-label="Retour" className="flex h-9 w-9 items-center justify-center rounded-full text-ecorce hover:bg-sable-2">
              <Icon name="chevronLeft" size={20} />
            </button>
            <span className="truncate font-display text-[15px] font-extrabold">{title}</span>
          </div>
        )}
        <div key={`${tab}-${stack.length}`} className="min-h-0 flex-1 animate-fade-in overflow-y-auto overscroll-contain px-4 pb-24">
          {screen}
        </div>

        {/* Ajouter une idée */}
        {adding && (
          <div className="absolute inset-0 z-20 flex flex-col justify-end bg-black/30" onClick={() => setAdding(false)}>
            <div className="animate-sheet-up rounded-t-[26px] bg-card p-4 pb-6" onClick={(e) => e.stopPropagation()}>
              <span className="mx-auto mb-3 block h-1 w-10 rounded-full bg-line" />
              <span className="font-display text-[17px] font-extrabold">Ajouter une idée</span>
              <div className="mt-3 flex items-center gap-2 rounded-medium bg-sable px-3 py-2.5 text-[12px] text-sec">
                <Icon name="link" size={15} />
                <span className="truncate">instagram.com/p/pique-nique-tete-dor</span>
              </div>
              <div className="mt-2 flex gap-1.5 text-[11px] font-bold">
                <span className="rounded-full bg-sortie-bg px-2.5 py-1 text-sortie-ink">Sortie</span>
                <span className="rounded-full bg-sable-2 px-2.5 py-1 text-sec">Dossier · Vacances d'été</span>
              </div>
              <button
                type="button"
                disabled={added}
                onClick={() => { setAdded(true); setAdding(false); setTab("home"); setStack([{ kind: "folder", id: "ete" }]); say("Idée ajoutée : toute la tribu la voit"); }}
                className="mt-4 flex h-11 w-full items-center justify-center rounded-medium bg-terracotta text-[14px] font-bold text-white disabled:opacity-50"
              >
                {added ? "Déjà ajoutée" : "Ajouter à la tribu"}
              </button>
            </div>
          </div>
        )}

        {toast && (
          <div key={toast.id} role="status" onAnimationEnd={() => { const id = toast.id; setTimeout(() => setToast((t) => (t?.id === id ? null : t)), 1800); }} className="absolute inset-x-4 bottom-[86px] z-30 animate-pop rounded-medium bg-ecorce px-3.5 py-2.5 text-[12px] font-semibold text-sable shadow-nav">
            {toast.text}
          </div>
        )}

        {/* Barre de navigation */}
        <nav aria-label="Navigation de l'aperçu" className="absolute inset-x-2.5 bottom-2.5 z-10 flex h-[60px] items-center rounded-[22px] bg-ecorce px-1">
          <NavTab icon="home" label="Accueil" on={tab === "home"} onClick={() => go("home")} />
          <NavTab icon="compass" label="Explorer" on={tab === "explorer"} onClick={() => go("explorer")} />
          <button type="button" onClick={() => { setTouched(true); setAdding(true); }} aria-label="Ajouter une idée" className="-mt-7 flex h-14 w-14 shrink-0 rotate-45 items-center justify-center rounded-[18px] border-4 border-sable bg-terracotta text-white">
            <Icon name="plus" size={24} strokeWidth={2.6} className="-rotate-45" />
          </button>
          <NavTab icon="cart" label="Courses" on={tab === "shopping"} onClick={() => go("shopping")} />
          <NavTab icon="tribe" label="Tribu" on={tab === "tribe"} onClick={() => go("tribe")} />
        </nav>
      </section>
      <span className={`relative font-hand text-[20px] text-terracotta transition-opacity ${touched ? "opacity-60" : ""}`}>
        {touched ? "Une famille fictive, rien n'est enregistré" : "↑ Touchez l'écran, c'est un aperçu à essayer"}
      </span>
    </div>
  );
}

function NavTab({ icon, label, on, onClick }: { icon: IconName; label: string; on: boolean; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} aria-current={on ? "page" : undefined} className={`flex flex-1 flex-col items-center gap-0.5 text-[10px] font-bold ${on ? "text-sable" : "text-bark-mute"}`}>
      <span className={`flex h-7 w-11 items-center justify-center rounded-full ${on ? "bg-sable text-ecorce" : ""}`}>
        <Icon name={icon} size={17} />
      </span>
      {label}
    </button>
  );
}

function Visual({ idea, size }: { idea: Idea; size: number }) {
  const cat = categoryMeta(idea.category);
  return (
    <span className={`flex shrink-0 items-center justify-center rounded-small ${cat.bg} ${cat.ic}`} style={{ width: size, height: size }}>
      <Icon name={cat.icon} size={Math.round(size * 0.42)} />
    </span>
  );
}

function By({ by }: { by: Member | string }) {
  if (typeof by === "string") return <span className="truncate text-sec">{by}</span>;
  return (
    <span className="flex min-w-0 items-center gap-1 text-sec">
      <Avatar name={by.name} color={by.color} ink={by.ink} size={14} />
      <span className="truncate">{by.name}</span>
    </span>
  );
}

function IdeaRow({ idea, done, onOpen }: { idea: Idea; done?: string; onOpen: () => void }) {
  const cat = categoryMeta(idea.category);
  return (
    <button type="button" onClick={onOpen} className="flex w-full items-center gap-3 rounded-medium bg-card p-2 text-left shadow-card active:scale-[0.99]">
      <Visual idea={idea} size={46} />
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="line-clamp-1 text-[13px] leading-tight font-bold">{idea.title}</span>
        <span className="flex items-center gap-1.5 text-[11px]">
          <span className={`shrink-0 font-bold ${cat.ink}`}>{cat.label}</span>
          <span className="text-muted">·</span>
          <By by={idea.by} />
        </span>
      </span>
      {done && (
        <span className="flex shrink-0 items-center gap-1 rounded-full bg-done-bg px-2 py-0.5 text-[10px] font-bold text-done">
          <Icon name="check" size={11} strokeWidth={3} />Fait
        </span>
      )}
    </button>
  );
}

function HomeScreen({ ideas, onFolder, onIdea }: { ideas: Idea[]; onFolder: (id: string) => void; onIdea: (id: string) => void }) {
  return (
    <div className="flex flex-col gap-4 pt-1">
      <div>
        <span className="font-hand text-[17px] text-terracotta">Bonjour Léa</span>
        <h3 className="font-display text-[22px] leading-tight font-extrabold">Les idées de la tribu</h3>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {FOLDERS.map((f) => (
          <button key={f.id} type="button" onClick={() => onFolder(f.id)} className="flex flex-col gap-2 rounded-medium bg-card p-3 text-left shadow-card active:scale-[0.98]">
            <span className={`flex h-9 w-9 items-center justify-center rounded-small ${f.tone}`}><Icon name={f.icon} size={18} /></span>
            <span className="text-[13px] leading-tight font-bold">{f.name}</span>
            <span className="text-[11px] text-sec">{plural(ideas.filter((i) => i.folder === f.id).length, "idée", "idées")}</span>
          </button>
        ))}
        <div className="flex flex-col items-center justify-center gap-1 rounded-medium border-2 border-dashed border-dash p-3 text-center text-[11px] font-bold text-sec">
          <Icon name="plus" size={16} />Nouveau dossier
        </div>
      </div>
      <div className="flex flex-col gap-2">
        <span className="text-[11px] font-extrabold tracking-[1.5px] text-muted uppercase">Ajoutées récemment</span>
        {[ideas[0], ideas.find((i) => i.id === "prince")!, ideas.find((i) => i.id === "crepes")!].map((i) => (
          <IdeaRow key={i.id} idea={i} onOpen={() => onIdea(i.id)} />
        ))}
      </div>
    </div>
  );
}

function FolderScreen({ folder, ideas, done, onOpen }: { folder: (typeof FOLDERS)[number]; ideas: Idea[]; done: Record<string, string>; onOpen: (id: string) => void }) {
  const cats = [...new Set(ideas.map((i) => i.category))];
  const [filter, setFilter] = useState("");
  const shown = filter ? ideas.filter((i) => i.category === filter) : ideas;
  return (
    <div className="flex flex-col gap-3 pt-1">
      <div className="flex items-center justify-between gap-2">
        <span className={`flex h-11 w-11 items-center justify-center rounded-small ${folder.tone}`}><Icon name={folder.icon} size={22} /></span>
        <span className="flex flex-1 flex-col">
          <span className="text-[12px] text-sec">{plural(ideas.length, "idée", "idées")} · partagé avec la tribu</span>
        </span>
        <span className="flex -space-x-1.5">
          {DEMO_TRIBE.slice(0, 3).map((m) => <Avatar key={m.name} name={m.name} color={m.color} ink={m.ink} size={22} className="ring-2 ring-sable" />)}
        </span>
      </div>
      {cats.length > 1 && (
        <div className="flex flex-wrap gap-1.5">
          {["", ...cats].map((c) => (
            <button key={c} type="button" onClick={() => setFilter(c)} className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${filter === c ? "bg-ecorce text-sable" : "bg-card text-sec"}`}>
              {c ? categoryMeta(c).plural : "Toutes"}
            </button>
          ))}
        </div>
      )}
      {shown.map((i) => <IdeaRow key={i.id} idea={i} done={i.done ?? done[i.id]} onOpen={() => onOpen(i.id)} />)}
    </div>
  );
}

function IdeaScreen({ idea, done, liked, inMenu, onDone, onLike, onMenu, onReserve }: {
  idea: Idea; done?: string; liked: boolean; inMenu: boolean; onDone: () => void; onLike: () => void; onMenu: () => void; onReserve: () => void;
}) {
  const cat = categoryMeta(idea.category);
  return (
    <div className="flex flex-col gap-3">
      <div className={`relative flex h-36 items-center justify-center rounded-large ${cat.bg} ${cat.ic}`}>
        <Icon name={cat.icon} size={54} />
        <button type="button" onClick={onLike} aria-pressed={liked} aria-label="J'aime" className="absolute top-2.5 right-2.5 flex h-9 items-center gap-1 rounded-full bg-card px-3 text-[12px] font-bold text-ecorce">
          <Icon name="heart" size={15} filled={liked} className={liked ? "text-terracotta" : ""} />{idea.likes + (liked ? 1 : 0)}
        </button>
      </div>
      <h3 className="font-display text-[19px] leading-tight font-extrabold">{idea.title}</h3>
      <div className="flex items-center gap-1.5 text-[12px]">
        <span className="text-sec">Ajoutée par</span><By by={idea.by} />
      </div>
      <div className="flex flex-wrap gap-1.5 text-[11px] font-bold">
        <span className={`rounded-full px-2.5 py-1 ${cat.bg} ${cat.ink}`}>{cat.label}</span>
        {idea.tags.map((t) => <span key={t} className="rounded-full bg-sable-2 px-2.5 py-1 text-sec">{t}</span>)}
      </div>
      <p className="text-[13px] leading-relaxed text-ecorce-2">{idea.text}</p>
      {idea.ingredients && (
        <div className="rounded-medium bg-card p-3">
          <span className="text-[11px] font-extrabold tracking-[1.5px] text-muted uppercase">Ingrédients</span>
          {idea.ingredients.map((g) => (
            <div key={g.name} className="flex justify-between py-0.5 text-[12px]"><span>{g.name}</span><span className="text-sec">{g.qty}</span></div>
          ))}
        </div>
      )}
      {idea.comment && (
        <div className="flex gap-2 rounded-medium bg-card p-3 text-[12px]">
          <Avatar name={idea.comment.by.name} color={idea.comment.by.color} ink={idea.comment.by.ink} size={22} />
          <span><b>{idea.comment.by.name}</b> <span className="text-ecorce-2">{idea.comment.text}</span></span>
        </div>
      )}
      {idea.category === "LINK_CATEGORY_CADEAU" ? (
        idea.reserved ? (
          <span className="flex h-11 items-center justify-center gap-1.5 rounded-medium bg-sable-2 text-[13px] font-bold text-sec"><Icon name="lock" size={14} />Réservé · on ne dit pas par qui</span>
        ) : (
          <button type="button" onClick={onReserve} className="h-11 rounded-medium bg-terracotta text-[13px] font-bold text-white">Je réserve ce cadeau</button>
        )
      ) : idea.ingredients ? (
        <button type="button" onClick={onMenu} className={`h-11 rounded-medium text-[13px] font-bold ${inMenu ? "bg-done-bg text-done" : "bg-terracotta text-white"}`}>
          {inMenu ? "✓ Au menu · dans les courses" : "Mettre au menu"}
        </button>
      ) : (
        <button type="button" onClick={onDone} disabled={!!idea.done} className={`flex h-11 items-center justify-center gap-1.5 rounded-medium text-[13px] font-bold ${done ? "bg-done-bg text-done" : "bg-terracotta text-white"}`}>
          {done ? <><Icon name="check" size={15} strokeWidth={3} />{done}</> : "C'est fait !"}
        </button>
      )}
    </div>
  );
}

function ExplorerScreen({ ideas, liked, onLike, onOpen }: { ideas: Idea[]; liked: Set<string>; onLike: (id: string) => void; onOpen: (id: string) => void }) {
  return (
    <div className="flex flex-col gap-3 pt-1">
      <div>
        <span className="font-hand text-[17px] text-terracotta">Les idées des autres familles</span>
        <h3 className="font-display text-[22px] leading-tight font-extrabold">Explorer</h3>
      </div>
      <div className="flex items-center gap-2 rounded-medium bg-card px-3 py-2.5 text-[12px] text-muted"><Icon name="search" size={15} />Une sortie, une recette, un âge…</div>
      <div className="grid grid-cols-2 gap-2">
        {ideas.map((i) => {
          const cat = categoryMeta(i.category);
          return (
            <div key={i.id} className="relative flex flex-col overflow-hidden rounded-medium bg-card shadow-card">
              <button type="button" onClick={() => onOpen(i.id)} className="flex flex-col text-left">
                <span className={`flex h-20 items-center justify-center ${cat.bg} ${cat.ic}`}><Icon name={cat.icon} size={30} /></span>
                <span className="flex flex-col gap-0.5 p-2">
                  <span className="line-clamp-2 text-[12px] leading-tight font-bold">{i.title}</span>
                  <span className={`text-[10px] font-bold ${cat.ink}`}>{cat.label}</span>
                </span>
              </button>
              <button type="button" onClick={() => onLike(i.id)} aria-pressed={liked.has(i.id)} aria-label="J'aime" className="absolute top-1.5 right-1.5 flex h-7 items-center gap-1 rounded-full bg-card px-2 text-[10px] font-bold">
                <Icon name="heart" size={12} filled={liked.has(i.id)} className={liked.has(i.id) ? "text-terracotta" : ""} />{i.likes + (liked.has(i.id) ? 1 : 0)}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function ShoppingScreen({ recipes, checked, onCheck, onRecipe, onExplore }: {
  recipes: Idea[]; checked: Set<string>; onCheck: (name: string) => void; onRecipe: (id: string) => void; onExplore: () => void;
}) {
  // Ingrédients des recettes au menu, regroupés par produit puis par rayon
  const items = new Map<string, Ingredient & { n: number }>();
  for (const r of recipes) for (const g of r.ingredients ?? []) {
    const cur = items.get(g.name);
    items.set(g.name, cur ? { ...cur, n: cur.n + 1, qty: addQty(cur.qty, g.qty) } : { ...g, n: 1 });
  }
  const aisles = [...new Set([...items.values()].map((g) => g.aisle))].sort((a, b) => a.localeCompare(b, "fr"));
  const left = [...items.keys()].filter((k) => !checked.has(k)).length;
  return (
    <div className="flex flex-col gap-3 pt-1">
      <div>
        <span className="font-hand text-[17px] text-terracotta">Partagée avec la tribu</span>
        <h3 className="font-display text-[22px] leading-tight font-extrabold">Les courses</h3>
        <span className="text-[12px] text-sec">{plural(left, "article", "articles")} à prendre</span>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {recipes.map((r) => (
          <button key={r.id} type="button" onClick={() => onRecipe(r.id)} className="rounded-full bg-recette-bg px-2.5 py-1 text-[11px] font-bold text-recette-ink">{r.title}</button>
        ))}
      </div>
      {!recipes.length && (
        <button type="button" onClick={onExplore} className="rounded-medium bg-card p-3 text-left text-[12px] text-sec">
          Le menu est vide. Ouvrez « Recettes du dimanche » et mettez une recette au menu.
        </button>
      )}
      {aisles.map((a) => (
        <div key={a} className="rounded-medium bg-card px-3 py-2">
          <span className="text-[10px] font-extrabold tracking-[1.5px] text-muted uppercase">{a}</span>
          {[...items.values()].filter((g) => g.aisle === a).map((g) => {
            const on = checked.has(g.name);
            return (
              <button key={g.name} type="button" onClick={() => onCheck(g.name)} aria-pressed={on} className="flex w-full items-center gap-2.5 py-1.5 text-left text-[13px]">
                <span className={`flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-[6px] ${on ? "bg-done-solid text-white" : "border-2 border-dash"}`}>
                  {on && <Icon name="check" size={12} strokeWidth={3} />}
                </span>
                <span className={`flex-1 font-semibold ${on ? "text-muted line-through" : ""}`}>{g.name}</span>
                <span className="text-[11px] text-sec">{g.qty}</span>
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}

/** « 50 cl » + « 40 cl » → « 90 cl » ; unités différentes : les deux. */
function addQty(a: string, b: string): string {
  const parse = (q: string) => /^(\d+(?:,\d+)?)\s*(.*)$/.exec(q);
  const x = parse(a), y = parse(b);
  if (!x || !y || x[2] !== y[2]) return `${a} + ${b}`;
  const n = Number(x[1].replace(",", ".")) + Number(y[1].replace(",", "."));
  return `${String(Math.round(n * 10) / 10).replace(".", ",")}${x[2] ? ` ${x[2]}` : ""}`;
}

function TribeScreen({ onInvite }: { onInvite: () => void }) {
  return (
    <div className="flex flex-col gap-3 pt-1">
      <div>
        <span className="font-hand text-[17px] text-terracotta">La tribu Martin</span>
        <h3 className="font-display text-[22px] leading-tight font-extrabold">Ma tribu</h3>
        <span className="text-[12px] text-sec">Tout le monde voit les mêmes idées</span>
      </div>
      {DEMO_TRIBE.map((m) => (
        <div key={m.name} className="flex items-center gap-3 rounded-medium bg-card p-2.5">
          <Avatar name={m.name} color={m.color} ink={m.ink} size={36} />
          <span className="flex flex-col">
            <span className="text-[13px] font-bold">{m.name}</span>
            <span className="text-[11px] text-sec">{m.role}</span>
          </span>
        </div>
      ))}
      <button type="button" onClick={onInvite} className="flex h-11 items-center justify-center gap-1.5 rounded-medium border-2 border-dashed border-dash text-[13px] font-bold text-sec">
        <Icon name="personPlus" size={16} />Inviter un proche
      </button>
      <p className="text-[11px] leading-relaxed text-sec">Paul n'a pas l'appli : il reçoit les listes de cadeaux par lien et réserve sans compte.</p>
    </div>
  );
}
