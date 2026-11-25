import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ACCESS_TOKEN_KEY } from '../api/client';
import { AppRoutes } from '../App';
import { AuthProvider } from '../hooks/AuthContext';

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

function fillAndSubmit(username: string, password: string) {
  fireEvent.change(screen.getByLabelText('Username'), {
    target: { value: username },
  });
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: password },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  it('stores the token and lands on the lobby after a valid submit', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation((input: RequestInfo | URL) =>
          Promise.resolve(
            String(input).endsWith('/api/auth/login')
              ? jsonResponse({ accessToken: 'new-token' })
              : String(input).endsWith('/api/rooms')
                ? jsonResponse([])
                : jsonResponse({ id: 7, username: 'alice' }),
          ),
        ),
    );
    renderAt('/login');

    fillAndSubmit('alice', 'secret-pass');

    expect(await screen.findByRole('heading', { name: 'Lobby' })).toBeVisible();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('new-token');
  });

  it('shows one generic message for a failed login', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ detail: 'Invalid username or password' }, 401),
        ),
    );
    renderAt('/login');

    fillAndSubmit('alice', 'wrong-pass');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Invalid username or password',
    );
    expect(screen.getByLabelText('Username')).not.toBeInvalid();
    expect(screen.getByLabelText('Password')).not.toBeInvalid();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
  });

  it('shows an error next to an empty field and does not call the server', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderAt('/login');

    fillAndSubmit('', '');

    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(
      'Enter your username',
    );
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(
      'Enter your password',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows backend field errors next to the right field', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            status: 400,
            message: 'Validation failed',
            fieldErrors: [{ field: 'password', message: 'must not be blank' }],
          },
          400,
        ),
      ),
    );
    renderAt('/login');

    fillAndSubmit('alice', 'x');

    await waitFor(() =>
      expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(
        'must not be blank',
      ),
    );
    expect(screen.getByLabelText('Username')).not.toBeInvalid();
  });

  it('disables the button and sends one request while signing in', async () => {
    let finishLogin: (response: Response) => void = () => undefined;
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) =>
      String(input).endsWith('/api/auth/login')
        ? new Promise<Response>((resolve) => {
            finishLogin = resolve;
          })
        : Promise.resolve(
            jsonResponse(String(input).endsWith('/api/rooms') ? [] : { id: 7 }),
          ),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderAt('/login');

    fillAndSubmit('alice', 'secret-pass');
    const button = screen.getByRole('button', { name: 'Sign in' });
    expect(button).toBeDisabled();
    fireEvent.submit(button.closest('form')!);
    fireEvent.submit(button.closest('form')!);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    finishLogin(jsonResponse({ accessToken: 'new-token' }));
    expect(await screen.findByRole('heading', { name: 'Lobby' })).toBeVisible();
  });

  it('is a form with inputs in tab order followed by the submit button', () => {
    renderAt('/login');

    const button = screen.getByRole('button', { name: 'Sign in' });
    const names = Array.from(
      button.closest('form')!.querySelectorAll('input, button'),
    ).map(
      (element) => element.getAttribute('name') ?? element.getAttribute('type'),
    );
    expect(names).toEqual(['username', 'password', 'submit']);
  });

  it('logs out with the logout button and clears the token', async () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-token');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ id: 7, username: 'alice' })),
    );
    renderAt('/lobby');

    fireEvent.click(await screen.findByRole('button', { name: 'Log out' }));

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeVisible();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Log out' })).toBeNull();
  });
});
