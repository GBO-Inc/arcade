import { describe, expect, it } from 'vitest';
import type {
  ChatMessage,
  ChatSendRequest,
  ClockUpdate,
  EchoRequest,
  EchoResponse,
  ErrorMessage,
  IncomingMessage,
  MatchFinalResult,
  MatchMoveEvent,
  MatchMoveRequest,
  MatchStartedEvent,
  MatchStateResponse,
  RoomClosedEvent,
  RoomDetail,
  RoomListChangedEvent,
  RoomUpdatedEvent,
} from './index';

type Validator = (value: unknown) => boolean;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isString(value: unknown): value is string {
  return typeof value === 'string';
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && !Number.isNaN(value);
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(isString);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || isString(value);
}

const isMatchState = (v: unknown): v is MatchStateResponse =>
  isRecord(v) &&
  isString(v.matchId) &&
  isNumber(v.roomId) &&
  isString(v.gameType) &&
  isString(v.status) &&
  isStringArray(v.players) &&
  isString(v.currentTurnPlayerId) &&
  'state' in v &&
  isNumber(v.moveCount);

const isRoomDetail = (v: unknown): v is RoomDetail =>
  isRecord(v) &&
  isNumber(v.id) &&
  isString(v.code) &&
  isString(v.gameType) &&
  isString(v.status) &&
  isNumber(v.creatorId) &&
  isNumber(v.capacity) &&
  isNullableString(v.matchId) &&
  Array.isArray(v.seats) &&
  v.seats.every(
    (seat) =>
      isRecord(seat) &&
      isNumber(seat.userId) &&
      isString(seat.displayName) &&
      isNullableString(seat.avatarColour) &&
      typeof seat.ready === 'boolean',
  );

const isMove = (v: unknown): v is MatchMoveRequest =>
  isRecord(v) && ((isNumber(v.row) && isNumber(v.col)) || isString(v.playerId));

const VALIDATORS: Record<string, Validator> = {
  EchoRequest: (v): v is EchoRequest => isRecord(v) && isString(v.content),
  EchoResponse: (v): v is EchoResponse =>
    isRecord(v) && isString(v.sender) && isString(v.content),
  RoomUpdatedEvent: (v): v is RoomUpdatedEvent =>
    isRecord(v) && v.type === 'ROOM_UPDATED' && isRoomDetail(v.room),
  MatchStartedEvent: (v): v is MatchStartedEvent =>
    isRecord(v) && v.type === 'MATCH_STARTED' && isString(v.matchId),
  RoomClosedEvent: (v): v is RoomClosedEvent =>
    isRecord(v) && v.type === 'ROOM_CLOSED',
  MatchStateResponse: isMatchState,
  MatchMoveRequest: isMove,
  MatchMoveEvent: (v): v is MatchMoveEvent =>
    isRecord(v) &&
    isMatchState(v.match) &&
    isMove(v.move) &&
    isString(v.movedBy),
  MatchFinalResult: (v): v is MatchFinalResult =>
    isRecord(v) &&
    isString(v.matchId) &&
    isNumber(v.roomId) &&
    isString(v.gameType) &&
    isStringArray(v.players) &&
    isString(v.outcome) &&
    isNullableString(v.winnerId) &&
    isString(v.reason),
  ErrorMessage: (v): v is ErrorMessage =>
    isRecord(v) &&
    isString(v.code) &&
    isString(v.message) &&
    (v.matchId === undefined || isNullableString(v.matchId)),
  ChatSendRequest: (v): v is ChatSendRequest => isRecord(v) && isString(v.text),
  ChatMessage: (v): v is ChatMessage =>
    isRecord(v) &&
    isString(v.matchId) &&
    isString(v.sender) &&
    isString(v.text) &&
    isString(v.sentAt),
  ClockUpdate: (v): v is ClockUpdate =>
    isRecord(v) &&
    isString(v.matchId) &&
    isString(v.activePlayer) &&
    isRecord(v.remainingMs) &&
    Object.values(v.remainingMs).every(isNumber) &&
    isString(v.serverTime),
  RoomListChangedEvent: (v): v is RoomListChangedEvent =>
    isRecord(v) && v.type === 'ROOM_LIST_CHANGED',
};

function extractExamples(markdownText: string) {
  const pattern = /<!--\s*type:\s*(\w+)\s*-->\s*```json\s*([\s\S]*?)```/g;
  const examples: { typeName: string; json: string }[] = [];
  for (const match of markdownText.matchAll(pattern)) {
    examples.push({ typeName: match[1], json: match[2].trim() });
  }
  return examples;
}

async function loadContractMarkdown(): Promise<string> {
  const proc = (
    globalThis as unknown as {
      process?: {
        cwd?: () => string;
        getBuiltinModule?: (id: string) => unknown;
      };
    }
  ).process;
  const fs = proc?.getBuiltinModule?.('node:fs') as
    { readFileSync: (path: string, encoding: 'utf8') => string } | undefined;
  const pathMod = proc?.getBuiltinModule?.('node:path') as
    { resolve: (...segments: string[]) => string } | undefined;
  if (!fs || !pathMod || !proc?.cwd) {
    throw new Error('node:fs or node:path builtin is unavailable');
  }
  const docPath = pathMod.resolve(proc.cwd(), '../docs/realtime-contract.md');
  return fs.readFileSync(docPath, 'utf8');
}

describe('docs/realtime-contract.md examples', () => {
  it('tags every json block in the document with a contract type', async () => {
    const markdown = await loadContractMarkdown();
    const examples = extractExamples(markdown);
    const totalJsonBlocks = (markdown.match(/```json/g) ?? []).length;
    expect(examples.length).toBeGreaterThan(0);
    expect(examples.length).toBe(totalJsonBlocks);
  });

  it('validates every documented JSON example against its TypeScript contract', async () => {
    const markdown = await loadContractMarkdown();
    const examples = extractExamples(markdown);
    for (const { typeName, json } of examples) {
      const validate = VALIDATORS[typeName];
      expect(validate, `Missing validator for ${typeName}`).toBeDefined();
      const parsed: unknown = JSON.parse(json);
      expect(validate(parsed), `${typeName} example does not match type`).toBe(
        true,
      );
    }
  });

  it('supports exhaustive narrowing on IncomingMessage', () => {
    const sample: IncomingMessage = {
      kind: 'error',
      payload: { code: 'INVALID_MOVE', message: 'Cell is occupied' },
    };
    expect(sample.kind).toBe('error');
    expect(sample.payload.code).toBe('INVALID_MOVE');
  });
});
