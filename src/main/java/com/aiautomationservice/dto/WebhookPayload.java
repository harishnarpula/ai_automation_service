package com.aiautomationservice.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("instanceId")
    private String instanceId;

    @JsonProperty("data")
    private MessageData data;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageData {

        @JsonProperty("id")
        private String id;

        @JsonProperty("from")
        private String from;

        @JsonProperty("to")
        private String to;

        @JsonProperty("body")
        private String body;

        @JsonProperty("type")
        private String type;

        // FIX: was 'boolean fromMe' — UltraMsg sends false (boolean) ✓ already correct
        @JsonProperty("fromMe")
        private boolean fromMe;

        // FIX: was 'int self' — UltraMsg sends false (boolean), not 0 (int)
        // Jackson was throwing deserialization error causing data to be null
        // Changing to boolean fixes silent payload drop
        @JsonProperty("self")
        private boolean self;

        @JsonProperty("ack")
        private String ack; // FIX: also changed from int to String — UltraMsg sends "" (empty string)

        @JsonProperty("time")
        private long time;

        @JsonProperty("chatId")
        private String chatId;

        @JsonProperty("author")
        private String author;

        @JsonProperty("pushname")
        private String pushname;

        @JsonProperty("media")
        private String media;

        /**
         * Strips the UltraMsg suffix from the sender number.
         * e.g. "916281565528@c.us" → "916281565528"
         */
        public String getPhoneNumber() {
            if (from == null) return null;
            return from.replace("@c.us", "").replace("@g.us", "");
        }
    }
}