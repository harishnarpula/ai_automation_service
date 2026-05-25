package com.askoxy.emailautomation.service;

import com.askoxy.emailautomation.dto.*;
import com.askoxy.emailautomation.entity.ContentItem;
import com.askoxy.emailautomation.enums.ContentStatus;
import com.askoxy.emailautomation.enums.PlatformType;
import com.askoxy.emailautomation.repository.ContentItemRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.askoxy.emailautomation.repository.VideoContentRepository;


import java.util.*;
import java.util.stream.Collectors;
import com.askoxy.emailautomation.entity.PaperclipItem;
import com.askoxy.emailautomation.repository.PaperclipRepository;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Slf4j
@Service
public class ContentService {

    private final AIService aiService;
    private final DocumentService documentService;
    private final IngestionService ingestionService;
    private final ContentItemRepository contentItemRepository;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final PaperclipRepository paperclipRepository;
    private final VideoContentRepository videoContentRepository;

    public ContentService(
            AIService aiService,
            DocumentService documentService,
            IngestionService ingestionService,
            ContentItemRepository contentItemRepository,
            @Qualifier("radhaAiVectorStore") VectorStore vectorStore,
            ObjectMapper objectMapper,
            S3Service s3Service,
            PaperclipRepository paperclipRepository,
            VideoContentRepository videoContentRepository
    ) {
        this.aiService = aiService;
        this.documentService = documentService;
        this.ingestionService = ingestionService;
        this.contentItemRepository = contentItemRepository;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.s3Service = s3Service;
        this.paperclipRepository = paperclipRepository;

        // THIS FIXES ERROR
        this.videoContentRepository = videoContentRepository;
    }
    private static final String REASONING_SYSTEM = """
            You are an expert content strategist and editor for AskOxy Group.

            Your job is to:
            1. READ the raw CEO input carefully — it may come from voice transcription
               or quick typing and MAY contain spelling mistakes, grammar errors,
               or unclear phrasing.
            2. CORRECT all spelling mistakes, grammar errors, and unclear phrasing.
            3. EXTRACT the core business idea(s) clearly.
            4. DECIDE if all ideas are about the same topic (sameTopicGroup=true)
               or multiple different topics (sameTopicGroup=false).

            Return ONLY valid JSON — no explanation, no markdown, no extra text:
            {
              "cleanedIdea": "corrected and clarified version of the CEO input",
              "sameTopicGroup": true
            }

            Rules for cleanedIdea:
            - Fix ALL spelling mistakes (especially from voice: "oxyloanz" → "OxyLoans")
            - Fix grammar but KEEP the original meaning
            - Keep business terms exact: OxyLoans, OxyGold.ai, OxyBricks, AskOxy.AI,
              StudyAbroad, OxyGlobal, Radha Sir, AskOxy Group
            - If input mentions multiple unrelated topics → sameTopicGroup: false
            - If input is about one topic or closely related topics → sameTopicGroup: true
            """;

    private static final String GENERATION_SYSTEM_GROUPED = """
        You are an expert business content writer for AskOxy Group.

        Write in a modern founder/CEO communication style.

        RULES:
        - Sound natural, intelligent, and human
        - Use a confident leadership tone
        - Keep content professional and insightful
        - Use first-person naturally only when needed
        - Avoid repeatedly mentioning founder names
        - Do NOT start with:
          "As Radha Krishna Thatavarthi..."
        - Avoid robotic or overly promotional wording
        - Write like a premium LinkedIn/blog post
        - Keep sentences clear and engaging
        - Focus on value, insights, and business impact

        BUSINESS RULES:
        - Zero spelling mistakes allowed
        - Zero grammar mistakes allowed
        - Business terms must remain exact:
          OxyLoans, OxyGold.ai, OxyBricks,
          AskOxy.AI, StudyAbroad, OxyGlobal,
          AskOxy Group
        - Do NOT invent fake statistics
        - If facts are unavailable, avoid hallucinations

        Return ONLY valid JSON:
        {
          "title": "compelling headline — 6-12 words",
          "summary": "2-3 sentence overview",
          "intro": "strong opening hook",
          "body": "150-300 words of premium business content",
          "closing": "strong concluding thought",
          "hashtags": "5-8 relevant hashtags",
          "isGrouped": true
        }
        """;

