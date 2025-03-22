package com.server.anki.amap;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.config.MapConfig;
import com.server.anki.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/map-config")
public class MapConfigController {

    private static final Logger logger = LoggerFactory.getLogger(MapConfigController.class);

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private MapConfig mapConfig;

    @GetMapping
    public ResponseEntity<?> getMapConfig(HttpServletRequest request, HttpServletResponse response) {
        logger.info("Received request to get amap configuration");
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("Unauthorized access attempt to get amap configuration");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Only return the key, not the securityJsCode
        MapConfigResponse mapConfigResponse = new MapConfigResponse(mapConfig.getKey());
        logger.info("Map configuration retrieved successfully for user ID: {}", user.getId());
        return ResponseEntity.ok(mapConfigResponse);
    }
}