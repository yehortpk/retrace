package dev.yetpk.retrace.service.error;

/**
 * A registration named a username or an email that is already in use. Carries no HTTP concepts;
 * the web layer maps it to a 409.
 *
 * <p>Unlike {@link NotFoundException}, this one says which field collided. Registration is the one
 * place where being specific is right rather than a leak: the taken name is one the caller just
 * proposed, and a signup form that refuses without saying why is unusable.
 */
public class CredentialTakenException extends RuntimeException {

    public CredentialTakenException(String message) {
        super(message);
    }
}