    private static final String GENERATION_SYSTEM_SEPARATE = """
        You are an expert business content writer for AskOxy Group.

        Write in a modern founder/CEO communication style.

        RULES:
        - Sound natural, intelligent, and human
        - Use a confident leadership tone
        - Keep content professional and insightful
        - Use first-person naturally only when needed
        - Avoid repeatedly mentioning founder names
        - Do NOT start with:
          "As Radha Krishna Thatavarthi..."
        - Avoid robotic or overly promotional wording
        - Write like a premium LinkedIn/blog post
        - Keep sentences clear and engaging
        - Focus on value, insights, and business impact

        BUSINESS RULES:
        - Zero spelling mistakes allowed
        - Zero grammar mistakes allowed
        - Business terms must remain exact:
          OxyLoans, OxyGold.ai, OxyBricks,
          AskOxy.AI, StudyAbroad, OxyGlobal,
          AskOxy Group
        - Do NOT invent fake statistics
        - If facts are unavailable, avoid hallucinations

        The CEO input contains MULTIPLE topics.
        Create separate sections for each topic.

        Return ONLY valid JSON:
        {
          "title": "overall title",
          "summary": "overall summary",
          "intro": "opening hook",
          "body": "short connecting overview",
          "sections": [
            {
              "heading": "Topic title",
              "content": "100-200 words of premium business content"
            }
          ],
          "closing": "strong concluding thought",
          "hashtags": "5-8 relevant hashtags",
          "isGrouped": false
        }
        """;

    @Transactional
    public ContentItem submit(ContentDto req) throws Exception {

        log.info("Content pipeline started — platform={}", req.getPlatform());

        String textInput = "";
        if (req.getRawInstruction() != null && !req.getRawInstruction().isBlank())
            textInput = normalizeText(req.getRawInstruction());

        String voiceInput = "";
        if (req.getVoiceFile() != null && !req.getVoiceFile().isEmpty()) {
            log.info("Transcribing voice file");
            voiceInput = normalizeText(aiService.transcribe(req.getVoiceFile()));
            log.info("VOICE TRANSCRIPT:\n{}", voiceInput);
        }

        String fileInput = "";
        if (req.getAttachment() != null && !req.getAttachment().isEmpty()) {
            fileInput = smartSummarizeFile(req.getAttachment());
            log.info("FILE SUMMARY:\n{}", fileInput);
        }

        String imageUrl = null;
        if (req.getAttachment() != null && !req.getAttachment().isEmpty()) {
            String name = req.getAttachment().getOriginalFilename().toLowerCase();
            if (name.endsWith(".png") || name.endsWith(".jpg")
                    || name.endsWith(".jpeg") || name.endsWith(".webp")) {
                String filename = UUID.randomUUID() + "_" + req.getAttachment().getOriginalFilename();
                String s3Key    = s3Service.uploadFile(req.getAttachment(), "images/" + filename);
                imageUrl        = s3Service.generatePresignedUrl(s3Key);
                log.info("Image uploaded to S3: key={}", s3Key);
            }
        }

        if (textInput.isEmpty() && voiceInput.isEmpty() && fileInput.isEmpty())
            throw new IllegalArgumentException("No input provided.");

        String finalInstruction = Stream.of(textInput, voiceInput, fileInput)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"))
                .trim();

        log.info("FINAL INPUT:\n{}", finalInstruction);

        String rawInstructionForStorage = !textInput.isEmpty() ? textInput
                : !voiceInput.isEmpty() ? voiceInput
                : "(file input only)";

        String platformLabel = resolvePlatformLabel(req.getPlatform(), req.getCustomPlatformName());

        String cleanedIdeaJson = reasonInstruction(finalInstruction, platformLabel);
        boolean sameTopicGroup = extractSameTopicGroup(cleanedIdeaJson);
        log.info("Reasoning complete — sameTopicGroup={}", sameTopicGroup);

        String ragContext = retrieveWithFallback(cleanedIdeaJson, 5, platformLabel);

        String generatedJson = sameTopicGroup
                ? generateGroupedContent(cleanedIdeaJson, platformLabel, ragContext)
                : generateSeparateContent(cleanedIdeaJson, platformLabel, ragContext);

        String savedImageUrl = imageUrl;
        boolean wantsImage = imageUrl == null && isImageRequested(finalInstruction);
        if (wantsImage) {
            try {
                log.info("Generating AI image");
                String dallePrompt = aiService.buildImagePrompt(generatedJson, platformLabel);
                savedImageUrl = aiService.generateImage(dallePrompt);
                log.info("Generated image saved: {}", savedImageUrl);
            } catch (Exception ex) {
                log.warn("Image generation failed: {}", ex.getMessage());
            }
        }

        String parsedTitle = null, parsedSummary = null;
        String parsedIntro = null, parsedBody = null, parsedClosing = null;
        try {
            String cleanJson = generatedJson
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "").trim();
            java.util.Map<String, Object> parsed = objectMapper.readValue(cleanJson, java.util.Map.class);
            parsedTitle   = (String) parsed.get("title");
            parsedSummary = (String) parsed.get("summary");
            parsedIntro   = (String) parsed.get("intro");
            parsedBody    = (String) parsed.get("body");
            parsedClosing = (String) parsed.get("closing");
        } catch (Exception ex) {
            log.warn("Could not parse structured fields: {}", ex.getMessage());
        }

