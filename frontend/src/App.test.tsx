import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import App, { AppRoutes } from './App';

describe('App routing', () => {
  it('navigates between pages without reload', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Lobby' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Leaderboard' }));
    expect(
      screen.getByRole('heading', { name: 'Leaderboard' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'History' }));
    expect(
      screen.getByRole('heading', { name: 'History' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Register' }));
    expect(
      screen.getByRole('heading', { name: 'Register' }),
    ).toBeInTheDocument();
  });

  it('loads a URL directly', () => {
    render(
      <MemoryRouter initialEntries={['/match/room-42']}>
        <AppRoutes />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', { name: 'Match room-42' }),
    ).toBeInTheDocument();
  });

  it('shows 404 on unknown route', () => {
    render(
      <MemoryRouter initialEntries={['/does-not-exist']}>
        <AppRoutes />
      </MemoryRouter>,
    );

    expect(screen.getByText(/404/i)).toBeInTheDocument();
  });
});
