package com.snippetsearcher.snippets.analysis;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// AnalysisQueue con Redis Streams: un XADD por pedido, al stream de la tarea.
@Component
public class RedisAnalysisQueue implements AnalysisQueue {

	static final String SNIPPET_ID = "snippetId";
	static final String REVISION = "revision";

	private final StringRedisTemplate redis;

	public RedisAnalysisQueue(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public void publish(AnalysisTask task, UUID snippetId, int revision) {
		redis.opsForStream().add(StreamRecords.newRecord()
				.in(task.stream())
				.ofMap(Map.of(SNIPPET_ID, snippetId.toString(), REVISION, Integer.toString(revision))));
	}
}
