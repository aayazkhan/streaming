import { describe, expect, it } from "vitest";
import { decodeRoleClaim } from "./tokenStore";

function base64UrlEncode(json: unknown): string {
  return btoa(JSON.stringify(json)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fakeJwt(payload: unknown): string {
  return `${base64UrlEncode({ alg: "HS256" })}.${base64UrlEncode(payload)}.signature`;
}

describe("decodeRoleClaim", () => {
  it("reads the role claim from a well-formed JWT", () => {
    expect(decodeRoleClaim(fakeJwt({ sub: "user-1", role: "ADMIN" }))).toBe("ADMIN");
  });

  it("returns null when there is no role claim", () => {
    expect(decodeRoleClaim(fakeJwt({ sub: "user-1" }))).toBeNull();
  });

  it("returns null for a malformed token instead of throwing", () => {
    expect(decodeRoleClaim("not-a-jwt")).toBeNull();
  });
});
