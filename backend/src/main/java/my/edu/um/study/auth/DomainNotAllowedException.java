package my.edu.um.study.auth;

public class DomainNotAllowedException extends RuntimeException {
    public DomainNotAllowedException(String message) {
        super(message);
    }
}
