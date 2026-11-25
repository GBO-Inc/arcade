package com.arcade.backend.lobby;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

  Optional<RoomMember> findByUserId(Long userId);

  Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

  List<RoomMember> findAllByRoomIdOrderByJoinedAtAscIdAsc(Long roomId);

  boolean existsByUserId(Long userId);

  boolean existsByRoomIdAndUserId(Long roomId, Long userId);

  long countByRoomId(Long roomId);
}
