package dev.yetpk.retrace.security;

/**
 * The authority that distinguishes <em>how</em> a request authenticated, as opposed to who it is.
 *
 * <p>Both credentials grant {@code ROLE_USER}, because both authorize exactly the same access to the
 * owner's history — that equivalence is the point of the design. Key management is the one exception:
 * a key must not be able to mint or revoke keys, or a leaked key could issue its own replacements and
 * outlive being revoked. Only a form login carries {@link #SESSION}, which is what lets
 * {@code /api/keys} be restricted in the security chain rather than re-checked inside a controller.
 */
public final class SessionAuthority {

    /** Granted to a session established by form login, and never to an API key. */
    public static final String SESSION = "SESSION";

    private SessionAuthority() {
    }
}
