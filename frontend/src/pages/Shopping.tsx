import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useComposer } from "../components/Composer";
import { useFeedback } from "../components/feedback";
import Icon, { type IconName } from "../components/Icon";
import Seo from "../components/Seo";
import Sheet from "../components/Sheet";
import { Page } from "../components/Shell";
import { Avatar, Button } from "../components/ui";
import { useMyData } from "../lib/data";
import { plural } from "../lib/format";
import { getSession } from "../lib/session";
import {
  AISLES,
  aisleName,
  pickRecipes,
  shareText,
  splitSpoken,
  suggestions,
  viewOf,
  type AisleId,
  type RecipeInfo,
  type ShoppingLine,
} from "../lib/shopping";
import { changeShopping, clearShoppingError, newItemId, useShopping } from "../lib/shoppingStore";
import type { Link } from "../lib/types";

// Rayons : icône et couleurs reprises des catégories (thème clair et sombre)
const AISLE_STYLE: Record<AisleId, { icon: IconName; tone: string }> = {
  "fruits-legumes": { icon: "leaf", tone: "bg-recette-bg text-recette-ink" },
  boulangerie: { icon: "bread", tone: "bg-cadeau-bg text-cadeau-ink" },
  boucherie: { icon: "meat", tone: "bg-sortie-bg text-sortie-ink" },
  cremerie: { icon: "milk", tone: "bg-activite-bg text-activite-ink" },
  epicerie: { icon: "jar", tone: "bg-idee-bg text-idee-ink" },
  surgeles: { icon: "snow", tone: "bg-activite-bg text-activite-ink" },
  boissons: { icon: "bottle", tone: "bg-livre-bg text-livre-ink" },
  "bebe-hygiene": { icon: "drop", tone: "bg-deco-bg text-deco-ink" },
  maison: { icon: "home", tone: "bg-livre-bg text-livre-ink" },
  autres: { icon: "dots", tone: "bg-sable-2 text-sec" },
};

// Teintes des cartes du menu sans photo, dans l'ordre
const MENU_TINTS = ["bg-cadeau-bg text-cadeau-ink", "bg-idee-bg text-idee-ink", "bg-recette-bg text-recette-ink", "bg-sortie-bg text-sortie-ink"];

// Ancienne liste gardée dans le navigateur (avant la liste partagée) : reprise une fois
const OLD_STORE = "tribbae.courses";

const recipeInfo = (l: Link): RecipeInfo => ({ title: l.title, ingredients: l.ingredients ?? [], imageUrl: l.imageUrl, servings: l.servings });

/** Date d'une idée en millisecondes (le serveur envoie des millisecondes ou une date). */
function stamp(v?: string | number): number {
  if (v === undefined || v === "") return 0;
  const n = Number(v);
  if (Number.isFinite(n)) return n < 1e12 ? n * 1000 : n;
  return Date.parse(String(v)) || 0;
}

/** « 6 bananes » → quantité « 6 », article « bananes ». */
function splitQuantity(text: string): { label: string; quantity: string } {
  const m = /^(\d+(?:[.,]\d+)?\s*(?:kg|g|l|cl|ml|x|paquets?|boîtes?|bouteilles?|packs?|lots?|sachets?|pots?)?)\s+(.+)$/i.exec(text.trim());
  return m ? { quantity: m[1].trim(), label: m[2].trim() } : { label: text.trim(), quantity: "" };
}

function readOldSelection(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(OLD_STORE) ?? "null");
    return Array.isArray(v?.recipes) ? v.recipes.map(String) : [];
  } catch {
    return [];
  }
}

function forgetOldSelection() {
  try {
    localStorage.removeItem(OLD_STORE);
  } catch {
    // Stockage bloqué : rien à effacer
  }
}

