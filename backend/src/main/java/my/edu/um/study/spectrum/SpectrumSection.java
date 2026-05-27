package my.edu.um.study.spectrum;

import java.util.List;

public record SpectrumSection(
        long id,
        int sectionIndex,
        String name,
        String summary,
        List<SpectrumModule> modules
) {}
