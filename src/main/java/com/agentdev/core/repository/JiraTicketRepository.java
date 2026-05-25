package com.agentdev.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.agentdev.core.entity.JiraTicketEntity;
import java.util.List;

@Repository
public interface JiraTicketRepository extends JpaRepository<JiraTicketEntity, Long> {
    List<JiraTicketEntity> findByConfluencePageId(String confluencePageId);
}
