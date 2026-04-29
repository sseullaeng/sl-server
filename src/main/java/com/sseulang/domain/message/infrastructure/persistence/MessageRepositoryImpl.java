package com.sseulang.domain.message.infrastructure.persistence;

import com.sseulang.domain.message.domain.Message;
import com.sseulang.domain.message.domain.MessageRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepositoryImpl implements MessageRepository {

    private final MessageMongoRepository mongo;
    private final MongoTemplate mongoTemplate;

    public MessageRepositoryImpl(MessageMongoRepository mongo, MongoTemplate mongoTemplate) {
        this.mongo = mongo;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Message save(Message message) {
        return mongo.save(message);
    }

    @Override
    public List<Message> findPage(Long chatRoomId, String beforeId, int size) {
        Criteria criteria = Criteria.where("chatRoomId").is(chatRoomId);
        if (beforeId != null && !beforeId.isBlank()) {
            criteria = criteria.and("_id").lt(beforeId);
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "_id"))
                .limit(size);
        return mongoTemplate.find(query, Message.class);
    }
}
