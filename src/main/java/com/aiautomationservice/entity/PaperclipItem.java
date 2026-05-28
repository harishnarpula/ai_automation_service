package com.aiautomationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "paperclip_items")
public class PaperclipItem extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String paperclipId;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(columnDefinition = "TEXT")
    private String analysisJson;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String s3FileUrl;

    @Column(columnDefinition = "TEXT")
    private String blogFormat;

    @Column(columnDefinition = "TEXT")
    private String fileName;

    @Builder.Default
    private Boolean addedToClone = false;

    @Builder.Default
    private Boolean addedToBlog = false;

    @Builder.Default
    private Boolean postedToSocial = false;

    @Builder.Default
    private Boolean blogPublished = false;
}