package com.stockai.controller;

import com.stockai.service.ProfitCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profit")
@CrossOrigin
@RequiredArgsConstructor
public class ProfitCheckController {

    private final ProfitCheckService profitCheckService;

    // 종목 코드 검색
    @GetMapping("/search")
    public Map<String, Object> searchStock(@RequestParam String name) {
        return profitCheckService.searchStock(name);
    }

    // 수익률 체크
    @GetMapping("/check")
    public Map<String, Object> checkProfit(@RequestParam String code) {
        return profitCheckService.checkProfit(code);
    }
}