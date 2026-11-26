import { useRef, useState, type ReactNode } from "react";
import { FolderCover, FolderGlyph } from "./FolderCover";
import Icon, { type IconName } from "./Icon";
import Logo from "./Logo";
import { Avatar } from "./ui";
import { CATEGORIES, categoryMeta, folderColor } from "../lib/categories";
import { DEMO_TRIBE, EVENT, EXPLORER, FOLDERS, IDEAS, NEW_IDEA, type DemoFolder, type Idea, type Ingredient, type Member } from "../lib/demo";
import { formatToday, plural } from "../lib/format";
import type { Folder } from "../lib/types";

/*
 * Aperçu interactif de l'appli sur l'accueil visiteur : les mêmes écrans et la
 * même barre que l'appli (Accueil, Explorer, +, Mes idées, Ma tribu ; les
 * courses sont dans Mes idées), avec une famille fictive. Tout est local :
 * rien n'est lu ni envoyé au serveur.
 */

type Tab = "home" | "explorer" | "ideas" | "tribe";
type Screen = { kind: "folder"; id: string } | { kind: "idea"; id: string } | { kind: "category"; key: string } | { kind: "shopping" };

const CARD = "rounded-[18px] bg-card shadow-card";

export default function DemoPhone() {
  const [tab, setTab] = useState<Tab>("home");
  const [stack, setStack] = useState<Screen[]>([]);
  const [done, setDone] = useState<Record<string, boolean>>({});
  const [liked, setLiked] = useState<Set<string>>(new Set());
  const [menu, setMenu] = useState<Set<string>>(new Set(["crepes", "gratin"]));
  const [checked, setChecked] = useState<Set<string>>(new Set(["Lait"]));
  const [added, setAdded] = useState(false);
  const [adding, setAdding] = useState(false);
  const [toast, setToast] = useState<{ text: string; id: number } | null>(null);
  const [touched, setTouched] = useState(false);
  const toastId = useRef(0);

  const ideas = added ? [NEW_IDEA, ...IDEAS] : IDEAS;
  const family = ideas.filter((i) => i.folder);
  const idea = (id: string) => ideas.find((i) => i.id === id)!;
  const top = stack[stack.length - 1];
  const open = (s: Screen) => { setTouched(true); setToast(null); setStack([...stack, s]); };
  const back = () => { setToast(null); setStack(stack.slice(0, -1)); };
  const go = (t: Tab) => { setTouched(true); setToast(null); setTab(t); setStack([]); setAdding(false); };
  const say = (text: string) => { toastId.current += 1; setToast({ text, id: toastId.current }); };
  const toggle = <T,>(set: Set<T>, v: T) => { const n = new Set(set); if (n.has(v)) n.delete(v); else n.add(v); return n; };
  const isDone = (i: Idea) => !!i.done || !!done[i.id];
  const openIdea = (id: string) => open({ kind: "idea", id });

  let screen: ReactNode;
  let title = "";
  if (top?.kind === "idea") {
    const i = idea(top.id);
    title = categoryMeta(i.category).label;
    screen = (
      <IdeaScreen
        idea={i}
        done={i.done ?? (done[i.id] ? "Fait aujourd'hui" : undefined)}
        liked={liked.has(i.id)}
        inMenu={menu.has(i.id)}
        onDone={() => { setDone({ ...done, [i.id]: !done[i.id] }); if (!done[i.id]) say("Bravo ! Gardé en souvenir"); }}
        onLike={() => setLiked(toggle(liked, i.id))}
        onMenu={() => { setMenu(toggle(menu, i.id)); say(menu.has(i.id) ? "Retirée du menu" : "Au menu : ingrédients ajoutés aux courses"); }}
        onReserve={() => say("Réservé, sans compte : la surprise reste entière")}
      />
    );
  } else if (top?.kind === "folder") {
    const f = FOLDERS.find((x) => x.id === top.id)!;
    title = f.name;
    screen = <FolderScreen folder={f} ideas={family.filter((i) => i.folder === f.id)} isDone={isDone} onOpen={openIdea} />;
  } else if (top?.kind === "category") {
    const c = categoryMeta(top.key);
    title = c.plural;
    screen = <ListScreen ideas={family.filter((i) => i.category === top.key)} isDone={isDone} onOpen={openIdea} empty={`Aucune ${c.label.toLowerCase()} pour l'instant`} />;
  } else if (top?.kind === "shopping") {
    title = "Les courses";
    screen = <ShoppingScreen recipes={[...menu].map(idea)} checked={checked} onCheck={(n) => setChecked(toggle(checked, n))} onRecipe={openIdea} onEmpty={() => open({ kind: "folder", id: "dimanche" })} />;
  } else if (tab === "home") {
    screen = (
      <HomeScreen
        family={family}
        onIdea={openIdea}
        onCategory={(key) => open({ kind: "category", key })}
        onSearch={() => go("explorer")}
        onAi={() => say("L'IA propose des idées selon l'âge des enfants")}
        onMyIdeas={() => go("ideas")}
        onExplorer={() => go("explorer")}
      />
    );
  } else if (tab === "explorer") {
    screen = <ExplorerScreen ideas={EXPLORER.map(idea)} liked={liked} onLike={(id) => setLiked(toggle(liked, id))} onOpen={openIdea} />;
  } else if (tab === "ideas") {
    screen = (
      <MyIdeasScreen
        family={family}
        liked={[...liked].map(idea)}
        menuCount={menu.size}
        toBuy={shoppingItems([...menu].map(idea)).filter((g) => !checked.has(g.name)).length}
        isDone={isDone}
        onShopping={() => open({ kind: "shopping" })}
        onFolder={(id) => open({ kind: "folder", id })}
        onIdea={openIdea}
        onNewFolder={() => say("Créez votre tribu pour ranger vos idées en dossiers")}
      />
    );
  } else {
    screen = <TribeScreen family={family} onInvite={() => say("Créez votre tribu pour inviter vos proches")} />;
  }

  return (
    <div className="relative mx-auto flex w-full max-w-[340px] flex-col items-center gap-4">
      <span aria-hidden="true" className="absolute -inset-8 rounded-full bg-[radial-gradient(closest-side,var(--color-soleil),transparent)]" />
      <section
        aria-label="Aperçu de l'application Tribbae, avec une famille fictive"
        className="relative flex aspect-[9/18.5] max-h-[680px] w-full flex-col overflow-clip rounded-[46px] border-[10px] border-(--phone-frame) bg-sable shadow-[0_24px_50px_rgba(58,33,27,0.25)]"
      >
        {/* Barre d'état */}
        <div aria-hidden="true" className="relative z-10 flex h-9 shrink-0 items-center justify-between px-6 text-[11px] font-bold">
          <span>9:41</span>
          <span className="absolute top-2 left-1/2 h-5 w-24 -translate-x-1/2 rounded-full bg-(--phone-frame)" />
          <span className="flex items-center gap-1"><Icon name="cloud" size={12} />100 %</span>
        </div>
        {top && (
          <div className="relative z-10 flex h-11 shrink-0 items-center gap-1 px-3">
            <button type="button" onClick={back} aria-label="Retour" className="flex h-9 w-9 items-center justify-center rounded-full bg-card text-ecorce shadow-soft">
              <Icon name="chevronLeft" size={20} />
            </button>
            <span className="ml-1 truncate font-display text-[16px] font-extrabold">{title}</span>
          </div>
        )}
        <div key={`${tab}-${stack.length}`} className="relative min-h-0 flex-1 animate-fade-in overflow-x-hidden overflow-y-auto overscroll-contain px-4 pb-24">
          {screen}
        </div>

        {/* Ajouter une idée */}
        {adding && (
          <div className="absolute inset-0 z-30 flex flex-col justify-end bg-black/30" onClick={() => setAdding(false)}>
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
                onClick={() => { setAdded(true); setAdding(false); setTab("ideas"); setStack([{ kind: "folder", id: "ete" }]); say("Idée ajoutée : toute la tribu la voit"); }}
                className="mt-4 flex h-11 w-full items-center justify-center rounded-medium bg-terracotta text-[14px] font-bold text-white disabled:opacity-50"
              >
                {added ? "Déjà ajoutée" : "Ajouter à la tribu"}
              </button>
            </div>
          </div>
        )}

        {toast && (
          <div key={toast.id} role="status" onAnimationEnd={() => { const id = toast.id; setTimeout(() => setToast((t) => (t?.id === id ? null : t)), 1800); }} className="absolute inset-x-4 bottom-[90px] z-30 animate-pop rounded-medium bg-ecorce px-3.5 py-2.5 text-[12px] font-semibold text-sable shadow-nav">
            {toast.text}
          </div>
        )}

        {/* Barre de navigation : la même que l'appli */}
        <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-24 bg-gradient-to-b from-sable/0 to-sable/90" />
        <nav aria-label="Navigation de l'aperçu" className="nav-bar absolute inset-x-2.5 bottom-2.5 z-20 flex h-[62px] items-center rounded-[24px] bg-ecorce px-1 shadow-nav">
          <NavTab icon="home" label="Accueil" on={tab === "home"} onClick={() => go("home")} />
          <NavTab icon="compass" label="Explorer" on={tab === "explorer"} onClick={() => go("explorer")} />
          <div className="flex flex-1 justify-center">
            <button type="button" onClick={() => { setTouched(true); setAdding(true); }} aria-label="Ajouter une idée" className="nav-plus -mt-7 flex h-[52px] w-[52px] rotate-45 items-center justify-center rounded-[18px] border-4 border-sable bg-terracotta text-white active:scale-95">
              <Icon name="plus" size={22} strokeWidth={2.6} className="-rotate-45" />
            </button>
          </div>
          <NavTab icon="tipi" label="Mes idées" on={tab === "ideas"} onClick={() => go("ideas")} />
          <NavTab icon="tribe" label="Ma tribu" on={tab === "tribe"} onClick={() => go("tribe")} />
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
    <button type="button" onClick={onClick} aria-current={on ? "page" : undefined} className="flex h-full min-w-0 flex-1 flex-col items-center justify-center gap-1">
      <span className={`flex h-[26px] w-10 items-center justify-center rounded-full transition-colors ${on ? "bg-sable text-terracotta" : "text-bark-mute"}`}>
        <Icon name={icon} size={18} />
      </span>
      <span className={`truncate text-[9.5px] leading-none font-bold ${on ? "text-sable" : "text-bark-mute"}`}>{label}</span>
    </button>
  );
}

