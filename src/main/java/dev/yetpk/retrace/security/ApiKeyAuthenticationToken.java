package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.AppUser;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * An authentication established by a valid {@code X-API-Key} header. The principal is the owning
 * {@link AppUser} itself, so past the filter chain a key-authenticated request and a session-
 * authenticated one carry the same identity in the same shape — which is what lets the agent and the
 * UI share one set of endpoints and one write path.
 *
 * <p>What a key adds is a <b>confinement</b>: the one project it belongs to. A session can reach every
 * project its user owns; a key reaches only {@link #getProjectId()}, which
 * {@link CurrentProjectResolver} enforces for every project-scoped request.
 *
 * <p>The credential is deliberately not retained: nothing downstream of authentication has any use
 * for the presented secret, and a token holding it would be one more place it could be logged.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private static final Collection<GrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final AppUser owner;
    private final UUID projectId;

    public ApiKeyAuthenticationToken(AppUser owner, UUID projectId) {
        super(AUTHORITIES);
        this.owner = owner;
        this.projectId = projectId;
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

    /** The one project this key may read and write. */
    public UUID getProjectId() {
        return projectId;
    }
}
