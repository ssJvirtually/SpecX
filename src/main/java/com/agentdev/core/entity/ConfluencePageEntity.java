package com.agentdev.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "confluence_pages")
public class ConfluencePageEntity {

    @Id
    private String pageId;

    private String title;
    private String url;
    private String status;

    public ConfluencePageEntity() {}

    public ConfluencePageEntity(String pageId, String title, String url, String status) {
        this.pageId = pageId;
        this.title = title;
        this.url = url;
        this.status = status;
    }

    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
