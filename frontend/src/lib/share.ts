/** Copie dans le presse-papiers, ou ouvre la feuille de partage native sur mobile. */
export async function shareOrCopy(url: string, title: string): Promise<"shared" | "copied" | "failed"> {
  if (navigator.share && matchMedia("(pointer: coarse)").matches) {
    try {
      await navigator.share({ title, url });
      return "shared";
    } catch (e) {
      if ((e as Error).name === "AbortError") return "shared";
    }
  }
  try {
    await navigator.clipboard.writeText(url);
    return "copied";
  } catch {
    return "failed";
  }
}
