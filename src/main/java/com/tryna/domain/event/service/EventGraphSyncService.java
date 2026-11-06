
package com.tryna.domain.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventGraphSyncService {

    private final Neo4jClient neo4jClient;

    public void syncEventToGraph(
            Long eventId,
            String title,
            String activityCode,
            List<Map<String, String>> entities
    ) {
        try {
            // 1. Event 노드 생성 또는 갱신
            String eventCypher = """
                MERGE (e:EventRef {id: $eventId})
                SET e.title = $title
                """;

            neo4jClient.query(eventCypher)
                    .bind(eventId).to("eventId")
                    .bind(title != null ? title : "제목 없음").to("title")
                    .run();

            // 2. Activity 노드와 Event 노드 연결
            if (activityCode != null && !activityCode.isBlank()) {

                String activityCypher = """
                    MATCH (e:EventRef {id: $eventId})
                    MATCH (a:Activity {code: $activityCode})
                    MERGE (e)-[:HAS_ACTIVITY]->(a)
                    """;

                neo4jClient.query(activityCypher)
                        .bind(eventId).to("eventId")
                        .bind(activityCode).to("activityCode")
                        .run();
            }

            // 3. Entity 노드 생성 및 Event 노드와 연결
            if (entities != null && !entities.isEmpty()) {

                String entityCypher = """
                    MATCH (e:EventRef {id: $eventId})
                    UNWIND $entities AS ent
                    MERGE (entity:Entity {
                        name: ent.name,
                        role: ent.role
                    })
                    MERGE (e)-[:INVOLVES]->(entity)
                    """;

                neo4jClient.query(entityCypher)
                        .bind(eventId).to("eventId")
                        .bind(entities).to("entities")
                        .run();
            }

            log.info(
                    "Neo4j Event Graph Sync Completed: eventId={}",
                    eventId
            );

        } catch (Exception e) {
            log.error(
                    "Failed to sync event to Neo4j graph: eventId={}",
                    eventId,
                    e
            );
        }
    }
}
