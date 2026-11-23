package com.airedgesoft.sharekernel.workingset.infrastructure.providers;

import com.airedgesoft.sharekernel.workingset.application.port.TrackerItemSourceLock;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reads the lifecycle metadata maintained in the owning business object's write transaction. */
@Component
@RequiredArgsConstructor
public class JdbcTrackerItemSourceLock implements TrackerItemSourceLock {
    private final JdbcTemplate jdbc;

    @Override
    public Optional<State> read(Long localId) {
        return load(localId, "");
    }

    @Override
    public Optional<State> lock(Long localId) {
        return load(localId, " FOR UPDATE");
    }

    private Optional<State> load(Long localId, String lockingClause) {
        return jdbc.query("SELECT revision, delete_flag FROM air_inst_common WHERE id = ?" + lockingClause, (row, index) -> {
            Long revision = row.getObject("revision", Long.class);
            return new State(revision == null ? null : Math.toIntExact(revision), row.getInt("delete_flag") != 0);
        }, localId).stream().findFirst();
    }
}