export default function Shopping() {
  const navigate = useNavigate();
  const { toast, confirm } = useFeedback();
  const { editLink } = useComposer();
  const { links, tribe } = useMyData();
  const [params, setParams] = useSearchParams();
  const snap = useShopping({ poll: true });
  const { list, loaded } = snap;
  const me = getSession()?.userId ?? "";

  const [chosenMode, setMode] = useState<"prep" | "shop" | null>(null);
  const [sheet, setSheet] = useState<"pick" | "share" | null>(null);
  const [lineSheet, setLineSheet] = useState<ShoppingLine | null>(null);
  const [cartOpen, setCartOpen] = useState(false);
  const [expanded, setExpanded] = useState<string[]>([]);
  // En magasin : une seule annonce, remplacée à chaque article coché
  const [lastChecked, setLastChecked] = useState<ShoppingLine | null>(null);
  const lastTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const { ready, missing } = useMemo(() => pickRecipes(links), [links]);
  const { lines, arranged } = useMemo(() => viewOf(list), [list]);

  // Revenu sur la page pendant ses courses : on reste en magasin
  const mode = chosenMode ?? (loaded && list.shopperId === me ? "shop" : "prep");

  const members = tribe?.members ?? [];
  const nameOf = (id?: string) => (id === me ? "vous" : (members.find((m) => m.userId === id)?.displayName ?? "un proche"));
  const others = members.filter((m) => m.userId !== me);
  // Le serveur oublie « X est en magasin » au bout de 2 h
  const shopperAway = list.shopperId && list.shopperId !== me ? nameOf(list.shopperId) : "";

  // Erreur d'un changement refusé : montrée une fois
  useEffect(() => {
    if (!snap.error) return;
    toast(snap.error, "error");
    clearShoppingError();
  }, [snap.error, toast]);

  // « Préparer la liste de courses » depuis une recette (?recette=) et ancienne
  // liste du navigateur : les recettes passent au menu de la tribu
  useEffect(() => {
    if (!loaded || !ready.length) return;
    const wanted = [...readOldSelection(), params.get("recette") ?? ""].filter(Boolean);
    for (const id of new Set(wanted)) {
      const r = ready.find((l) => l.id === id);
      if (r && !list.menu.some((e) => e.linkId === id)) changeShopping({ type: "menu_add", linkId: id }, recipeInfo(r));
    }
    forgetOldSelection();
    if (params.has("recette")) setParams({}, { replace: true });
    // Une seule fois, quand la liste et les recettes sont là
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loaded, ready.length > 0]);

  // En magasin : l'écran reste allumé
  useEffect(() => {
    if (mode !== "shop" || !("wakeLock" in navigator)) return;
    let lock: WakeLockSentinel | undefined;
    let alive = true;
    navigator.wakeLock.request("screen").then((l) => (alive ? (lock = l) : void l.release()), () => undefined);
    return () => {
      alive = false;
      void lock?.release();
    };
  }, [mode]);

  const startShopping = () => {
    changeShopping({ type: "shopping_start" });
    setMode("shop");
    window.scrollTo({ top: 0 });
  };
  const stopShopping = () => {
    setLastChecked(null);
    changeShopping({ type: "shopping_stop" });
    setMode("prep");
  };
  const finish = () => {
    setLastChecked(null);
    changeShopping({ type: "finish" });
    setMode("prep");
    toast("Courses terminées · le menu reste en place");
  };
  const newList = async () => {
    const ok = await confirm({
      title: "Commencer une nouvelle liste ?",
      text: "Le menu, les articles et les cases cochées sont effacés pour toute la tribu. Le placard et les rayons restent.",
      confirmLabel: "Nouvelle liste",
      danger: true,
    });
    if (ok) changeShopping({ type: "clear" });
  };

  const toggle = (line: ShoppingLine, inShop: boolean) => {
    const checked = list.checked.some((c) => c.key === line.key);
    changeShopping({ type: checked ? "uncheck" : "check", key: line.key });
    if (inShop) {
      clearTimeout(lastTimer.current);
      setLastChecked(checked ? null : line);
      if (!checked) lastTimer.current = setTimeout(() => setLastChecked(null), 3200);
    }
  };
  const checkedBy = (line: ShoppingLine) => list.checked.find((c) => c.key === line.key)?.by;

  const addItem = (label: string, quantity = "", aisle = "") => {
    if (!label.trim()) return;
    changeShopping({ type: "item_add", id: newItemId(), label, quantity, aisle });
  };

  // Dictée : chaque article entendu est ajouté, « Annuler » les retire tous
  const addDictated = (spoken: string) => {
    const added = splitSpoken(spoken).map((text) => ({ id: newItemId(), ...splitQuantity(text) }));
    if (!added.length) return;
    for (const it of added) changeShopping({ type: "item_add", id: it.id, label: it.label, quantity: it.quantity, aisle: "" });
    const names = added.map((it) => (it.quantity ? `${it.quantity} ${it.label}` : it.label)).join(", ");
    toast(`${names} ${added.length > 1 ? "ajoutés" : "ajouté"}`, "ok", {
      action: { label: "Annuler", onClick: () => added.forEach((it) => changeShopping({ type: "item_remove", id: it.id })) },
    });
  };

  const status = snap.offline ? "hors ligne · envoyé au retour du réseau" : snap.pending ? "envoi…" : "à jour";
  const menuParts = list.menu.reduce((n, e) => n + e.servings, 0);
  const empty = loaded && !list.menu.length && !list.items.length;

  if (mode === "shop") {
    const byOthers = arranged.cart.filter((l) => {
      const by = checkedBy(l);
      return by && by !== me;
    });
    const pct = arranged.total ? Math.round(((arranged.total - arranged.remaining) / arranged.total) * 100) : 0;
    return (
      <Page className="max-w-3xl">
        <Seo title="En magasin · Les courses · Tribbae" noindex />
        <div className="sticky top-0 z-10 -mx-5 flex flex-col gap-3 bg-sable px-5 pt-1 pb-3 md:-mx-6 md:px-6">
          <div className="flex items-center justify-between gap-3">
            <button type="button" onClick={stopShopping} className="flex h-10 items-center gap-1 rounded-full bg-card pr-4 pl-2 text-sm font-bold shadow-soft">
              <Icon name="chevronLeft" size={20} />Préparer
            </button>
            {"wakeLock" in navigator && (
              <span className="flex items-center gap-1.5 text-xs font-semibold text-sec">
                <span className="h-2 w-2 rounded-full bg-done" aria-hidden="true" />Écran toujours allumé
              </span>
            )}
          </div>
          <div className="flex flex-col">
            <span className="text-xs font-extrabold tracking-[0.8px] text-terracotta uppercase">En magasin</span>
            <h1 className="font-display text-[30px] leading-tight font-extrabold tracking-[-0.6px]">
              {arranged.remaining ? `Encore ${arranged.remaining}` : "C'est tout bon"}
            </h1>
            <span className="text-sm text-sec">{arranged.total - arranged.remaining} sur {arranged.total} dans le panier</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-sable-2" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Avancement des courses">
            <div className="h-full rounded-full bg-done transition-[width] duration-300" style={{ width: `${pct}%` }} />
          </div>
        </div>

        {byOthers.length > 0 && arranged.remaining > 0 && (
          <p className="flex items-center gap-2.5 rounded-medium bg-card px-3.5 py-3 text-sm text-ecorce-2">
            <Avatar name={nameOf(checkedBy(byOthers[0]))} size={26} color="var(--color-livre-bg)" ink="var(--color-livre-ink)" />
            <span>
              <b className="text-ecorce">{nameOf(checkedBy(byOthers[0]))}</b> a déjà pris {byOthers.length > 1 ? `${byOthers.length} articles` : byOthers[0].name.toLowerCase()}. La liste se met à jour pour vous tous.
            </span>
          </p>
        )}

        {arranged.total > 0 && arranged.remaining === 0 && (
          <div className="flex animate-pop flex-col items-center gap-2 rounded-large bg-card px-5 py-6 text-center">
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-done text-white"><Icon name="check" size={28} strokeWidth={2.6} /></span>
            <span className="font-display text-xl font-extrabold">Tout est dans le panier !</span>
            <span className="text-sm text-sec">
              {plural(arranged.total, "article", "articles")} · {plural(list.menu.length, "recette", "recettes")} au menu. Elles y restent jusqu'à ce que vous les cuisiniez.
            </span>
            <Button className="mt-2" onClick={finish}>Terminer les courses</Button>
          </div>
        )}

        {arranged.groups.map((g) => (
          <section key={g.aisle} className="flex flex-col gap-2">
            <AisleTitle aisle={g.aisle} name={g.name} count={g.lines.length} />
            {g.lines.map((l) => (
              <button
                key={l.key}
                type="button"
                role="checkbox"
                aria-checked={false}
                onClick={() => toggle(l, true)}
                className="flex min-h-16 items-center gap-3.5 rounded-medium bg-card px-4 py-3 text-left shadow-soft active:scale-[0.99]"
              >
                <span aria-hidden="true" className="h-7 w-7 shrink-0 rounded-[9px] border-2 border-dash" />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="text-[17px] font-bold">{l.name}</span>
                  <span className="truncate text-[13px] text-sec">{lineSub(l, nameOf)}</span>
                </span>
                {l.qty && <span className="shrink-0 text-[17px] font-bold">{l.qty}</span>}
              </button>
            ))}
          </section>
        ))}

        {lastChecked && (
          <div role="status" className="pointer-events-none fixed inset-x-0 bottom-[calc(116px+env(safe-area-inset-bottom))] z-[60] flex justify-center px-4 md:bottom-8">
            <div className="pointer-events-auto flex animate-pop items-center gap-2 rounded-medium bg-ecorce py-2.5 pr-2.5 pl-4 text-sm font-semibold text-sable shadow-nav">
              <Icon name="check" size={18} />
              {lastChecked.name} dans le panier
              <button
                type="button"
                onClick={() => {
                  changeShopping({ type: "uncheck", key: lastChecked.key });
                  setLastChecked(null);
                }}
                className="ml-1 h-8 rounded-[10px] bg-white/15 px-3 text-[13px] font-bold text-[#FFC857]"
              >
                Annuler
              </button>
            </div>
          </div>
        )}

        {arranged.cart.length > 0 && (
          <section className="flex flex-col gap-2 pb-6">
            <h2 className="text-xs font-extrabold tracking-[0.8px] text-sec uppercase">Dans le panier · {arranged.cart.length}</h2>
            <div className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-large bg-card">
              {arranged.cart.map((l) => (
                <button key={l.key} type="button" role="checkbox" aria-checked onClick={() => toggle(l, true)} className="flex min-h-12 items-center gap-3 px-4 py-2.5 text-left">
                  <span aria-hidden="true" className="flex h-6 w-6 shrink-0 items-center justify-center rounded-[8px] bg-done text-white"><Icon name="check" size={14} strokeWidth={3} /></span>
                  <span className="flex-1 text-[15px] text-muted line-through">{l.name}</span>
                  {checkedBy(l) && checkedBy(l) !== me && <span className="text-xs text-sec">par {nameOf(checkedBy(l))}</span>}
                </button>
              ))}
            </div>
          </section>
        )}
      </Page>
    );
  }

  return (
    <Page className="max-w-3xl">
      <Seo title="Les courses · Tribbae" noindex />
      <div className="flex items-center justify-between gap-3">
        <button type="button" onClick={() => navigate(-1)} aria-label="Retour" className="flex h-11 w-11 items-center justify-center rounded-full bg-card shadow-soft hover:bg-sable-2">
          <Icon name="chevronLeft" size={22} />
        </button>
        <div className="flex gap-2">
          {!empty && (
            <button type="button" onClick={newList} className="flex h-11 items-center rounded-full bg-card px-4 text-sm font-bold shadow-soft hover:bg-sable-2">
              Nouvelle liste
            </button>
          )}
          <button type="button" onClick={() => setSheet("share")} aria-label="Envoyer la liste" className="flex h-11 w-11 items-center justify-center rounded-full bg-card shadow-soft hover:bg-sable-2">
            <Icon name="share" size={20} />
          </button>
        </div>
      </div>
      <div className="-mt-2 flex flex-col gap-1.5">
        <h1 className="font-display text-[32px] leading-tight font-extrabold tracking-[-0.8px]">Les courses</h1>
        <span className="flex flex-wrap items-center gap-2 text-sm text-sec">
          {others.length > 0 && (
            <span className="flex -space-x-1.5">
              {[me, ...others.map((m) => m.userId)].slice(0, 4).map((id) => (
                <Avatar key={id} name={id === me ? getSession()?.displayName : nameOf(id)} size={22} color="var(--color-ecorce)" ink="var(--color-sable)" className="ring-2 ring-sable" />
              ))}
            </span>
          )}
          {snap.offline && <Icon name="cloud" size={16} />}
          {others.length ? `Partagée avec ${others.map((m) => m.displayName ?? "un proche").join(", ")}` : "Votre liste"} · {status}
        </span>
        {shopperAway && (
          <span className="flex w-fit items-center gap-1.5 rounded-full bg-livre-bg py-1 pr-3 pl-1 text-xs font-bold text-livre-ink">
            <Avatar name={shopperAway} size={18} color="var(--color-livre-ink)" ink="var(--color-livre-bg)" />
            {shopperAway} est en magasin
          </span>
        )}
      </div>

      {/* Au menu */}
      <section className="flex flex-col gap-2.5">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-lg font-bold">Au menu</h2>
          <span className="text-sm text-sec">
            {list.menu.length ? `${plural(list.menu.length, "recette", "recettes")} · ${plural(menuParts, "part", "parts")}` : "Rien au menu"}
          </span>
        </div>
        {empty ? (
          <button type="button" onClick={() => setSheet("pick")} className="flex items-center gap-3.5 rounded-large bg-card p-4 text-left shadow-soft">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-medium bg-recette-bg text-recette-ink"><Icon name="cutlery" size={24} /></span>
            <span className="flex flex-col">
              <span className="font-bold">Qu'est-ce qu'on mange cette semaine ?</span>
              <span className="text-sm text-sec">Choisissez 2 ou 3 recettes, la liste se fait toute seule.</span>
            </span>
          </button>
        ) : (
          <div className="-mx-5 flex snap-x scroll-px-5 gap-2.5 overflow-x-auto px-5 pb-1 md:-mx-6 md:scroll-px-6 md:px-6">
            {list.menu.map((e, i) => (
              <div key={e.linkId} className={`flex w-[150px] shrink-0 snap-start flex-col overflow-hidden rounded-large bg-card shadow-soft ${e.cooked ? "opacity-60" : ""}`}>
                <div className={`relative flex h-[76px] items-center justify-center ${MENU_TINTS[i % MENU_TINTS.length]}`}>
                  {e.imageUrl ? <img src={e.imageUrl} alt="" className="h-full w-full object-cover" loading="lazy" /> : <Icon name="cutlery" size={26} />}
                  {e.cooked && (
                    <span className="absolute top-2 left-2 flex items-center gap-1 rounded-full bg-done px-2 py-0.5 text-[11px] font-bold text-white">
                      <Icon name="check" size={11} strokeWidth={3} />Cuisinée
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      changeShopping({ type: "menu_remove", linkId: e.linkId });
                      toast(`${e.title} retirée du menu`, "ok", { action: { label: "Annuler", onClick: () => changeShopping({ type: "menu_add", linkId: e.linkId, servings: e.servings }, e) } });
                    }}
                    aria-label={`Retirer ${e.title} du menu`}
                    className="absolute top-2 right-2 flex h-7 w-7 items-center justify-center rounded-full bg-card text-ecorce shadow-soft"
                  >
                    <Icon name="close" size={14} />
                  </button>
                </div>
                <div className="flex flex-1 flex-col justify-between gap-2 p-2.5">
                  <span className="line-clamp-2 text-sm leading-tight font-bold">{e.title}</span>
                  <Stepper
                    value={e.servings}
                    onChange={(n) => changeShopping({ type: "menu_servings", linkId: e.linkId, servings: n })}
                    hint={e.servings !== e.baseServings ? `×${String(Math.round((e.servings / e.baseServings) * 10) / 10).replace(".", ",")}` : ""}
                  />
                </div>
              </div>
            ))}
            <button type="button" onClick={() => setSheet("pick")} className="flex w-[110px] shrink-0 flex-col items-center justify-center gap-2 rounded-large border-2 border-dashed border-dash text-sm font-bold text-sec">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-card text-terracotta"><Icon name="plus" size={20} /></span>
              Ajouter<br />une recette
            </button>
          </div>
        )}
      </section>

      {/* Ajouter un article */}
      <div className="flex flex-col gap-2.5 rounded-large bg-card p-3">
        <AddItemField
          onAdd={(text) => { const { label, quantity } = splitQuantity(text); addItem(label, quantity); }}
          onDictate={addDictated}
          onError={(text) => toast(text, "error")}
        />
        {suggestions(lines).length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-xs text-sec">Souvent :</span>
            {suggestions(lines).map((s) => (
              <button
                key={s.label}
                type="button"
                onClick={() => { addItem(s.label, "", s.aisle); toast(`${s.label} ajouté · ${aisleName(s.aisle)}`); }}
                className={`flex h-8 items-center gap-1 rounded-full px-3 text-[13px] font-bold ${AISLE_STYLE[s.aisle].tone}`}
              >
                <Icon name="plus" size={13} strokeWidth={2.6} />{s.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Placard */}
      {arranged.staples.length > 0 && (
        <div className="flex flex-col gap-3 rounded-large bg-idee-bg p-3.5 text-idee-ink">
          <div className="flex items-start gap-2.5">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-small bg-card"><Icon name="jar" size={18} /></span>
            <span className="flex flex-col">
              <b className="text-[15px] text-ecorce">Déjà dans le placard ?</b>
              <span className="text-[13px]">Touchez ce que vous avez : ça sort de la liste.</span>
            </span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {arranged.staples.map((l) => {
              const have = list.pantry.includes(l.product);
              return (
                <button
                  key={l.product}
                  type="button"
                  aria-pressed={have}
                  onClick={() => changeShopping({ type: have ? "pantry_out" : "pantry_have", key: l.product })}
                  className={`flex h-9 items-center gap-1 rounded-full px-3.5 text-[13px] font-bold ${have ? "bg-done-solid text-on-solid" : "bg-card text-idee-ink"}`}
                >
                  {have && <Icon name="check" size={13} strokeWidth={3} />}
                  {l.name}
                  {have && <span className="font-normal opacity-80"> · on en a</span>}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* La liste par rayon */}
      <section className="flex flex-col gap-3 pb-4">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-lg font-bold">La liste</h2>
          <span className="text-sm text-sec">{arranged.remaining} à prendre · par rayon</span>
        </div>
        {!loaded && <p className="text-sm text-sec">Chargement de la liste…</p>}
        {loaded && arranged.total === 0 && (
          <p className="rounded-large bg-card px-4 py-5 text-center text-sm text-sec">
            La liste est vide : ajoutez une recette au menu ou un article.
          </p>
        )}
        {arranged.groups.map((g) => (
          <div key={g.aisle} className="flex flex-col gap-1.5">
            <AisleTitle aisle={g.aisle} name={g.name} count={g.lines.length} />
            <ul className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-large bg-card">
              {g.lines.map((l) => {
                const open = expanded.includes(l.key);
                return (
                  <li key={l.key}>
                    <div className="flex items-center gap-1 pr-1.5">
                      <button type="button" role="checkbox" aria-checked={false} aria-label={l.name} onClick={() => toggle(l, false)} className="flex h-14 w-12 shrink-0 items-center justify-center">
                        <span aria-hidden="true" className="h-[26px] w-[26px] rounded-[8px] border-2 border-dash" />
                      </button>
                      <span className="flex min-w-0 flex-1 flex-col py-2">
                        <span className="flex items-center gap-1.5 text-[15px] font-semibold">
                          {l.name}
                          {l.staple && <span className="rounded-full bg-idee-bg px-1.5 text-[11px] font-bold text-idee-ink">placard ?</span>}
                        </span>
                        <span className="truncate text-xs text-sec">{lineSub(l, nameOf)}</span>
                      </span>
                      {l.qty && <span className="shrink-0 px-1 text-[15px] font-bold">{l.qty}</span>}
                      {l.from.length > 1 && (
                        <button
                          type="button"
                          aria-expanded={open}
                          aria-label="Détail par recette"
                          onClick={() => setExpanded((x) => (open ? x.filter((k) => k !== l.key) : [...x, l.key]))}
                          className="flex h-9 w-8 items-center justify-center text-sec"
                        >
                          <Icon name="chevronDown" size={18} className={`transition-transform ${open ? "rotate-180" : ""}`} />
                        </button>
                      )}
                      <button type="button" aria-label={`Modifier ${l.name}`} onClick={() => setLineSheet(l)} className="flex h-9 w-8 items-center justify-center text-sec">
                        <Icon name="dots" size={18} />
                      </button>
                    </div>
                    {open && (
                      <div className="mx-3 mb-3 flex flex-col gap-1 rounded-medium bg-sable px-3 py-2 text-[13px] text-sec">
                        {l.from.map((f) => (
                          <span key={f.recipe} className="flex justify-between gap-3">
                            <span className="truncate">{f.recipe}</span>
                            <b className="shrink-0 text-ecorce">{f.qty}</b>
                          </span>
                        ))}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        ))}

        {arranged.cart.length > 0 && (
          <div className="flex flex-col overflow-hidden rounded-large bg-card">
            <button type="button" aria-expanded={cartOpen} onClick={() => setCartOpen((o) => !o)} className="flex h-12 items-center gap-2.5 px-4 text-left">
              <span className="flex h-6 w-6 items-center justify-center rounded-[8px] bg-done text-white"><Icon name="check" size={14} strokeWidth={3} /></span>
              <span className="flex-1 font-bold">Dans le panier</span>
              <span className="text-sm text-sec">{arranged.cart.length}</span>
              <Icon name="chevronDown" size={18} className={`text-sec transition-transform ${cartOpen ? "rotate-180" : ""}`} />
            </button>
            {cartOpen && (
              <ul className="flex flex-col divide-y divide-sable-2 border-t border-sable-2">
                {arranged.cart.map((l) => (
                  <li key={l.key} className="flex items-center gap-1 pr-4">
                    <button type="button" role="checkbox" aria-checked aria-label={l.name} onClick={() => toggle(l, false)} className="flex h-12 w-12 shrink-0 items-center justify-center">
                      <span aria-hidden="true" className="flex h-6 w-6 items-center justify-center rounded-[8px] bg-done text-white"><Icon name="check" size={14} strokeWidth={3} /></span>
                    </button>
                    <span className="flex-1 text-[15px] text-muted line-through">{l.name}</span>
                    {checkedBy(l) && <span className="text-xs text-sec">par {nameOf(checkedBy(l))}</span>}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {arranged.have.length > 0 && (
          <p className="text-[13px] text-sec">Au placard, pas sur la liste : {arranged.have.map((l) => l.name.toLowerCase()).join(", ")}</p>
        )}
        {arranged.removed.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5 text-[13px] text-sec">
            Retirés de la liste :
            {arranged.removed.map((l) => (
              <button key={l.product} type="button" onClick={() => changeShopping({ type: "restore", key: l.product })} className="flex h-7 items-center gap-1 rounded-full bg-sable-2 px-2.5 font-semibold text-ecorce-2" aria-label={`Remettre ${l.name} sur la liste`}>
                <Icon name="refresh" size={12} />{l.name}
              </button>
            ))}
          </div>
        )}
      </section>

      {/* Barre du bas */}
      {arranged.total > 0 && (
        <div className="sticky bottom-[calc(108px+env(safe-area-inset-bottom))] z-10 pb-2 md:bottom-6">
          <button type="button" onClick={startShopping} className="flex h-14 w-full items-center justify-center gap-2.5 rounded-large bg-terracotta text-[17px] font-bold text-white shadow-nav active:scale-[0.99]">
            <Icon name="cart" size={22} />
            Faire les courses
            <span className="rounded-full bg-white/25 px-2 py-0.5 text-sm">{arranged.remaining}</span>
          </button>
        </div>
      )}

      {sheet === "pick" && (
        <RecipePicker
          ready={ready}
          missing={missing}
          inMenu={(id) => list.menu.some((e) => e.linkId === id)}
          onToggle={(r) => {
            const on = list.menu.some((e) => e.linkId === r.id);
            changeShopping(on ? { type: "menu_remove", linkId: r.id } : { type: "menu_add", linkId: r.id }, recipeInfo(r));
          }}
          onComplete={(r) => { setSheet(null); editLink(r); }}
          menuCount={list.menu.length}
          onClose={() => setSheet(null)}
        />
      )}
      {sheet === "share" && (
        <ShareSheet
          text={shareText(arranged)}
          shared={others.map((m) => m.displayName ?? "un proche")}
          onDone={(msg) => { setSheet(null); if (msg) toast(msg); }}
        />
      )}
      {lineSheet && (
        <LineSheet
          line={lineSheet}
          onClose={() => setLineSheet(null)}
          onQuantity={(qty) => {
            if (lineSheet.itemId) {
              const it = list.items.find((x) => x.id === lineSheet.itemId);
              if (it) changeShopping({ type: "item_update", id: it.id, label: it.label, quantity: qty, aisle: it.aisle });
            } else {
              changeShopping({ type: "quantity", key: lineSheet.product, value: qty });
            }
          }}
          onAisle={(aisle) => {
            if (lineSheet.itemId) {
              const it = list.items.find((x) => x.id === lineSheet.itemId);
              if (it) changeShopping({ type: "item_update", id: it.id, label: it.label, quantity: it.quantity, aisle });
            } else {
              changeShopping({ type: "aisle", key: lineSheet.product, value: aisle });
            }
          }}
          onRemove={() => {
            const l = lineSheet;
            if (l.itemId) changeShopping({ type: "item_remove", id: l.itemId });
            else {
              changeShopping({ type: "remove", key: l.product });
              toast(`${l.name} retiré de la liste`, "ok", { action: { label: "Annuler", onClick: () => changeShopping({ type: "restore", key: l.product }) } });
            }
            setLineSheet(null);
          }}
        />
      )}
    </Page>
  );
}

/** Sous-titre d'une ligne : d'où elle vient, qui l'a ajoutée, si elle a été modifiée. */
function lineSub(l: ShoppingLine, nameOf: (id?: string) => string): string {
  if (l.itemId) return nameOf(l.addedBy) === "vous" ? "Ajouté par vous" : `Ajouté par ${nameOf(l.addedBy)}`;
  return [...l.from.map((f) => f.recipe), ...(l.edited ? ["modifié"] : [])].join(" · ");
}

function AisleTitle({ aisle, name, count }: { aisle: AisleId; name: string; count: number }) {
  const s = AISLE_STYLE[aisle];
  return (
    <h3 className="flex items-center gap-2 px-1 text-sm font-bold">
      <span className={`flex h-7 w-7 items-center justify-center rounded-[9px] ${s.tone}`}><Icon name={s.icon} size={15} /></span>
      <span className="flex-1">{name}</span>
      <span className="text-xs font-semibold text-muted">{count}</span>
    </h3>
  );
}

function Stepper({ value, onChange, hint }: { value: number; onChange: (n: number) => void; hint?: string }) {
  return (
    <div className="flex items-center justify-between rounded-full bg-sable p-0.5">
      <button type="button" onClick={() => onChange(Math.max(1, value - 1))} disabled={value <= 1} aria-label="Moins de parts" className="flex h-8 w-8 items-center justify-center rounded-full bg-card shadow-soft disabled:opacity-40">
        <Icon name="minus" size={14} strokeWidth={2.6} />
      </button>
      <span className="flex flex-col items-center leading-none">
        <span className="text-[13px] font-bold">{value} pers.</span>
        {hint && <span className="text-[10px] font-semibold text-terracotta">{hint}</span>}
      </span>
      <button type="button" onClick={() => onChange(Math.min(50, value + 1))} disabled={value >= 50} aria-label="Plus de parts" className="flex h-8 w-8 items-center justify-center rounded-full bg-card shadow-soft disabled:opacity-40">
        <Icon name="plus" size={14} strokeWidth={2.6} />
      </button>
    </div>
  );
}

// Reconnaissance vocale du navigateur (Chrome, Edge, Safari ; absente de Firefox)
interface Recognition {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((e: SpeechRecognitionEvent) => void) | null;
  onerror: ((e: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
}

function recognitionClass(): (new () => Recognition) | undefined {
  if (typeof window === "undefined") return undefined;
  const w = window as unknown as { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition;
}

function AddItemField({ onAdd, onDictate, onError }: { onAdd: (text: string) => void; onDictate: (spoken: string) => void; onError: (text: string) => void }) {
  const [text, setText] = useState("");
  const [listening, setListening] = useState(false);
  const recognition = useRef<Recognition | null>(null);
  const Recognizer = recognitionClass();
  useEffect(() => () => recognition.current?.abort(), []);

  const dictate = () => {
    if (recognition.current) {
      recognition.current.stop();
      return;
    }
    if (!Recognizer) return;
    const r = new Recognizer();
    r.lang = "fr-FR";
    r.interimResults = true;
    r.continuous = false;
    let heard = "";
    r.onresult = (e) => {
      heard = Array.from(e.results, (res) => res[0].transcript).join(" ");
      setText(heard);
    };
    r.onerror = (e) => {
      heard = "";
      if (e.error === "not-allowed" || e.error === "service-not-allowed") onError("Autorise le micro pour dicter tes courses");
      else if (e.error === "no-speech") onError("Je n'ai rien entendu, réessaie");
      else if (e.error !== "aborted") onError("La dictée n'a pas marché, réessaie");
    };
    r.onend = () => {
      recognition.current = null;
      setListening(false);
      setText("");
      if (heard.trim()) onDictate(heard);
    };
    recognition.current = r;
    setText("");
    setListening(true);
    r.start();
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (listening || !text.trim()) return;
        onAdd(text);
        setText("");
      }}
      className="flex items-center gap-2 rounded-medium bg-sable px-3"
    >
      <Icon name="plus" size={18} className="text-sec" />
      <input
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={listening ? "J'écoute… « pain et sucre »" : "Ajouter un article…"}
        aria-label="Ajouter un article"
        maxLength={listening ? undefined : 80}
        readOnly={listening}
        enterKeyHint="done"
        className="h-11 min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted"
      />
      {text.trim() && !listening ? (
        <button type="submit" className="text-sm font-bold text-terracotta">Ajouter</button>
      ) : (
        Recognizer && (
          <button
            type="button"
            onClick={dictate}
            aria-label={listening ? "Arrêter la dictée" : "Dicter des articles"}
            aria-pressed={listening}
            className={`-mr-1.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${listening ? "animate-pulse bg-terracotta text-white" : "text-sec hover:text-terracotta"}`}
          >
            <Icon name="mic" size={19} />
          </button>
        )
      )}
    </form>
  );
}

function RecipePicker({ ready, missing, inMenu, onToggle, onComplete, menuCount, onClose }: {
  ready: Link[]; missing: Link[]; inMenu: (id: string) => boolean; onToggle: (r: Link) => void; onComplete: (r: Link) => void; menuCount: number; onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<"all" | "liked" | "recent">("all");
  const q = query.trim().toLowerCase();
  const all = [...ready, ...missing];
  const base =
    tab === "liked"
      ? all.filter((l) => l.likedByMe || l.favorite)
      : tab === "recent"
        ? [...all].sort((a, b) => Math.max(stamp(b.createdAt), stamp(b.lastDoneAt)) - Math.max(stamp(a.createdAt), stamp(a.lastDoneAt))).slice(0, 12)
        : [...all].sort((a, b) => a.title.localeCompare(b.title, "fr"));
  const shown = q ? base.filter((l) => l.title.toLowerCase().includes(q) || l.ingredients?.some((i) => i.toLowerCase().includes(q))) : base;
  return (
    <Sheet
      open
      onClose={onClose}
      title="Qu'est-ce qu'on mange ?"
      tall
      footer={<Button className="w-full" onClick={onClose}>{menuCount ? `Voir la liste · ${plural(menuCount, "recette", "recettes")}` : "Fermer"}</Button>}
    >
      <div className="flex flex-col gap-3">
        <label className="flex items-center gap-2 rounded-medium bg-card px-3">
          <Icon name="search" size={18} className="text-sec" />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Chercher une recette, un ingrédient…" aria-label="Chercher une recette" className="h-11 min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted" />
        </label>
        <div className="flex gap-1.5" role="group" aria-label="Recettes à montrer">
          {([["all", "Toutes"], ["liked", "J'aime"], ["recent", "Récentes"]] as const).map(([k, label]) => (
            <button key={k} type="button" aria-pressed={tab === k} onClick={() => setTab(k)} className={`h-9 rounded-full px-4 text-sm font-bold ${tab === k ? "bg-ecorce text-sable" : "bg-card text-ecorce shadow-[inset_0_0_0_1.5px_var(--color-line)]"}`}>
              {label}
            </button>
          ))}
        </div>
        {all.length === 0 && <p className="py-6 text-center text-sm text-sec">Aucune recette pour l'instant : ajoutez-en une depuis Mes idées.</p>}
        {all.length > 0 && shown.length === 0 && <p className="py-6 text-center text-sm text-sec">Aucune recette ne correspond.</p>}
        <ul className="flex flex-col gap-2">
          {shown.map((r) => {
            const noIngredients = !r.ingredients?.some((i) => i.trim());
            const on = inMenu(r.id);
            const count = r.ingredients?.filter((i) => i.trim()).length ?? 0;
            return (
              <li key={r.id} className={`flex items-center gap-3 rounded-medium bg-card p-2 pr-2.5 ${on ? "shadow-[inset_0_0_0_2px_var(--color-done)]" : ""}`}>
                <span className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-small bg-recette-bg text-recette-ink">
                  {r.imageUrl ? <img src={r.imageUrl} alt="" className="h-full w-full object-cover" loading="lazy" /> : <Icon name="cutlery" size={20} />}
                </span>
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate text-[15px] font-bold">{r.title}</span>
                  <span className={`text-xs ${noIngredients ? "font-semibold text-terracotta" : "text-sec"}`}>
                    {noIngredients ? "Pas encore d'ingrédients" : `${plural(count, "ingrédient", "ingrédients")}${r.servings ? ` · ${r.servings} pers.` : ""}`}
                  </span>
                </span>
                {noIngredients ? (
                  <button type="button" onClick={() => onComplete(r)} aria-label={`Compléter les ingrédients de ${r.title}`} className="flex h-9 items-center gap-1 rounded-full bg-sable-2 pr-3.5 pl-2.5 text-[13px] font-bold text-terracotta">
                    <Icon name="pencil" size={14} />Compléter
                  </button>
                ) : (
                  <button
                    type="button"
                    aria-pressed={on}
                    aria-label={on ? `Retirer ${r.title} du menu` : `Ajouter ${r.title} au menu`}
                    onClick={() => onToggle(r)}
                    className={`flex h-9 items-center justify-center gap-1 rounded-full text-[13px] font-bold text-white ${on ? "bg-done-solid pr-3.5 pl-2.5" : "w-9 bg-terracotta"}`}
                  >
                    <Icon name={on ? "check" : "plus"} size={16} strokeWidth={2.6} />
                    {on && "Au menu"}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      </div>
    </Sheet>
  );
}

function ShareSheet({ text, shared, onDone }: { text: string; shared: string[]; onDone: (message?: string) => void }) {
  const canShare = typeof navigator.share === "function";
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      onDone("Liste copiée");
    } catch {
      onDone("La copie n'est pas possible dans ce navigateur");
    }
  };
  const share = async () => {
    try {
      await navigator.share({ title: "Courses Tribbae", text });
      onDone();
    } catch {
      // Partage annulé
    }
  };
  return (
    <Sheet open onClose={() => onDone()} title="Envoyer la liste">
      <div className="flex flex-col gap-3">
        <Note>
          {shared.length ? (
            <><b>Déjà partagée avec la tribu.</b> {shared.join(", ")} {shared.length > 1 ? "voient" : "voit"} la même liste et {shared.length > 1 ? "peuvent" : "peut"} cocher en magasin.</>
          ) : (
            <>Invitez vos proches dans votre tribu (Ma tribu) : ils verront la même liste et pourront cocher en magasin.</>
          )}
        </Note>
        <span className="text-xs font-extrabold tracking-[0.8px] text-sec uppercase">Pour quelqu'un sans Tribbae : en texte</span>
        <pre className="max-h-64 overflow-auto rounded-medium bg-card p-3.5 font-sans text-[13px] leading-relaxed whitespace-pre-wrap text-ecorce-2">{text}</pre>
        <div className="flex gap-2">
          <Button className="flex-1" variant={canShare ? "ghost" : undefined} icon="copy" onClick={copy}>Copier</Button>
          {canShare && <Button className="flex-1" icon="share" onClick={share}>Partager…</Button>}
        </div>
      </div>
    </Sheet>
  );
}

function Note({ children }: { children: ReactNode }) {
  return <p className="rounded-medium bg-livre-bg px-3.5 py-3 text-sm text-livre-ink">{children}</p>;
}

function LineSheet({ line, onClose, onQuantity, onAisle, onRemove }: {
  line: ShoppingLine; onClose: () => void; onQuantity: (qty: string) => void; onAisle: (aisle: string) => void; onRemove: () => void;
}) {
  const [qty, setQty] = useState(line.qty);
  const [aisle, setAisle] = useState<string>(line.aisle);
  return (
    <Sheet open onClose={onClose} title={line.name}>
      <form
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          if (qty.trim() !== line.qty) onQuantity(qty.trim());
          if (aisle !== line.aisle) onAisle(aisle);
          onClose();
        }}
      >
        <label className="flex flex-col gap-1.5">
          <span className="text-sm font-bold">Quantité</span>
          <input
            value={qty}
            onChange={(e) => setQty(e.target.value)}
            maxLength={30}
            placeholder="Par exemple 2 bouteilles"
            className="h-12 rounded-medium bg-card px-3.5 text-[15px] outline-none focus:ring-2 focus:ring-terracotta"
          />
          {!line.itemId && <span className="text-xs text-sec">Retenue pour ce produit. Videz le champ pour revenir à la quantité des recettes.</span>}
        </label>
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1.5 text-sm font-bold">Rayon</legend>
          <div className="flex flex-wrap gap-1.5">
            {AISLES.map((a) => (
              <button
                key={a.id}
                type="button"
                aria-pressed={aisle === a.id}
                onClick={() => setAisle(a.id)}
                className={`flex h-9 items-center gap-1.5 rounded-full px-3 text-[13px] font-bold ${aisle === a.id ? "bg-ecorce text-sable" : AISLE_STYLE[a.id].tone}`}
              >
                <Icon name={AISLE_STYLE[a.id].icon} size={14} />{a.name}
              </button>
            ))}
          </div>
          {!line.itemId && <span className="text-xs text-sec">Retenu pour la tribu, d'une liste à l'autre.</span>}
        </fieldset>
        <Button type="submit">Enregistrer</Button>
        <Button type="button" variant="ghost" icon="trash" onClick={onRemove}>
          Retirer de la liste
        </Button>
        {!line.itemId && <span className="-mt-2 text-center text-xs text-sec">Retirer un ingrédient ne touche pas la recette.</span>}
      </form>
    </Sheet>
  );
}
