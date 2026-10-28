package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import java.time.Instant;
import java.util.Objects;

public record PromotionConfiguration(long productId, long version, PromotionTerms terms,
        Instant startsAt, Instant endsAt, boolean enabled) {
    public PromotionConfiguration {
        Objects.requireNonNull(terms);
        Objects.requireNonNull(startsAt);
        Objects.requireNonNull(endsAt);
        if (productId <= 0 || version < 0 || !endsAt.isAfter(startsAt)
                || startsAt.getNano() % 1_000_000 != 0 || endsAt.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("活动起止时间无效，结束必须晚于开始。");
        }
        startsAt.toEpochMilli();
        endsAt.toEpochMilli();
    }

    public boolean activeAt(Instant now) {
        return enabled && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
