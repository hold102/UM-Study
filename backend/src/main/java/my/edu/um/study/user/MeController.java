package my.edu.um.study.user;

import jakarta.servlet.http.HttpSession;
import my.edu.um.study.auth.MeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class MeController {

    private final UserRepository userRepo;

    public MeController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/me")
    public ResponseEntity<MeDto> me(HttpSession session) {
        Object raw = session.getAttribute("userId");
        if (!(raw instanceof UUID userId)) {
            return ResponseEntity.status(401).build();
        }
        return userRepo.findById(userId)
                .map(u -> ResponseEntity.ok(new MeDto(u.getId().toString(), u.getEmail(), u.getDisplayName())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
