CREATE TABLE rooms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(6) NOT NULL,
    game_type VARCHAR(30) NOT NULL,
    creator_id BIGINT NOT NULL REFERENCES users (id),
    capacity INTEGER NOT NULL CHECK (capacity >= 2),
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'FULL', 'STARTED', 'CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A code only has to be unique among rooms that are still alive, so closed rooms free their code.
CREATE UNIQUE INDEX rooms_active_code_key ON rooms (code) WHERE status <> 'CLOSED';
CREATE INDEX rooms_open_idx ON rooms (created_at) WHERE status = 'OPEN';

-- Rows exist only while a player sits in a room that is not closed (a closing room is emptied).
CREATE TABLE room_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- One room per user at a time. Enforced here so that two concurrent requests cannot both win.
    CONSTRAINT room_members_user_key UNIQUE (user_id)
);

CREATE INDEX room_members_room_idx ON room_members (room_id);
