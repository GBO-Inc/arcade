import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { apiClient, ACCESS_TOKEN_KEY } from './api/client';
import { AppRoutes } from './App';
import { AuthProvider } from './hooks/AuthContext';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('authentication', () => {
  it('restores a valid stored token on app start', async () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-token');
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(jsonResponse({ id: 7, email: 'player@example.com' })),
      );
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/lobby');

    expect(await screen.findByRole('heading', { name: 'Lobby' })).toBeVisible();
    const requestOptions = fetchMock.mock.calls[0][1] as RequestInit;
    expect(new Headers(requestOptions.headers).get('Authorization')).toBe(
      'Bearer valid-token',
    );
  });

  it('clears an invalid token and redirects to login on app start', async () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-token');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 401)));

    renderAt('/profile');

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeVisible();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Profile' })).toBeNull();
  });

  it('returns to the originally requested protected page after login', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      if (String(input).endsWith('/api/auth/login')) {
        return Promise.resolve(
          jsonResponse({
            accessToken: 'new-token',
            tokenType: 'Bearer',
            expiresAt: '2026-11-15T00:00:00Z',
          }),
        );
      }

      return Promise.resolve(
        jsonResponse({ id: 7, email: 'player@example.com' }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/match/room-42');

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeVisible();
    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'player@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(
      await screen.findByRole('heading', { name: 'Match room-42' }),
    ).toBeVisible();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('new-token');
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          email: 'player@example.com',
          password: 'secret',
        }),
      }),
    );
  });

  it('logs out after any API call returns 401', async () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-token');
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ id: 7, email: 'player@example.com' }),
      )
      .mockResolvedValueOnce(jsonResponse({ message: 'Unauthorized' }, 401));
    vi.stubGlobal('fetch', fetchMock);

    function TriggerUnauthorizedRequest() {
      return (
        <button
          onClick={() => {
            void apiClient.get('/api/private').catch(() => undefined);
          }}
        >
          Load private data
        </button>
      );
    }

    render(
      <MemoryRouter initialEntries={['/profile']}>
        <AuthProvider>
          <AppRoutes />
          <TriggerUnauthorizedRequest />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole('heading', { name: 'Profile' }),
    ).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Load private data' }));

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeVisible();
    await waitFor(() =>
      expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull(),
    );
  });
});
