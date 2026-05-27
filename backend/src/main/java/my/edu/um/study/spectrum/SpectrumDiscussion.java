package my.edu.um.study.spectrum;

import java.util.List;

public record SpectrumDiscussion(
        long id,
        String subject,
        String message,
        String userfullname,
        long created,
        List<SpectrumAttachment> attachments
) {}
