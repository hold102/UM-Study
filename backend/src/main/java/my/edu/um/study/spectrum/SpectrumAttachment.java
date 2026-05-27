package my.edu.um.study.spectrum;

public record SpectrumAttachment(
        String filename,
        String fileurl,
        long filesize,
        String mimetype
) {}
