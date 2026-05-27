package my.edu.um.study.spectrum;

public record SpectrumSubmissionStatus(
        String status,          // "new" | "draft" | "submitted" | "reopened" | null
        String gradingStatus,   // "graded" | "notgraded" | null
        long timeSubmitted      // 0 if not submitted
) {}
