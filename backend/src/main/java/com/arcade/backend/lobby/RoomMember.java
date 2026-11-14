package com.arcade.backend.lobby;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "room_members")
public class RoomMember {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long roomId;
  private Long userId;
  private Instant joinedAt;

  protected RoomMember() {}

  public RoomMember(long roomId, long userId, Instant joinedAt) {
    this.roomId = roomId;
    this.userId = userId;
    this.joinedAt = joinedAt;
  }

  public Long getId() {
    return id;
  }

  public Long getRoomId() {
    return roomId;
  }

  public Long getUserId() {
    return userId;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }
}
