package com.aiautomationservice.repository;

import com.aiautomationservice.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /** Newest first — reversed in code to get chronological order */
    List<ConversationMessage> findTop20ByPhoneOrderByCreatedAtDesc(String phone);

    /** Used by WebhookAsyncHandler — larger window to handle re-asks */
    List<ConversationMessage> findTop50ByPhoneOrderByCreatedAtDesc(String phone);
}