// ── Petits éléments, mêmes styles que les cartes de l'appli ──────────────────

function Visual({ idea, size, height, className = "" }: { idea: Idea; size?: number; height?: number; className?: string }) {
  const cat = categoryMeta(idea.category);
  return (
    <span className={`flex shrink-0 items-center justify-center ${cat.bg} ${cat.ic} ${className}`} style={{ width: size, height: height ?? size }}>
      <Icon name={cat.icon} size={Math.round((height ?? size ?? 48) * 0.42)} />
    </span>
  );
}

function byName(by: Member | string) {
  return typeof by === "string" ? by : by.name;
}

function RowCard({ idea, done, onOpen }: { idea: Idea; done?: boolean; onOpen: () => void }) {
  const cat = categoryMeta(idea.category);
  return (
    <button type="button" onClick={onOpen} className={`${CARD} flex w-full items-center gap-3 p-2 pr-3 text-left active:scale-[0.99]`}>
      <span className="relative">
        <Visual idea={idea} size={46} className="rounded-[13px]" />
        {done && (
          <span className="absolute -right-1 -bottom-1 flex h-[18px] w-[18px] items-center justify-center rounded-full border-2 border-card bg-done-solid text-white">
            <Icon name="check" size={10} strokeWidth={3.4} />
          </span>
        )}
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[13px] font-bold">{idea.title}</span>
        <span className="truncate text-[11px] text-sec">{[cat.label, idea.tags[0], byName(idea.by)].join(" · ")}</span>
      </span>
      <Icon name="chevronRight" size={16} className="text-muted" />
    </button>
  );
}

