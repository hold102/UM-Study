package my.edu.um.study.spectrum;

public record SpectrumContent(
        String type,
        String filename,
        long filesize,
        String fileurl,
        String mimetype,
        long timemodified,
        String author
) {}
