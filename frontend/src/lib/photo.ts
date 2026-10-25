import { useEffect, useRef, useState } from "react";
import { photos } from "./api";

/** Plus grand côté gardé, comme le serveur (backend/internal/photo) et Android. */
export const PHOTO_MAX_SIDE = 1600;

/**
 * Réduit la photo choisie avant l'envoi (redressée selon son orientation EXIF,
 * réencodée en JPEG sans métadonnées) : l'envoi est rapide même en 4G. Si le
 * navigateur ne sait pas la décoder, le fichier part tel quel et le serveur
 * s'en charge (ou la refuse).
 */
export async function shrinkPhoto(file: Blob): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    const scale = Math.min(1, PHOTO_MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const ctx = canvas.getContext("2d");
    if (!ctx) return file;
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
    return blob ?? file;
  } catch {
    return file;
  }
}

/** Réduit puis envoie la photo ; renvoie son adresse sur le serveur. */
export async function uploadPhoto(file: Blob): Promise<string> {
  return photos.upload(await shrinkPhoto(file));
}

export type PhotoUpload = { status: "uploading" | "done" | "failed"; local: string; file: Blob; error?: string };

/** Envoi d'une photo : aperçu local immédiat, puis l'adresse renvoyée par le serveur. */
export function usePhotoUpload() {
  const [photo, setPhoto] = useState<PhotoUpload>();
  const request = useRef(0);
  useEffect(() => {
    const local = photo?.local;
    return () => {
      if (local) URL.revokeObjectURL(local);
    };
  }, [photo?.local]);

  const send = (file: Blob, onUploaded: (url: string) => void) => {
    const id = ++request.current;
    const local = photo?.file === file ? photo.local : URL.createObjectURL(file);
    setPhoto({ status: "uploading", local, file });
    uploadPhoto(file).then(
      (url) => {
        if (id !== request.current) return;
        setPhoto({ status: "done", local, file });
        onUploaded(url);
      },
      (e) => id === request.current && setPhoto({ status: "failed", local, file, error: (e as Error).message }),
    );
  };
  const reset = () => {
    request.current++;
    setPhoto(undefined);
  };
  return { photo, send, reset };
}