function SectionTitle({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <h4 className="font-display text-[17px] font-extrabold">{title}</h4>
      {action && <button type="button" onClick={onAction} className="shrink-0 text-[11px] font-semibold text-link">{action}</button>}
    </div>
  );
}

function Rail({ children }: { children: ReactNode }) {
  return <div className="-mx-4 flex gap-2.5 overflow-x-auto px-4 pb-1 [scrollbar-width:none]">{children}</div>;
}

// ── Accueil ──────────────────────────────────────────────────────────────────

function HomeScreen({ family, onIdea, onCategory, onSearch, onAi, onMyIdeas, onExplorer }: {
  family: Idea[]; onIdea: (id: string) => void; onCategory: (key: string) => void; onSearch: () => void; onAi: () => void; onMyIdeas: () => void; onExplorer: () => void;
}) {
  const community = EXPLORER.map((id) => IDEAS.find((i) => i.id === id)!).filter((i) => !i.folder);
  const event = IDEAS.find((i) => i.id === EVENT.id)!;
  return (
    <div className="relative flex flex-col gap-4">
      {/* Motif du logo : grand soleil */}
      <span aria-hidden="true" className="pointer-events-none absolute -top-[150px] -right-[110px] h-[240px] w-[240px] rounded-full bg-soleil" />
      <div className="relative flex items-center justify-between">
        <Logo size={26} />
        <span className="relative flex h-9 w-9 items-center justify-center rounded-full bg-card text-ecorce shadow-soft">
          <Icon name="bell" size={17} />
          <span className="absolute top-2 right-[9px] h-1.5 w-1.5 rounded-full bg-terracotta" />
        </span>
      </div>
      <div className="relative flex flex-col gap-0.5">
        <span className="text-[12px] text-sec">{formatToday()}</span>
        <h3 className="font-display text-[23px] leading-[1.05] font-extrabold tracking-[-0.5px]">
          La tribu Martin,<br />on fait quoi ce week-end ?
        </h3>
      </div>
      <div className="relative flex gap-2">
        <button type="button" onClick={onSearch} className="flex h-11 min-w-0 flex-1 items-center gap-2 rounded-medium bg-card px-3 text-left text-[12px] text-muted shadow-card">
          <Icon name="search" size={16} /><span className="truncate">Une sortie, une recette, un cadeau…</span>
        </button>
        <button type="button" onClick={onAi} aria-label="Générer des idées avec l'IA" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-medium bg-prune text-soleil">
          <Icon name="sparkles" size={19} />
        </button>
      </div>
      <button type="button" onClick={() => onIdea(event.id)} className="ecorce-panel flex items-center gap-3 rounded-large bg-ecorce p-3 text-left">
        <span className="flex h-14 w-[50px] shrink-0 flex-col items-center justify-center rounded-[14px] bg-braise text-ecorce">
          <span className="text-[9px] font-bold tracking-[0.6px] uppercase">{EVENT.weekday}</span>
          <span className="font-display text-[22px] leading-none font-extrabold">{EVENT.day}</span>
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="text-[10px] font-semibold tracking-[0.8px] text-braise-soft uppercase">À venir · rappel activé</span>
          <span className="truncate font-display text-[14px] font-bold text-sable">{event.title}</span>
          <span className="truncate text-[11px] text-bark-soft">{EVENT.meta}</span>
        </span>
        <Icon name="chevronRight" size={18} className="text-bark-soft" />
      </button>

      <section className="flex flex-col gap-2.5">
        <SectionTitle title="Nos catégories" action="Tout voir" onAction={onMyIdeas} />
        <Rail>
          {CATEGORIES.map((c) => (
            <button key={c.key} type="button" onClick={() => onCategory(c.key)} className="flex w-[60px] shrink-0 flex-col items-center gap-1">
              <span className={`flex h-[52px] w-[52px] items-center justify-center rounded-[18px] ${c.bg} ${c.ic}`}><Icon name={c.icon} size={23} /></span>
              <span className="text-[11px] font-semibold">{c.plural}</span>
              <span className="-mt-1 text-[10px] text-muted">{family.filter((i) => i.category === c.key).length}</span>
            </button>
          ))}
        </Rail>
      </section>

      <section className="flex flex-col gap-2.5">
        <SectionTitle title="De la communauté" action="Tout voir dans Explorer" onAction={onExplorer} />
        <Rail>
          {community.map((i) => {
            const cat = categoryMeta(i.category);
            return (
              <button key={i.id} type="button" onClick={() => onIdea(i.id)} className="w-[150px] shrink-0 overflow-hidden rounded-large bg-card text-left shadow-card">
                <span className="relative block">
                  <Visual idea={i} height={84} className="w-full" />
                  <span className={`absolute top-2 left-2 rounded-full bg-card px-2 py-0.5 text-[10px] font-semibold ${cat.ic}`}>{cat.label}</span>
                </span>
                <span className="flex flex-col gap-0.5 px-2.5 pt-2 pb-2.5">
                  <span className="line-clamp-2 font-display text-[13px] leading-tight font-bold">{i.title}</span>
                  <span className="truncate text-[11px] text-sec">{[i.tags[0], byName(i.by)].join(" · ")}</span>
                </span>
              </button>
            );
          })}
        </Rail>
      </section>

      <section className="flex flex-col gap-2.5">
        <SectionTitle title="De votre tribu" action="Mes idées" onAction={onMyIdeas} />
        {family.slice(0, 4).map((i) => <RowCard key={i.id} idea={i} onOpen={() => onIdea(i.id)} />)}
      </section>
    </div>
  );
}

