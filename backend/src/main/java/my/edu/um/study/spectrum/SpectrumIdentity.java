package my.edu.um.study.spectrum;

public record SpectrumIdentity(
        String wstoken,
        long spectrumUserId,
        String username,
        String fullName,
        String email
) {}
