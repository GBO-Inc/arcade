package com.arcade.backend.lobby;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rooms")
public class Room {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String code;

  @Enumerated(EnumType.STRING)
  private GameType gameType;

  private Long creatorId;
  private int capacity;

  @Enumerated(EnumType.STRING)
  private RoomStatus status;

  private Instant createdAt;

  protected Room() {}

  public Room(String code, GameType gameType, long creatorId, Instant createdAt) {
    this.code = code;
    this.gameType = gameType;
    this.creatorId = creatorId;
    this.capacity = gameType.seats();
    this.status = RoomStatus.OPEN;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public GameType getGameType() {
    return gameType;
  }

  public Long getCreatorId() {
    return creatorId;
  }

  public int getCapacity() {
    return capacity;
  }

  public RoomStatus getStatus() {
    return status;
  }

  public void setStatus(RoomStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
