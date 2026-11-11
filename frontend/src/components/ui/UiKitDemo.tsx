import { useState } from 'react';
import { Button, Card, Dialog, TextField, Toast } from './index';

export function UiKitDemo() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [toastVisible, setToastVisible] = useState(true);

  return (
    <div className="space-y-ui-8">
      <div>
        <p className="text-ui-sm font-medium uppercase tracking-wide text-ui-primary-focus">
          Shared components
        </p>
        <h1 className="mt-ui-1 text-ui-2xl font-bold text-ui-text">UI kit</h1>
      </div>

      <Card className="space-y-ui-5">
        <h2 className="text-ui-lg font-semibold text-ui-text">Buttons</h2>
        <div className="flex flex-wrap gap-ui-3">
          <Button>Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="danger">Danger</Button>
          <Button variant="ghost" size="sm">
            Ghost
          </Button>
          <Button disabled>Disabled</Button>
          <Button loading>Loading</Button>
        </div>
        <h2 className="text-ui-lg font-semibold text-ui-text">Text fields</h2>
        <div className="max-w-sm space-y-ui-4">
          <TextField
            label="Player name"
            placeholder="Enter a name"
            hint="Visible to other players"
          />
          <TextField
            label="Email"
            type="email"
            error="Enter a valid email address"
            defaultValue="not-an-email"
          />
          <TextField label="Username" disabled defaultValue="Guest player" />
        </div>
      </Card>

      <Card className="space-y-ui-4">
        <h2 className="text-ui-lg font-semibold text-ui-text">
          Dialog and toast
        </h2>
        <div className="flex flex-wrap gap-ui-3">
          <Button onClick={() => setDialogOpen(true)}>Open dialog</Button>
          <Button variant="secondary" onClick={() => setToastVisible(true)}>
            Show toast
          </Button>
        </div>
        {toastVisible && (
          <div className="max-w-md space-y-ui-3">
            <Toast
              variant="success"
              message="Your changes have been saved."
              onDismiss={() => setToastVisible(false)}
            />
            <Toast variant="error" message="Could not save your changes." />
          </div>
        )}
      </Card>

      <Dialog
        open={dialogOpen}
        title="Ready to play?"
        onClose={() => setDialogOpen(false)}
      >
        <p className="text-ui-sm text-ui-subtle">
          Invite a friend to start a new match.
        </p>
        <div className="mt-ui-6 flex justify-end gap-ui-3">
          <Button variant="secondary" onClick={() => setDialogOpen(false)}>
            Cancel
          </Button>
          <Button onClick={() => setDialogOpen(false)}>Start match</Button>
        </div>
      </Dialog>
    </div>
  );
}
