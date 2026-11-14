package com.arcade.backend.lobby;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

  Optional<RoomMember> findByUserId(Long userId);

  Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

  boolean existsByUserId(Long userId);

  long countByRoomId(Long roomId);
}
