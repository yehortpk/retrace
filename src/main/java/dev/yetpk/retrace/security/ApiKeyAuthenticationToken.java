package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.AppUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * An authentication established by a valid {@code X-API-Key} header. The principal is the owning
 * {@link AppUser} itself, so past the filter chain a key-authenticated request and a session-
 * authenticated one carry the same identity in the same shape — which is what lets the agent and the
 * UI share one set of endpoints and one write path.
 *
 * <p>The credential is deliberately not retained: nothing downstream of authentication has any use
 * for the presented secret, and a token holding it would be one more place it could be logged.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private static final Collection<GrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final AppUser owner;

    public ApiKeyAuthenticationToken(AppUser owner) {
        super(AUTHORITIES);
        this.owner = owner;
        setAuthenticated(true);
    }

    @Override
    public AppUser getPrincipal() {
        return owner;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return owner.getUsername();
    }
}
