package com.example.airesume.service;

import com.example.airesume.dto.AnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AnalysisResult analyze(String resumeText, String jobDescription) {

        try {
            String requestBody = buildRequestBody(resumeText, jobDescription);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String raw = response.body();

            return parseResponse(raw);
        } catch (Exception e) {
            // If quota exceeded, use local heuristic analysis
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("You exceeded your current quota")) {
                AnalysisResult fallback = localFallbackAnalysis(resumeText, jobDescription);
                List<String> sug = new ArrayList<>();
                sug.add("(Using offline heuristic analysis because your OpenAI quota is exceeded.)");
                if (fallback.getSuggestions() != null) {
                    sug.addAll(fallback.getSuggestions());
                }
                fallback.setSuggestions(sug);
                return fallback;
            }

            // Generic fallback if some other error
            e.printStackTrace();
            AnalysisResult fallback = new AnalysisResult();
            fallback.setMatchScore(0);
            fallback.setMatchedSkills(List.of());
            fallback.setMissingSkills(List.of());
            List<String> sug = new ArrayList<>();
            sug.add("AI analysis failed: " + msg);
            fallback.setSuggestions(sug);
            return fallback;
        }
    }

    /**
     * Build JSON request body for OpenAI Chat Completions API.
     */
    private String buildRequestBody(String resumeText, String jobDescription) throws Exception {

        String systemPrompt = """
                You are an ATS-style resume analyzer.
                Compare the RESUME with the JOB DESCRIPTION.
                Respond ONLY with valid JSON in this exact structure:
                {
                  "matchScore": <0-100>,
                  "matchedSkills": ["Java", "Spring Boot", ...],
                  "missingSkills": ["Docker", "Kubernetes", ...],
                  "suggestions": ["sentence1", "sentence2", ...]
                }
                """;

        String userContent = "JOB DESCRIPTION:\n" + jobDescription + "\n\nRESUME:\n" + resumeText;

        var root = objectMapper.createObjectNode();
        root.put("model", model);

        var messages = root.putArray("messages");

        var sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Parse the OpenAI JSON response where the assistant's content is itself JSON.
     */
    private AnalysisResult parseResponse(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);

        if (root.has("error")) {
            String msg = root.path("error").path("message").asText("Unknown error");
            throw new IllegalStateException("OpenAI API error: " + msg);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("No choices in OpenAI response");
        }

        String content = choices.get(0).path("message").path("content").asText();
        JsonNode resultNode = objectMapper.readTree(content);

        AnalysisResult result = new AnalysisResult();

        result.setMatchScore(resultNode.path("matchScore").asInt(0));

        List<String> matched = new ArrayList<>();
        resultNode.path("matchedSkills").forEach(n -> matched.add(n.asText()));
        result.setMatchedSkills(matched);

        List<String> missing = new ArrayList<>();
        resultNode.path("missingSkills").forEach(n -> missing.add(n.asText()));
        result.setMissingSkills(missing);

        List<String> suggestions = new ArrayList<>();
        resultNode.path("suggestions").forEach(n -> suggestions.add(n.asText()));
        result.setSuggestions(suggestions);

        return result;
    }

    /**
     * Local heuristic analysis when API quota is exceeded.
     * Very simple: compares keywords from JD and Resume.
     */
    private AnalysisResult localFallbackAnalysis(String resumeText, String jobDescription) {
        AnalysisResult result = new AnalysisResult();

        // Basic tokenization
        Set<String> resumeTokens = tokenize(resumeText);
        Set<String> jdTokens = tokenize(jobDescription);

        // Pretend these are "skills" = meaningful tokens in JD
        Set<String> matched = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();

        for (String token : jdTokens) {
            if (resumeTokens.contains(token)) {
                matched.add(token);
            } else {
                missing.add(token);
            }
        }

        // Compute a simple match score
        int score;
        if (jdTokens.isEmpty()) {
            score = 0;
        } else {
            score = (int) Math.round((matched.size() * 100.0) / jdTokens.size());
        }

        result.setMatchScore(score);
        result.setMatchedSkills(new ArrayList<>(matched));
        result.setMissingSkills(new ArrayList<>(missing));

        List<String> suggestions = new ArrayList<>();
        suggestions.add("Highlight more of the required keywords from the job description in your resume.");
        suggestions.add("Add concrete project examples showing how you used the matched skills.");
        suggestions.add("Consider learning or mentioning missing skills if they are important for this role.");
        result.setSuggestions(suggestions);

        return result;
    }

    /**
     * Tokenize text into lowercase keywords, removing very short/common words.
     */
    private Set<String> tokenize(String text) {
        if (text == null) text = "";
        String[] rawTokens = text.toLowerCase()
                .replaceAll("[^a-z0-9+.#]", " ")
                .split("\\s+");

        Set<String> stopWords = Set.of(
                "and", "or", "the", "a", "an", "to", "of", "in", "for", "on", "with",
                "as", "at", "by", "is", "are", "be", "this", "that", "it", "you",
                "your", "we", "our", "they", "their", "i", "me"
        );

        return Arrays.stream(rawTokens)
                .filter(t -> t.length() > 1)
                .filter(t -> !stopWords.contains(t))
                .limit(200) // keep it small
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
