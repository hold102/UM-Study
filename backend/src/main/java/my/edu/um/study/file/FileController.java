package my.edu.um.study.file;

import jakarta.servlet.http.HttpSession;
import my.edu.um.study.config.AppProperties;
import my.edu.um.study.section.SemesterCalendar;
import my.edu.um.study.section.WeekClassifier;
import my.edu.um.study.security.TokenCipher;
import my.edu.um.study.spectrum.SpectrumClient;
import my.edu.um.study.spectrum.SpectrumContent;
import my.edu.um.study.spectrum.SpectrumCourseContents;
import my.edu.um.study.spectrum.SpectrumModule;
import my.edu.um.study.spectrum.SpectrumSection;
import my.edu.um.study.user.User;
import my.edu.um.study.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/courses")
public class FileController {

    private final UserRepository userRepo;
    private final TokenCipher cipher;
    private final SpectrumClient spectrumClient;
    private final SemesterCalendar semesterCalendar;

    // Broad lecture/slides indicators in a filename or module name.
    // Short tokens (lect, chap, mod, l\d) require look-around so "future"/"unlock" don't match "tut"/"lock".
    private static final Pattern SLIDES_PAT = Pattern.compile(
            "(?i)(slides?|lecture|chapter|topic|module|reading|handout|notes?|"
            + "(?<![a-z])(?:lect|chap|ch|mod)(?:[_\\- ]?\\d+)?(?![a-z])|"
            + "(?<![a-z])l\\d{1,2}(?![a-z]))"
    );
    private static final Pattern TUTORIAL_PAT = Pattern.compile(
            "(?i)(tutorial|practical|exercise|worksheet|seminar|discussion|"
            + "(?<![a-z])(?:tut|prac|lab|ws|disc)(?:[_\\- ]?\\d+)?(?![a-z])|"
            + "(?<![a-z])t\\d{1,2}(?![a-z]))"
    );
    private static final Pattern LECTURE_SECTION = Pattern.compile("(?i)(lecture|week|topic|chapter|slide|module)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern BLOCK_BREAK = Pattern.compile("(?i)</p>|<br\\s*/?>|</div>|</li>");

    public FileController(UserRepository userRepo, TokenCipher cipher,
                          SpectrumClient spectrumClient, AppProperties appProperties) {
        this.userRepo = userRepo;
        this.cipher = cipher;
        this.spectrumClient = spectrumClient;
        this.semesterCalendar = appProperties.getSemesterStartDate() != null
                ? new SemesterCalendar(appProperties.getSemesterStartDate(),
                                       appProperties.getSemesterBreakStartDate())
                : null;
    }

    @GetMapping("/{courseId}/files")
    public ResponseEntity<List<FileItem>> list(
            @PathVariable long courseId,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String type,
            HttpSession session
    ) {
        Object raw = session.getAttribute("userId");
        if (!(raw instanceof UUID userId)) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getSpectrumTokenEnc() == null) {
            return ResponseEntity.status(401).build();
        }
        String wstoken = cipher.decrypt(user.getSpectrumTokenEnc());

        if ("announcement".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(List.of());
        }

        SpectrumCourseContents contents = spectrumClient.getCourseContents(wstoken, courseId);

        List<FileItem> out = new ArrayList<>();
        for (SpectrumSection s : contents.sections()) {
            WeekClassifier.Classification cls = WeekClassifier.classify(s.name());

            if (bucket != null && (cls.bucket() == null || !cls.bucket().equalsIgnoreCase(bucket))) continue;

            for (SpectrumModule m : s.modules()) {
                String modname = m.modname() == null ? "" : m.modname();
                boolean isResource = modname.equals("resource");
                boolean isUrlMod = modname.equals("url");
                boolean isFolderMod = modname.equals("folder");
                if (!isResource && !isUrlMod && !isFolderMod) continue;

                if (m.contents() != null) {
                    for (SpectrumContent c : m.contents()) {
                        String cType = c.type() == null ? "" : c.type();
                        if (!"file".equals(cType) && !"url".equals(cType)) continue;
                        if (type != null && !matchesType(type, c, m, s)) continue;

                        Integer resolvedWeek = resolveWeek(cls.week(), c.timemodified());
                        if (week != null && !week.equals(resolvedWeek)) continue;

                        boolean isLink = "url".equals(cType);
                        String displayName = isLink && (c.filename() == null || c.filename().isBlank())
                                ? (m.name() == null ? "Link" : m.name())
                                : c.filename();
                        String folderName = isFolderMod ? m.name() : null;
                        String idKey = s.id() + "-" + m.id() + "-" + URLEncoder.encode(displayName == null ? "" : displayName, StandardCharsets.UTF_8);
                        String fileType = isLink ? "link" : deriveFileType(c.mimetype(), c.filename());
                        String downloadUrl = isLink ? c.fileurl() : appendToken(c.fileurl(), wstoken);
                        Long size = c.filesize() > 0 ? c.filesize() : null;
                        String description = stripHtml(m.description());
                        String category = matchesType("slides", c, m, s) ? "slides"
                                : matchesType("tutorial", c, m, s) ? "tutorial"
                                : null;
                        out.add(new FileItem(idKey, displayName, fileType, downloadUrl, size, resolvedWeek, folderName,
                                description.isEmpty() ? null : description, category));
                    }
                } else if (isUrlMod && m.url() != null) {
                    Integer resolvedWeek = cls.week();
                    if (week != null && !week.equals(resolvedWeek)) continue;
                    String displayName = m.name() == null ? "Link" : m.name();
                    String idKey = s.id() + "-" + URLEncoder.encode(displayName, StandardCharsets.UTF_8);
                    String description = stripHtml(m.description());
                    String modName = m.name() == null ? "" : m.name();
                    String category = (SLIDES_PAT.matcher(displayName).find() || SLIDES_PAT.matcher(modName).find())
                            ? "slides"
                            : (TUTORIAL_PAT.matcher(displayName).find() || TUTORIAL_PAT.matcher(modName).find())
                                ? "tutorial"
                                : null;
                    out.add(new FileItem(idKey, displayName, "link", m.url(), null, resolvedWeek, null,
                            description.isEmpty() ? null : description, category));
                }
            }
        }
        return ResponseEntity.ok(out);
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        String spaced = BLOCK_BREAK.matcher(html).replaceAll("\n");
        String noTags = HTML_TAG.matcher(spaced).replaceAll("");
        String decoded = noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return decoded.replaceAll("\n{3,}", "\n\n").trim();
    }

