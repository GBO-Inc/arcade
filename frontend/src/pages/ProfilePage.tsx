import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  AVATAR_PALETTE,
  getProfile,
  updateOwnProfile,
  type Profile,
} from '../api/profile';
import { Button, TextField, Toast } from '../components/ui';
import { useAuth } from '../hooks/auth';

/** `/profile` is the own profile; `/profile/:userId` is anyone's, editable only if it is yours. */
export function ProfilePage() {
  const { userId } = useParams();
  const { user } = useAuth();
  const ownId = user?.id === undefined ? undefined : String(user.id);
  const targetId = userId ?? ownId;

  if (targetId === undefined) {
    return null;
  }
  // The key gives each profile a fresh component, so no state from the previous one leaks in.
  return (
    <ProfileView
      key={targetId}
      userId={targetId}
      editable={targetId === ownId}
    />
  );
}

function ProfileView({
  userId,
  editable,
}: {
  userId: string;
  editable: boolean;
}) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getProfile(userId)
      .then((loaded) => {
        if (active) {
          setProfile(loaded);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(
            error instanceof Error
              ? error.message
              : 'Could not load the profile',
          );
        }
      });
    return () => {
      active = false;
    };
  }, [userId]);

  return (
    <div className="mx-auto max-w-sm space-y-ui-4">
      {loadError ? (
        <p role="alert">{loadError}</p>
      ) : !profile ? (
        <p>Loading profile…</p>
      ) : editable ? (
        <ProfileForm profile={profile} />
      ) : (
        <ReadOnlyProfile profile={profile} />
      )}
    </div>
  );
}

function ReadOnlyProfile({ profile }: { profile: Profile }) {
  return (
    <div className="flex items-center gap-ui-4">
      <Swatch colour={profile.avatarColour} />
      <div>
        <h1 className="text-ui-xl font-bold">{profile.displayName}</h1>
        <p className="text-ui-sm text-ui-muted">
          Joined {new Date(profile.createdAt).toLocaleDateString()}
        </p>
      </div>
    </div>
  );
}

function ProfileForm({ profile }: { profile: Profile }) {
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [avatarColour, setAvatarColour] = useState<string>(
    profile.avatarColour ?? AVATAR_PALETTE[0].hex,
  );
  const [error, setError] = useState<ApiError | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSaved(false);
    setSaving(true);
    try {
      const updated = await updateOwnProfile({ displayName, avatarColour });
      // The server trims the name; show what was actually stored.
      setDisplayName(updated.displayName);
      setSaved(true);
    } catch (saveError) {
      setError(
        saveError instanceof ApiError
          ? saveError
          : new ApiError(0, 'Could not save the profile'),
      );
    } finally {
      setSaving(false);
    }
  }

  const colourError = error?.fieldError('avatarColour');
  const generalError =
    error && error.fieldErrors.length === 0 ? error.message : null;

  return (
    <form className="space-y-ui-4" onSubmit={handleSubmit} noValidate>
      <h1 className="text-ui-xl font-bold">Profile</h1>
      <TextField
        label="Display name"
        name="displayName"
        value={displayName}
        onChange={(event) => setDisplayName(event.target.value)}
        error={error?.fieldError('displayName')}
        hint="2 to 20 characters"
      />
      <fieldset
        className="space-y-ui-2"
        aria-describedby={colourError ? 'avatar-colour-error' : undefined}
      >
        <legend className="text-ui-sm font-medium text-ui-text">
          Avatar colour
        </legend>
        <div className="flex flex-wrap gap-ui-2">
          {AVATAR_PALETTE.map(({ hex, name }) => (
            <label key={hex} className="cursor-pointer">
              <input
                type="radio"
                name="avatarColour"
                value={hex}
                checked={avatarColour === hex}
                onChange={() => setAvatarColour(hex)}
                className="peer sr-only"
              />
              <Swatch
                colour={hex}
                className="peer-checked:ring-2 peer-checked:ring-ui-text peer-focus-visible:ring-2 peer-focus-visible:ring-ui-primary-focus"
              />
              <span className="sr-only">{name}</span>
            </label>
          ))}
        </div>
        {colourError && (
          <p id="avatar-colour-error" className="text-ui-sm text-ui-error">
            {colourError}
          </p>
        )}
      </fieldset>
      {generalError && <p role="alert">{generalError}</p>}
      <Button type="submit" loading={saving}>
        Save
      </Button>
      {saved && (
        <Toast
          message="Profile saved"
          variant="success"
          onDismiss={() => setSaved(false)}
        />
      )}
    </form>
  );
}

function Swatch({
  colour,
  className = '',
}: {
  colour: string | null;
  className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={`block size-8 rounded-full border border-ui-border ring-offset-2 ring-offset-ui-surface ${className}`}
      style={{ backgroundColor: colour ?? 'transparent' }}
    />
  );
}
