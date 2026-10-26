const TOKEN_KEY = 'hiretrack.token';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

let onUnauthorized: () => void = () => {};

/** 토큰 만료 등으로 401 이 오면 호출할 콜백(로그아웃 처리)을 등록한다. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStorage.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.');
  }

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError(
      response.status,
      error?.code ?? 'UNKNOWN',
      error?.message ?? '요청을 처리하지 못했습니다.',
    );
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
