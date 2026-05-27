package my.edu.um.study.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import my.edu.um.study.config.AppProperties;
import my.edu.um.study.spectrum.InvalidSpectrumCredentialsException;
import my.edu.um.study.spectrum.SpectrumAuthenticator;
import my.edu.um.study.spectrum.SpectrumIdentity;
import my.edu.um.study.user.User;
import my.edu.um.study.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SpectrumAuthenticator authenticator;
    private final UserService userService;
    private final AppProperties props;

    public AuthController(SpectrumAuthenticator authenticator, UserService userService, AppProperties props) {
        this.authenticator = authenticator;
        this.userService = userService;
        this.props = props;
    }

    @PostMapping("/spectrum-login")
    public MeDto login(@Valid @RequestBody SpectrumLoginRequest req, HttpSession session) {
        log.info("spectrum-login attempt with token (len={})",
                req.token() == null ? 0 : req.token().length());
        SpectrumIdentity identity = authenticator.authenticateWithToken(req.token());

        String domain = props.getAllowedEmailDomain();
        String email = identity.email() == null ? null : identity.email().toLowerCase();
        log.info("spectrum-login ok: username={}, spectrumUserId={}, email={}",
                identity.username(), identity.spectrumUserId(), email);
        boolean allowed = email != null
                && (email.endsWith("@" + domain) || email.endsWith("." + domain));
        if (!allowed) {
            throw new DomainNotAllowedException("domain");
        }

        User user = userService.upsertFromSpectrum(identity);
        session.setAttribute("userId", user.getId());
        return new MeDto(user.getId().toString(), user.getEmail(), user.getDisplayName());
    }

    @GetMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session, HttpServletResponse response) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidSpectrumCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCreds(InvalidSpectrumCredentialsException e) {
        log.warn("spectrum-login invalid credentials: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
    }

    @ExceptionHandler(DomainNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleDomain(DomainNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "domain"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAny(Exception e) {
        log.error("spectrum-login unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error", "detail", String.valueOf(e.getMessage())));
    }
}
