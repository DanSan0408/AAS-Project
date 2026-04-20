package com.capstone.adproject.util;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

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
}