import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import Icon, { type IconName } from "./Icon";

type Variant = "primary" | "secondary" | "ai" | "ghost" | "dark" | "danger";

const VARIANTS: Record<Variant, string> = {
  primary: "bg-terracotta text-white hover:bg-terracotta-dark",
  secondary: "border-2 border-ecorce bg-transparent text-ecorce hover:bg-ecorce/5",
  ai: "bg-prune text-sable hover:bg-[#4d3848]",
  ghost: "bg-card text-ecorce border-[1.5px] border-line-2 hover:bg-sable-2/60",
  dark: "bg-ecorce text-sable hover:bg-black",
  danger: "bg-card text-sortie-ink border-[1.5px] border-sortie-bg hover:bg-sortie-bg",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: "md" | "sm";
  icon?: IconName;
  loading?: boolean;
  block?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", size = "md", icon, loading, block, className = "", children, disabled, ...rest },
  ref,
) {
  const sizing = size === "md" ? "h-[52px] px-5 rounded-medium text-[15px]" : "h-10 px-3.5 rounded-small text-[13px]";
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 font-bold transition-colors disabled:opacity-55 ${sizing} ${VARIANTS[variant]} ${block ? "w-full" : ""} ${className}`}
      {...rest}
    >
      {loading ? <Spinner size={18} /> : icon && <Icon name={icon} size={size === "md" ? 20 : 16} />}
      {children}
    </button>
  );
});

export function IconButton({
  icon, label, className = "", size = 44, iconSize = 21, ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { icon: IconName; label: string; size?: number; iconSize?: number }) {
  return (
    <button
      aria-label={label}
      title={label}
      className={`flex items-center justify-center rounded-full transition-colors ${className}`}
      style={{ width: size, height: size }}
      {...rest}
    >
      <Icon name={icon} size={iconSize} />
    </button>
  );
}

export function Spinner({ size = 24, className = "" }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" className={`animate-spin ${className}`} aria-hidden="true">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeOpacity="0.2" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function PageSpinner() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center text-terracotta" role="status" aria-label="Chargement">
      <Spinner size={32} />
    </div>
  );
}

/** Champ de saisie avec libellé visible. */
export const Field = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; icon?: IconName }>(
  function Field({ label, hint, icon, id, className = "", ...rest }, ref) {
    const fid = id ?? `f-${label.replace(/\W+/g, "-").toLowerCase()}`;
    return (
      <div className="flex flex-col gap-1.5">
        <label htmlFor={fid} className="text-[13px] font-bold text-sec">{label}</label>
        <div className="flex h-[52px] items-center gap-2.5 rounded-medium border-[1.5px] border-line-2 bg-card px-4 text-muted focus-within:border-terracotta">
          {icon && <Icon name={icon} size={19} />}
          <input
            ref={ref}
            id={fid}
            className={`min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ecorce outline-none placeholder:text-muted ${className}`}
            {...rest}
          />
        </div>
        {hint && <span className="text-xs text-sec">{hint}</span>}
      </div>
    );
  },
);

export function TextArea({ label, id, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string }) {
  const fid = id ?? `f-${label.replace(/\W+/g, "-").toLowerCase()}`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={fid} className="text-[13px] font-bold text-sec">{label}</label>
      <textarea
        id={fid}
        className="min-h-[96px] resize-y rounded-medium border-[1.5px] border-line-2 bg-card px-4 py-3 text-[15px] text-ecorce outline-none placeholder:text-muted focus:border-terracotta"
        {...rest}
      />
    </div>
  );
}

/** Pastille de filtre arrondie. */
export function Chip({
  active, onClick, children, activeClass = "bg-ecorce text-sable", idleClass = "bg-card text-ecorce border-[1.5px] border-line-2", className = "",
}: { active?: boolean; onClick?: () => void; children: ReactNode; activeClass?: string; idleClass?: string; className?: string }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`h-[38px] shrink-0 rounded-full px-4 text-sm font-semibold transition-colors ${active ? activeClass : idleClass} ${className}`}
    >
      {children}
    </button>
  );
}

/** Interrupteur accessible (case à cocher stylée). */
export function Toggle({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-7 w-[46px] shrink-0 rounded-full transition-colors ${checked ? "bg-ecorce" : "bg-dash"}`}
    >
      <span className={`absolute top-[3px] h-[22px] w-[22px] rounded-full bg-sable transition-all ${checked ? "right-[3px]" : "right-[21px]"}`} />
    </button>
  );
}

/**
 * En-tête des écrans principaux (Explorer, Mes idées, Ma tribu) : même titre,
 * même sous-titre, action éventuelle à droite. Même composant côté Android
 * (`PageHeader` dans ui/campfire/Campfire.kt).
 */
export function PageHeader({ title, subtitle, action }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div className="flex min-w-0 flex-col gap-0.5">
        <h1 className="font-display text-[32px] leading-tight font-extrabold tracking-[-0.8px] md:text-[40px]">{title}</h1>
        {subtitle && <span className="text-sm text-sec md:text-base">{subtitle}</span>}
      </div>
      {action && <div className="shrink-0 pt-1">{action}</div>}
    </div>
  );
}

export function SectionTitle({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <h2 className="font-display text-xl font-bold">{title}</h2>
      {action}
    </div>
  );
}

export function EmptyState({ icon, title, text, action }: { icon: IconName; title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-large border-2 border-dashed border-dash px-6 py-10 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-[18px] bg-sable-2 text-terracotta">
        <Icon name={icon} size={26} />
      </span>
      <p className="font-display text-lg font-bold">{title}</p>
      {text && <p className="max-w-sm text-sm text-sec">{text}</p>}
      {action}
    </div>
  );
}

export function ErrorNote({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-small bg-sortie-bg px-4 py-2.5 text-sm font-medium text-sortie-ink">
      {children}
    </p>
  );
}

export function Avatar({ name, color = "#F28C38", ink = "var(--color-ecorce)", size = 26, rounded = "rounded-full", className = "" }: {
  name?: string; color?: string; ink?: string; size?: number; rounded?: string; className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center font-display font-extrabold ${rounded} ${className}`}
      style={{ width: size, height: size, background: color, color: ink, fontSize: Math.round(size * 0.4) }}
    >
      {(name?.trim().charAt(0) || "?").toUpperCase()}
    </span>
  );
}


export function MenuItem({ icon, label, onClick, danger }: { icon: IconName; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex h-14 items-center gap-3 rounded-medium bg-card px-4 text-left text-[15px] font-semibold transition-colors hover:bg-sable-2 ${danger ? "text-sortie-ink" : ""}`}
    >
      <Icon name={icon} size={20} />
      {label}
    </button>
  );
}
