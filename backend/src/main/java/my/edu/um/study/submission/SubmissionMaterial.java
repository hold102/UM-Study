package my.edu.um.study.submission;

public record SubmissionMaterial(
        String name,
        String fileType,
        String downloadUrl,
        Long sizeBytes
) {}
