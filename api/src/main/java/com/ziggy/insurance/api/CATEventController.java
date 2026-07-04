// REST controller for CAT event operations.
// Maps HTTP endpoints to Temporal workflow start and query.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.cat.CATEventStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cat")
public class CATEventController {

    private final CATEventService catEventService;

    public CATEventController(CATEventService catEventService) {
        this.catEventService = catEventService;
    }

    @PostMapping
    public ResponseEntity<DeclareCATEventResponse> declare(
            @RequestBody DeclareCATEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(catEventService.declareCATEvent(request));
    }

    @GetMapping("/{catEventId}")
    public CATEventStatus get(@PathVariable String catEventId) {
        return catEventService.getCATEventStatus(catEventId);
    }
}
