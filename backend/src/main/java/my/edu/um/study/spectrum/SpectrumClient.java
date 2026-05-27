package my.edu.um.study.spectrum;

import java.util.List;

public interface SpectrumClient {
    List<SpectrumCourse> getCourses(String wstoken, long userId);

    SpectrumCourseContents getCourseContents(String wstoken, long courseId);

    List<SpectrumDiscussion> getForumDiscussions(String wstoken, long forumId);

    List<SpectrumAssignment> getAssignments(String wstoken, long courseId);

    SpectrumSubmissionStatus getSubmissionStatus(String wstoken, long assignmentId, long userId);

    List<SpectrumQuiz> getQuizzes(String wstoken, long courseId);

    List<SpectrumQuizAttempt> getQuizAttempts(String wstoken, long quizId, long userId);
}
