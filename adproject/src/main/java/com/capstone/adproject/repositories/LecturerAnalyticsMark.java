package com.capstone.adproject.repositories;

import java.time.LocalDateTime;
import com.capstone.adproject.model.Mark;

/** Flat query result avoids fetching lazy entity graphs for each score row. */
public interface LecturerAnalyticsMark {
    Long getId();
    Long getLecturerId();
    String getLecturerName();
    String getSourceTag();
    Long getStudentId();
    String getStudentName();
    /** The student's own (legacy) group, which may belong to another course. */
    Long getTeamId();
    /** The student's group in the course being analysed, from their course enrolment (null when none). */
    Long getAssignedTeamId();
    String getTeamName();
    Long getRubricId();
    Long getSubRubricId();
    String getAssessmentType();
    Double getEarned();
    Mark.SubmissionStatus getStatus();
    LocalDateTime getSubmittedAt();
}
