package my.edu.um.study.spectrum;

import my.edu.um.study.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

@Component
public class MoodleSpectrumAuthenticator implements SpectrumAuthenticator {

    private final RestClient restClient;
    private final AppProperties props;

    public MoodleSpectrumAuthenticator(RestClient restClient, AppProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    @Override
    public SpectrumIdentity authenticateWithToken(String wstoken) {
        if (wstoken == null || wstoken.isBlank()) {
            throw new InvalidSpectrumCredentialsException("empty token");
        }
        String token = wstoken.trim();
        String baseUrl = props.getSpectrum().getBaseUrl();

        Map<String, Object> info;
        try {
            info = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme(baseUrl.startsWith("https") ? "https" : "http")
                            .host(URI.create(baseUrl).getHost())
                            .path("/webservice/rest/server.php")
                            .queryParam("wstoken", token)
                            .queryParam("wsfunction", "core_webservice_get_site_info")
                            .queryParam("moodlewsrestformat", "json")
                            .build())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new InvalidSpectrumCredentialsException("site_info call failed: " + e.getMessage());
        }

        if (info == null || info.get("userid") == null) {
            String code = info == null ? "no_response" : String.valueOf(info.get("errorcode"));
            throw new InvalidSpectrumCredentialsException("site_info rejected: " + code);
        }

        long userId = ((Number) info.get("userid")).longValue();
        String username = info.get("username") == null ? "" : info.get("username").toString();
        String fullName = info.get("fullname") == null ? "" : info.get("fullname").toString();
        Object emailObj = info.get("useremail");
        String email = emailObj != null ? emailObj.toString()
                : (username.contains("@") ? username : null);

        return new SpectrumIdentity(token, userId, username, fullName, email);
    }
}
