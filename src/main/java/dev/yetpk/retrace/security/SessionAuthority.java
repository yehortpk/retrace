package dev.yetpk.retrace.security;

/**
 * The authority that distinguishes <em>how</em> a request authenticated, as opposed to who it is.
 *
 * <p>Both credentials grant {@code ROLE_USER}, because both authorize exactly the same access to the
 * project's history — that equivalence is the point of the design. The exceptions are what a key
 * confined to one project must not do: list or create the owner's projects, and mint or revoke keys —
 * a leaked key that could issue its own replacements would outlive being revoked. Only a form login
 * carries {@link #SESSION}, which is what lets those paths be restricted in the security chain rather
 * than re-checked inside a controller.
 */
public final class SessionAuthority {

    /** Granted to a session established by form login, and never to an API key. */
    public static final String SESSION = "SESSION";

    private SessionAuthority() {
    }
}
