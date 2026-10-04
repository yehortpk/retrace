package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.security.ApiKeyService;
import dev.yetpk.retrace.security.CurrentUserResolver;
import dev.yetpk.retrace.security.IssuedApiKey;
import dev.yetpk.retrace.web.dto.ApiKeyView;
import dev.yetpk.retrace.web.dto.IssueKeyRequest;
import dev.yetpk.retrace.web.dto.IssuedKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues, lists, and revokes API keys — <b>reachable by session only</b>. A key cannot mint or
 * revoke keys: if it could, a leaked key could issue its own replacements and survive being revoked.
 * The security chain enforces this; the {@code @SecurityRequirement} here publishes it in the spec.
 */
@RestController
@RequestMapping("/api/keys")
@SecurityRequirement(name = "session")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final CurrentUserResolver currentUserResolver;

    public ApiKeyController(ApiKeyService apiKeyService, CurrentUserResolver currentUserResolver) {
        this.apiKeyService = apiKeyService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "The caller's active API keys",
            description = "A key's value is never listed — after issuance it is not stored anywhere to show.")
    @GetMapping
    public List<ApiKeyView> findKeys() {
        AppUser owner = currentUserResolver.findCurrentUser();
        return apiKeyService.findKeys(owner).stream()
                .map(ApiKeyController::toView)
                .toList();
    }

    @Operation(summary = "Issue a new API key",
            description = "The response carries the plaintext key, for the only time it exists.")
    @ApiResponse(responseCode = "201", description = "Issued; the body carries the key once")
    @PostMapping
    public ResponseEntity<IssuedKeyResponse> issueKey(@Valid @RequestBody IssueKeyRequest request) {
        AppUser owner = currentUserResolver.findCurrentUser();
        IssuedApiKey issued = apiKeyService.issueKey(owner, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IssuedKeyResponse(
                issued.key().getId(), issued.key().getName(), issued.value()));
    }

    @Operation(summary = "Revoke an API key",
            description = "A key owned by somebody else is reported as not found, exactly like one that "
                    + "never existed.")
    @ApiResponse(responseCode = "204", description = "Revoked")
    @ApiResponse(responseCode = "404", description = "No such key for this owner", content = @Content)
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeKey(@PathVariable String keyId) {
        AppUser owner = currentUserResolver.findCurrentUser();
        apiKeyService.revokeKey(owner, keyId);
        return ResponseEntity.noContent().build();
    }

    private static ApiKeyView toView(ApiKey key) {
        return new ApiKeyView(key.getId(), key.getName(), key.getCreatedAt(), key.getLastUsedAt());
    }
}
