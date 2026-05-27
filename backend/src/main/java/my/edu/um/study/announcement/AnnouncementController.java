package my.edu.um.study.announcement;

import jakarta.servlet.http.HttpSession;
import my.edu.um.study.config.AppProperties;
import my.edu.um.study.section.SemesterCalendar;
import my.edu.um.study.section.WeekClassifier;
import my.edu.um.study.security.TokenCipher;
import my.edu.um.study.spectrum.SpectrumAttachment;
import my.edu.um.study.spectrum.SpectrumClient;
import my.edu.um.study.spectrum.SpectrumCourseContents;
import my.edu.um.study.spectrum.SpectrumDiscussion;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/courses")
public class AnnouncementController {

    private final UserRepository userRepo;
    private final TokenCipher cipher;
    private final SpectrumClient spectrumClient;
    private final SemesterCalendar semesterCalendar;
    private final String spectrumBaseUrl;

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern BLOCK_BREAK = Pattern.compile("(?i)</p>|<br\\s*/?>|</div>|</li>");
    // Matches "Week 5", "Week5", "W5", "W 5", "W05" — used to pull a week hint out of a label's own text.
    private static final Pattern LABEL_WEEK = Pattern.compile("(?i)\\b(?:week|w)\\s*0*(\\d{1,2})\\b");
    private static final Pattern IMG_SRC =
            Pattern.compile("(?i)<img[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern A_TAG =
            Pattern.compile("(?is)<a[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");

    public AnnouncementController(UserRepository userRepo, TokenCipher cipher,
                                  SpectrumClient spectrumClient, AppProperties appProperties) {
        this.userRepo = userRepo;
        this.cipher = cipher;
        this.spectrumClient = spectrumClient;
        this.semesterCalendar = appProperties.getSemesterStartDate() != null
                ? new SemesterCalendar(appProperties.getSemesterStartDate(),
                                       appProperties.getSemesterBreakStartDate())
                : null;
        this.spectrumBaseUrl = appProperties.getSpectrum() != null
                ? appProperties.getSpectrum().getBaseUrl()
                : null;
    }

    @GetMapping("/{courseId}/announcements")
    public ResponseEntity<List<Announcement>> list(
            @PathVariable long courseId,
            @RequestParam(required = false) Integer week,
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

        SpectrumCourseContents contents = spectrumClient.getCourseContents(wstoken, courseId);

        SpectrumModule forum = null;
        SpectrumModule firstForum = null;
        for (SpectrumSection s : contents.sections()) {
            for (SpectrumModule m : s.modules()) {
                if (!"forum".equals(m.modname())) continue;
                if (firstForum == null) firstForum = m;
                String name = m.name() == null ? "" : m.name();
                if (name.toLowerCase().contains("announcement")) {
                    forum = m;
                    break;
                }
            }
            if (forum != null) break;
        }
        if (forum == null) forum = firstForum;
        if (forum == null) return ResponseEntity.ok(List.of());

        List<SpectrumDiscussion> discussions = spectrumClient.getForumDiscussions(wstoken, forum.instance());

        List<Announcement> out = new ArrayList<>();
        // Forum announcements: always treated as general (week=null), regardless of subject.
        // When a specific week is requested, filter by the post date falling within that week.
        for (SpectrumDiscussion d : discussions) {
            if (week != null) {
                Integer postedWeek = semesterCalendar != null
                        ? semesterCalendar.weekOf(d.created())
                        : null;
                if (!week.equals(postedWeek)) continue;
            }
            String subject = d.subject() == null ? "" : d.subject();
            String rawMessage = d.message() == null ? "" : d.message();
            List<String> images = extractImages(rawMessage, wstoken);
            List<AnnouncementLink> links = new ArrayList<>(extractLinks(rawMessage, wstoken, images));
            appendAttachmentLinks(links, d.attachments(), wstoken);
            String body = stripHtml(rawMessage);
            String postedAt = Instant.ofEpochSecond(d.created()).toString();
            out.add(new Announcement(String.valueOf(d.id()), subject, body, postedAt,
                    d.userfullname(), null, images, links));
        }

        // Per-week notes that aren't in any forum: section summaries + label modules
        for (SpectrumSection s : contents.sections()) {
            WeekClassifier.Classification cls = WeekClassifier.classify(s.name());
            Integer sectionWeek = cls.week();
            // Fall back to Moodle's sectionIndex when the section name doesn't carry a week
            // and isn't a known bucket (project/pastyear/assignment).
            if (sectionWeek == null && cls.bucket() == null
                    && s.sectionIndex() >= 1 && s.sectionIndex() <= 14) {
                sectionWeek = s.sectionIndex();
            }

            // Section summary — uses the section's own week.
            if (sectionWeek != null && (week == null || week.equals(sectionWeek))) {
                String summaryHtml = s.summary();
                if (summaryHtml != null && !summaryHtml.isBlank()) {
                    String body = stripHtml(summaryHtml);
                    if (!body.isEmpty()) {
                        List<String> images = extractImages(summaryHtml, wstoken);
                        List<AnnouncementLink> links = extractLinks(summaryHtml, wstoken, images);
                        String title = (s.name() != null && !s.name().isBlank())
                                ? s.name()
                                : firstLine(body, 80);
                        out.add(new Announcement(
                                "section-" + s.id(), title, body, synthPostedAt(sectionWeek),
                                null, sectionWeek, images, links));
                    }
                }
            }

            // Label modules — each label can override the section's week from its own text
            // (lecturers sometimes group multi-week labels under a single section).
            for (SpectrumModule m : s.modules()) {
                if (!"label".equals(m.modname())) continue;
                // Moodle stores the full label body in `description`; `name` is just an
                // auto-generated short title. Prefer description when present.
                String labelHtml = (m.description() != null && !m.description().isBlank())
                        ? m.description()
                        : m.name();
                if (labelHtml == null || labelHtml.isBlank()) continue;
                String body = stripHtml(labelHtml);
                if (body.length() < 10) continue;
                String title = (m.name() != null && !m.name().isBlank())
                        ? stripHtml(m.name())
                        : firstLine(body, 80);

                Integer labelWeek = extractWeekHint(title);
                if (labelWeek == null) labelWeek = extractWeekHint(body);
                if (labelWeek == null) labelWeek = sectionWeek;
                if (labelWeek == null) continue;
                if (week != null && !week.equals(labelWeek)) continue;

                List<String> images = extractImages(labelHtml, wstoken);
                List<AnnouncementLink> links = extractLinks(labelHtml, wstoken, images);
                // If the label has no separate description, body just duplicates the title — drop it.
                String bodyOut = body.equalsIgnoreCase(title) ? "" : body;
                out.add(new Announcement(
                        "label-" + m.id(), title, bodyOut, synthPostedAt(labelWeek),
                        null, labelWeek, images, links));
            }
        }

        out.sort(Comparator.comparing(Announcement::postedAt).reversed());
        return ResponseEntity.ok(out);
    }

    private String synthPostedAt(Integer week) {
        if (semesterCalendar != null && week != null) {
            LocalDate start = semesterCalendar.startOfWeek(week);
            if (start != null) {
                return start.atStartOfDay(ZoneId.of("Asia/Kuala_Lumpur")).toInstant().toString();
            }
        }
        return Instant.now().toString();
    }

    private String firstLine(String body, int maxLen) {
        String[] lines = body.split("\\R", 2);
        String head = lines[0].trim();
        if (head.isEmpty()) return "Note";
        return head.length() > maxLen ? head.substring(0, maxLen - 1) + "…" : head;
    }

    private Integer extractWeekHint(String text) {
        if (text == null) return null;
        Matcher m = LABEL_WEEK.matcher(text);
        if (!m.find()) return null;
        int w = Integer.parseInt(m.group(1));
        return (w >= 1 && w <= 14) ? w : null;
    }

    private void appendAttachmentLinks(List<AnnouncementLink> links, List<SpectrumAttachment> attachments, String wstoken) {
        if (attachments == null || attachments.isEmpty()) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AnnouncementLink l : links) seen.add(l.url());
        for (SpectrumAttachment a : attachments) {
            if (a.fileurl() == null || a.fileurl().isBlank()) continue;
            String url = needsToken(a.fileurl()) ? appendToken(a.fileurl(), wstoken) : a.fileurl();
            if (!seen.add(url)) continue;
            String label = a.filename() == null || a.filename().isBlank() ? url : a.filename();
            links.add(new AnnouncementLink(url, label));
        }
    }

