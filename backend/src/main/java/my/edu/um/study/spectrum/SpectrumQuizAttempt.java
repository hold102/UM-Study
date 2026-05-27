package my.edu.um.study.spectrum;

public record SpectrumQuizAttempt(
        long id,
        long quiz,
        String state,   // "inprogress" | "overdue" | "finished" | "abandoned"
        long timefinish
) {}
