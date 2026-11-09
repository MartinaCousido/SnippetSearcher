package com.snippetsearcher.snippets.analysis;

import java.util.UUID;

// Donde se publican los pedidos de lint y formateo. Lo implementa Redis Streams.
@FunctionalInterface
public interface AnalysisQueue {

	void publish(AnalysisTask task, UUID snippetId, int revision);
}
