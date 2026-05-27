package my.edu.um.study.spectrum;

import my.edu.um.study.config.AppProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MoodleSpectrumClient implements SpectrumClient {

    private final RestClient restClient;
    private final AppProperties props;

    public MoodleSpectrumClient(RestClient restClient, AppProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    @Override
    public List<SpectrumCourse> getCourses(String wstoken, long userId) {
        URI base = URI.create(props.getSpectrum().getBaseUrl());

        List<Map<String, Object>> rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(base.getScheme())
                        .host(base.getHost())
                        .path("/webservice/rest/server.php")
                        .queryParam("wstoken", wstoken)
                        .queryParam("wsfunction", "core_enrol_get_users_courses")
                        .queryParam("moodlewsrestformat", "json")
                        .queryParam("userid", userId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        if (rows == null) return List.of();

        List<SpectrumCourse> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String shortname = String.valueOf(row.get("shortname"));
            String fullname = String.valueOf(row.get("fullname"));
            out.add(new SpectrumCourse(id, shortname, fullname));
        }
        return out;
    }

    @Override
    public SpectrumCourseContents getCourseContents(String wstoken, long courseId) {
        URI base = URI.create(props.getSpectrum().getBaseUrl());

        List<Map<String, Object>> rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(base.getScheme())
                        .host(base.getHost())
                        .path("/webservice/rest/server.php")
                        .queryParam("wstoken", wstoken)
                        .queryParam("wsfunction", "core_course_get_contents")
                        .queryParam("moodlewsrestformat", "json")
                        .queryParam("courseid", courseId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        if (rows == null) return new SpectrumCourseContents(List.of());

        List<SpectrumSection> sections = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            int sectionIndex = row.get("section") == null ? 0 : ((Number) row.get("section")).intValue();
            String name = row.get("name") == null ? "" : String.valueOf(row.get("name"));
            String summary = row.get("summary") == null ? "" : String.valueOf(row.get("summary"));

            List<SpectrumModule> modules = new ArrayList<>();
            Object modsRaw = row.get("modules");
            if (modsRaw instanceof List<?> modsList) {
                for (Object m : modsList) {
                    if (!(m instanceof Map<?, ?> modMap)) continue;
                    modules.add(toModule(modMap));
                }
            }
            sections.add(new SpectrumSection(id, sectionIndex, name, summary, modules));
        }
        return new SpectrumCourseContents(sections);
    }

    @Override
    public List<SpectrumDiscussion> getForumDiscussions(String wstoken, long forumId) {
        URI base = URI.create(props.getSpectrum().getBaseUrl());

        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(base.getScheme())
                        .host(base.getHost())
                        .path("/webservice/rest/server.php")
                        .queryParam("wstoken", wstoken)
                        .queryParam("wsfunction", "mod_forum_get_forum_discussions")
                        .queryParam("moodlewsrestformat", "json")
                        .queryParam("forumid", forumId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) return List.of();
        Object discRaw = response.get("discussions");
        if (!(discRaw instanceof List<?> discList)) return List.of();

        List<SpectrumDiscussion> out = new ArrayList<>(discList.size());
        for (Object d : discList) {
            if (!(d instanceof Map<?, ?> dm)) continue;
            long id = ((Number) dm.get("id")).longValue();
            String subject = dm.get("subject") == null ? "" : String.valueOf(dm.get("subject"));
            String message = dm.get("message") == null ? "" : String.valueOf(dm.get("message"));
            String userfullname = dm.get("userfullname") == null ? null : String.valueOf(dm.get("userfullname"));
            long created = dm.get("created") == null ? 0L : ((Number) dm.get("created")).longValue();
            List<SpectrumAttachment> attachments = parseAttachments(dm.get("attachments"));
            if (attachments.isEmpty()) attachments = parseAttachments(dm.get("messageattachment"));
            out.add(new SpectrumDiscussion(id, subject, message, userfullname, created, attachments));
        }
        return out;
    }

    @Override
    public List<SpectrumAssignment> getAssignments(String wstoken, long courseId) {
        URI base = URI.create(props.getSpectrum().getBaseUrl());

        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(base.getScheme())
                        .host(base.getHost())
                        .path("/webservice/rest/server.php")
                        .queryParam("wstoken", wstoken)
                        .queryParam("wsfunction", "mod_assign_get_assignments")
                        .queryParam("moodlewsrestformat", "json")
                        .queryParam("courseids[0]", courseId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) return List.of();
        Object coursesRaw = response.get("courses");
        if (!(coursesRaw instanceof List<?> coursesList) || coursesList.isEmpty()) return List.of();

        List<SpectrumAssignment> out = new ArrayList<>();
        for (Object course : coursesList) {
            if (!(course instanceof Map<?, ?> cm)) continue;
            Object assignsRaw = cm.get("assignments");
            if (!(assignsRaw instanceof List<?> assignList)) continue;
            for (Object a : assignList) {
                if (!(a instanceof Map<?, ?> am)) continue;
                long id = ((Number) am.get("id")).longValue();
                long cmid = am.get("cmid") == null ? 0L : ((Number) am.get("cmid")).longValue();
                long course2 = am.get("course") == null ? 0L : ((Number) am.get("course")).longValue();
                String name = am.get("name") == null ? "" : String.valueOf(am.get("name"));
                long duedate = am.get("duedate") == null ? 0L : ((Number) am.get("duedate")).longValue();
                long allowFrom = am.get("allowsubmissionsfromdate") == null ? 0L
                        : ((Number) am.get("allowsubmissionsfromdate")).longValue();
                String intro = am.get("intro") == null ? null : String.valueOf(am.get("intro"));
                List<SpectrumAttachment> introAttachments = parseAttachments(am.get("introattachments"));
                List<SpectrumAttachment> introFiles = parseAttachments(am.get("introfiles"));
                out.add(new SpectrumAssignment(id, cmid, course2, name, duedate, allowFrom,
                        intro, introAttachments, introFiles));
            }
        }
        return out;
    }

    @Override
    public SpectrumSubmissionStatus getSubmissionStatus(String wstoken, long assignmentId, long userId) {
        URI base = URI.create(props.getSpectrum().getBaseUrl());

        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(base.getScheme())
                        .host(base.getHost())
                        .path("/webservice/rest/server.php")
                        .queryParam("wstoken", wstoken)
                        .queryParam("wsfunction", "mod_assign_get_submission_status")
                        .queryParam("moodlewsrestformat", "json")
                        .queryParam("assignid", assignmentId)
                        .queryParam("userid", userId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) return new SpectrumSubmissionStatus(null, null, 0L);
        Object lastRaw = response.get("lastattempt");
        if (!(lastRaw instanceof Map<?, ?> last)) return new SpectrumSubmissionStatus(null, null, 0L);

        String status = null;
        long submittedAt = 0L;
        Object subRaw = last.get("submission");
        if (subRaw instanceof Map<?, ?> sub) {
            status = sub.get("status") == null ? null : String.valueOf(sub.get("status"));
            Object tm = sub.get("timemodified");
            if (tm instanceof Number n) submittedAt = n.longValue();
        }
        String gradingStatus = last.get("gradingstatus") == null ? null : String.valueOf(last.get("gradingstatus"));
        return new SpectrumSubmissionStatus(status, gradingStatus, submittedAt);
    }

    @Override
    public List<SpectrumQuiz> getQuizzes(String wstoken, long courseId) {
        URI base = URI.create(props.getSpectrum().getBaseUrl());

        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(base.getScheme())
                        .host(base.getHost())
                        .path("/webservice/rest/server.php")
                        .queryParam("wstoken", wstoken)
                        .queryParam("wsfunction", "mod_quiz_get_quizzes_by_courses")
                        .queryParam("moodlewsrestformat", "json")
                        .queryParam("courseids[0]", courseId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) return List.of();
        Object quizzesRaw = response.get("quizzes");
        if (!(quizzesRaw instanceof List<?> qList)) return List.of();

        List<SpectrumQuiz> out = new ArrayList<>(qList.size());
        for (Object q : qList) {
            if (!(q instanceof Map<?, ?> qm)) continue;
            long id = ((Number) qm.get("id")).longValue();
            long cm = qm.get("coursemodule") == null ? 0L : ((Number) qm.get("coursemodule")).longValue();
            long course = qm.get("course") == null ? 0L : ((Number) qm.get("course")).longValue();
            String name = qm.get("name") == null ? "" : String.valueOf(qm.get("name"));
            long timeopen = qm.get("timeopen") == null ? 0L : ((Number) qm.get("timeopen")).longValue();
            long timeclose = qm.get("timeclose") == null ? 0L : ((Number) qm.get("timeclose")).longValue();
            String intro = qm.get("intro") == null ? null : String.valueOf(qm.get("intro"));
            List<SpectrumAttachment> introAttachments = parseAttachments(qm.get("introattachments"));
            List<SpectrumAttachment> introFiles = parseAttachments(qm.get("introfiles"));
            out.add(new SpectrumQuiz(id, cm, course, name, timeopen, timeclose, intro, introAttachments, introFiles));
        }
        return out;
    }

    @Override
    public List<SpectrumQuizAttempt> getQuizAttempts(String wstoken, long quizId, long userId) {
        URI base = URI.create(props.getSpectrum().getBaseUrl());

        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(base.getScheme())
                        .host(base.getHost())
                        .path("/webservice/rest/server.php")
                        .queryParam("wstoken", wstoken)
                        .queryParam("wsfunction", "mod_quiz_get_user_attempts")
                        .queryParam("moodlewsrestformat", "json")
                        .queryParam("quizid", quizId)
                        .queryParam("userid", userId)
                        .queryParam("status", "all")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) return List.of();
        Object attRaw = response.get("attempts");
        if (!(attRaw instanceof List<?> attList)) return List.of();

        List<SpectrumQuizAttempt> out = new ArrayList<>(attList.size());
        for (Object a : attList) {
            if (!(a instanceof Map<?, ?> am)) continue;
            long id = ((Number) am.get("id")).longValue();
            long quiz = am.get("quiz") == null ? 0L : ((Number) am.get("quiz")).longValue();
            String state = am.get("state") == null ? "" : String.valueOf(am.get("state"));
            long timefinish = am.get("timefinish") == null ? 0L : ((Number) am.get("timefinish")).longValue();
            out.add(new SpectrumQuizAttempt(id, quiz, state, timefinish));
        }
        return out;
    }

    private SpectrumModule toModule(Map<?, ?> modMap) {
        long id = ((Number) modMap.get("id")).longValue();
        long instance = modMap.get("instance") == null ? 0L : ((Number) modMap.get("instance")).longValue();
        String name = modMap.get("name") == null ? "" : String.valueOf(modMap.get("name"));
        String modname = modMap.get("modname") == null ? "" : String.valueOf(modMap.get("modname"));
        String url = modMap.get("url") == null ? null : String.valueOf(modMap.get("url"));
        Object descRaw = modMap.get("description");
        if (descRaw == null) descRaw = modMap.get("intro");
        String description = descRaw == null ? null : String.valueOf(descRaw);

        List<SpectrumContent> contents = new ArrayList<>();
        Object cRaw = modMap.get("contents");
        if (cRaw instanceof List<?> cList) {
            for (Object c : cList) {
                if (!(c instanceof Map<?, ?> cm)) continue;
                contents.add(toContent(cm));
            }
        }
        return new SpectrumModule(id, instance, name, modname, url, description, contents);
    }

    private List<SpectrumAttachment> parseAttachments(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
        List<SpectrumAttachment> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) continue;
            String filename = map.get("filename") == null ? "" : String.valueOf(map.get("filename"));
            if (filename.isBlank() || ".".equals(filename)) continue;
            String fileurl = map.get("fileurl") == null ? null : String.valueOf(map.get("fileurl"));
            long filesize = map.get("filesize") == null ? 0L : ((Number) map.get("filesize")).longValue();
            String mimetype = map.get("mimetype") == null ? null : String.valueOf(map.get("mimetype"));
            out.add(new SpectrumAttachment(filename, fileurl, filesize, mimetype));
        }
        return out;
    }

    private SpectrumContent toContent(Map<?, ?> cm) {
        String type = cm.get("type") == null ? "" : String.valueOf(cm.get("type"));
        String filename = cm.get("filename") == null ? "" : String.valueOf(cm.get("filename"));
        long filesize = cm.get("filesize") == null ? 0L : ((Number) cm.get("filesize")).longValue();
        String fileurl = cm.get("fileurl") == null ? null : String.valueOf(cm.get("fileurl"));
        String mimetype = cm.get("mimetype") == null ? null : String.valueOf(cm.get("mimetype"));
        long timemodified = cm.get("timemodified") == null ? 0L : ((Number) cm.get("timemodified")).longValue();
        String author = cm.get("author") == null ? null : String.valueOf(cm.get("author"));
        return new SpectrumContent(type, filename, filesize, fileurl, mimetype, timemodified, author);
    }
}
