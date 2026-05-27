package my.edu.um.study.file;

public record FileItem(
        String id,
        String name,
        String fileType,
        String downloadUrl,
        Long sizeBytes,
        Integer week,
        String folder,
        String description,
        String category
) {}
