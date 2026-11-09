package com.arcade.backend.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// lower() instead of derived IgnoreCase queries (which use upper()), so that Postgres can use
// the lower-case unique indexes from V2.
public interface UserRepository extends JpaRepository<User, Long> {

  @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
  boolean existsByUsernameIgnoreCase(@Param("username") String username);

  @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
  boolean existsByEmailIgnoreCase(@Param("email") String email);

  @Query("select u from User u where lower(u.username) = lower(:username)")
  Optional<User> findByUsernameIgnoreCase(@Param("username") String username);
}
