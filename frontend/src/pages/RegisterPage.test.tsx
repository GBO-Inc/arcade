import { fireEvent, render, screen } from '@testing-library/react';
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

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

function fillValid() {
  fill('Username', 'alice');
  fill('Email', 'alice@example.com');
  fill('Password', 'secret-pass');
  fill('Repeat password', 'secret-pass');
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
}

function mockRegisterResponse(body: unknown, status: number) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(body, status)));
}

describe('RegisterPage', () => {
  it('registers, signs in and lands on the lobby after a valid submit', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/register')) {
        return Promise.resolve(jsonResponse({ id: 7 }, 201));
      }
      if (url.endsWith('/api/auth/login')) {
        return Promise.resolve(jsonResponse({ accessToken: 'new-token' }));
      }
      if (url.endsWith('/api/rooms')) {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.resolve(jsonResponse({ id: 7, username: 'alice' }));
    });
    vi.stubGlobal('fetch', fetchMock);
    renderRegister();

    fillValid();
    submit();

    expect(await screen.findByRole('heading', { name: 'Lobby' })).toBeVisible();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('new-token');
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/auth/register',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          username: 'alice',
          email: 'alice@example.com',
          password: 'secret-pass',
        }),
      }),
    );
  });

  it('shows the backend validation rules inline and does not call the server', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderRegister();

    fill('Username', 'al');
    fill('Email', 'not-an-email');
    fill('Password', 'short');
    fill('Repeat password', 'different');
    submit();

    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(
      'Username must be 3 to 20 characters',
    );
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address',
    );
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(
      'Password must be 8 to 72 characters',
    );
    expect(
      screen.getByLabelText('Repeat password'),
    ).toHaveAccessibleDescription('Passwords do not match');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('requires every field', () => {
    vi.stubGlobal('fetch', vi.fn());
    renderRegister();

    submit();

    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(
      'Username must be 3 to 20 characters',
    );
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address',
    );
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(
      'Password must be 8 to 72 characters',
    );
  });

  it('rejects a username of only spaces and a password over 72 characters', () => {
    vi.stubGlobal('fetch', vi.fn());
    renderRegister();

    fill('Username', '     ');
    fill('Email', 'alice@example.com');
    fill('Password', 'x'.repeat(73));
    fill('Repeat password', 'x'.repeat(73));
    submit();

    expect(screen.getByLabelText('Username')).toBeInvalid();
    expect(screen.getByLabelText('Password')).toBeInvalid();
    expect(screen.getByLabelText('Email')).not.toBeInvalid();
  });

  it('shows backend 400 field errors next to the right field', async () => {
    mockRegisterResponse(
      {
        status: 400,
        message: 'Validation failed',
        fieldErrors: [
          { field: 'email', message: 'must be a well-formed email address' },
        ],
      },
      400,
    );
    renderRegister();

    fillValid();
    submit();

    expect(
      await screen.findByText('must be a well-formed email address'),
    ).toBeVisible();
    expect(screen.getByLabelText('Email')).toBeInvalid();
    expect(screen.getByLabelText('Username')).not.toBeInvalid();
  });

  it('shows a 409 duplicate username next to the username field', async () => {
    mockRegisterResponse({ detail: 'Username is already taken' }, 409);
    renderRegister();

    fillValid();
    submit();

    expect(await screen.findByText('Username is already taken')).toBeVisible();
    expect(screen.getByLabelText('Username')).toBeInvalid();
    expect(screen.getByLabelText('Email')).not.toBeInvalid();
  });

  it('shows a 409 duplicate email next to the email field', async () => {
    mockRegisterResponse({ detail: 'Email is already registered' }, 409);
    renderRegister();

    fillValid();
    submit();

    expect(
      await screen.findByText('Email is already registered'),
    ).toBeVisible();
    expect(screen.getByLabelText('Email')).toBeInvalid();
    expect(screen.getByLabelText('Username')).not.toBeInvalid();
  });

  it('shows a 409 that names neither field as a form message', async () => {
    mockRegisterResponse({ detail: 'Username or email is already taken' }, 409);
    renderRegister();

    fillValid();
    submit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Username or email is already taken',
    );
  });

  it('disables the button and sends one request while registering', () => {
    const fetchMock = vi
      .fn()
      .mockReturnValue(new Promise<Response>(() => undefined));
    vi.stubGlobal('fetch', fetchMock);
    renderRegister();

    fillValid();
    submit();
    const button = screen.getByRole('button', { name: 'Create account' });
    expect(button).toBeDisabled();
    fireEvent.submit(button.closest('form')!);
    fireEvent.submit(button.closest('form')!);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('is a form with inputs in tab order followed by the submit button', () => {
    renderRegister();

    const button = screen.getByRole('button', { name: 'Create account' });
    const names = Array.from(
      button.closest('form')!.querySelectorAll('input, button'),
    ).map(
      (element) => element.getAttribute('name') ?? element.getAttribute('type'),
    );
    expect(names).toEqual([
      'username',
      'email',
      'password',
      'repeatPassword',
      'submit',
    ]);
  });
});
