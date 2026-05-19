package com.sseulang.domain.chat.domain;

import java.util.Collection;
import java.util.Map;

public interface ItemView {

    

    Map<Long, ItemProjection> findByIds(Collection<Long> itemIds);

    

    record ItemProjection(Long id, String title, String thumbnailUrl, Long sellerId, Long price) { }
}
