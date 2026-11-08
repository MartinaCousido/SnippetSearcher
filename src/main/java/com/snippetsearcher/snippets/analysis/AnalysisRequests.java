package com.snippetsearcher.snippets.analysis;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.snippetsearcher.snippets.snippet.SnippetRepository;

// Pide lintear o formatear snippets en segundo plano (US-12, US-15): sube la
// revisión de cada uno (el lint lo deja en PENDING) y, cuando eso ya está
// guardado, publica un mensaje por snippet. Quien pide no espera el resultado.
@Service
public class AnalysisRequests {

	private static final Logger log = LoggerFactory.getLogger(AnalysisRequests.class);

	private final SnippetRepository snippets;
	private final AnalysisQueue queue;

	public AnalysisRequests(SnippetRepository snippets, AnalysisQueue queue) {
		this.snippets = snippets;
		this.queue = queue;
	}

	@Transactional
	public void request(Collection<UUID> snippetIds, Set<AnalysisTask> tasks) {
		for (var snippet : snippets.findAllById(snippetIds)) {
			for (var task : tasks) {
				int revision = task == AnalysisTask.LINT ? snippet.requestLint() : snippet.requestFormat();
				afterCommit(() -> publish(task, snippet.id(), revision));
			}
		}
	}

	// Los snippets de un usuario, cuando cambia sus reglas: se lintean o
	// formatean con las nuevas.
	@Transactional
	public void requestForAuthor(String author, AnalysisTask task) {
		request(snippets.findIdsByAuthor(author), Set.of(task));
	}

	// Si Redis no responde, el snippet queda en PENDING y se registra: lo que
	// ya se guardó no se deshace.
	private void publish(AnalysisTask task, UUID snippetId, int revision) {
		try {
			queue.publish(task, snippetId, revision);
		} catch (RuntimeException e) {
			log.warn("No se pudo publicar el pedido de {} del snippet {}", task, snippetId, e);
		}
	}

	private static void afterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}
}
