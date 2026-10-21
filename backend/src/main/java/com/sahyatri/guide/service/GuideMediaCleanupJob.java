package com.sahyatri.guide.service;

import com.sahyatri.common.config.GuideMediaProperties;
import com.sahyatri.guide.entity.GuideMedia;
import com.sahyatri.guide.entity.GuideMediaStatus;
import com.sahyatri.guide.repository.GuideMediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Hourly: a video still PENDING after {@code app.guide-media.pending-ttl} (default a day) was never confirmed, so
 * its row goes and then its file. One transaction per row; the state is re-checked under the row lock.
 */
@Component
public class GuideMediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(GuideMediaCleanupJob.class);

    private final GuideMediaRepository media;
    private final GuideMediaService service;
    private final GuideMediaProperties props;
    private final TransactionTemplate tx;

    public GuideMediaCleanupJob(GuideMediaRepository media, GuideMediaService service, GuideMediaProperties props,
                                TransactionTemplate tx) {
        this.media = media;
        this.service = service;
        this.props = props;
        this.tx = tx;
    }

    @Scheduled(cron = "0 25 * * * *", zone = "Asia/Kolkata")
    public void run() {
        Instant before = Instant.now().minus(props.pendingTtl());
        for (UUID id : media.findIdsByStatusCreatedBefore(GuideMediaStatus.PENDING, before)) {
            GuideMedia removed = tx.execute(status -> media.findByIdForUpdate(id)
                    .filter(m -> m.getStatus() == GuideMediaStatus.PENDING && m.getCreatedAt().isBefore(before))
                    .map(m -> {
                        media.delete(m);
                        media.flush();
                        return m;
                    })
                    .orElse(null));
            if (removed != null) {
                service.removeFiles(removed);
                log.info("GUIDE_MEDIA_PENDING_REMOVED {}", id);
            }
        }
    }
}