    private List<AnnouncementLink> extractLinks(String html, String wstoken, List<String> imageUrls) {
        if (html == null || html.isEmpty()) return List.of();
        java.util.Set<String> seenUrls = new java.util.HashSet<>(imageUrls);
        List<AnnouncementLink> out = new ArrayList<>();
        Matcher m = A_TAG.matcher(html);
        while (m.find()) {
            String url = decodeHtmlEntities(m.group(1)).trim();
            if (url.isBlank() || url.startsWith("#") || url.toLowerCase().startsWith("javascript:")) continue;
            if (needsToken(url)) url = appendToken(url, wstoken);
            if (!seenUrls.add(url)) continue;
            String label = stripHtml(m.group(2));
            if (label.isBlank()) label = url;
            out.add(new AnnouncementLink(url, label));
        }
        return out;
    }

    private List<String> extractImages(String html, String wstoken) {
        if (html == null || html.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = IMG_SRC.matcher(html);
        while (m.find()) {
            String url = decodeHtmlEntities(m.group(1));
            if (url.startsWith("data:") || url.isBlank()) continue;
            if (needsToken(url)) url = appendToken(url, wstoken);
            out.add(url);
        }
        return out;
    }

    private boolean needsToken(String url) {
        if (url.contains("pluginfile.php")) return true;
        if (spectrumBaseUrl != null && url.startsWith(spectrumBaseUrl)) return true;
        return false;
    }

    private String appendToken(String url, String wstoken) {
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "token=" + URLEncoder.encode(wstoken, StandardCharsets.UTF_8);
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        String spaced = BLOCK_BREAK.matcher(html).replaceAll("\n");
        String noTags = HTML_TAG.matcher(spaced).replaceAll("");
        return decodeHtmlEntities(noTags).replaceAll("\n{3,}", "\n\n").trim();
    }

    private String decodeHtmlEntities(String s) {
        return s
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
