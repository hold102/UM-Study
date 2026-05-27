package my.edu.um.study.course;

import jakarta.servlet.http.HttpSession;
import my.edu.um.study.security.TokenCipher;
import my.edu.um.study.spectrum.SpectrumClient;
import my.edu.um.study.spectrum.SpectrumCourse;
import my.edu.um.study.user.User;
import my.edu.um.study.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final UserRepository userRepo;
    private final TokenCipher cipher;
    private final SpectrumClient spectrumClient;

    public CourseController(UserRepository userRepo, TokenCipher cipher, SpectrumClient spectrumClient) {
        this.userRepo = userRepo;
        this.cipher = cipher;
        this.spectrumClient = spectrumClient;
    }

    @GetMapping
    public ResponseEntity<List<Course>> list(HttpSession session) {
        Object raw = session.getAttribute("userId");
        if (!(raw instanceof UUID userId)) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getSpectrumTokenEnc() == null || user.getSpectrumUserId() == null) {
            return ResponseEntity.status(401).build();
        }
        String wstoken = cipher.decrypt(user.getSpectrumTokenEnc());
        List<SpectrumCourse> courses = spectrumClient.getCourses(wstoken, user.getSpectrumUserId());
        List<Course> out = courses.stream()
                .map(c -> new Course(String.valueOf(c.id()), c.shortname(), c.fullname()))
                .toList();
        return ResponseEntity.ok(out);
    }
}
