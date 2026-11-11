package de.homelabs.app.shell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shell's chrome texts: both languages carry the same keys, and every key the
 * markup and the scripts use exists — a missing translation can never show a raw key.
 */
class ShellMessagesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void bothLanguagesCarryTheSameKeys() throws IOException {
        assertThat(messages("en").keySet()).isEqualTo(messages("de").keySet());
    }

    @Test
    void everyKeyTheShellUsesExists() throws IOException {
        Set<String> used = new TreeSet<>();
        collect(used, read("static/index.html"), Pattern.compile("data-i18n(?:-aria)?=\"([a-zA-Z.]+)\""));
        collect(used, read("static/shell/app.js"), Pattern.compile("\\bt\\('([a-zA-Z.]+)'\\)"));
        collect(used, read("static/shell/app.js"), Pattern.compile("'(rail\\.[a-zA-Z]+)'"));
        for (String state : new String[] { "noApps", "noAccess", "notFound", "loadError" }) {
            used.add("state." + state + ".title");
            used.add("state." + state + ".text");
        }

        assertThat(used).isNotEmpty();
        assertThat(messages("de").keySet()).containsAll(used);
    }

    private static Map<String, String> messages(String locale) throws IOException {
        try (InputStream in = new ClassPathResource("static/shell/i18n/" + locale + ".json").getInputStream()) {
            return JSON.readValue(in, new TypeReference<Map<String, String>>() { });
        }
    }

    private static String read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void collect(Set<String> into, String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }
}
