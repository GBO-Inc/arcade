-- Ready state lives with the seat, so it disappears with the member row when a player leaves.
ALTER TABLE room_members ADD COLUMN ready BOOLEAN NOT NULL DEFAULT FALSE;
