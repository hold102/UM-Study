package my.edu.um.study.user;

import my.edu.um.study.security.TokenCipher;
import my.edu.um.study.spectrum.SpectrumIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repo;
    private final TokenCipher cipher;

    public UserService(UserRepository repo, TokenCipher cipher) {
        this.repo = repo;
        this.cipher = cipher;
    }

    @Transactional
    public User upsertFromSpectrum(SpectrumIdentity identity) {
        User user = repo.findByEmail(identity.email()).orElseGet(() -> {
            User u = new User();
            u.setId(UUID.randomUUID());
            u.setEmail(identity.email());
            u.setCreatedAt(OffsetDateTime.now());
            return u;
        });
        user.setDisplayName(identity.fullName());
        user.setLastLoginAt(OffsetDateTime.now());
        user.setSpectrumUserId(identity.spectrumUserId());
        user.setSpectrumTokenEnc(cipher.encrypt(identity.wstoken()));
        return repo.save(user);
    }
}
