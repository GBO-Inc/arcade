import { useState } from 'react';
import { Button, Card, Dialog, Input, Toast } from './index';

export function UiKitDemo() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [toastVisible, setToastVisible] = useState(true);

  return (
    <div className="space-y-8">
      <div>
        <p className="text-sm font-medium uppercase tracking-wide text-indigo-400">
          Shared components
        </p>
        <h1 className="mt-1 text-2xl font-bold text-white">UI kit</h1>
      </div>

      <Card className="space-y-5">
        <h2 className="text-lg font-semibold text-white">Buttons and input</h2>
        <div className="flex flex-wrap gap-3">
          <Button>Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="danger">Danger</Button>
          <Button variant="ghost" size="sm">
            Ghost
          </Button>
          <Button disabled>Disabled</Button>
        </div>
        <div className="max-w-sm">
          <Input
            label="Player name"
            placeholder="Enter a name"
            hint="Visible to other players"
          />
        </div>
      </Card>

      <Card className="space-y-4">
        <h2 className="text-lg font-semibold text-white">Dialog and toast</h2>
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => setDialogOpen(true)}>Open dialog</Button>
          <Button variant="secondary" onClick={() => setToastVisible(true)}>
            Show toast
          </Button>
        </div>
        {toastVisible && (
          <div className="max-w-md">
            <Toast
              variant="success"
              message="Your changes have been saved."
              onDismiss={() => setToastVisible(false)}
            />
          </div>
        )}
      </Card>

      <Dialog
        open={dialogOpen}
        title="Ready to play?"
        onClose={() => setDialogOpen(false)}
      >
        <p className="text-sm text-slate-300">
          Invite a friend to start a new match.
        </p>
        <div className="mt-6 flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setDialogOpen(false)}>
            Cancel
          </Button>
          <Button onClick={() => setDialogOpen(false)}>Start match</Button>
        </div>
      </Dialog>
    </div>
  );
}
