package my.edu.um.study.section;

import jakarta.servlet.http.HttpSession;
import my.edu.um.study.security.TokenCipher;
import my.edu.um.study.spectrum.SpectrumClient;
import my.edu.um.study.spectrum.SpectrumCourseContents;
import my.edu.um.study.spectrum.SpectrumSection;
import my.edu.um.study.user.User;
import my.edu.um.study.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class SectionController {

    private final UserRepository userRepo;
    private final TokenCipher cipher;
    private final SpectrumClient spectrumClient;

    public SectionController(UserRepository userRepo, TokenCipher cipher, SpectrumClient spectrumClient) {
        this.userRepo = userRepo;
        this.cipher = cipher;
        this.spectrumClient = spectrumClient;
    }

    @GetMapping("/{courseId}/sections")
    public ResponseEntity<List<Section>> list(@PathVariable long courseId, HttpSession session) {
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

        List<Section> out = new ArrayList<>(contents.sections().size());
        for (SpectrumSection s : contents.sections()) {
            WeekClassifier.Classification c = WeekClassifier.classify(s.name());
            out.add(new Section(String.valueOf(s.id()), s.name(), c.week(), c.bucket()));
        }
        return ResponseEntity.ok(out);
    }
}
