package com.blockcred.api;

import com.blockcred.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody AuthLoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(
                request.usernameOrEmail(),
                request.password(),
                clientIp(httpRequest),
                userAgent(httpRequest)
        );
    }

    @PostMapping("/refresh")
    public AuthSessionResponse refresh(@Valid @RequestBody AuthRefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(
                request.refreshToken(),
                clientIp(httpRequest),
                userAgent(httpRequest)
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public AuthResultResponse logout(@RequestBody(required = false) AuthLogoutRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return new AuthResultResponse("LOGGED_OUT");
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.me();
    }

    @PostMapping("/change-password")
    public AuthResultResponse changePassword(@Valid @RequestBody AuthChangePasswordRequest request) {
        authService.changePassword(request.currentPassword(), request.newPassword());
        return new AuthResultResponse("PASSWORD_UPDATED");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? "" : ua;
    }
}
