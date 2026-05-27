package my.edu.um.study.submission;

import jakarta.servlet.http.HttpSession;
import my.edu.um.study.config.AppProperties;
import my.edu.um.study.section.SemesterCalendar;
import my.edu.um.study.section.WeekClassifier;
import my.edu.um.study.security.TokenCipher;
import my.edu.um.study.spectrum.SpectrumAssignment;
import my.edu.um.study.spectrum.SpectrumAttachment;
import my.edu.um.study.spectrum.SpectrumClient;
import my.edu.um.study.spectrum.SpectrumContent;
import my.edu.um.study.spectrum.SpectrumCourseContents;
import my.edu.um.study.spectrum.SpectrumModule;
import my.edu.um.study.spectrum.SpectrumQuiz;
import my.edu.um.study.spectrum.SpectrumQuizAttempt;
import my.edu.um.study.spectrum.SpectrumSection;
import my.edu.um.study.spectrum.SpectrumSubmissionStatus;
import my.edu.um.study.user.User;
import my.edu.um.study.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/courses")
public class SubmissionController {

    private static final Logger log = LoggerFactory.getLogger(SubmissionController.class);

    private final UserRepository userRepo;
    private final TokenCipher cipher;
    private final SpectrumClient spectrumClient;
    private final SemesterCalendar semesterCalendar;

    public SubmissionController(UserRepository userRepo, TokenCipher cipher,
                                SpectrumClient spectrumClient, AppProperties appProperties) {
        this.userRepo = userRepo;
        this.cipher = cipher;
        this.spectrumClient = spectrumClient;
        this.semesterCalendar = appProperties.getSemesterStartDate() != null
                ? new SemesterCalendar(appProperties.getSemesterStartDate(),
                                       appProperties.getSemesterBreakStartDate())
                : null;
    }

    private record SectionContext(Integer week, SpectrumModule module) {}

    @GetMapping("/{courseId}/submissions")
    public ResponseEntity<List<Submission>> list(
            @PathVariable long courseId,
            @RequestParam(required = false) Integer week,
            HttpSession session
    ) {
        Object raw = session.getAttribute("userId");
        if (!(raw instanceof UUID userId)) return ResponseEntity.status(401).build();
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getSpectrumTokenEnc() == null || user.getSpectrumUserId() == null) {
            return ResponseEntity.status(401).build();
        }
        String wstoken = cipher.decrypt(user.getSpectrumTokenEnc());
        long moodleUserId = user.getSpectrumUserId();
        long now = Instant.now().getEpochSecond();

        Map<Long, SectionContext> cmidContext = buildCmidContextMap(wstoken, courseId);

        List<Submission> out = new ArrayList<>();
        out.addAll(collectAssignments(wstoken, courseId, moodleUserId, cmidContext, now));
        out.addAll(collectQuizzes(wstoken, courseId, moodleUserId, cmidContext, now));

