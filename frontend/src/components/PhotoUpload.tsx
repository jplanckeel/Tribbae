import type { RefObject } from "react";
import type { PhotoUpload } from "../lib/photo";
import Icon from "./Icon";

// Photo prise ou choisie sur l'appareil, envoyée au serveur (backend/internal/photo).
// Même chose côté Android : data/ImagePickerHelper.kt + LinkViewModel.uploadLocalPhoto.
// L'envoi lui-même : usePhotoUpload (lib/photo.ts).

/**
 * Champ fichier caché, ouvert par un bouton : sur mobile, le système propose
 * l'appareil photo ou la galerie.
 */
export function PhotoInput({ inputRef, onFile }: { inputRef: RefObject<HTMLInputElement | null>; onFile: (file: File) => void }) {
  return (
    <input
      ref={inputRef}
      type="file"
      accept="image/*"
      hidden
      onChange={(e) => {
        const file = e.target.files?.[0];
        e.target.value = "";
        if (file) onFile(file);
      }}
    />
  );
}

/** Grande vignette de la photo, avec l'état de l'envoi. */
export function PhotoPreview({ photo, onPick, onRetry }: { photo?: PhotoUpload; onPick: () => void; onRetry: () => void }) {
  if (!photo) {
    return (
      <button
        type="button"
        onClick={onPick}
        className="flex h-44 flex-col items-center justify-center gap-2 rounded-large border-2 border-dashed border-line-2 bg-card text-sm font-bold text-ecorce transition-colors hover:border-ecorce"
      >
        <span className="flex h-11 w-11 items-center justify-center rounded-[14px] bg-[var(--color-recette-bg)] text-[var(--color-recette-ink)]"><Icon name="camera" size={22} /></span>
        Prendre ou choisir une photo
      </button>
    );
  }
  return (
    <div className="relative h-44 overflow-hidden rounded-large bg-sable-2">
      <img src={photo.local} alt="Votre photo" className="h-full w-full object-cover" />
      {photo.status === "uploading" && (
        <span role="status" className="absolute inset-0 flex items-center justify-center bg-black/35 text-sm font-bold text-white">
          Envoi de la photo…
        </span>
      )}
      {photo.status === "failed" && (
        <span role="alert" className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/55 px-6 text-center text-sm font-bold text-white">
          {photo.error || "L'envoi de la photo a échoué."}
          <button type="button" onClick={onRetry} className="rounded-full bg-white px-3.5 py-1.5 text-[13px] text-[#3a211b]">Réessayer</button>
        </span>
      )}
      <button
        type="button"
        onClick={onPick}
        className="absolute right-2.5 bottom-2.5 flex items-center gap-1.5 rounded-full bg-black/55 px-3 py-1.5 text-[13px] font-bold text-white hover:bg-black/70"
      >
        <Icon name="camera" size={14} />
        Changer
      </button>
    </div>
  );
}
