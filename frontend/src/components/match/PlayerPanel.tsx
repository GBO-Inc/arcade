import { AVATAR_PALETTE, type Profile } from '../../api/profile';
import { Card } from '../ui';
import { playerName } from './playerName';

const NO_COLOUR = '#9CA3AF';

function colourName(hex: string | null | undefined) {
  return AVATAR_PALETTE.find((colour) => colour.hex === hex)?.name ?? 'Grey';
}

export function PlayerPanel({
  playerId,
  profile,
  isMe,
  isTurn,
}: {
  playerId: string;
  profile?: Profile;
  isMe: boolean;
  isTurn: boolean;
}) {
  const name = playerName(playerId, profile);
  return (
    <Card
      role="group"
      aria-label={name}
      className={`flex items-center gap-ui-3 ${isTurn ? 'ring-2 ring-ui-primary-focus' : ''}`}
    >
      <span
        aria-hidden="true"
        className="size-ui-8 shrink-0 rounded-full"
        style={{ backgroundColor: profile?.avatarColour ?? NO_COLOUR }}
      />
      <div>
        <p className="font-semibold">
          {name}
          {isMe && (
            <span className="ml-ui-2 text-ui-sm text-ui-muted">you</span>
          )}
        </p>
        <p className="text-ui-sm text-ui-muted">
          {colourName(profile?.avatarColour)}
        </p>
      </div>
    </Card>
  );
}
