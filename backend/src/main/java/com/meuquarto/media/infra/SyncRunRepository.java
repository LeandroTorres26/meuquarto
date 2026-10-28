package com.meuquarto.media.infra;

import com.meuquarto.media.domain.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {
}
