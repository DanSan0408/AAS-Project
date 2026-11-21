package com.capstone.adproject.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;

/** The only user-facing endpoint of the announcement pop-up: "Got it". Open to every signed-in role. */
@Controller
@RequestMapping("/announcements")
public class AnnouncementController {

    @PostMapping("/dismiss")
    public ResponseEntity<Void> dismiss(HttpSession session) {
        AnnouncementAdvice.markSeen(session);
        return ResponseEntity.noContent().build();
    }
}
