package com.aiautomationservice.repository;

import com.aiautomationservice.entity.PaperclipItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperclipRepository extends JpaRepository<PaperclipItem, Long> {
    Optional<PaperclipItem> findByPaperclipId(String paperclipId);
    List<PaperclipItem>     findByAddedToBlogTrue();    // ✅ for approved paperclips
    List<PaperclipItem>     findByAddedToCloneTrue();   // ✅ for cloned paperclips
    List<PaperclipItem> findByS3FileUrlIsNotNull();
}


