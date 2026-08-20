package app.nowtask.config;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.ApiKeyScope;

final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private final ApiKeyIdentity identity;

    ApiKeyAuthenticationToken(ApiKeyIdentity identity) {
        super(authorities(identity));
        this.identity = identity;
        setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> authorities(ApiKeyIdentity identity) {
        List<GrantedAuthority> granted = identity.scopes().stream()
                .map(ApiKeyScope::code)
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + code))
                .toList();
        return granted;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public ApiKeyIdentity getPrincipal() {
        return identity;
    }

    @Override
    public String getName() {
        return identity.ownerEmail();
    }
}
