package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.repo.AppUserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs Spring Security's form login with the {@code app_user} table, granting the same
 * {@code ROLE_USER} an API key gets plus {@link SessionAuthority#SESSION}. The two credentials
 * authorize identical access to the owner's history; the extra authority exists only so key
 * management can require a session.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user named '%s'".formatted(username)));
        // SESSION is what marks this as a form login rather than an API key, which is how
        // /api/keys stays reachable by session only.
        return new User(user.getUsername(), user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(SessionAuthority.SESSION)));
    }
}
