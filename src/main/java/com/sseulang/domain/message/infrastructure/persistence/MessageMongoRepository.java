package com.sseulang.domain.message.infrastructure.persistence;

import com.sseulang.domain.message.domain.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

interface MessageMongoRepository extends MongoRepository<Message, String> {
}
