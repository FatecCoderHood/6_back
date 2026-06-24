package com.enersight.auth.repository;

import com.enersight.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Conditional UPDATE, not a read-then-write: lets the database serialize concurrent rotation
    // attempts on the same token at the row level. Returns 1 only for whichever caller's UPDATE
    // wins the race; a second concurrent caller sees revoked already flipped and gets 0.
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.tokenHash = :tokenHash AND r.revoked = false")
    int revokeIfActive(@Param("tokenHash") String tokenHash);
}
