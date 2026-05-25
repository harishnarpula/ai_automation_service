package com.askoxy.emailautomation.service;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;
    private final OpenAIClient openAIClient;
    private final S3Service s3Service;

    private String extractResponseText(Response response) {
        return response.output().stream()
                .filter(item -> item.isMessage())
                .map(item -> item.asMessage())
                .flatMap(msg -> msg.content().stream())
                .filter(cnt -> cnt.isOutputText())
                .map(cnt -> cnt.asOutputText().text())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public String chat(String systemPrompt, String userPrompt) {
        log.info("Sending chat request to OpenAI SDK");
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model("gpt-4.1-mini")                    .instructions(systemPrompt)
                    .input(userPrompt)
                    .build();
            Response response = openAIClient.responses().create(params);
            String content = extractResponseText(response);
            log.info("Chat completion successful: {} chars", content.length());
            return content;
        } catch (Exception ex) {
            log.error("Chat completion failed", ex);
            throw new RuntimeException("AI chat generation failed: " + ex.getMessage(), ex);
        }
    }

    public String chatWithModel(String systemPrompt, String userPrompt, String model) {
        log.info("Sending chat request to OpenAI SDK [model={}]", model);
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .instructions(systemPrompt)
                    .input(userPrompt)
                    .build();
            Response response = openAIClient.responses().create(params);
            String content = extractResponseText(response);
            log.info("Chat completion successful: {} chars", content.length());
            return content;
        } catch (Exception ex) {
            log.error("Chat completion failed", ex);
            throw new RuntimeException("AI chat generation failed: " + ex.getMessage(), ex);
        }
    }

    public String chat(String prompt) {
        log.info("Sending chat request to OpenAI SDK");
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model("gpt-4.1-mini")                    .input(prompt)
                    .build();
            Response response = openAIClient.responses().create(params);
            String content = extractResponseText(response);
            log.info("Chat completion successful: {} chars", content.length());
            return content;
        } catch (Exception ex) {
            log.error("Chat completion failed", ex);
            throw new RuntimeException("AI chat generation failed: " + ex.getMessage(), ex);
        }
    }

    public String transcribe(MultipartFile audioFile) {
        log.info("Transcribing audio via OpenAI Whisper SDK: name={} size={}KB", 
                audioFile.getOriginalFilename(), audioFile.getSize() / 1024);
        java.io.File tempFile = null;
        try {
            String originalName = audioFile.getOriginalFilename();
            String suffix = ".mp3";
            if (originalName != null && originalName.contains(".")) {
                suffix = originalName.substring(originalName.lastIndexOf("."));
            }
            tempFile = java.io.File.createTempFile("whisper_", suffix);
            audioFile.transferTo(tempFile);

            com.openai.models.audio.transcriptions.TranscriptionCreateParams params = 
                    com.openai.models.audio.transcriptions.TranscriptionCreateParams.builder()
                            .model("whisper-1")
                            .file(tempFile.toPath())
                            .build();

            com.openai.models.audio.transcriptions.TranscriptionCreateResponse response = 
                    openAIClient.audio().transcriptions().create(params);
            String text = response.asTranscription().text();
            log.info("Transcription complete: {} chars", text.length());
            return text;
        } catch (Exception ex) {
            log.error("Whisper transcription failed", ex);
            throw new RuntimeException("Voice transcription failed: " + ex.getMessage(), ex);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public String extractImageText(MultipartFile imageFile) {
        log.info("Processing image via OpenAI SDK: name={} size={}KB", 
                imageFile.getOriginalFilename(), imageFile.getSize() / 1024);
        try {
            byte[] bytes = imageFile.getBytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);

            String prompt = """
Read this newspaper/document image carefully.

Extract ALL readable text exactly.

Important:
- Read headlines
- Read paragraphs
- Read article text
- Read company names
- Read people names
- Read report names
- Preserve meaning
- Do not summarize
- Return FULL extracted text only

If text is unclear, still try best OCR extraction.
""";

            com.openai.models.responses.ResponseInputItem messageItem = 
                com.openai.models.responses.ResponseInputItem.ofMessage(
                    com.openai.models.responses.ResponseInputItem.Message.builder()
                        .role(com.openai.models.responses.ResponseInputItem.Message.Role.of("user"))
                        .addContent(com.openai.models.responses.ResponseInputText.builder().text(prompt).build())
                            .addContent(com.openai.models.responses.ResponseInputImage.builder()
                                    .imageUrl("data:image/jpeg;base64," + base64)
                                    .detail(
                                            com.openai.models.responses.ResponseInputImage.Detail.HIGH
                                    )
                                    .build())
                        .build()
                );

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model("gpt-4.1-mini")                    .inputOfResponse(List.of(messageItem))
                    .maxOutputTokens(1000)
                    .build();

            Response response = openAIClient.responses().create(params);
            String text = extractResponseText(response);
            log.info("OCR RESULT:\n{}", text);
            log.info("Image extraction complete: {} chars", text.length());
            return text;
        } catch (Exception ex) {
            log.error("Image extraction failed", ex);
            return "";
        }
    }

    public String generateImage(String prompt) {

        try {

            com.openai.models.images.ImageGenerateParams params =
                    com.openai.models.images.ImageGenerateParams.builder()

                            .model("gpt-image-1")

                            .prompt(prompt)

                            .size(
                                    com.openai.models.images.ImageGenerateParams.Size.of(
                                            "1024x1024"
                                    )
                            )

                            .build();

            com.openai.models.images.ImagesResponse response =
                    openAIClient
                            .images()
                            .generate(params);

            String base64Image =
                    response.data()
                            .flatMap(
                                    x -> x.stream()
                                            .findFirst()
                            )
                            .flatMap(
                                    x -> x.b64Json()
                            )
                            .orElseThrow(
                                    () -> new RuntimeException(
                                            "No image generated"
                                    )
                            );

            byte[] imageBytes =
                    Base64.getDecoder()
                            .decode(base64Image);

            // Upload directly to S3 instead of saving to local disk
            String s3Key = "images/" + UUID.randomUUID() + ".png";

            return s3Service.uploadBytes(
                    imageBytes,
                    s3Key,
                    "image/png"
            );

        }
        catch (Exception ex) {

            log.error(
                    "IMAGE FAILED",
                    ex
            );

            throw new RuntimeException(
                    ex.getMessage(),
                    ex
            );
        }
    }

    public String buildImagePrompt(String generatedContent, String platformLabel) {
        String system = """
You are an expert image prompt writer.

Rules:
- corporate modern design
- premium business look
- social media quality
- no text
- no letters
- no watermark
- clean composition
- return only prompt
""";

        String user = """
                Platform: %s

                Content:
                %s

                Create a DALL-E prompt.
                """.formatted(
                platformLabel,
                (generatedContent != null
                        ? generatedContent.substring(
                        0,
                        Math.min(generatedContent.length(), 500))
                        : "")        );

        return chat(system, user);
    }


    public String webSearch(String query) {

        log.info("OpenAI web search: {}", query);

        try {

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);
            Map<String, Object> payload = Map.of(
                    "model", "gpt-4o-search-preview",
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", query
                            )
                    )
            );

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            "https://api.openai.com/v1/chat/completions",
                            HttpMethod.POST,
                            new HttpEntity<>(payload, headers),
                            Map.class
                    );

            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>)
                            response.getBody().get("choices");

            if (choices == null || choices.isEmpty())
                return null;

            Map<String, Object> message =
                    (Map<String, Object>)
                            choices.get(0).get("message");

            Object contentObj = message.get("content");

            String content = contentObj != null
                    ? contentObj.toString()
                    : null;

            return content != null ? content.trim() : null;

        } catch (Exception ex) {

            log.error("OpenAI web search failed", ex);

            return null;
        }
    }
}
