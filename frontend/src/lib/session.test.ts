import { describe, expect, it } from "vitest";
import { RENEW_AFTER_MS, shouldRenew, tokenIssuedAt } from "./session";

// Mêmes règles qu'Android (composeApp/.../data/TokenRenewalTest.kt).
const b64url = (s: string) => btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const jwt = (payload: string) => `${b64url('{"alg":"HS256","typ":"JWT"}')}.${b64url(payload)}.signature`;

describe("renouvellement de la session", () => {
  const now = 1_800_000_000_000;

  it("lit la date d'émission du jeton", () => {
    expect(tokenIssuedAt(jwt('{"sub":"6a","iat":1799990000,"exp":1802582000}'))).toBe(1_799_990_000_000);
    expect(tokenIssuedAt("pas-un-jeton")).toBeNull();
    expect(tokenIssuedAt(jwt('{"sub":"6a"}'))).toBeNull();
  });

  it("renouvelle au plus une fois par jour", () => {
    expect(shouldRenew(jwt(`{"iat":${(now - 3_600_000) / 1000}}`), now)).toBe(false);
    expect(shouldRenew(jwt(`{"iat":${(now - 2 * RENEW_AFTER_MS) / 1000}}`), now)).toBe(true);
    expect(shouldRenew("illisible", now)).toBe(true);
  });
});
