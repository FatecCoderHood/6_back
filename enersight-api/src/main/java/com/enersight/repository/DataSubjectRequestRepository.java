package com.enersight.repository;

import com.enersight.entity.DataSubjectRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, UUID> {
}
