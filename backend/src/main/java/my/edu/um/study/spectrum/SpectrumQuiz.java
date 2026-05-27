package my.edu.um.study.spectrum;

import java.util.List;

public record SpectrumQuiz(
        long id,
        long coursemodule,
        long course,
        String name,
        long timeopen,
        long timeclose,
        String intro,
        List<SpectrumAttachment> introAttachments,
        List<SpectrumAttachment> introFiles
) {}
