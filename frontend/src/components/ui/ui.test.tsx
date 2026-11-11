import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Button, Card, Dialog, TextField, Toast } from './index';

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
    expect(screen.getByRole('button')).toHaveClass('bg-ui-surface-raised');
  });

  it('shows a loading state and disables the button', () => {
    render(<Button loading>Saving</Button>);

    const button = screen.getByRole('button', { name: 'Saving' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(button.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('associates text field labels and error text accessibly', () => {
    render(<TextField label="Email" error="Enter a valid email" disabled />);

    const input = screen.getByRole('textbox', { name: 'Email' });
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toBeDisabled();
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

  it('traps focus, closes with Escape, and restores previous focus', () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <>
        <button type="button">Open dialog</button>
        <Dialog open={false} title="Confirm match" onClose={onClose}>
          <button type="button">Last action</button>
        </Dialog>
      </>,
    );

    const trigger = screen.getByRole('button', { name: 'Open dialog' });
    trigger.focus();
    rerender(
      <>
        <button type="button">Open dialog</button>
        <Dialog open title="Confirm match" onClose={onClose}>
          <button type="button">Last action</button>
        </Dialog>
      </>,
    );

    const closeButton = screen.getByRole('button', { name: 'Close dialog' });
    const lastAction = screen.getByRole('button', { name: 'Last action' });
    expect(closeButton).toHaveFocus();

    fireEvent.keyDown(lastAction, { key: 'Tab' });
    expect(closeButton).toHaveFocus();
    fireEvent.keyDown(closeButton, { key: 'Tab', shiftKey: true });
    expect(lastAction).toHaveFocus();

    expect(
      screen.getByRole('dialog', { name: 'Confirm match' }),
    ).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledOnce();
    rerender(
      <>
        <button type="button">Open dialog</button>
        <Dialog open={false} title="Confirm match" onClose={onClose}>
          <button type="button">Last action</button>
        </Dialog>
      </>,
    );
    expect(trigger).toHaveFocus();
  });

  it('announces and auto-removes toast messages', () => {
    const onDismiss = vi.fn();
    vi.useFakeTimers();
    render(
      <Toast
        variant="error"
        message="Could not save changes"
        onDismiss={onDismiss}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Could not save changes',
    );
    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(onDismiss).toHaveBeenCalledOnce();
    vi.useRealTimers();
  });

  it('dismisses toast messages on request', () => {
    const onDismiss = vi.fn();
    render(
      <Toast message="Changes saved" onDismiss={onDismiss} duration={10000} />,
    );

    fireEvent.click(
      screen.getByRole('button', { name: 'Dismiss notification' }),
    );

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
