package dev.yetpk.retrace.service.error;

/**
 * Something addressed by the caller does not exist, or exists but belongs to another owner.
 * Both cases deliberately raise the same exception: a project or artifact someone else owns must be
 * indistinguishable from one that was never created, so ids cannot be probed for existence.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
