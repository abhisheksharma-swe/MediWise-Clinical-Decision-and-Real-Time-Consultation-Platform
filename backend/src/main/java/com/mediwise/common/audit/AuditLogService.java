package com.mediwise.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final MongoTemplate mongoTemplate;

    public Page<AuditLogResponse> search(String actorId, String outcome, Instant start, Instant end,
                                          int page, int size) {
        Criteria criteria = new Criteria();
        var conditions = new java.util.ArrayList<Criteria>();
        if (actorId != null && !actorId.isBlank()) conditions.add(Criteria.where("actorId").is(actorId));
        if (outcome != null && !outcome.isBlank()) conditions.add(Criteria.where("outcome").is(outcome.toUpperCase()));
        if (start != null || end != null) {
            Criteria timeCriteria = Criteria.where("timestamp");
            if (start != null) timeCriteria = timeCriteria.gte(start);
            if (end != null) timeCriteria = timeCriteria.lte(end);
            conditions.add(timeCriteria);
        }
        if (!conditions.isEmpty()) {
            criteria.andOperator(conditions.toArray(new Criteria[0]));
        }

        long total = mongoTemplate.count(new Query(criteria), AuditLog.class);

        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp"));
        Query pageQuery = new Query(criteria).with(pageable);
        var results = mongoTemplate.find(pageQuery, AuditLog.class).stream().map(AuditLogResponse::from).toList();

        return new PageImpl<>(results, pageable, total);
    }
}
