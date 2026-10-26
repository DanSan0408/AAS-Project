package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CommentSentimentAnalysisServiceTest {

    private CommentSentimentAnalysisService sentimentService;

    @BeforeEach
    public void setUp() {
        sentimentService = new CommentSentimentAnalysisService();
    }

    @Test
    public void testPositiveCommentNotFlagged() {
        String comment = "Great teamwork, very cooperative and completed all tasks on time!";
        CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentService.analyze(comment);

        assertFalse(result.isFlaggedNegative());
        assertTrue(result.getSentimentScore() > 0);
    }

    @Test
    public void testSarcasmDetection_ThanksForNothing() {
        String comment = "Thanks for nothing, showed up 5 minutes before presentation.";
        CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentService.analyze(comment);

        assertTrue(result.isFlaggedNegative());
        assertNotNull(result.getFlagReason());
        assertTrue(result.getFlagReason().toLowerCase().contains("sarcastic") || result.getFlagReason().toLowerCase().contains("thanks for nothing"));
    }

    @Test
    public void testSarcasmDetection_QuotesAroundPraise() {
        String comment = "He was our 'leader' who did nothing.";
        CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentService.analyze(comment);

        assertTrue(result.isFlaggedNegative());
    }

    @Test
    public void testSarcasmDetection_HelpfulNot() {
        String comment = "Super helpful not, never replied to any WhatsApp messages.";
        CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentService.analyze(comment);

        assertTrue(result.isFlaggedNegative());
    }

    @Test
    public void testNegativeInfluence_ToxicTerms() {
        String comment = "Useless and completely lazy member. Dragged the team down.";
        CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentService.analyze(comment);

        assertTrue(result.isFlaggedNegative());
        assertTrue(result.getFlagReason().toLowerCase().contains("useless") || result.getFlagReason().toLowerCase().contains("lazy"));
    }

    @Test
    public void testNegativeInfluence_LocalSlang() {
        String comment = "Budak ni beban gila, langsung tak buat kerja.";
        CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentService.analyze(comment);

        assertTrue(result.isFlaggedNegative());
        assertTrue(result.getFlagReason().toLowerCase().contains("beban") || result.getFlagReason().toLowerCase().contains("tak buat kerja"));
    }

    @Test
    public void testNegatedPositive() {
        String comment = "The student was not helpful and did not contribute.";
        CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentService.analyze(comment);

        assertTrue(result.isFlaggedNegative());
    }
}
