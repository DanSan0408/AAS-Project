package com.capstone.adproject.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.capstone.adproject.model.AssessmentComment;

public class HtmlSanitizerUtilTest {

    @Test
    public void testSanitizePlainText() {
        // Test basic text with ampersand
        String input1 = "Assessment & Rubrics Details";
        String expected1 = "Assessment & Rubrics Details";
        assertEquals(expected1, HtmlSanitizerUtil.sanitizePlainText(input1));

        // Test OneDrive link with parameters
        String input2 = "https://onedrive.live.com/?id=root&cid=12345&authkey=abc";
        String expected2 = "https://onedrive.live.com/?id=root&cid=12345&authkey=abc";
        assertEquals(expected2, HtmlSanitizerUtil.sanitizePlainText(input2));

        // Test script tag stripping
        String input3 = "Hello <script>alert('XSS')</script> World";
        String expected3 = "Hello  World";
        assertEquals(expected3, HtmlSanitizerUtil.sanitizePlainText(input3));

        // Test other HTML tag stripping
        String input4 = "Hello <b>World</b> & Friends";
        String expected4 = "Hello <b>World</b> & Friends";
        assertEquals(expected4, HtmlSanitizerUtil.sanitizePlainText(input4));

        // Test equals sign and double quotes without double escaping
        String input5 = "L/D ratio = 2, \"Table x.x\" and <script>alert(1)</script>";
        String expected5 = "L/D ratio = 2, \"Table x.x\" and ";
        assertEquals(expected5, HtmlSanitizerUtil.sanitizePlainText(input5));

        // Test null input
        assertNull(HtmlSanitizerUtil.sanitizePlainText(null));
    }

    @Test
    public void testLinkify() {
        // Test basic linkification
        String input1 = "Check this out: https://onedrive.live.com/?id=root&cid=123";
        String expected1 = "Check this out: <a href=\"https://onedrive.live.com/?id=root&amp;cid=123\" target=\"_blank\" rel=\"noopener noreferrer\" style=\"color: #2563eb; text-decoration: underline;\">https://onedrive.live.com/?id=root&amp;cid=123</a>";
        assertEquals(expected1, HtmlSanitizerUtil.linkify(input1));

        // Test XSS prevention during linkification
        String input2 = "Hello <script>alert(1)</script> check https://google.com";
        String expected2 = "Hello &lt;script&gt;alert(1)&lt;/script&gt; check <a href=\"https://google.com\" target=\"_blank\" rel=\"noopener noreferrer\" style=\"color: #2563eb; text-decoration: underline;\">https://google.com</a>";
        assertEquals(expected2, HtmlSanitizerUtil.linkify(input2));

        // Test null input
        assertNull(HtmlSanitizerUtil.linkify(null));
    }

    @Test
    public void testAssessmentCommentUnescapingOnRead() {
        AssessmentComment comment = new AssessmentComment();
        // Simulate legacy DB stored comment that was saved with old sanitize() (containing HTML entities)
        comment.setCommentText("L/D ratio &#61; 2 and &#34;Table x.x&#34;");
        assertEquals("L/D ratio = 2 and \"Table x.x\"", comment.getCommentText());
    }
}
