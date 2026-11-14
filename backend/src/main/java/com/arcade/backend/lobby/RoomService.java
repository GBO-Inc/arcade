package com.arcade.backend.lobby;

import com.arcade.backend.auth.AccountNotFoundException;
import com.arcade.backend.auth.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RoomService {

  private static final int MAX_CODE_ATTEMPTS = 10;

  private final RoomRepository rooms;
  private final RoomMemberRepository members;
  private final UserRepository users;
  private final RoomCodeGenerator codeGenerator;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public RoomService(
      RoomRepository rooms,
      RoomMemberRepository members,
      UserRepository users,
      RoomCodeGenerator codeGenerator,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.rooms = rooms;
    this.members = members;
    this.users = users;
    this.codeGenerator = codeGenerator;
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
    return summary(room.getId());
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
    } else {
      room.setStatus(RoomStatus.OPEN);
    }
  }

  private RoomResponse createOnce(long userId, GameType gameType) {
    requireUser(userId);
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
