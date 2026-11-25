package com.arcade.backend.lobby;

import com.arcade.backend.auth.AccountNotFoundException;
import com.arcade.backend.auth.User;
import com.arcade.backend.auth.UserRepository;
import com.arcade.backend.match.Match;
import com.arcade.backend.match.MatchRepository;
import com.arcade.backend.match.MatchService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RoomService {

  private static final int MAX_CODE_ATTEMPTS = 10;

  private final RoomRepository rooms;
  private final RoomMemberRepository members;
  private final UserRepository users;
  private final RoomCodeGenerator codeGenerator;
  private final MatchService matchService;
  private final MatchRepository matchRepository;
  private final RoomEventPublisher roomEvents;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public RoomService(
      RoomRepository rooms,
      RoomMemberRepository members,
      UserRepository users,
      RoomCodeGenerator codeGenerator,
      MatchService matchService,
      MatchRepository matchRepository,
      RoomEventPublisher roomEvents,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.rooms = rooms;
    this.members = members;
    this.users = users;
    this.codeGenerator = codeGenerator;
    this.matchService = matchService;
    this.matchRepository = matchRepository;
    this.roomEvents = roomEvents;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /**
   * Each attempt runs in its own transaction: a failed insert aborts a Postgres transaction, so a
   * code collision can only be retried in a fresh one.
   */
  public RoomResponse create(long userId, GameType gameType) {
    for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
      try {
        return transaction.execute(status -> createOnce(userId, gameType));
      } catch (CodeCollisionException e) {
        // try again with another code
      }
    }
    throw new IllegalStateException("Could not find a free room code");
  }

  @Transactional(readOnly = true)
  public List<RoomResponse> listOpen() {
    return rooms.findOpenSummaries();
  }

  @Transactional
  public RoomResponse join(long userId, String code) {
    requireUser(userId);
    releaseSeatOfFinishedMatch(userId);
    Room room =
        rooms
            .findActiveByCodeForUpdate(code.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(RoomException::unknownCode);
    var current = members.findByUserId(userId);
    if (current.isPresent()) {
      if (current.get().getRoomId().equals(room.getId())) {
        return summary(room.getId());
      }
      throw RoomException.alreadyInRoom();
    }
    if (room.getStatus() == RoomStatus.STARTED) {
      throw RoomException.started();
    }
    if (room.getStatus() != RoomStatus.OPEN) {
      throw RoomException.full();
    }
    try {
      members.saveAndFlush(new RoomMember(room.getId(), userId, Instant.now(clock)));
    } catch (DataIntegrityViolationException e) {
      // The same user joined another room at the same moment and won.
      throw RoomException.alreadyInRoom();
    }
    if (members.countByRoomId(room.getId()) >= room.getCapacity()) {
      room.setStatus(RoomStatus.FULL);
    }
    publishRoomUpdatedAfterCommit(room);
    return summary(room.getId());
  }

  @Transactional(readOnly = true)
  public RoomDetail get(long userId, long roomId) {
    Room room =
        rooms
            .findById(roomId)
            .filter(r -> r.getStatus() != RoomStatus.CLOSED)
            .orElseThrow(RoomException::roomNotFound);
    if (!members.existsByRoomIdAndUserId(roomId, userId)) {
      throw RoomException.notAMember();
    }
    return detail(room);
  }

  /**
   * The room row is locked first, so two players pressing Ready at the same moment are handled one
   * after the other: the second sees the first's ready flag, and exactly one of them starts the
   * match. Starting means creating the match and marking the room STARTED in the same transaction.
   */
  @Transactional
  public RoomDetail setReady(long userId, long roomId, boolean ready) {
    Room room = rooms.findActiveByIdForUpdate(roomId).orElseThrow(RoomException::roomNotFound);
    RoomMember member =
        members.findByRoomIdAndUserId(roomId, userId).orElseThrow(RoomException::notInRoom);
    if (room.getStatus() == RoomStatus.STARTED) {
      throw RoomException.started();
    }
    if (ready && room.getStatus() != RoomStatus.FULL) {
      throw RoomException.waitingForOpponent();
    }
    member.setReady(ready);
    List<RoomMember> seated = members.findAllByRoomIdOrderByJoinedAtAscIdAsc(roomId);
    if (seated.size() == room.getCapacity() && seated.stream().allMatch(RoomMember::isReady)) {
      startMatch(room, seated);
    } else {
      publishRoomUpdatedAfterCommit(room);
    }
    return detail(room);
  }

  /**
   * Closing empties the room, like the last player leaving, so every player is free to join or
   * create another room.
   */
  @Transactional
  public void close(long userId, long roomId) {
    Room room = rooms.findActiveByIdForUpdate(roomId).orElseThrow(RoomException::roomNotFound);
    if (!room.getCreatorId().equals(userId) || !members.existsByRoomIdAndUserId(roomId, userId)) {
      throw RoomException.notCreator();
    }
    if (room.getStatus() == RoomStatus.STARTED) {
      throw RoomException.started();
    }
    members.deleteAll(members.findAllByRoomIdOrderByJoinedAtAscIdAsc(roomId));
    room.setStatus(RoomStatus.CLOSED);
    afterCommit(() -> roomEvents.publishRoomClosed(roomId));
  }

  /**
   * The creator is an ordinary player once the room exists: if the creator leaves while others are
   * still inside, the room stays open for them. Only the last player leaving closes it.
   */
  @Transactional
  public void leave(long userId, long roomId) {
    Room room = rooms.findActiveByIdForUpdate(roomId).orElseThrow(RoomException::roomNotFound);
    RoomMember member =
        members.findByRoomIdAndUserId(roomId, userId).orElseThrow(RoomException::notInRoom);
    if (room.getStatus() == RoomStatus.STARTED) {
      throw RoomException.started();
    }
    members.delete(member);
    if (members.countByRoomId(roomId) == 0) {
      room.setStatus(RoomStatus.CLOSED);
      return;
    }
    // Whoever stays has to confirm again once someone new sits down.
    members.findAllByRoomIdOrderByJoinedAtAscIdAsc(roomId).forEach(m -> m.setReady(false));
    room.setStatus(RoomStatus.OPEN);
    publishRoomUpdatedAfterCommit(room);
  }

  /**
   * A finished match leaves memory, but its room stays STARTED with both players still seated. A
   * seat whose match is over holds nobody any more, so it is given up before the player sits down
   * somewhere else; the room closes when its last seat goes. This runs before the target room is
   * locked, so rooms are always locked in the same order.
   */
  private void releaseSeatOfFinishedMatch(long userId) {
    members
        .findByUserId(userId)
        .ifPresent(seat -> {
          Room room = rooms.findActiveByIdForUpdate(seat.getRoomId()).orElse(null);
          if (room == null
              || room.getStatus() != RoomStatus.STARTED
              || matchRepository.findByRoomId(room.getId()).isPresent()) {
            return;
          }
          // Flushed now: Hibernate would otherwise insert the new seat before deleting this one.
          members.delete(seat);
          members.flush();
          if (members.countByRoomId(room.getId()) == 0) {
            room.setStatus(RoomStatus.CLOSED);
          }
        });
  }

  private void startMatch(Room room, List<RoomMember> seated) {
    List<String> playerIds = seated.stream().map(m -> String.valueOf(m.getUserId())).toList();
    UUID matchId =
        matchService.createFromFullRoom(room.getId(), room.getGameType(), playerIds).getId();
    room.setStatus(RoomStatus.STARTED);
    afterCommit(() -> roomEvents.publishMatchStarted(room.getId(), matchId));
  }

  private void publishRoomUpdatedAfterCommit(Room room) {
    afterCommit(() -> roomEvents.publishRoomUpdated(detail(room)));
  }

  /** A rolled-back change must never reach the players, so events wait for the commit. */
  private static void afterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private RoomDetail detail(Room room) {
    List<RoomMember> seated = members.findAllByRoomIdOrderByJoinedAtAscIdAsc(room.getId());
    Map<Long, User> byId =
        users.findAllById(seated.stream().map(RoomMember::getUserId).toList()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    List<RoomSeat> seats = seated.stream().map(m -> seat(m, byId.get(m.getUserId()))).toList();
    return new RoomDetail(
        room.getId(),
        room.getCode(),
        room.getGameType(),
        room.getStatus(),
        room.getCreatorId(),
        room.getCapacity(),
        matchIdOf(room),
        seats);
  }

  private UUID matchIdOf(Room room) {
    if (room.getStatus() != RoomStatus.STARTED) {
      return null;
    }
    return matchRepository.findByRoomId(room.getId()).map(Match::getId).orElse(null);
  }

  private static RoomSeat seat(RoomMember member, User user) {
    return new RoomSeat(
        member.getUserId(), user.getDisplayName(), user.getAvatarColour(), member.isReady());
  }

  private RoomResponse createOnce(long userId, GameType gameType) {
    requireUser(userId);
    releaseSeatOfFinishedMatch(userId);
    if (members.existsByUserId(userId)) {
      throw RoomException.alreadyInRoom();
    }
    String code = codeGenerator.next();
    if (rooms.existsActiveByCode(code)) {
      throw new CodeCollisionException();
    }
    Room room;
    try {
      room = rooms.saveAndFlush(new Room(code, gameType, userId, Instant.now(clock)));
    } catch (DataIntegrityViolationException e) {
      // Only the partial unique index on the code can fail here: another room took it just now.
      throw new CodeCollisionException();
    }
    try {
      members.saveAndFlush(new RoomMember(room.getId(), userId, Instant.now(clock)));
    } catch (DataIntegrityViolationException e) {
      // A parallel request from the same user created a room first.
      throw RoomException.alreadyInRoom();
    }
    return summary(room.getId());
  }

  private void requireUser(long userId) {
    if (!users.existsById(userId)) {
      throw new AccountNotFoundException();
    }
  }

  private RoomResponse summary(long roomId) {
    return rooms.findSummaryById(roomId).orElseThrow(RoomException::roomNotFound);
  }

  private static final class CodeCollisionException extends RuntimeException {
    CodeCollisionException() {
      super(null, null, false, false);
    }
  }
}
