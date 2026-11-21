package com.capstone.adproject.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Announcement;
import com.capstone.adproject.model.Announcement.Audience;
import com.capstone.adproject.model.Announcement.Category;
import com.capstone.adproject.repositories.AnnouncementRepository;

/** Announcements the Super Admin writes and users see as a pop-up after signing in. */
@Service
public class AnnouncementService {

    static final int MAX_TITLE = 120;
    static final int MAX_MESSAGE = 2000;
    private static final DateTimeFormatter POSTED_ON = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final AnnouncementRepository repository;

    public AnnouncementService(AnnouncementRepository repository) {
        this.repository = repository;
    }

    /**
     * What a signed-in user needs for the pop-up. A plain serializable snapshot, so it can sit in the HTTP session
     * and be shown on every page until it is dismissed, without touching the database again.
     */
    public record AnnouncementView(Long id, String title, String message, String categoryKey, String categoryLabel,
            String postedOn) implements Serializable {
    }

    public List<Announcement> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<Announcement> findById(Long id) {
        return repository.findById(id);
    }

    /** Everyone sees "everyone" announcements; the rest follow the account's roles. */
    public Set<Audience> audiencesFor(Collection<? extends GrantedAuthority> authorities) {
        Set<Audience> audiences = EnumSet.of(Audience.ALL);
        for (GrantedAuthority authority : authorities) {
            switch (authority.getAuthority()) {
                case "ROLE_STUDENT" -> audiences.add(Audience.STUDENTS);
                case "ROLE_LECTURER" -> audiences.add(Audience.ASSESSORS);
                case "ROLE_ADMIN" -> audiences.add(Audience.ADMINS);
                default -> {
                }
            }
        }
        return audiences;
    }

    /** Announcements for this person in the course they are working in (plus the ones for every course). */
    public List<AnnouncementView> liveFor(Collection<? extends GrantedAuthority> authorities, LocalDateTime now, Long courseId) {
        return repository.findLive(audiencesFor(authorities), now, courseId).stream()
                .map(a -> new AnnouncementView(a.getId(), a.getTitle(), a.getMessage(),
                        a.getCategory().name().toLowerCase(Locale.ROOT), a.getCategory().getLabel(),
                        a.getCreatedAt().format(POSTED_ON)))
                .toList();
    }

    /** Creates the announcement, or edits it when {@code id} is given. Throws {@link IllegalArgumentException} with a user-readable reason. */
    @Transactional
    public Announcement save(Long id, String title, String message, Category category, Audience audience,
            Long courseId, boolean active, LocalDateTime startsAt, LocalDateTime endsAt, String actor) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanMessage = message == null ? "" : message.replace("\r\n", "\n").trim();
        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("Give the announcement a title.");
        }
        if (cleanTitle.length() > MAX_TITLE) {
            throw new IllegalArgumentException("The title can be at most " + MAX_TITLE + " characters.");
        }
        if (cleanMessage.isEmpty()) {
            throw new IllegalArgumentException("Write the message people should read.");
        }
        if (cleanMessage.length() > MAX_MESSAGE) {
            throw new IllegalArgumentException("The message can be at most " + MAX_MESSAGE + " characters.");
        }
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("\"Show until\" must be after \"Show from\".");
        }

        LocalDateTime now = LocalDateTime.now();
        Announcement announcement;
        if (id == null) {
            announcement = new Announcement();
            announcement.setCreatedBy(actor == null || actor.isBlank() ? "super admin" : actor);
            announcement.setCreatedAt(now);
        } else {
            announcement = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("That announcement no longer exists."));
            announcement.setUpdatedAt(now);
        }
        announcement.setTitle(cleanTitle);
        announcement.setMessage(cleanMessage);
        announcement.setCategory(category == null ? Category.NOTICE : category);
        announcement.setAudience(audience == null ? Audience.ALL : audience);
        announcement.setCourseId(courseId);
        announcement.setActive(active);
        announcement.setStartsAt(startsAt);
        announcement.setEndsAt(endsAt);
        return repository.save(announcement);
    }

    /** Switches an announcement on or off. Returns the new state. */
    @Transactional
    public boolean toggle(Long id) {
        Announcement announcement = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("That announcement no longer exists."));
        announcement.setActive(!announcement.isActive());
        announcement.setUpdatedAt(LocalDateTime.now());
        return repository.save(announcement).isActive();
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
