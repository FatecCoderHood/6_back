package com.enersight.repository;

import com.enersight.entity.TermsOfUse;
import com.enersight.entity.enums.TermType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TermsOfUseRepository extends JpaRepository<TermsOfUse, UUID> {
    List<TermsOfUse> findAllByActiveTrue();
    List<TermsOfUse> findAllByActiveTrueAndType(TermType type);
}