// ── Mes idées : les courses, puis les dossiers ───────────────────────────────

function MyIdeasScreen({ family, liked, menuCount, toBuy, isDone, onShopping, onFolder, onIdea, onNewFolder }: {
  family: Idea[]; liked: Idea[]; menuCount: number; toBuy: number; isDone: (i: Idea) => boolean;
  onShopping: () => void; onFolder: (id: string) => void; onIdea: (id: string) => void; onNewFolder: () => void;
}) {
  const [view, setView] = useState<"dossiers" | "jaime" | "tags">("dossiers");
  const tags = [...new Set(family.flatMap((i) => i.tags.slice(0, 1)))];
  return (
    <div className="flex flex-col gap-3.5 pt-1">
      <div>
        <h3 className="font-display text-[24px] leading-tight font-extrabold tracking-[-0.5px]">Mes idées</h3>
        <span className="text-[12px] text-sec">{[plural(family.length, "idée", "idées"), plural(FOLDERS.length, "dossier", "dossiers"), plural(tags.length, "tag", "tags")].join(" · ")}</span>
      </div>
      <div role="tablist" aria-label="Affichage" className="grid h-10 grid-cols-3 gap-1 rounded-[14px] bg-sable-2 p-1">
        {([["dossiers", "Dossiers"], ["jaime", "J'aime"], ["tags", "Tags"]] as const).map(([k, label]) => (
          <button key={k} type="button" role="tab" aria-selected={view === k} onClick={() => setView(k)} className={`flex items-center justify-center gap-1 rounded-[10px] text-[12px] ${view === k ? "bg-card font-bold shadow-[0_1px_4px_rgba(58,33,27,0.1)]" : "font-semibold text-sec"}`}>
            {k === "dossiers" && <Icon name="tipi" size={14} className={view === k ? "text-terracotta" : ""} />}
            {label}
          </button>
        ))}
      </div>

      {view === "dossiers" && (
        <>
          <button type="button" onClick={onShopping} className={`flex items-center gap-3 rounded-[18px] px-3 py-2.5 text-left shadow-card ${menuCount ? "bg-ecorce text-sable" : "bg-card"}`}>
            <span className={`flex h-9 w-9 items-center justify-center rounded-[11px] ${menuCount ? "bg-terracotta text-white" : "bg-recette-bg text-recette-ic"}`}>
              <Icon name="cart" size={18} />
            </span>
            <span className="flex flex-1 flex-col">
              <span className="text-[13px] font-bold">Les courses</span>
              <span className={`text-[11px] ${menuCount ? "opacity-80" : "text-sec"}`}>
                {menuCount ? `${plural(menuCount, "recette", "recettes")} au menu · ${toBuy} à prendre` : "Le menu de la semaine et la liste, avec la tribu"}
              </span>
            </span>
            <Icon name="chevronRight" size={18} className={menuCount ? "opacity-70" : "text-muted"} />
          </button>
          <span className="text-[10px] font-extrabold tracking-[0.8px] text-sec uppercase">Tous les dossiers</span>
          <div className="grid grid-cols-2 gap-2.5">
            {FOLDERS.map((f) => (
              <FolderCard key={f.id} folder={f} ideas={family.filter((i) => i.folder === f.id)} isDone={isDone} onOpen={() => onFolder(f.id)} />
            ))}
            <button type="button" onClick={onNewFolder} className="flex min-h-[120px] flex-col items-center justify-center gap-1.5 rounded-large border-2 border-dashed border-dash text-[11px] font-bold text-sec">
              <span className="flex h-9 w-9 items-center justify-center rounded-[12px] bg-sable-2 text-terracotta"><Icon name="plus" size={18} /></span>
              Nouveau dossier
            </button>
          </div>
        </>
      )}
      {view === "jaime" &&
        (liked.length ? (
          liked.map((i) => <RowCard key={i.id} idea={i} onOpen={() => onIdea(i.id)} />)
        ) : (
          <p className="rounded-large bg-card p-4 text-center text-[12px] text-sec">Les idées que vous aimez dans Explorer arrivent ici. Touchez un cœur pour essayer.</p>
        ))}
      {view === "tags" && (
        <div className="flex flex-wrap gap-1.5">
          {tags.map((t) => <span key={t} className="rounded-full bg-card px-3 py-1.5 text-[12px] font-semibold shadow-card">#{t}</span>)}
        </div>
      )}
    </div>
  );
}

function asFolder(f: DemoFolder): Folder {
  return { id: f.id, name: f.name, color: f.color, icon: f.icon } as unknown as Folder;
}

function FolderCard({ folder, ideas, isDone, onOpen }: { folder: DemoFolder; ideas: Idea[]; isDone: (i: Idea) => boolean; onOpen: () => void }) {
  const color = folderColor(folder.color);
  const doneCount = ideas.filter(isDone).length;
  return (
    <button type="button" onClick={onOpen} className="relative block pt-2 text-left active:scale-[0.98]">
      <span aria-hidden="true" className="absolute top-0 left-0 h-[18px] w-[48px] rounded-t-[9px] rounded-tr-[11px] bg-(--color-vis-shared-bg)" />
      <span className="relative block overflow-hidden rounded-[18px] rounded-tl-none bg-card shadow-card">
        <span className="relative block h-12 overflow-hidden"><FolderCover folder={asFolder(folder)} /></span>
        <span className="relative -mt-[15px] flex flex-col gap-1.5 px-2.5 pb-2.5">
          <span className="flex h-8 w-8 items-center justify-center rounded-[11px] border-[3px] border-card text-on-solid" style={{ background: color.solid }}>
            <FolderGlyph icon={folder.icon} size={16} />
          </span>
          <span className="line-clamp-2 font-display text-[13px] leading-tight font-bold">{folder.name}</span>
          <span className="flex items-center gap-1 text-[10px] text-sec">
            {plural(ideas.length, "idée", "idées")}
            <span className="flex -space-x-1">{DEMO_TRIBE.slice(0, 3).map((m) => <Avatar key={m.name} name={m.name} color={m.color} ink={m.ink} size={13} className="ring-1 ring-card" />)}</span>
          </span>
          <span className="flex items-center gap-1.5 text-[10px] text-sec">
            <span className="h-1 flex-1 overflow-hidden rounded-full bg-sable-2"><span className="block h-full rounded-full bg-done" style={{ width: `${ideas.length ? (doneCount / ideas.length) * 100 : 0}%` }} /></span>
            {doneCount}/{ideas.length}
          </span>
        </span>
      </span>
    </button>
  );
}

function FolderScreen({ folder, ideas, isDone, onOpen }: { folder: DemoFolder; ideas: Idea[]; isDone: (i: Idea) => boolean; onOpen: (id: string) => void }) {
  const cats = [...new Set(ideas.map((i) => i.category))];
  const [filter, setFilter] = useState("");
  const shown = filter ? ideas.filter((i) => i.category === filter) : ideas;
  return (
    <div className="flex flex-col gap-3">
      <div className="relative -mx-4 h-20 overflow-hidden"><FolderCover folder={asFolder(folder)} /></div>
      <div className="flex items-center justify-between gap-2">
        <span className="text-[12px] text-sec">{plural(ideas.length, "idée", "idées")} · partagé avec la tribu</span>
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
      {shown.map((i) => <RowCard key={i.id} idea={i} done={isDone(i)} onOpen={() => onOpen(i.id)} />)}
    </div>
  );
}

function ListScreen({ ideas, isDone, onOpen, empty }: { ideas: Idea[]; isDone: (i: Idea) => boolean; onOpen: (id: string) => void; empty: string }) {
  if (!ideas.length) return <p className="rounded-large bg-card p-4 text-center text-[12px] text-sec">{empty}</p>;
  return <div className="flex flex-col gap-2">{ideas.map((i) => <RowCard key={i.id} idea={i} done={isDone(i)} onOpen={() => onOpen(i.id)} />)}</div>;
}

// ── Fiche d'une idée ─────────────────────────────────────────────────────────

function IdeaScreen({ idea, done, liked, inMenu, onDone, onLike, onMenu, onReserve }: {
  idea: Idea; done?: string; liked: boolean; inMenu: boolean; onDone: () => void; onLike: () => void; onMenu: () => void; onReserve: () => void;
}) {
  const cat = categoryMeta(idea.category);
  return (
    <div className="flex flex-col gap-3">
      <div className="relative">
        <Visual idea={idea} height={144} className="w-full rounded-large" />
        <button type="button" onClick={onLike} aria-pressed={liked} aria-label="J'aime" className="absolute top-2.5 right-2.5 flex h-9 items-center gap-1 rounded-full bg-card px-3 text-[12px] font-bold text-ecorce">
          <Icon name="heart" size={15} filled={liked} className={liked ? "text-terracotta" : ""} />{idea.likes + (liked ? 1 : 0)}
        </button>
      </div>
      <h3 className="font-display text-[19px] leading-tight font-extrabold">{idea.title}</h3>
      <div className="flex items-center gap-1.5 text-[12px] text-sec">
        Ajoutée par
        {typeof idea.by === "string" ? (
          <b className="text-ecorce">{idea.by}</b>
        ) : (
          <>
            <Avatar name={idea.by.name} color={idea.by.color} ink={idea.by.ink} size={16} />
            <b className="text-ecorce">{idea.by.name}</b>
          </>
        )}
      </div>
      <div className="flex flex-wrap gap-1.5 text-[11px] font-bold">
        <span className={`rounded-full px-2.5 py-1 ${cat.bg} ${cat.ink}`}>{cat.label}</span>
        {idea.tags.map((t) => <span key={t} className="rounded-full bg-sable-2 px-2.5 py-1 text-sec">{t}</span>)}
      </div>
      <p className="text-[13px] leading-relaxed text-ecorce-2">{idea.text}</p>
      {idea.ingredients && (
        <div className="rounded-medium bg-card p-3">
          <span className="text-[10px] font-extrabold tracking-[1.5px] text-muted uppercase">Ingrédients</span>
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

// ── Explorer ─────────────────────────────────────────────────────────────────

// Hauteurs variées pour l'effet mosaïque, comme l'Explorer de l'appli
const MOSAIC = [96, 72, 64, 84, 90, 70];

function ExplorerScreen({ ideas, liked, onLike, onOpen }: { ideas: Idea[]; liked: Set<string>; onLike: (id: string) => void; onOpen: (id: string) => void }) {
  const cols = [ideas.filter((_, k) => k % 2 === 0), ideas.filter((_, k) => k % 2 === 1)];
  return (
    <div className="flex flex-col gap-3 pt-1">
      <div>
        <h3 className="font-display text-[24px] leading-tight font-extrabold tracking-[-0.5px]">Explorer</h3>
        <span className="text-[12px] text-sec">Les idées publiées par les familles</span>
      </div>
      <div className="flex items-center gap-2 rounded-medium bg-card px-3 py-2.5 text-[12px] text-muted shadow-card"><Icon name="search" size={15} />Une sortie, une recette, un âge…</div>
      <div className="flex gap-1.5 overflow-hidden">
        {["Tout", ...CATEGORIES.slice(0, 4).map((c) => c.plural)].map((c, k) => (
          <span key={c} className={`shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold ${k === 0 ? "bg-ecorce text-sable" : "bg-card text-sec"}`}>{c}</span>
        ))}
      </div>
      <div className="grid grid-cols-2 gap-2">
        {cols.map((col, c) => (
          <div key={c} className="flex flex-col gap-2">
            {col.map((i, k) => {
              const cat = categoryMeta(i.category);
              const on = liked.has(i.id);
              return (
                <div key={i.id} className="relative overflow-hidden rounded-large bg-card shadow-card">
                  <button type="button" onClick={() => onOpen(i.id)} className="flex w-full flex-col text-left">
                    <Visual idea={i} height={MOSAIC[(c + k * 2) % MOSAIC.length]} className="w-full" />
                    <span className="flex flex-col gap-0.5 p-2">
                      <span className="line-clamp-2 text-[12px] leading-tight font-bold">{i.title}</span>
                      <span className={`text-[10px] font-bold ${cat.ink}`}>{cat.label}</span>
                    </span>
                  </button>
                  <button type="button" onClick={() => onLike(i.id)} aria-pressed={on} aria-label="J'aime" className="absolute top-1.5 right-1.5 flex h-7 items-center gap-1 rounded-full bg-card px-2 text-[10px] font-bold">
                    <Icon name="heart" size={12} filled={on} className={on ? "text-terracotta" : ""} />{i.likes + (on ? 1 : 0)}
                  </button>
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Les courses (depuis Mes idées) ───────────────────────────────────────────

/** Ingrédients des recettes au menu, regroupés par produit (mêmes unités additionnées). */
function shoppingItems(recipes: Idea[]): Ingredient[] {
  const items = new Map<string, Ingredient>();
  for (const r of recipes) {
    for (const g of r.ingredients ?? []) {
      const cur = items.get(g.name);
      items.set(g.name, cur ? { ...cur, qty: addQty(cur.qty, g.qty) } : g);
    }
  }
  return [...items.values()];
}

/** « 50 cl » + « 40 cl » → « 90 cl » ; unités différentes : les deux. */
function addQty(a: string, b: string): string {
  const parse = (q: string) => /^(\d+(?:,\d+)?)\s*(.*)$/.exec(q);
  const x = parse(a), y = parse(b);
  if (!x || !y || x[2] !== y[2]) return `${a} + ${b}`;
  const n = Number(x[1].replace(",", ".")) + Number(y[1].replace(",", "."));
  return `${String(Math.round(n * 10) / 10).replace(".", ",")}${x[2] ? ` ${x[2]}` : ""}`;
}

function ShoppingScreen({ recipes, checked, onCheck, onRecipe, onEmpty }: {
  recipes: Idea[]; checked: Set<string>; onCheck: (name: string) => void; onRecipe: (id: string) => void; onEmpty: () => void;
}) {
  const items = shoppingItems(recipes);
  const aisles = [...new Set(items.map((g) => g.aisle))].sort((a, b) => a.localeCompare(b, "fr"));
  const left = items.filter((g) => !checked.has(g.name)).length;
  return (
    <div className="flex flex-col gap-3">
      <span className="text-[12px] text-sec">Partagée avec la tribu · {plural(left, "article", "articles")} à prendre</span>
      <div className="flex flex-wrap gap-1.5">
        {recipes.map((r) => (
          <button key={r.id} type="button" onClick={() => onRecipe(r.id)} className="rounded-full bg-recette-bg px-2.5 py-1 text-[11px] font-bold text-recette-ink">{r.title}</button>
        ))}
      </div>
      {!recipes.length && (
        <button type="button" onClick={onEmpty} className="rounded-medium bg-card p-3 text-left text-[12px] text-sec">
          Le menu est vide. Ouvrez « Recettes du dimanche » et mettez une recette au menu.
        </button>
      )}
      {aisles.map((a) => (
        <div key={a} className="rounded-medium bg-card px-3 py-2">
          <span className="text-[10px] font-extrabold tracking-[1.5px] text-muted uppercase">{a}</span>
          {items.filter((g) => g.aisle === a).map((g) => {
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

// ── Ma tribu ─────────────────────────────────────────────────────────────────

function TribeScreen({ family, onInvite }: { family: Idea[]; onInvite: () => void }) {
  const [view, setView] = useState<"foyer" | "cercle">("foyer");
  const foyer = DEMO_TRIBE.filter((m) => m.home);
  const circle = DEMO_TRIBE.filter((m) => !m.home);
  return (
    <div className="flex flex-col gap-3 pt-1">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-display text-[24px] leading-tight font-extrabold tracking-[-0.5px]">Ma tribu</h3>
          <span className="text-[12px] text-sec">{foyer.length} au foyer · {circle.length} dans le cercle</span>
        </div>
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-card text-ecorce shadow-soft"><Icon name="settings" size={17} /></span>
      </div>
      {/* La tribu installée autour du feu, sous le soleil */}
      <div aria-hidden="true" className="relative -mx-4 h-[150px] overflow-hidden">
        <span className="absolute top-[36px] left-1/2 -ml-[120px] h-[240px] w-[240px] rounded-full bg-soleil" />
        {DEMO_TRIBE.map((m, k) => (
          <span key={m.name} className="absolute flex w-12 flex-col items-center gap-0.5" style={{ left: `calc(50% + ${[-112, -56, 8, 64][k]}px)`, top: [58, 26, 26, 58][k] }}>
            <span className="flex h-10 w-10 items-center justify-center rounded-[14px] border-[3px] border-sable font-display text-[16px] font-extrabold" style={{ background: m.color, color: m.ink }}>{m.name.charAt(0)}</span>
            <span className="text-[10px] font-bold">{m.name}</span>
          </span>
        ))}
        <span className="absolute inset-x-0 bottom-1.5 text-center font-display text-[17px] font-extrabold">La tribu Martin</span>
      </div>
      <div role="tablist" aria-label="Ma tribu" className="grid h-10 grid-cols-2 rounded-[14px] bg-sable-2 p-1">
        {([["foyer", "Le foyer", foyer.length], ["cercle", "Le cercle", circle.length]] as const).map(([k, label, n]) => (
          <button key={k} type="button" role="tab" aria-selected={view === k} onClick={() => setView(k)} className={`flex items-center justify-center gap-1 rounded-[10px] text-[12px] font-bold ${view === k ? "bg-card text-ecorce shadow-[0_2px_6px_rgba(58,33,27,0.12)]" : "text-sec"}`}>
            {label} <span className="font-semibold text-muted">{n}</span>
          </button>
        ))}
      </div>
      {(view === "foyer" ? foyer : circle).map((m) => (
        <div key={m.name} className={`${CARD} flex items-center gap-3 px-3 py-2.5`}>
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] font-display text-[16px] font-extrabold" style={{ background: m.color, color: m.ink }}>{m.name.charAt(0)}</span>
          <span className="flex min-w-0 flex-col">
            <span className="text-[13px] font-bold">{m.name}</span>
            <span className="truncate text-[11px] text-sec">{m.home ? `${m.role} · ${plural(family.filter((i) => i.by === m).length, "idée", "idées")}` : m.role}</span>
          </span>
        </div>
      ))}
      <button type="button" onClick={onInvite} className="flex h-11 items-center justify-center gap-1.5 rounded-medium border-2 border-dashed border-dash text-[13px] font-bold text-sec">
        <Icon name="personPlus" size={16} />Inviter autour du feu
      </button>
    </div>
  );
}
