package com.agentdev.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.agentdev.core.entity.ConfluencePageEntity;

@Repository
public interface ConfluencePageRepository extends JpaRepository<ConfluencePageEntity, String> {
}
