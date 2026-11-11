package de.homelabs.app.mfe;

import java.util.function.Supplier;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Stage one of authorization for {@code /mfe/{id}/**} and {@code /api/{id}/**}:
 * fail-closed — an unknown id or a missing role {@code mfe-<id>} is denied. Without a
 * session the denial becomes a 401 (the entry point), with one it is a 403. What a person
 * may do INSIDE the MFE stays the backend's decision (stage two).
 */
public final class MfeGate implements AuthorizationManager<RequestAuthorizationContext> {

    private final MfeRegistry registry;

    public MfeGate(MfeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(registry.grants(context.getVariables().get("id"), current.getAuthorities()));
    }
}
