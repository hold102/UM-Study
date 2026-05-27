package my.edu.um.study.section;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WeekClassifier {

    public record Classification(Integer week, String bucket, double confidence) {}

    private static final Pattern WEEK = Pattern.compile("(?i)\\bweek\\s*0*(\\d{1,2})\\b");
    private static final Pattern TOPIC = Pattern.compile("(?i)\\btopic\\s*0*(\\d{1,2})\\b");
    private static final Pattern LECTURE = Pattern.compile("(?i)\\blecture\\s*0*(\\d{1,2})\\b");
    private static final Pattern PROJECT = Pattern.compile("(?i)(final\\s*project|project\\s*\\d?|group\\s*project)");
    private static final Pattern PASTYEAR = Pattern.compile("(?i)(past\\s*year|past\\s*paper|previous\\s*year|past[-\\s]exam)");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)(assignment|tutorial)\\s*0*(\\d{1,2})?");

    private WeekClassifier() {}

    public static Classification classify(String title) {
        if (title == null || title.isBlank()) {
            return new Classification(null, null, 0.0);
        }

        Matcher m = WEEK.matcher(title);
        if (m.find()) {
            int w = Integer.parseInt(m.group(1));
            return new Classification(w > 14 ? null : w, null, 0.95);
        }
        m = TOPIC.matcher(title);
        if (m.find()) {
            int w = Integer.parseInt(m.group(1));
            return new Classification(w > 14 ? null : w, null, 0.8);
        }
        m = LECTURE.matcher(title);
        if (m.find()) {
            int w = Integer.parseInt(m.group(1));
            return new Classification(w > 14 ? null : w, null, 0.7);
        }
        if (PROJECT.matcher(title).find()) {
            return new Classification(null, "project", 0.9);
        }
        if (PASTYEAR.matcher(title).find()) {
            return new Classification(null, "pastyear", 0.9);
        }
        if (ASSIGNMENT.matcher(title).find()) {
            return new Classification(null, "project", 0.85);
        }
        return new Classification(null, null, 0.0);
    }
}
