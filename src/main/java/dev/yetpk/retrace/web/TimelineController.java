package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.CurrentUserResolver;
import dev.yetpk.retrace.service.ProjectService;
import dev.yetpk.retrace.service.TimelineService;
import dev.yetpk.retrace.service.dto.TimelineEntryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reads a project's history: what happened, in order. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class TimelineController {

    /**
     * The largest page anyone can ask for. Without a cap, {@code size=1000000} would ask the server
     * to build a project's entire history, with every version attached, into one response.
     */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TimelineService timelineService;
    private final ProjectService projectService;
    private final CurrentUserResolver currentUserResolver;

    public TimelineController(TimelineService timelineService, ProjectService projectService,
                              CurrentUserResolver currentUserResolver) {
        this.timelineService = timelineService;
        this.projectService = projectService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "The project's entries, newest first",
            description = """
                    Ordered by `(occurredAt DESC, id DESC)`. The id tiebreak is not cosmetic: entries \
                    recorded in one burst share a timestamp, and without it a row could appear on both \
                    page 1 and page 2.

                    Passing `since` returns every entry after that instant instead of a page — what a \
                    starting agent session reads to learn what is already recorded.""")
    @ApiResponse(responseCode = "404", description = "No such project for this owner", content = @Content)
    @GetMapping("/timeline")
    public List<TimelineEntryView> findTimeline(
            @PathVariable UUID projectId,
            @Parameter(description = "Zero-based page index; ignored when `since` is given")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Entries per page, capped at 100")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return everything recorded after this instant instead of a page")
            @RequestParam(required = false) OffsetDateTime since) {
        Project project = projectService.findProject(currentUserResolver.findCurrentUser(), projectId);
        if (since != null) {
            return timelineService.findTimelineSince(project, since);
        }
        return timelineService.findTimeline(project, toPageRequest(page, size)).getContent();
    }

    private static PageRequest toPageRequest(int page, int size) {
        int boundedSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), boundedSize);
    }
}
