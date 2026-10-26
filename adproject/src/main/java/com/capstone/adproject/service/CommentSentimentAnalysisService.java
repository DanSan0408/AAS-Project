package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class CommentSentimentAnalysisService {

    public static class SentimentAnalysisResult {
        private final boolean flaggedNegative;
        private final String flagReason;
        private final double sentimentScore;
        private final List<String> detectedTriggers;

        public SentimentAnalysisResult(boolean flaggedNegative, String flagReason, double sentimentScore, List<String> detectedTriggers) {
            this.flaggedNegative = flaggedNegative;
            this.flagReason = flagReason;
            this.sentimentScore = sentimentScore;
            this.detectedTriggers = detectedTriggers != null ? Collections.unmodifiableList(detectedTriggers) : Collections.emptyList();
        }

        public boolean isFlaggedNegative() {
            return flaggedNegative;
        }

        public String getFlagReason() {
            return flagReason;
        }

        public double getSentimentScore() {
            return sentimentScore;
        }

        public List<String> getDetectedTriggers() {
            return detectedTriggers;
        }

        @Override
        public String toString() {
            return "SentimentAnalysisResult{" +
                    "flaggedNegative=" + flaggedNegative +
                    ", flagReason='" + flagReason + '\'' +
                    ", sentimentScore=" + sentimentScore +
                    ", detectedTriggers=" + detectedTriggers +
                    '}';
        }
    }

    // Explicit Sarcasm Patterns (Regex)
    private static final List<Pattern> SARCASM_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)\\b(thanks?|thank you)\\s+for\\s+(nothing|doing nothing|zero|not helping)\\b"),
            Pattern.compile("(?i)\\b(great|good|amazing|wonderful|stellar|super)\\s+job\\s+(doing\\s+)?(nothing|zero|bare minimum)\\b"),
            Pattern.compile("(?i)\\b(super|so|very|extremely)\\s+helpful\\s*(\\.\\.\\.|\\s+)?(not|sike|jk)\\b"),
            Pattern.compile("(?i)\\bhelpful\\s*\\(\\s*(not|sarcasm|sarcastic)\\s*\\)"),
            Pattern.compile("(?i)\\b(as if|yeah right|no surprise there|what a surprise)\\b"),
            Pattern.compile("(?i)\\b(obviously|clearly)\\s+(a\\s+)?(genius|mastermind|prodigy)\\b"),
            Pattern.compile("(?i)\\bmust be (so )?(nice|hard) (to )?(do nothing|relax|chill|sleep)\\b"),
            Pattern.compile("(?i)\\bcongrats? (on|for) doing (nothing|the bare minimum)\\b"),
            Pattern.compile("(?i)\\bglad you (finally )?decided to show up\\b"),
            Pattern.compile("(?i)\\b(['\"])(\\s*)(great|helpful|leader|hard working|hardworker|contributor)(\\s*)(['\"])"),
            Pattern.compile("(?i)\\b(best|greatest)\\s+contribution\\s+was\\s+(doing\\s+nothing|being\\s+absent|staying\\s+silent)\\b")
    );

    // Hostile, Toxic & Derogatory Phrases
    private static final List<String> HOSTILE_PHRASES = Arrays.asList(
            "did nothing", "does nothing", "did zero", "done nothing",
            "didn't contribute", "did not contribute", "never contribute",
            "didn't help", "did not help", "never helped", "never help",
            "never showed up", "never show up", "didn't show up", "did not show up",
            "waste of time", "waste of space", "freeloader", "free rider", "freerider",
            "pushed all the work", "dumped the work", "left all the work",
            "ghosted us", "ghosted the team", "never reply", "never replies", "ignored messages",
            "dragged us down", "dragged the team down", "zero effort", "no effort",
            "bare minimum", "deadweight", "dead weight",
            // Local / Malay student terms
            "tak buat kerja", "tidak buat kerja", "tak tolong", "tidak tolong",
            "beban", "menyusahkan", "pemalas", "hilang", "buat hal", "tak guna", "hauk"
    );

    // Negative Influence Tokens
    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(Arrays.asList(
            "useless", "lazy", "horrible", "terrible", "awful", "pathetic", "stupid", "idiot",
            "clueless", "worst", "hate", "toxic", "garbage", "trash", "incompetent", "annoying",
            "selfish", "unreliable", "slacker", "disaster", "worthless", "rude", "arrogant",
            "disgrace", "disappointing", "failure", "unhelpful", "hopeless", "careless"
    ));

    // Positive Tokens (for baseline sentiment scoring)
    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "great", "excellent", "good", "amazing", "helpful", "dedicated", "hardworking",
            "punctual", "cooperative", "reliable", "responsible", "supportive", "active",
            "initiative", "creative", "efficient", "organized", "friendly", "constructive",
            "valuable", "superb", "brilliant", "fantastic", "proactive"
    ));

    // Contrastive markers often indicating sarcasm or backhanded compliments
    private static final Pattern CONTRASTIVE_DISMISSAL = Pattern.compile(
            "(?i)\\b(good|great|nice|okay|fine)\\s+(teammate|worker|member|partner|effort)\\s+(if|except|only|but)\\s+.*(never|nothing|barely|zero|didn't|lazy)\\b"
    );

    public SentimentAnalysisResult analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new SentimentAnalysisResult(false, null, 0.0, Collections.emptyList());
        }

        String cleaned = text.trim();
        String lower = cleaned.toLowerCase(Locale.ENGLISH);
        List<String> detectedTriggers = new ArrayList<>();
        StringBuilder reasons = new StringBuilder();

        // 1. Check Explicit Sarcasm Patterns
        for (Pattern p : SARCASM_PATTERNS) {
            Matcher m = p.matcher(cleaned);
            if (m.find()) {
                String matchStr = m.group();
                detectedTriggers.add(matchStr);
                if (reasons.length() > 0) reasons.append("; ");
                reasons.append("Sarcastic pattern detected: '").append(matchStr).append("'");
            }
        }

        // 2. Check Contrastive Sarcasm
        Matcher cm = CONTRASTIVE_DISMISSAL.matcher(cleaned);
        if (cm.find()) {
            String matchStr = cm.group();
            detectedTriggers.add(matchStr);
            if (reasons.length() > 0) reasons.append("; ");
            reasons.append("Backhanded / sarcastic contrast: '").append(matchStr).append("'");
        }

        // 3. Check Hostile Phrases
        for (String phrase : HOSTILE_PHRASES) {
            if (lower.contains(phrase)) {
                detectedTriggers.add(phrase);
                if (reasons.length() > 0) reasons.append("; ");
                reasons.append("Hostile / dismissive phrasing: '").append(phrase).append("'");
            }
        }

        // 4. Token-level Sentiment Calculation
        String[] words = lower.split("[^a-zA-Z0-9_'-]+");
        int positiveCount = 0;
        int negativeCount = 0;

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            boolean negated = (i > 0 && isNegation(words[i - 1])) || (i > 1 && isNegation(words[i - 2]));

            if (NEGATIVE_WORDS.contains(word)) {
                if (!negated) {
                    negativeCount++;
                    detectedTriggers.add(word);
                    if (reasons.length() == 0 || !reasons.toString().contains("Negative term")) {
                        if (reasons.length() > 0) reasons.append("; ");
                        reasons.append("Negative term: '").append(word).append("'");
                    }
                } else {
                    positiveCount++; // e.g. "not lazy"
                }
            } else if (POSITIVE_WORDS.contains(word)) {
                if (!negated) {
                    positiveCount++;
                } else {
                    negativeCount++; // e.g. "not helpful"
                    detectedTriggers.add("not " + word);
                    if (reasons.length() > 0) reasons.append("; ");
                    reasons.append("Negated positive term: 'not ").append(word).append("'");
                }
            }
        }

        double score;
        int totalTokens = positiveCount + negativeCount;
        if (totalTokens == 0) {
            score = 0.0;
        } else {
            score = (double) (positiveCount - negativeCount) / totalTokens;
        }

        boolean flagged = !detectedTriggers.isEmpty() || score <= -0.4;
        String finalReason = reasons.length() > 0 ? reasons.toString() : (flagged ? "Overall negative sentiment score (" + String.format(Locale.ENGLISH, "%.2f", score) + ")" : null);

        return new SentimentAnalysisResult(flagged, finalReason, score, detectedTriggers);
    }

    private boolean isNegation(String word) {
        return "not".equals(word) || "never".equals(word) || "no".equals(word)
                || "hardly".equals(word) || "barely".equals(word) || "scarcely".equals(word)
                || "don't".equals(word) || "dont".equals(word) || "didn't".equals(word)
                || "didnt".equals(word) || "wasn't".equals(word) || "wasnt".equals(word)
                || "isn't".equals(word) || "isnt".equals(word) || "tak".equals(word)
                || "bukan".equals(word);
    }
}
