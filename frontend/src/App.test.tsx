import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('ARC-10 App Shell Navigation', () => {
    it('renders the lobby by default and navigates between placeholder pages', () => {
        render(<App />);

        expect(screen.getByRole('heading', { name: /lobby/i })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('link', { name: /leaderboard/i }));
        expect(screen.getByRole('heading', { name: /leaderboard/i })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('link', { name: /history/i }));
        expect(screen.getByRole('heading', { name: /match history/i })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('link', { name: /profile/i }));
        expect(screen.getByRole('heading', { name: /player profile/i })).toBeInTheDocument();
    });
});