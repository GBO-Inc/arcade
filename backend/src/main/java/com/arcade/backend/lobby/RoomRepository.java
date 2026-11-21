package com.arcade.backend.lobby;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

  String SUMMARY =
      "select new com.arcade.backend.lobby.RoomResponse("
          + "r.id, r.code, r.gameType, r.status, u.displayName, r.capacity, "
          + "cast(r.capacity - (select count(m) from RoomMember m where m.roomId = r.id) "
          + "as integer)) "
          + "from Room r, User u where u.id = r.creatorId ";

  // The row lock serialises joins and leaves on one room, so the seat count cannot be overrun.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.code = :code and r.status <> 'CLOSED'")
  Optional<Room> findActiveByCodeForUpdate(@Param("code") String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.id = :id and r.status <> 'CLOSED'")
  Optional<Room> findActiveByIdForUpdate(@Param("id") Long id);

  @Query("select count(r) > 0 from Room r where r.code = :code and r.status <> 'CLOSED'")
  boolean existsActiveByCode(@Param("code") String code);

  @Query(SUMMARY + "and r.status = 'OPEN' order by r.createdAt desc, r.id desc")
  List<RoomResponse> findOpenSummaries();

  @Query(SUMMARY + "and r.id = :id")
  Optional<RoomResponse> findSummaryById(@Param("id") Long id);
}
