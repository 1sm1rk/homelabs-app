package de.homelabs.app.shell;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single-page fallback: every navigation the shell owns answers with the same
 * {@code index.html}, and the shell's router decides what to show.
 *
 * <p>Owned paths are the start page, the design guide and {@code /<mfe-id>/**}. The id
 * pattern excludes the reserved first segments (BFF, proxy, shell assets, login, health),
 * so a mistyped API path is a 404, never the shell page. The page itself is never cached:
 * a new deploy must reach the browser on the next navigation.
 */
@RestController
class ShellController {

    /** First path segments that belong to the BFF, not to a micro frontend. */
    private static final String MFE_ID =
            "{id:(?!(?:bff|api|mfe|shell|oauth2|login|logout|health|error)$)[a-z][a-z0-9-]*}";

    private final byte[] index;

    ShellController() throws IOException {
        try (InputStream in = new ClassPathResource("static/index.html").getInputStream()) {
            this.index = in.readAllBytes();
        }
    }

    @GetMapping({ "/", "/" + MFE_ID, "/" + MFE_ID + "/**" })
    ResponseEntity<byte[]> shell() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(index);
    }
}
