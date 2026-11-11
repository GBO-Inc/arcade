import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Button, Card, Dialog, Input, Toast } from './index';

describe('shared UI components', () => {
  it('renders buttons with variants and handles clicks', () => {
    const onClick = vi.fn();
    render(
      <Button variant="secondary" onClick={onClick}>
        Join game
      </Button>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Join game' }));

    expect(onClick).toHaveBeenCalledOnce();
    expect(screen.getByRole('button')).toHaveClass('bg-slate-800');
  });

  it('associates input labels and error text accessibly', () => {
    render(<Input label="Email" error="Enter a valid email" />);

    const input = screen.getByRole('textbox', { name: 'Email' });
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Enter a valid email');
  });

  it('renders card content', () => {
    render(
      <Card>
        <h2>Player stats</h2>
      </Card>,
    );

    expect(
      screen.getByRole('heading', { name: 'Player stats' }),
    ).toBeInTheDocument();
  });

  it('closes the dialog from its button, backdrop, and Escape key', () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <Dialog open title="Confirm match" onClose={onClose}>
        Match details
      </Dialog>,
    );

    expect(
      screen.getByRole('dialog', { name: 'Confirm match' }),
    ).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledOnce();

    onClose.mockClear();
    fireEvent.click(screen.getByRole('button', { name: 'Close dialog' }));
    expect(onClose).toHaveBeenCalledOnce();

    onClose.mockClear();
    fireEvent.mouseDown(screen.getByRole('dialog').parentElement!);
    expect(onClose).toHaveBeenCalledOnce();

    rerender(
      <Dialog open={false} title="Confirm match" onClose={onClose}>
        Match details
      </Dialog>,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('announces toast messages and supports dismissal', () => {
    const onDismiss = vi.fn();
    render(
      <Toast
        variant="error"
        message="Could not save changes"
        onDismiss={onDismiss}
      />,
    );

    fireEvent.click(
      screen.getByRole('button', { name: 'Dismiss notification' }),
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Could not save changes',
    );
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
