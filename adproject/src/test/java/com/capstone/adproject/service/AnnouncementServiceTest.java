package com.capstone.adproject.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.capstone.adproject.model.Announcement;
import com.capstone.adproject.model.Announcement.Audience;
import com.capstone.adproject.model.Announcement.Category;
import com.capstone.adproject.model.Announcement.Status;
import com.capstone.adproject.repositories.AnnouncementRepository;
import com.capstone.adproject.service.AnnouncementService.AnnouncementView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

    @Mock
    private AnnouncementRepository repository;

    @InjectMocks
    private AnnouncementService service;

    private static SimpleGrantedAuthority role(String name) {
        return new SimpleGrantedAuthority(name);
    }

    @Test
    void studentsSeeEveryoneAndStudentNoticesOnly() {
        assertEquals(Set.of(Audience.ALL, Audience.STUDENTS), service.audiencesFor(List.of(role("ROLE_STUDENT"))));
    }

    @Test
    void assessorsAndAdminsGetTheirOwnAudience() {
        assertEquals(Set.of(Audience.ALL, Audience.ASSESSORS), service.audiencesFor(List.of(role("ROLE_LECTURER"))));
        assertEquals(Set.of(Audience.ALL, Audience.ADMINS), service.audiencesFor(List.of(role("ROLE_ADMIN"))));
    }

    @Test
    void aSuperAdminWhoIsAlsoAdminAndAssessorSeesAllOfThose() {
        Set<Audience> audiences = service.audiencesFor(
                List.of(role("ROLE_SUPER_ADMIN"), role("ROLE_ADMIN"), role("ROLE_LECTURER")));

        assertEquals(Set.of(Audience.ALL, Audience.ADMINS, Audience.ASSESSORS), audiences);
    }

    @Test
    void anUnknownRoleStillGetsTheEveryoneAnnouncements() {
        assertEquals(Set.of(Audience.ALL), service.audiencesFor(List.of(role("ROLE_SOMETHING_NEW"))));
    }

    @Test
    void liveForAsksForTheCourseThePersonIsWorkingIn() {
        when(repository.findLive(anySet(), any(LocalDateTime.class), eq(10L))).thenReturn(List.of());

        service.liveFor(List.of(role("ROLE_STUDENT")), NOW, 10L);

        verify(repository).findLive(anySet(), eq(NOW), eq(10L));
    }

    @Test
    void anAnnouncementCanBeForOneCourseOrForAll() {
        when(repository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        Announcement forOne = service.save(null, "Title", "Text", Category.NOTICE, Audience.ALL, 10L, true, null, null, "root");
        Announcement forAll = service.save(null, "Title", "Text", Category.NOTICE, Audience.ALL, null, true, null, null, "root");

        assertEquals(10L, forOne.getCourseId());
        assertNull(forAll.getCourseId());
    }

    @Test
    void liveForTurnsRowsIntoPlainViewsForTheSession() {
        Announcement a = new Announcement();
        a.setId(7L);
        a.setTitle("Nightly maintenance");
        a.setMessage("Down 02:00-02:30.");
        a.setCategory(Category.MAINTENANCE);
        a.setCreatedAt(LocalDateTime.of(2026, 11, 18, 9, 30));
        when(repository.findLive(anySet(), any(LocalDateTime.class), any())).thenReturn(List.of(a));

        List<AnnouncementView> views = service.liveFor(List.of(role("ROLE_STUDENT")), NOW, null);

        assertEquals(1, views.size());
        AnnouncementView view = views.get(0);
        assertEquals(7L, view.id());
        assertEquals("Nightly maintenance", view.title());
        assertEquals("maintenance", view.categoryKey());
        assertEquals("Maintenance", view.categoryLabel());
        assertEquals("18 Nov 2026", view.postedOn());
    }

    @Test
    void savingANewAnnouncementRecordsWhoWroteItAndWhen() {
        when(repository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        Announcement saved = service.save(null, "  New release  ", "Faster evaluation page.\r\nLess waiting.",
                Category.RELEASE, Audience.ASSESSORS, null, true, null, null, "root");

        assertEquals("New release", saved.getTitle());
        assertEquals("Faster evaluation page.\nLess waiting.", saved.getMessage());
        assertEquals("root", saved.getCreatedBy());
        assertEquals(Audience.ASSESSORS, saved.getAudience());
        assertTrue(saved.isActive());
        assertTrue(saved.getCreatedAt() != null);
    }

    @Test
    void editingKeepsTheOriginalAuthorAndCreationTime() {
        Announcement existing = new Announcement();
        existing.setId(3L);
        existing.setTitle("Old");
        existing.setMessage("Old text");
        existing.setCreatedBy("first.admin");
        existing.setCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        Announcement saved = service.save(3L, "Updated", "New text", Category.NOTICE, Audience.ALL, null, false, null, null, "second.admin");

        assertEquals("first.admin", saved.getCreatedBy());
        assertEquals(LocalDateTime.of(2026, 1, 1, 8, 0), saved.getCreatedAt());
        assertEquals("Updated", saved.getTitle());
        assertFalse(saved.isActive());
        assertTrue(saved.getUpdatedAt() != null);
    }

    @Test
    void rejectsBlankTitleBlankMessageAndOverlongText() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(null, "   ", "text", Category.NOTICE, Audience.ALL, null, true, null, null, "root"));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(null, "Title", "  ", Category.NOTICE, Audience.ALL, null, true, null, null, "root"));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(null, "x".repeat(121), "text", Category.NOTICE, Audience.ALL, null, true, null, null, "root"));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(null, "Title", "x".repeat(2001), Category.NOTICE, Audience.ALL, null, true, null, null, "root"));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAWindowThatEndsBeforeItStarts() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.save(null, "Title", "Text", Category.NOTICE, Audience.ALL, null, true, NOW, NOW.minusHours(1), "root"));

        assertTrue(error.getMessage().contains("after"));
    }

    @Test
    void editingAnAnnouncementThatNoLongerExistsFailsClearly() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.save(99L, "Title", "Text", Category.NOTICE, Audience.ALL, null, true, null, null, "root"));
    }

    @Test
    void toggleFlipsActiveAndReportsTheNewState() {
        Announcement a = new Announcement();
        a.setActive(true);
        when(repository.findById(5L)).thenReturn(Optional.of(a));
        when(repository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertFalse(service.toggle(5L));

        ArgumentCaptor<Announcement> saved = ArgumentCaptor.forClass(Announcement.class);
        verify(repository).save(saved.capture());
        assertFalse(saved.getValue().isActive());
    }

    @Test
    void statusReflectsTheSwitchAndTheScheduleWindow() {
        Announcement a = new Announcement();
        a.setActive(true);
        assertEquals(Status.LIVE, a.statusAt(NOW));

        a.setStartsAt(NOW.plusHours(2));
        assertEquals(Status.SCHEDULED, a.statusAt(NOW));

        a.setStartsAt(NOW.minusDays(2));
        a.setEndsAt(NOW.minusDays(1));
        assertEquals(Status.EXPIRED, a.statusAt(NOW));

        a.setActive(false);
        assertEquals(Status.OFF, a.statusAt(NOW));
    }
}
