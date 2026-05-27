package my.edu.um.study.section;

import my.edu.um.study.config.AppProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    private final AppProperties props;
    private final SemesterCalendar calendar;

    public CalendarController(AppProperties props) {
        this.props = props;
        this.calendar = props.getSemesterStartDate() != null
                ? new SemesterCalendar(props.getSemesterStartDate(), props.getSemesterBreakStartDate())
                : null;
    }

    public record CalendarInfo(
            LocalDate semesterStartDate,
            LocalDate semesterBreakStartDate,
            Integer currentWeek,
            int totalWeeks
    ) {}

    @GetMapping
    public ResponseEntity<CalendarInfo> info() {
        LocalDate today = LocalDate.now(MALAYSIA);
        Integer current = calendar != null ? calendar.weekOfDate(today) : null;
        return ResponseEntity.ok(new CalendarInfo(
                props.getSemesterStartDate(),
                props.getSemesterBreakStartDate(),
                current,
                14
        ));
    }
}
