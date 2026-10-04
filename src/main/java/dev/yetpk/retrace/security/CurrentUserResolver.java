package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.repo.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated {@link AppUser} from either way of authenticating, so no controller
 * reads the {@code SecurityContextHolder} itself and the two credentials are indistinguishable
 * past the filter chain.
 *
 * <p>This is the only place an owner comes from. <b>No request body and no path ever carries an
 * owner id</b> — if it did, every service below would have to be trusted to re-check it, which is
 * exactly the kind of check that gets forgotten once.
 */
@Component
public class CurrentUserResolver {

    private final AppUserRepository appUserRepository;

    public CurrentUserResolver(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * The user the current request is acting as.
     *
     * @throws AuthenticationException if the request is not authenticated, which the security chain
     *         should already have prevented for every path that calls this
     */
    @Transactional(readOnly = true)
    public AppUser findCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("This request is not authenticated");
        }
        // An API key carries the owner directly; a form login carries the username it signed in with.
        if (authentication.getPrincipal() instanceof AppUser owner) {
            return appUserRepository.findById(owner.getId())
                    .orElseThrow(() -> new InsufficientAuthenticationException("The authenticated user is gone"));
        }
        return appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new InsufficientAuthenticationException("The authenticated user is gone"));
    }
}
