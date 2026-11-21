import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ACCESS_TOKEN_KEY } from '../api/client';
import { AppRoutes } from '../App';
import { AuthProvider } from '../hooks/AuthContext';

const ME = { id: 7, username: 'alice', displayName: 'Alice' };
const OWN_PROFILE = {
  id: 7,
  displayName: 'Alice',
  avatarColour: '#3B82F6',
  createdAt: '2026-11-01T10:00:00Z',
};
const OTHER_PROFILE = {
  id: 8,
  displayName: 'Bob',
  avatarColour: '#EF4444',
  createdAt: '2026-11-02T10:00:00Z',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Answers GETs from the given profiles and PUTs with `onPut`. */
function mockApi(onPut: (body: unknown) => Response = () => jsonResponse({})) {
  const fetchMock = vi
    .fn()
    .mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse(ME));
      }
      if (init?.method === 'PUT') {
        return Promise.resolve(onPut(JSON.parse(String(init.body))));
      }
      if (url.endsWith('/api/profile/7')) {
        return Promise.resolve(jsonResponse(OWN_PROFILE));
      }
      if (url.endsWith('/api/profile/8')) {
        return Promise.resolve(jsonResponse(OTHER_PROFILE));
      }
      return Promise.resolve(
        jsonResponse({ message: 'Player not found' }, 404),
      );
    });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderAt(path: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-token');
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ProfilePage', () => {
  it('shows an editable form with the current values for the own profile', async () => {
    mockApi();
    renderAt('/profile');

    expect(await screen.findByLabelText('Display name')).toHaveValue('Alice');
    expect(screen.getByRole('radio', { name: 'Blue' })).toBeChecked();
    expect(screen.getByRole('button', { name: 'Save' })).toBeVisible();
  });

  it('saves the changes and shows a success message', async () => {
    const fetchMock = mockApi((body) =>
      jsonResponse({ ...OWN_PROFILE, ...(body as object) }),
    );
    renderAt('/profile');

    fireEvent.change(await screen.findByLabelText('Display name'), {
      target: { value: 'Queen Alice' },
    });
    fireEvent.click(screen.getByRole('radio', { name: 'Green' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Profile saved',
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/profile',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          displayName: 'Queen Alice',
          avatarColour: '#22C55E',
        }),
      }),
    );
    expect(screen.getByLabelText('Display name')).toHaveValue('Queen Alice');
  });

  it('shows server validation errors next to the right field', async () => {
    mockApi(() =>
      jsonResponse(
        {
          status: 400,
          message: 'Validation failed',
          fieldErrors: [
            { field: 'displayName', message: 'size must be between 2 and 20' },
            {
              field: 'avatarColour',
              message: 'must be one of the palette colours',
            },
          ],
        },
        400,
      ),
    );
    renderAt('/profile');

    fireEvent.change(await screen.findByLabelText('Display name'), {
      target: { value: 'A' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText('size must be between 2 and 20'),
    ).toBeVisible();
    expect(screen.getByLabelText('Display name')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(
      screen.getByText('must be one of the palette colours'),
    ).toBeVisible();
    expect(screen.queryByText('Profile saved')).toBeNull();
  });

  it('shows another player read-only', async () => {
    const fetchMock = mockApi();
    renderAt('/profile/8');

    expect(await screen.findByRole('heading', { name: 'Bob' })).toBeVisible();
    expect(screen.queryByLabelText('Display name')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
    expect(
      fetchMock.mock.calls.some(
        ([, init]) => (init as RequestInit | undefined)?.method === 'PUT',
      ),
    ).toBe(false);
  });

  it('shows the own profile as editable when opened by id', async () => {
    mockApi();
    renderAt('/profile/7');

    expect(await screen.findByLabelText('Display name')).toHaveValue('Alice');
  });

  it('shows an error for an unknown player', async () => {
    mockApi();
    renderAt('/profile/999');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Player not found',
    );
  });
});