        ContentItem item = ContentItem.builder()
                .contentId(UUID.randomUUID().toString())
                .rawInstruction(rawInstructionForStorage)
                .extractedInputs(finalInstruction)
                .platform(req.getPlatform())
                .customPlatformName(req.getCustomPlatformName())
                .generatedContent(generatedJson)
                .title(parsedTitle)
                .summary(parsedSummary)
                .intro(parsedIntro)
                .body(parsedBody)
                .closing(parsedClosing)
                .imageUrl(savedImageUrl)
                .imageRequested(wantsImage)
                .isGrouped(sameTopicGroup)
                .status(ContentStatus.PENDING)
                .build();

        contentItemRepository.save(item);
        log.info("Saved content: {}", item.getContentId());
        return item;
    }

    @Transactional
    public ContentItem processApproval(ContentApprovalRequest req)
            throws JsonProcessingException {

        ContentItem item = findByContentId(req.getContentId());

        if (Boolean.TRUE.equals(req.getApproved())) {
            if (req.getEditedContent() != null && !req.getEditedContent().isBlank())
                item.setEditedContent(req.getEditedContent());
            if (req.getFeedback() != null)
                item.setAdminFeedback(req.getFeedback());

            String contentToStore = (req.getEditedContent() != null
                    && !req.getEditedContent().isBlank())
                    ? req.getEditedContent()
                    : item.getGeneratedContent();

            String platform = item.getPlatform() != null
                    ? item.getPlatform().name() : "GENERAL";

            ingestionService.storeContent(item.getContentId(), contentToStore, platform);
            item.setStatus(ContentStatus.APPROVED);
        } else {
            item.setStatus(ContentStatus.REJECTED);
            item.setAdminFeedback(req.getFeedback());
        }

        return contentItemRepository.save(item);
    }

    public ContentItem getByContentId(String contentId) {
        return findByContentId(contentId);
    }

    public List<ContentItem> getPending() {
        return contentItemRepository.findByStatus(ContentStatus.PENDING);
    }

    public List<ContentItem> getApproved() {
        return contentItemRepository.findByStatus(ContentStatus.APPROVED);
    }

    public List<PaperclipItem> getApprovedPaperclips() {
        return paperclipRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAddedToClone())
                        || Boolean.TRUE.equals(p.getAddedToBlog()))
                .collect(java.util.stream.Collectors.toList());
    }

    public Map<String, Object> getAllApproved() {
        return Map.of(
                "content",    contentItemRepository.findByStatus(ContentStatus.APPROVED),
                "videos",     videoContentRepository.findByStatus(ContentStatus.APPROVED),
                "paperclips", getApprovedPaperclips()
        );
    }


    private String reasonInstruction(String rawInstruction, String platformLabel) {
        String user = """
                BUSINESS PLATFORM: %s

                CEO RAW INPUT (may contain spelling mistakes from voice or typing):
                %s
                """.formatted(platformLabel, rawInstruction);
        return cleanJson(aiService.chat(REASONING_SYSTEM, user));
    }

    private boolean extractSameTopicGroup(String reasoningJson) {
        try {
            return objectMapper.readTree(cleanJson(reasoningJson))
                    .path("sameTopicGroup").asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    private String retrieveWithFallback(String query, int topK, String platformLabel) {
        List<Document> combined = new ArrayList<>();

        if (platformLabel != null && !platformLabel.isBlank()) {
            try {
                List<Document> scoped = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(topK / 2 + 1)
                                .filterExpression("clientName == '" + platformLabel + "'")
                                .build());
                combined.addAll(scoped);
                log.info("Content RAG Pass-1 (platform={}) → {} docs", platformLabel, scoped.size());
            } catch (Exception ex) {
                log.warn("Content RAG Pass-1 failed: {}", ex.getMessage());
            }
        }

        try {
            List<Document> broad = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK).build());
            combined.addAll(broad);
            log.info("Content RAG Pass-2 (broad) → {} docs", broad.size());
        } catch (Exception ex) {
            log.warn("Content RAG Pass-2 failed: {}", ex.getMessage());
        }

        return combined.stream()
                .collect(Collectors.toMap(
                        d -> d.getId() != null ? d.getId() : d.getText(),
                        d -> d,
                        (existing, replacement) -> existing))
                .values().stream()
                .limit(topK)
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private String generateGroupedContent(String cleanedIdeaJson, String platformLabel, String ragContext) {
        String user = """
                PLATFORM: %s

                CEO IDEA (spelling already corrected):
                %s

                KNOWLEDGE BASE CONTEXT:
                %s
                """.formatted(platformLabel, cleanedIdeaJson, ragContext);
        return cleanJson(aiService.chat(GENERATION_SYSTEM_GROUPED, user));
    }

    private String generateSeparateContent(String cleanedIdeaJson, String platformLabel, String ragContext) {
        String user = """
                PLATFORM: %s

                CEO IDEA (spelling already corrected):
                %s

                KNOWLEDGE BASE CONTEXT:
                %s
                """.formatted(platformLabel, cleanedIdeaJson, ragContext);
        return cleanJson(aiService.chat(GENERATION_SYSTEM_SEPARATE, user));
    }

    private String smartSummarizeFile(MultipartFile file) {
        String rawText = extractAttachment(file);
        if (rawText.isBlank()) return "";
        return aiService.chat(
                "Extract key business facts only. Fix any spelling or grammar mistakes.",
                rawText.substring(0, Math.min(rawText.length(), 5000)));
    }

    private String extractAttachment(MultipartFile file) {
        try {
            if (file.getOriginalFilename() == null) return "";
            String name = file.getOriginalFilename().toLowerCase();
            String extracted = "";
            if (name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt"))
                extracted = documentService.extractText(file);
            else if (name.endsWith(".png") || name.endsWith(".jpg")
                    || name.endsWith(".jpeg") || name.endsWith(".webp"))
                extracted = aiService.extractImageText(file);
            else if (name.endsWith(".mp3") || name.endsWith(".wav")
                    || name.endsWith(".m4a") || name.endsWith(".aac"))
                extracted = aiService.transcribe(file);
            return extracted.replaceAll("\\s+", " ")
                    .replaceAll("[^\\x20-\\x7E\\n]", "").trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private ContentItem findByContentId(String contentId) {
        return contentItemRepository.findByContentId(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));
    }

    private String resolvePlatformLabel(PlatformType platform, String customPlatformName) {
        if (platform == PlatformType.OTHER && customPlatformName != null
                && !customPlatformName.isBlank())
            return customPlatformName.trim();
        return platform != null ? platform.name() : "ASK_OXY_AI";
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("\r\n", "\n").replaceAll("\r", "\n")
                .replaceAll("[ \t]+", " ").replaceAll("\n{3,}", "\n\n").trim();
    }

    private boolean isImageRequested(String instruction) {
        if (instruction == null) return false;
        String lower = instruction.toLowerCase();
        return lower.contains("with image") || lower.contains("generate image")
                || lower.contains("create image") || lower.contains("add image")
                || lower.contains("include image") || lower.contains("image post")
                || lower.contains("with picture") || lower.contains("with photo");
    }

    // ═══════════════════════════════════════════════════
    // PAPERCLIP
    // ═══════════════════════════════════════════════════

    private static final String PAPERCLIP_SYSTEM = """
        You are a professional document intelligence AI.
        Analyze the provided document(s) and return ONLY valid JSON. No explanation, no markdown.

        JSON SCHEMA:
        {
          "summary": {
            "shortSummary": "1-2 sentence summary",
            "detailedSummary": "Comprehensive summary",
            "keyPoints": ["point 1"],
            "actionItems": ["action 1"]
          },
          "people": [
            { "name": "Full Name", "designation": "Job Title", "company": "Company" }
          ],
          "companies": [
            { "name": "Company Name" }
          ],
          "mentionedReports": ["Exact report title as mentioned"]
        }

            RULES:
            - People: founders, speakers, executives, investors, authors.
            - Companies: startups, vendors, partners, investors, organizations.
            - Reports: ANY mention of Gartner, McKinsey, Deloitte, WHO, annual reports,
              research papers, ESG reports, surveys, studies.
            - ONLY include facts explicitly visible in the source content
            - Do NOT infer announcements or investments
            - Do NOT use external knowledge
            - Do NOT invent statistics
            - If information is unclear, omit it
        """;

    public PaperclipResponse analyzePaperclip(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw new RuntimeException("Upload at least one file.");
        if (files.size() > 20) throw new RuntimeException("Max 20 files.");

        // ✅ STEP 0: Upload raw files to S3 before extraction
        List<String> s3Keys = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            try {
                String key = "paperclips/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
                s3Service.uploadFile(file, key);
                s3Keys.add(key);
                log.info("Paperclip file uploaded to S3: {}", key);
            } catch (Exception ex) {
                log.warn("S3 upload failed for {}: {}", file.getOriginalFilename(), ex.getMessage());
            }
        }
        String s3FileUrl = String.join(",", s3Keys);

        StringBuilder combined = new StringBuilder();
        for (MultipartFile file : files) {
            try {

                String name =
                        file.getOriginalFilename() != null
                                ? file.getOriginalFilename().toLowerCase()
                                : "";

                String text;

                // IMAGE FILES
                if (
                        name.endsWith(".png") ||
                                name.endsWith(".jpg") ||
                                name.endsWith(".jpeg") ||
                                name.endsWith(".webp")
                ) {

                    log.info(
                            "Using GPT Vision OCR for image: {}",
                            name
                    );

                    text =
                            aiService.extractImageText(file);

                }

                // OTHER FILES
                else {

                    text =
                            documentService.extractText(file);
                }

                log.info(
                        "EXTRACTED PAPERCLIP TEXT:\n{}",
                        text
                );

                if (text != null && !text.isBlank()) {

                    combined.append("\n\n=== FILE: ")
                            .append(file.getOriginalFilename())
                            .append(" ===\n\n")
                            .append(text);
                }

            } catch (Exception ex) {

                log.warn(
                        "Could not extract: {} — {}",
                        file.getOriginalFilename(),
                        ex.getMessage()
                );
            }
        }
        String docText = combined.toString().trim();
        if (docText.isBlank()) throw new RuntimeException("No text extracted.");
        if (docText.length() > 12_000) docText = docText.substring(0, 12_000) + "\n[truncated]";

        String rawJson = aiService.chatWithModel(
                PAPERCLIP_SYSTEM,
                "Analyze the following document:\n\n" + docText + "\n\nReturn ONLY the JSON.",
                "gpt-4.1"
        );
        rawJson = rawJson
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        log.info("PAPERCLIP AI RAW JSON:\n{}", rawJson);

        PaperclipRawExtraction raw;
        try { raw = objectMapper.readValue(rawJson, PaperclipRawExtraction.class); }
        catch (Exception ex) { log.error("Parse failed: {}", ex.getMessage()); raw = PaperclipRawExtraction.empty(); }

        final PaperclipRawExtraction r = raw;
        CompletableFuture<List<PaperclipAnalysisResult.Person>>  pf =
                CompletableFuture.supplyAsync(() -> pcEnrichPeople(r.getPeople()));
        CompletableFuture<List<PaperclipAnalysisResult.Company>> cf =
                CompletableFuture.supplyAsync(() -> pcEnrichCompanies(r.getCompanies()));
        CompletableFuture<List<PaperclipAnalysisResult.Report>>  rf =
                CompletableFuture.supplyAsync(() -> pcEnrichReports(r.getMentionedReports()));

        PaperclipAnalysisResult result = PaperclipAnalysisResult.builder()
                .summary(pcBuildSummary(r.getSummary()))
                .people(pf.join()).companies(cf.join()).reports(rf.join()).build();

        String fileNames = files.stream()
                .map(MultipartFile::getOriginalFilename)
                .collect(Collectors.joining(", "));

        PaperclipItem item = PaperclipItem.builder()
                .paperclipId(UUID.randomUUID().toString())
                .fileName(fileNames)           // ← ADD
                .extractedText(docText)
                .analysisJson(pcToJson(result))
                .s3FileUrl(s3FileUrl)
                .build();

        paperclipRepository.save(item);

        log.info("Paperclip saved: {}", item.getPaperclipId());
        return PaperclipResponse.builder()
                .paperclipId(item.getPaperclipId()).analysis(result).build();
    }

    public PaperclipResponse getPaperclip(String paperclipId) {
        PaperclipItem item = paperclipRepository.findByPaperclipId(paperclipId)
                .orElseThrow(() -> new RuntimeException("Not found: " + paperclipId));
        try {
            return PaperclipResponse.builder().paperclipId(paperclipId)
                    .analysis(objectMapper.readValue(item.getAnalysisJson(),
                            PaperclipAnalysisResult.class)).build();
        } catch (Exception ex) { throw new RuntimeException("Parse error", ex); }
    }

    private List<PaperclipAnalysisResult.Person> pcEnrichPeople(List<PaperclipRawExtraction.RawPerson> list) {
        if (list == null) return List.of();
        return list.stream().map(p -> PaperclipAnalysisResult.Person.builder()
                .name(p.getName()).designation(p.getDesignation()).company(p.getCompany())
                .linkedin(pcLinkedIn(p.getName(), p.getCompany()))
                .build()).collect(Collectors.toList());
    }


    private String pcLinkedIn(String name, String company) {

        if (name == null || name.isBlank())
            return null;

        // OPENAI WEB SEARCH
        try {

            String result = aiService.webSearch("""
            Find the official LinkedIn profile URL for:

            Name: %s
            Company: %s

            Return ONLY the LinkedIn URL.
            If not found return NONE.
            """.formatted(name, company));

            if (result != null
                    && result.contains("linkedin.com/in/")) {

                java.util.regex.Matcher matcher =
                        java.util.regex.Pattern
                                .compile(
                                        "https://([a-z]{2,3}\\.)?linkedin\\.com/in/[A-Za-z0-9\\-_%]+",
                                        java.util.regex.Pattern.CASE_INSENSITIVE
                                )
                                .matcher(result);

                if (matcher.find())
                    return matcher.group();
            }

        } catch (Exception ex) {

            log.warn("OpenAI LinkedIn search failed: {}", name);
        }

        // FALLBACK SEARCH
        try {

            String q = java.net.URLEncoder.encode(
                    name + " " + (company != null ? company : "")
                            + " site:linkedin.com/in/",
                    java.nio.charset.StandardCharsets.UTF_8);

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup
                    .connect("https://duckduckgo.com/html/?q=" + q)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .get();

            java.util.regex.Matcher m =
                    java.util.regex.Pattern
                            .compile(
                                    "linkedin\\.com/in/([a-zA-Z0-9\\-_%]+)"
                            )
                            .matcher(doc.html());

            if (m.find())
                return "https://www.linkedin.com/in/" + m.group(1);

        } catch (Exception ex) {

            log.warn("LinkedIn fallback failed: {}", name);
        }

        return null;
    }

    private static final Map<String, String> PC_SITES = Map.ofEntries(
            Map.entry("openai","https://openai.com"), Map.entry("google","https://google.com"),
            Map.entry("microsoft","https://microsoft.com"), Map.entry("amazon","https://amazon.com"),
            Map.entry("meta","https://meta.com"), Map.entry("nvidia","https://nvidia.com"),
            Map.entry("deloitte","https://deloitte.com"), Map.entry("mckinsey","https://mckinsey.com"),
            Map.entry("gartner","https://gartner.com"), Map.entry("who","https://who.int"),
            Map.entry("accenture","https://accenture.com"), Map.entry("infosys","https://infosys.com"),
            Map.entry("wipro","https://wipro.com"), Map.entry("tcs","https://tcs.com"),
            Map.entry("ibm","https://ibm.com"), Map.entry("pwc","https://pwc.com"),
            Map.entry("kpmg","https://kpmg.com"), Map.entry("bcg","https://bcg.com"));

    private List<PaperclipAnalysisResult.Company> pcEnrichCompanies(List<PaperclipRawExtraction.RawCompany> list) {
        if (list == null) return List.of();
        return list.stream().map(c -> PaperclipAnalysisResult.Company.builder()
                .name(c.getName()).website(pcWebsite(c.getName()))
                .linkedin(pcCompanyLinkedIn(c.getName())).build()).collect(Collectors.toList());
    }

    private String pcWebsite(String name) {
        if (name == null) return null;
        for (var e : PC_SITES.entrySet()) if (name.toLowerCase().contains(e.getKey())) return e.getValue();
        try {
            String r = aiService.webSearch("Official website for: " + name + ". Return URL only.");
            if (r != null && r.startsWith("http") && !r.contains("linkedin")) return r.trim();
        } catch (Exception ignored) {}
        return null;
    }

    private String pcCompanyLinkedIn(String name) {
        if (name == null) return null;
        try {
            String r = aiService.webSearch("LinkedIn company page for: " + name + ". Return URL only.");
            if (r != null && r.contains("linkedin.com/company/")) return r.trim();
        } catch (Exception ignored) {}
        return null;
    }

    private List<PaperclipAnalysisResult.Report> pcEnrichReports(List<String> titles) {
        if (titles == null) return List.of();
        return titles.stream().map(t -> PaperclipAnalysisResult.Report.builder()
                .title(t).source(pcSource(t)).downloadUrl(pcReportUrl(t))
                .build()).collect(Collectors.toList());
    }

    private String pcReportUrl(String title) {
        if (title == null) return null;
        try {
            String r = aiService.webSearch("Download PDF URL for report: " + title + ". Return URL only.");
            if (r != null && r.startsWith("http")) return r.trim();
        } catch (Exception ignored) {}
        return null;
    }

    private String pcSource(String t) {
        if (t == null) return "Research";
        String l = t.toLowerCase();
        if (l.contains("gartner"))  return "Gartner";
        if (l.contains("mckinsey")) return "McKinsey";
        if (l.contains("deloitte")) return "Deloitte";
        if (l.contains("pwc"))      return "PwC";
        if (l.contains("kpmg"))     return "KPMG";
        if (l.contains("who"))      return "WHO";
        if (l.contains("bcg"))      return "BCG";
        if (l.contains("forrester"))return "Forrester";
        if (l.contains("ibm"))      return "IBM";
        return "Research";
    }

    private PaperclipAnalysisResult.Summary pcBuildSummary(PaperclipRawExtraction.RawSummary raw) {
        if (raw == null) return PaperclipAnalysisResult.Summary.builder()
                .shortSummary("").detailedSummary("").keyPoints(List.of()).actionItems(List.of()).build();
        return PaperclipAnalysisResult.Summary.builder()
                .shortSummary(raw.getShortSummary()).detailedSummary(raw.getDetailedSummary())
                .keyPoints(raw.getKeyPoints() != null ? raw.getKeyPoints() : List.of())
                .actionItems(raw.getActionItems() != null ? raw.getActionItems() : List.of()).build();
    }

    private String pcToJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }


    private String cleanJson(String s) {
        return s.replaceAll("```json", "").replaceAll("```", "").trim();
    }

    public List<PaperclipResponse> getAllPaperclips() {

        return paperclipRepository.findByS3FileUrlIsNotNull()  // ← only uploaded ones
                .stream()
                .map(item -> {
                    try {
                        PaperclipAnalysisResult analysis =
                                objectMapper.readValue(
                                        item.getAnalysisJson(),
                                        PaperclipAnalysisResult.class
                                );
                        return PaperclipResponse.builder()
                                .paperclipId(item.getPaperclipId())
                                .fileName(item.getFileName())             // ← ADD
                                .s3FileUrl(item.getS3FileUrl())           // ← ADD
                                .uploadedAt(item.getCreatedAt().toString()) // ← ADD
                                .analysis(analysis)
                                .build();
                    } catch (Exception ex) {
                        log.error("Paperclip parse failed for {}", item.getPaperclipId(), ex);
                        return null;  // ← skip broken records
                    }
                })
                .filter(java.util.Objects::nonNull)  // ← remove nulls
                .toList();
    }


}
