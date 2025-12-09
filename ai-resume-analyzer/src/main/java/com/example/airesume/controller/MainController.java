package com.example.airesume.controller;

import com.example.airesume.dto.AnalysisResult;
import com.example.airesume.service.AiService;
import com.example.airesume.service.ResumeParserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class MainController {

    private final ResumeParserService resumeParserService;
    private final AiService aiService;

    public MainController(ResumeParserService resumeParserService, AiService aiService) {
        this.resumeParserService = resumeParserService;
        this.aiService = aiService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam("resumeFile") MultipartFile resumeFile,
                          @RequestParam("jdFile") MultipartFile jdFile,
                          Model model) {

        try {
            String resumeText = resumeParserService.extractText(resumeFile);
            String jdText = resumeParserService.extractText(jdFile);

            AnalysisResult result = aiService.analyze(resumeText, jdText);

            model.addAttribute("analysisResult", result);
            model.addAttribute("resumePreview", truncate(resumeText, 1200));
            model.addAttribute("jobDescriptionPreview", truncate(jdText, 1200));

            return "result";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Error: " + e.getMessage());
            return "index";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
