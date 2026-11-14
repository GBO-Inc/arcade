const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
export const ACCESS_TOKEN_KEY = 'arcade_access_token';

const unauthorizedHandlers = new Set<() => void>();
type ApiRequestInit = Omit<RequestInit, 'body'> & { body?: unknown };

export class ApiError extends Error {
  readonly status: number;
  readonly message: string;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.message = message;
  }
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string) {
  // localStorage keeps the token available after a page refresh.
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function clearAccessToken() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export function subscribeToUnauthorized(handler: () => void) {
  unauthorizedHandlers.add(handler);
  return () => unauthorizedHandlers.delete(handler);
}

async function parseResponse(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return undefined;
  }

  if (response.headers.get('content-type')?.includes('json')) {
    try {
      return JSON.parse(text) as unknown;
    } catch {
      return text;
    }
  }

  return text;
}

function getErrorMessage(body: unknown, statusText: string) {
  if (typeof body === 'string' && body) {
    return body;
  }

  if (body && typeof body === 'object' && 'message' in body) {
    const message = body.message;
    if (typeof message === 'string' && message) {
      return message;
    }
  }

  return statusText || 'Request failed';
}

async function request<T>(
  path: string,
  options: ApiRequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  const token = getAccessToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const { body: requestBody, ...requestOptions } = options;
  const hasBody = requestBody !== undefined && requestBody !== null;
  const isFormData = hasBody && requestBody instanceof FormData;
  let body: BodyInit | undefined;
  if (hasBody) {
    if (isFormData || typeof requestBody === 'string') {
      body = requestBody as BodyInit;
    } else {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(requestBody);
    }
  }

  const baseUrl = API_BASE_URL.replace(/\/$/, '');
  const url = `${baseUrl}/${path.replace(/^\//, '')}`;
  const response = await fetch(url, { ...requestOptions, headers, body });

  if (!response) {
    throw new ApiError(0, 'No response received');
  }

  if (response.status === 401) {
    unauthorizedHandlers.forEach((handler) => handler());
  }

  const responseBody = await parseResponse(response);
  if (!response.ok) {
    throw new ApiError(
      response.status,
      getErrorMessage(responseBody, response.statusText),
    );
  }

  return responseBody as T;
}

export const apiClient = {
  request,
  get<T>(path: string, options?: Omit<RequestInit, 'method' | 'body'>) {
    return request<T>(path, { ...options, method: 'GET' });
  },
  post<T>(path: string, body?: unknown) {
    return request<T>(path, { method: 'POST', body });
  },
};
