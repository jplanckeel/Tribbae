import { describe, expect, it } from "vitest";
import { CATEGORIES, categoryBySlug, normalizeCategory } from "./categories";

// Mêmes cas que composeApp/.../LinkCategoryTest.kt
describe("catégorie Idée fusionnée dans Activité", () => {
  it("n'est plus proposée", () => {
    expect(CATEGORIES.map((c) => c.key)).not.toContain("LINK_CATEGORY_IDEE");
  });

  it("est relue en Activité, comme une catégorie absente", () => {
    expect(normalizeCategory("LINK_CATEGORY_IDEE")).toBe("LINK_CATEGORY_ACTIVITE");
    expect(normalizeCategory(1)).toBe("LINK_CATEGORY_ACTIVITE");
    expect(normalizeCategory("1")).toBe("LINK_CATEGORY_ACTIVITE");
    expect(normalizeCategory("LINK_CATEGORY_UNSPECIFIED")).toBe("LINK_CATEGORY_ACTIVITE");
    expect(normalizeCategory("")).toBe("LINK_CATEGORY_ACTIVITE");
    expect(normalizeCategory(undefined)).toBe("LINK_CATEGORY_ACTIVITE");
    expect(normalizeCategory("LINK_CATEGORY_RECETTE")).toBe("LINK_CATEGORY_RECETTE");
    expect(normalizeCategory(4)).toBe("LINK_CATEGORY_EVENEMENT");
  });

  it("garde son ancienne adresse, qui mène aux activités", () => {
    expect(categoryBySlug("idee")?.key).toBe("LINK_CATEGORY_ACTIVITE");
  });
});