    private Integer resolveWeek(Integer sectionWeek, long timemodified) {
        if (sectionWeek != null) return sectionWeek;
        if (semesterCalendar == null || timemodified <= 0) return null;
        return semesterCalendar.weekOf(timemodified);
    }

    private boolean matchesType(String type, SpectrumContent c, SpectrumModule m, SpectrumSection s) {
        String filename = c.filename() == null ? "" : c.filename();
        String mimetype = c.mimetype() == null ? "" : c.mimetype();
        String modName = m.name() == null ? "" : m.name();
        String secName = s.name() == null ? "" : s.name();

        boolean looksLikeTutorial = TUTORIAL_PAT.matcher(filename).find()
                || TUTORIAL_PAT.matcher(modName).find();
        return switch (type.toLowerCase()) {
            case "slides" -> SLIDES_PAT.matcher(filename).find()
                    || SLIDES_PAT.matcher(modName).find()
                    || ((mimetype.contains("presentation") || mimetype.contains("pdf"))
                        && LECTURE_SECTION.matcher(secName).find()
                        && !looksLikeTutorial);
            case "tutorial" -> looksLikeTutorial;
            default -> true;
        };
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

    private String appendToken(String url, String wstoken) {
        if (url == null) return null;
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "token=" + URLEncoder.encode(wstoken, StandardCharsets.UTF_8);
    }
}
