package my.edu.um.study.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String allowedEmailDomain;
    private String frontendBaseUrl;
    private String secretKey;
    private Spectrum spectrum = new Spectrum();
    private LocalDate semesterStartDate;
    private LocalDate semesterBreakStartDate;

    public String getAllowedEmailDomain() { return allowedEmailDomain; }
    public void setAllowedEmailDomain(String v) { this.allowedEmailDomain = v; }

    public String getFrontendBaseUrl() { return frontendBaseUrl; }
    public void setFrontendBaseUrl(String v) { this.frontendBaseUrl = v; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String v) { this.secretKey = v; }

    public Spectrum getSpectrum() { return spectrum; }
    public void setSpectrum(Spectrum v) { this.spectrum = v; }

    public LocalDate getSemesterStartDate() { return semesterStartDate; }
    public void setSemesterStartDate(LocalDate v) { this.semesterStartDate = v; }

    public LocalDate getSemesterBreakStartDate() { return semesterBreakStartDate; }
    public void setSemesterBreakStartDate(LocalDate v) { this.semesterBreakStartDate = v; }

    public static class Spectrum {
        private String baseUrl;
        private String client;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }

        public String getClient() { return client; }
        public void setClient(String v) { this.client = v; }
    }
}
