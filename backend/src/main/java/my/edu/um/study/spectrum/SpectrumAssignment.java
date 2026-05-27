package my.edu.um.study.spectrum;

import java.util.List;

public record SpectrumAssignment(
        long id,
        long cmid,
        long course,
        String name,
        long duedate,
        long allowsubmissionsfromdate,
        String intro,
        List<SpectrumAttachment> introAttachments,
        List<SpectrumAttachment> introFiles
) {}
