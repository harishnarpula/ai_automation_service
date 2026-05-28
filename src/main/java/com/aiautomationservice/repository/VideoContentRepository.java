package com.aiautomationservice.repository;

import com.aiautomationservice.entity.VideoContent;
import com.aiautomationservice.enums.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoContentRepository extends JpaRepository<VideoContent, Long> {
    Optional<VideoContent> findByVideoId(String videoId);
    List<VideoContent> findByStatus(ContentStatus status);
    List<VideoContent> findByAddedToCloneTrue();
    List<VideoContent> findByStatusNot(ContentStatus status);
}