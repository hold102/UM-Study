package my.edu.um.study.spectrum;

public interface SpectrumAuthenticator {
    SpectrumIdentity authenticateWithToken(String wstoken);
}