        if (week != null) {
            out.removeIf(s -> !week.equals(s.week()));
        }
        out.sort(Comparator.comparing((Submission s) -> s.dueAt() == null ? "" : s.dueAt()));
        return ResponseEntity.ok(out);
    }

    private Map<Long, SectionContext> buildCmidContextMap(String wstoken, long courseId) {
        Map<Long, SectionContext> map = new HashMap<>();
        try {
            SpectrumCourseContents contents = spectrumClient.getCourseContents(wstoken, courseId);
            for (SpectrumSection s : contents.sections()) {
                WeekClassifier.Classification cls = WeekClassifier.classify(s.name());
                Integer sectionWeek = cls.week();
                for (SpectrumModule m : s.modules()) {
                    map.put(m.id(), new SectionContext(sectionWeek, m));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch course contents for cmid→module mapping: {}", e.getMessage());
        }
        return map;
    }

    private List<SubmissionMaterial> materialsFor(SpectrumModule module,
                                                  List<SpectrumAttachment> introAttachments,
                                                  List<SpectrumAttachment> introFiles,
                                                  String wstoken) {
        List<SubmissionMaterial> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        if (module != null && module.contents() != null) {
            for (SpectrumContent c : module.contents()) {
                String cType = c.type() == null ? "" : c.type();
                if (!"file".equals(cType)) continue;
                String name = c.filename() == null ? "" : c.filename();
                if (name.isBlank()) continue;
                if (!seen.add(name)) continue;
                String url = appendToken(c.fileurl(), wstoken);
                Long size = c.filesize() > 0 ? c.filesize() : null;
                out.add(new SubmissionMaterial(name, deriveFileType(c.mimetype(), name), url, size));
            }
        }

        addAttachments(out, seen, introAttachments, wstoken);
        addAttachments(out, seen, introFiles, wstoken);

        return out;
    }

    private void addAttachments(List<SubmissionMaterial> out,
                                java.util.Set<String> seen,
                                List<SpectrumAttachment> atts,
                                String wstoken) {
        if (atts == null) return;
        for (SpectrumAttachment a : atts) {
            String name = a.filename() == null ? "" : a.filename();
            if (name.isBlank()) continue;
            if (!seen.add(name)) continue;
            String url = appendToken(a.fileurl(), wstoken);
            Long size = a.filesize() > 0 ? a.filesize() : null;
            out.add(new SubmissionMaterial(name, deriveFileType(a.mimetype(), name), url, size));
        }
    }

    private List<Submission> collectAssignments(String wstoken, long courseId, long userId,
                                                Map<Long, SectionContext> cmidContext, long now) {
        List<SpectrumAssignment> assignments;
        try {
            assignments = spectrumClient.getAssignments(wstoken, courseId);
        } catch (Exception e) {
            log.warn("Failed to fetch assignments: {}", e.getMessage());
            return List.of();
        }

        List<Submission> out = new ArrayList<>(assignments.size());
        for (SpectrumAssignment a : assignments) {
            SpectrumSubmissionStatus status;
            try {
                status = spectrumClient.getSubmissionStatus(wstoken, a.id(), userId);
            } catch (Exception e) {
                log.warn("Failed to fetch submission status for assignment {}: {}", a.id(), e.getMessage());
                status = new SpectrumSubmissionStatus(null, null, 0L);
            }

            SectionContext ctx = cmidContext.get(a.cmid());
            Integer w = ctx != null ? ctx.week() : null;
            if (w == null && semesterCalendar != null && a.duedate() > 0) {
                w = semesterCalendar.weekOf(a.duedate());
            }

            String state;
            boolean submitted = "submitted".equals(status.status());
            if (submitted) state = "submitted";
            else if (a.duedate() > 0 && now > a.duedate()) state = "overdue";
            else if (a.allowsubmissionsfromdate() > 0 && now < a.allowsubmissionsfromdate()) state = "not-open-yet";
            else state = "pending";

            List<SubmissionMaterial> materials = materialsFor(
                    ctx != null ? ctx.module() : null,
                    a.introAttachments(),
                    a.introFiles(),
                    wstoken);

            out.add(new Submission(
                    "assign-" + a.id(),
                    a.name(),
                    "assignment",
                    w,
                    a.duedate() > 0 ? Instant.ofEpochSecond(a.duedate()).toString() : null,
                    a.allowsubmissionsfromdate() > 0 ? Instant.ofEpochSecond(a.allowsubmissionsfromdate()).toString() : null,
                    state,
                    submitted && status.timeSubmitted() > 0 ? Instant.ofEpochSecond(status.timeSubmitted()).toString() : null,
                    stripIntroHtml(a.intro()),
                    materials
            ));
        }
        return out;
    }

    private List<Submission> collectQuizzes(String wstoken, long courseId, long userId,
                                            Map<Long, SectionContext> cmidContext, long now) {
        List<SpectrumQuiz> quizzes;
        try {
            quizzes = spectrumClient.getQuizzes(wstoken, courseId);
        } catch (Exception e) {
            log.warn("Failed to fetch quizzes: {}", e.getMessage());
            return List.of();
        }

        List<Submission> out = new ArrayList<>(quizzes.size());
        for (SpectrumQuiz q : quizzes) {
            List<SpectrumQuizAttempt> attempts;
            try {
                attempts = spectrumClient.getQuizAttempts(wstoken, q.id(), userId);
            } catch (Exception e) {
                log.warn("Failed to fetch quiz attempts for quiz {}: {}", q.id(), e.getMessage());
                attempts = List.of();
            }

            SpectrumQuizAttempt finished = attempts.stream()
                    .filter(a -> "finished".equals(a.state()))
                    .findFirst().orElse(null);

            SectionContext ctx = cmidContext.get(q.coursemodule());
            Integer w = ctx != null ? ctx.week() : null;
            if (w == null && semesterCalendar != null && q.timeclose() > 0) {
                w = semesterCalendar.weekOf(q.timeclose());
            }

            String state;
            if (finished != null) state = "submitted";
            else if (q.timeclose() > 0 && now > q.timeclose()) state = "overdue";
            else if (q.timeopen() > 0 && now < q.timeopen()) state = "not-open-yet";
            else state = "pending";

            List<SubmissionMaterial> materials = materialsFor(
                    ctx != null ? ctx.module() : null,
                    q.introAttachments(),
                    q.introFiles(),
                    wstoken);

            out.add(new Submission(
                    "quiz-" + q.id(),
                    q.name(),
                    "quiz",
                    w,
                    q.timeclose() > 0 ? Instant.ofEpochSecond(q.timeclose()).toString() : null,
                    q.timeopen() > 0 ? Instant.ofEpochSecond(q.timeopen()).toString() : null,
                    state,
                    finished != null && finished.timefinish() > 0
                            ? Instant.ofEpochSecond(finished.timefinish()).toString() : null,
                    stripIntroHtml(q.intro()),
                    materials
            ));
        }
        return out;
    }

    private static final Pattern BLOCK_BREAK =
            Pattern.compile("(?i)</p>|</div>|</li>|<br\\s*/?>|</h[1-6]>|</tr>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\n{3,}");

    private String stripIntroHtml(String html) {
        if (html == null) return null;
        String withBreaks = BLOCK_BREAK.matcher(html).replaceAll("\n");
        String noTags = HTML_TAG.matcher(withBreaks).replaceAll("");
        String decoded = noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        String collapsed = MULTI_NEWLINE.matcher(decoded).replaceAll("\n\n").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    private String appendToken(String url, String wstoken) {
        if (url == null) return null;
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "token=" + URLEncoder.encode(wstoken, StandardCharsets.UTF_8);
    }

    private String deriveFileType(String mimetype, String filename) {
        if (mimetype != null) {
            if (mimetype.equals("application/pdf")) return "pdf";
            if (mimetype.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) return "pptx";
            if (mimetype.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) return "docx";
            if (mimetype.equals("application/vnd.ms-powerpoint")) return "ppt";
            if (mimetype.equals("application/msword")) return "doc";
            int slash = mimetype.indexOf('/');
            if (slash >= 0 && slash < mimetype.length() - 1) return mimetype.substring(slash + 1);
        }
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) return filename.substring(dot + 1).toLowerCase();
        }
        return null;
    }
}
