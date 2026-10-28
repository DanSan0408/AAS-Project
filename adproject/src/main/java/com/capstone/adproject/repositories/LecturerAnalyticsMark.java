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
    Long getTeamId();
    String getTeamName();
    Long getRubricId();
    Long getSubRubricId();
    String getAssessmentType();
    Double getEarned();
    Mark.SubmissionStatus getStatus();
    LocalDateTime getSubmittedAt();
}
