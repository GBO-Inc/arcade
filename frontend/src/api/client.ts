const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
export const ACCESS_TOKEN_KEY = 'arcade_access_token';

const unauthorizedHandlers = new Set<() => void>();
type ApiRequestInit = Omit<RequestInit, 'body'> & { body?: unknown };

export interface FieldError {
  field: string;
  message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly message: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, message: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.message = message;
    this.fieldErrors = fieldErrors;
  }

  /** The first server message for a request field, for showing it next to that field. */
  fieldError(field: string) {
    return this.fieldErrors.find((error) => error.field === field)?.message;
  }
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string) {
  // localStorage keeps the token after a page refresh. The backend reads the token from the
  // Authorization header, not a cookie, so an httpOnly cookie is not an option without backend
  // changes. The trade-off is that script injected through XSS could read the token, so never
  // render untrusted HTML.
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

  // The backend answers errors with a ProblemDetail, which puts the text in `detail`.
  if (body && typeof body === 'object') {
    for (const key of ['detail', 'message'] as const) {
      const value = (body as Record<string, unknown>)[key];
      if (typeof value === 'string' && value) {
        return value;
      }
    }
  }

  return statusText || 'Request failed';
}

// A 400 from bean validation lists the rejected fields in `fieldErrors`.
function getFieldErrors(body: unknown): FieldError[] {
  if (!body || typeof body !== 'object') {
    return [];
  }
  const fieldErrors = (body as Record<string, unknown>).fieldErrors;
  if (!Array.isArray(fieldErrors)) {
    return [];
  }
  return fieldErrors.filter(
    (error): error is FieldError =>
      typeof error?.field === 'string' && typeof error?.message === 'string',
  );
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

  // Without a token a 401 means bad credentials (login), not an expired session.
  if (response.status === 401 && token) {
    unauthorizedHandlers.forEach((handler) => handler());
  }

  const responseBody = await parseResponse(response);
  if (!response.ok) {
    throw new ApiError(
      response.status,
      getErrorMessage(responseBody, response.statusText),
      getFieldErrors(responseBody),
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
  put<T>(path: string, body?: unknown) {
    return request<T>(path, { method: 'PUT', body });
  },
};
