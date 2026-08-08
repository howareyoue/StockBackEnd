package com.stockai.service;

import com.stockai.repository.RecommendationHistoryRepository;
import com.stockai.repository.StockHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private RecommendationHistoryRepository recommendationHistoryRepository;

    @InjectMocks
    private StockService stockService;

    @Test
    void shouldUseStoredRecommendationsWhenMarketIsClosed() {
        boolean shouldUseStoredRecommendations = stockService.shouldUseStoredRecommendations(
                LocalDate.of(2026, 7, 12),
                LocalTime.of(20, 0)
        );

        assertThat(shouldUseStoredRecommendations).isTrue();
    }

    @Test
    void shouldResolveCurrentDateUsingKoreaStandardTime() {
        Clock clock = Clock.fixed(
                LocalDateTime.of(2026, 7, 28, 9, 30)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                ZoneId.of("Asia/Seoul")
        );

        assertThat(stockService.getCurrentDateForRecommendation(clock))
                .isEqualTo(LocalDate.of(2026, 7, 28));
    }
}
