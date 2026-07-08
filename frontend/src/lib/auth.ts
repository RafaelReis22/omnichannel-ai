// Guarda de tokens no browser. MVP pragmático: localStorage + refresh
// automático no 401 (ver api.ts). Access token de 15 min limita a janela
// de exposição; revisitar para cookie httpOnly quando houver BFF.

const ACCESS_KEY = "oc.accessToken";
const REFRESH_KEY = "oc.refreshToken";

export const tokenStore = {
  access(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(ACCESS_KEY);
  },
  refresh(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(REFRESH_KEY);
  },
  save(accessToken: string, refreshToken: string) {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
  isLoggedIn(): boolean {
    return this.access() !== null;
  },
};
