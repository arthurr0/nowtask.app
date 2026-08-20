package app.nowtask.identity.api;

import java.util.Optional;

public interface ApiKeyAuthenticator {
    String TOKEN_PREFIX = "nt_";

    Optional<ApiKeyIdentity> authenticate(String presentedToken);
}
