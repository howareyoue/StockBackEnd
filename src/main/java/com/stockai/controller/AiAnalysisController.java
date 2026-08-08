package com.stockai.controller;

import com.stockai.entity.AiAnalysis;
import com.stockai.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-analysis")
@CrossOrigin
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    // AI 분석 화면 클릭 시 호출
    @GetMapping
    public List<AiAnalysis> getAiAnalysis() {
        return aiAnalysisService.getAiAnalysis();
    }
}