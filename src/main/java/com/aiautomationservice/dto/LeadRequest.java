package com.aiautomationservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeadRequest {

    @NotBlank(message = "Name is required")
    @JsonProperty("name")
    private String name;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    @JsonProperty("phone")
    private String phone;

    @JsonProperty("email")
    private String email;

    @JsonProperty("product")
    private String product;

    @JsonProperty("source")
    private String source;

    @JsonProperty("rowNumber")
    private Integer rowNumber;

    @JsonProperty("city")
    private String city;

    @JsonProperty("adName")
    private String adName;

    @JsonProperty("formName")
    private String formName;

    @JsonProperty("isOrganic")
    private String isOrganic;

    @JsonProperty("createdTime")
    private String createdTime;

    /** Lead's comment / notes from the sheet — e.g. "Interested in MBA program details" */
    @JsonProperty("notes")
    private String notes;
}