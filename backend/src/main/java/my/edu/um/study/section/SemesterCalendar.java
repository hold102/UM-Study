package my.edu.um.study.section;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class SemesterCalendar {

    private static final int TOTAL_WEEKS = 14;
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    private final LocalDate weekOneStart;
    private final Integer breakCalendarWeek;

    public SemesterCalendar(LocalDate weekOneStart) {
        this(weekOneStart, null);
    }

    public SemesterCalendar(LocalDate weekOneStart, LocalDate breakStart) {
        this.weekOneStart = weekOneStart;
        if (breakStart == null) {
            this.breakCalendarWeek = null;
        } else {
            long offset = ChronoUnit.DAYS.between(weekOneStart, breakStart);
            this.breakCalendarWeek = offset < 0 ? null : (int) (offset / 7) + 1;
        }
    }

    /**
     * Returns the academic week (1–14) for the given Unix epoch second,
     * skipping the mid-semester break week. Returns null when the timestamp
     * falls before Week 1, inside the break week, or past Week 14.
     */
    public Integer weekOf(long epochSecond) {
        LocalDate date = Instant.ofEpochSecond(epochSecond).atZone(MALAYSIA).toLocalDate();
        return weekOfDate(date);
    }

    public Integer weekOfDate(LocalDate date) {
        long dayOffset = ChronoUnit.DAYS.between(weekOneStart, date);
        if (dayOffset < 0) return null;
        int calendarWeek = (int) (dayOffset / 7) + 1;

        if (breakCalendarWeek != null) {
            if (calendarWeek == breakCalendarWeek) return null;
            if (calendarWeek > breakCalendarWeek) calendarWeek -= 1;
        }
        return calendarWeek > TOTAL_WEEKS ? null : calendarWeek;
    }

    /** Inverse of {@link #weekOfDate}: returns the Monday date of the given academic week. */
    public LocalDate startOfWeek(int academicWeek) {
        if (academicWeek < 1 || academicWeek > TOTAL_WEEKS) return null;
        int calendarWeek = academicWeek;
        if (breakCalendarWeek != null && calendarWeek >= breakCalendarWeek) {
            calendarWeek += 1;
        }
        return weekOneStart.plusWeeks(calendarWeek - 1L);
    }
}
