package com.snippetsearcher.snippets.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.snippetsearcher.snippets.analysis.AnalysisTask;

// La muestra de saturación de cada stream, contra Redis real.
@SpringBootTest
class RedisStreamMetricsTests {

	@Autowired
	private RedisStreamMetrics metrics;

	@Autowired
	private StringRedisTemplate redis;

	@BeforeEach
	void setUp() {
		redis.delete(AnalysisTask.FORMAT.stream());
	}

	@Test
	void measuresTheLengthAndThePendingMessagesOfAStream() {
		for (int i = 0; i < 2; i++) {
			redis.opsForStream().add(StreamRecords.newRecord().in(AnalysisTask.FORMAT.stream()).ofMap(Map.of("k", "v")));
		}

		var sample = metrics.sampleOf(AnalysisTask.FORMAT);

		assertThat(sample).containsEntry("stream", "snippets:format").containsEntry("length", 2L)
				.containsEntry("pending", 0L).containsEntry("service", "snippet-service");
	}
}
