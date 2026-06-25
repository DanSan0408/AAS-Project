package com.capstone.adproject.util;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.web.util.HtmlUtils;

public class HtmlSanitizerUtil {

    // Define the strict policy of what HTML tags are allowed.
    // This allows <b>, <i>, <p>, <div>, <br>, and safe <a> links.
    // Everything else (especially <script>, <iframe>, and event handlers like onerror) is stripped.
    public static final PolicyFactory SAFE_HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS);

    /**
     * Sanitizes raw HTML input to prevent XSS attacks.
     * @param input the raw input string
     * @return the sanitized string containing only safe HTML
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        
        String sanitized = SAFE_HTML_POLICY.sanitize(input);
        System.out.println("🛡️ [SECURITY] Original Input: " + input);
        System.out.println("🛡️ [SECURITY] Sanitized Output: " + sanitized);
        
        return sanitized;
    }

    /**
     * Sanitizes plain text input by stripping HTML tags and returning the unescaped plain text.
     * Use this for fields that are rendered using th:text (escaped) in templates.
     * @param input the raw input string
     * @return the sanitized plain text string
     */
    public static String sanitizePlainText(String input) {
        if (input == null) return null;
        
        String sanitized = SAFE_HTML_POLICY.sanitize(input);
        String unescaped = HtmlUtils.htmlUnescape(sanitized);
        System.out.println("🛡️ [SECURITY-PLAIN] Original Input: " + input);
        System.out.println("🛡️ [SECURITY-PLAIN] Sanitized & Unescaped Output: " + unescaped);
        
        return unescaped;
    }

    /**
     * Finds URLs in the given text and converts them into clickable HTML links
     * that open in a new tab.
     * @param text the raw plain text
     * @return the HTML string with linkified URLs
     */
    public static String linkify(String text) {
        if (text == null) return null;
        
        // 1. Escape any potential HTML characters in the text to prevent XSS
        String escaped = HtmlUtils.htmlEscape(text);
        
        // 2. Convert raw URL matches to HTML links
        // Matches http://, https://, ftp:// and file:// links
        String regex = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(escaped);
        
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group();
            // Create a safe target="_blank" link
            String replacement = "<a href=\"" + url + "\" target=\"_blank\" rel=\"noopener noreferrer\" style=\"color: #2563eb; text-decoration: underline;\">" + url + "</a>";
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}