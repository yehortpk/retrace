package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.ApiKeyService;
import dev.yetpk.retrace.security.CurrentProjectResolver;
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
import java.util.UUID;
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
 * Issues, lists, and revokes a project's API keys — <b>reachable by session only</b>. A key cannot
 * mint or revoke keys: if it could, a leaked key could issue its own replacements and survive being
 * revoked. The security chain enforces this; the {@code @SecurityRequirement} here publishes it in the
 * spec.
 *
 * <p>Each key belongs to the project it is issued under and reaches only that project, so revoking one
 * disconnects exactly one repo.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/keys")
@SecurityRequirement(name = "session")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final CurrentProjectResolver currentProjectResolver;

    public ApiKeyController(ApiKeyService apiKeyService, CurrentProjectResolver currentProjectResolver) {
        this.apiKeyService = apiKeyService;
        this.currentProjectResolver = currentProjectResolver;
    }

    @Operation(summary = "The project's active API keys",
            description = "A key's value is never listed — after issuance it is not stored anywhere to show.")
    @ApiResponse(responseCode = "404", description = "No such project for this owner", content = @Content)
    @GetMapping
    public List<ApiKeyView> findKeys(@PathVariable UUID projectId) {
        Project project = currentProjectResolver.findProject(projectId);
        return apiKeyService.findKeys(project).stream()
                .map(ApiKeyController::toView)
                .toList();
    }

    @Operation(summary = "Issue a new API key for the project",
            description = "The response carries the plaintext key, for the only time it exists. The key "
                    + "reaches this project and no other.")
    @ApiResponse(responseCode = "201", description = "Issued; the body carries the key once")
    @ApiResponse(responseCode = "404", description = "No such project for this owner", content = @Content)
    @PostMapping
    public ResponseEntity<IssuedKeyResponse> issueKey(@PathVariable UUID projectId,
                                                      @Valid @RequestBody IssueKeyRequest request) {
        Project project = currentProjectResolver.findProject(projectId);
        IssuedApiKey issued = apiKeyService.issueKey(project, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(IssuedKeyResponse.from(issued));
    }

    @Operation(summary = "Revoke one of the project's API keys",
            description = "A key of another project — even another project of the same owner — is reported "
                    + "as not found, exactly like one that never existed.")
    @ApiResponse(responseCode = "204", description = "Revoked")
    @ApiResponse(responseCode = "404", description = "No such key in this project", content = @Content)
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeKey(@PathVariable UUID projectId, @PathVariable String keyId) {
        apiKeyService.revokeKey(currentProjectResolver.findProject(projectId), keyId);
        return ResponseEntity.noContent().build();
    }

    private static ApiKeyView toView(ApiKey key) {
        return new ApiKeyView(key.getId(), key.getName(), key.getCreatedAt(), key.getLastUsedAt());
    }
}
