package com.enersight.repository;

import com.enersight.entity.UserTermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserTermsAcceptanceRepository extends JpaRepository<UserTermsAcceptance, UUID> {

    List<UserTermsAcceptance> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT uta FROM UserTermsAcceptance uta
            WHERE uta.user.id = :userId
            AND uta.createdAt = (
                SELECT MAX(uta2.createdAt) FROM UserTermsAcceptance uta2
                WHERE uta2.user.id = :userId AND uta2.term.id = uta.term.id
            )
            """)
    List<UserTermsAcceptance> findLatestStatusPerTermByUserId(@Param("userId") UUID userId);
}
