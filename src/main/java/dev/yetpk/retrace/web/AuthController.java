package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.security.CurrentProjectResolver;
import dev.yetpk.retrace.security.CurrentUserResolver;
import dev.yetpk.retrace.security.RegistrationService;
import dev.yetpk.retrace.web.dto.CurrentUserView;
import dev.yetpk.retrace.web.dto.RegisterRequest;
import dev.yetpk.retrace.web.dto.RegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and "who am I". Login and logout are Spring Security's own form-login endpoints at
 * {@code /api/auth/login} and {@code /api/auth/logout}, configured in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final CurrentUserResolver currentUserResolver;
    private final CurrentProjectResolver currentProjectResolver;

    public AuthController(RegistrationService registrationService, CurrentUserResolver currentUserResolver,
                          CurrentProjectResolver currentProjectResolver) {
        this.registrationService = registrationService;
        this.currentUserResolver = currentUserResolver;
        this.currentProjectResolver = currentProjectResolver;
    }

    @Operation(summary = "Register an account",
            description = "No API key is issued here: a key belongs to a project, and creating a project "
                    + "issues its first one.")
    @ApiResponse(responseCode = "201", description = "Registered")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    @ApiResponse(responseCode = "409", description = "Username or email already registered", content = @Content)
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = registrationService.register(request.username(), request.email(), request.password())
                .user();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getId(), user.getUsername(), user.getEmail()));
    }

    @Operation(summary = "The account the current credential belongs to",
            description = "Under an API key, `projectId` names the one project the key belongs to — how an "
                    + "agent configured with nothing but a key learns where its entries go. Under a "
                    + "session it is null.")
    @GetMapping("/me")
    public CurrentUserView findCurrentUser() {
        AppUser user = currentUserResolver.findCurrentUser();
        return new CurrentUserView(user.getId(), user.getUsername(), user.getEmail(),
                currentProjectResolver.findConfinedProjectId().orElse(null));
    }
}
