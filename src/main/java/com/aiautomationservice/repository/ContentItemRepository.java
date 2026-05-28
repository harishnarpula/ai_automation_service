package com.aiautomationservice.repository;

import com.aiautomationservice.entity.ContentItem;
import com.aiautomationservice.enums.ContentStatus;
import com.aiautomationservice.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {
    Optional<ContentItem> findByContentId(String contentId);
    List<ContentItem> findByStatus(ContentStatus status);
    List<ContentItem> findByPlatform(PlatformType platform);
}
