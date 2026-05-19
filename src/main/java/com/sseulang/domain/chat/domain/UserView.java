package com.sseulang.domain.chat.domain;

import java.util.Collection;
import java.util.Map;

public interface UserView {

    

    Map<Long, UserProjection> findByIds(Collection<Long> userIds);

    
    record UserProjection(Long id, String nickname, String profileImage) { }
}
