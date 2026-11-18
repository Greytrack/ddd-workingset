package bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import org.springframework.jdbc.core.JdbcTemplate;

public final class FixtureIds {
    private FixtureIds() {}

    public static String fixtureId(String label) {
        CRC32 digest = new CRC32();
        digest.update(label.getBytes(StandardCharsets.UTF_8));
        return Long.toString(((126_230_400_000L + digest.getValue()) << 11) | 1984);
    }

    static void insertWorkingSet(JdbcTemplate jdbc, String parent, String child) {
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, ?)",
                Long.valueOf(child), Long.valueOf(parent));
    }
}
