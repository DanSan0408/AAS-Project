package com.capstone.adproject.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Announcement;
import com.capstone.adproject.model.Announcement.Audience;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByCreatedAtDesc();

    /**
     * Switched on, addressed to one of the audiences, for every course or for the given one, and inside its optional
     * start/end window. Newest first. A null course id returns only the all-course announcements.
     */
    @Query("""
            select a from Announcement a
            where a.active = true
              and a.audience in :audiences
              and (a.courseId is null or a.courseId = :courseId)
              and (a.startsAt is null or a.startsAt <= :now)
              and (a.endsAt is null or a.endsAt > :now)
            order by a.createdAt desc
            """)
    List<Announcement> findLive(@Param("audiences") Set<Audience> audiences, @Param("now") LocalDateTime now,
                                @Param("courseId") Long courseId);
}
