package my.edu.um.study.spectrum;

import java.util.List;

public record SpectrumModule(
        long id,
        long instance,
        String name,
        String modname,
        String url,
        String description,
        List<SpectrumContent> contents
) {}
