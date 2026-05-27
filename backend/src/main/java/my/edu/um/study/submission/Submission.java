package my.edu.um.study.submission;

import java.util.List;

public record Submission(
        String id,
        String title,
        String kind,        // "assignment" | "quiz"
        Integer week,
        String dueAt,       // ISO instant, null if no due date
        String openAt,      // ISO instant, null if no open date
        String status,      // "submitted" | "pending" | "overdue" | "not-open-yet"
        String submittedAt, // ISO instant, null if not submitted
        String description, // cleaned plain-text intro, null if empty
        List<SubmissionMaterial> materials
) {}
