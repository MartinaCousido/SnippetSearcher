package com.snippetsearcher.snippets.snippet;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.snippetsearcher.snippets.analysis.AnalysisRequests;
import com.snippetsearcher.snippets.analysis.AnalysisTask;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.rules.RuleKind;
import com.snippetsearcher.snippets.rules.RuleService;
import com.snippetsearcher.snippets.security.CurrentUser;
import com.snippetsearcher.snippets.testcase.TestCaseService;
import com.snippetsearcher.snippets.users.UserDirectory;

@Service
public class SnippetService {

	private static final Logger log = LoggerFactory.getLogger(SnippetService.class);

	private final LanguageCatalog languages;
	private final SnippetStorage storage;
	private final SnippetRepository repository;
	private final PermissionClient permissions;
	private final CurrentUser currentUser;
	private final UserDirectory users;
	private final TestCaseService testCases;
	private final RuleService rules;
	private final AnalysisRequests analysis;
	private final TransactionTemplate transaction;

	public SnippetService(LanguageCatalog languages, SnippetStorage storage, SnippetRepository repository,
			PermissionClient permissions, CurrentUser currentUser, UserDirectory users, TestCaseService testCases,
			RuleService rules, AnalysisRequests analysis, TransactionTemplate transaction) {
		this.languages = languages;
		this.storage = storage;
		this.repository = repository;
		this.permissions = permissions;
		this.currentUser = currentUser;
		this.users = users;
		this.testCases = testCases;
		this.rules = rules;
		this.analysis = analysis;
		this.transaction = transaction;
	}

	// Crea un snippet (US-1, US-3). Si el código no es válido, no se guarda nada.
	// Si algo falla después de guardar el archivo, se borra: no queda un
	// snippet a medias.
	public SnippetResponse create(CreateSnippetRequest request) {
		requireCode(request.content());
		var language = languages.resolve(request.language(), request.version());
		var diagnostics = language.language().validate(request.content(), language.version());
		if (!diagnostics.isEmpty()) {
			throw new InvalidSnippetException(diagnostics);
		}

		var id = UUID.randomUUID();
		var contentKey = id.toString();
		var description = request.description() == null ? "" : request.description();
		storage.put(contentKey, request.content());
		try {
			var snippet = transaction.execute(status -> {
				var saved = repository.saveAndFlush(new Snippet(id, request.name(), description,
						language.name(), language.version(), contentKey, currentUser.id()));
				permissions.registerOwner(id);
				return saved;
			});
			// Se lintea y se formatea en segundo plano (US-12, US-15): por ahora,
			// pending.
			requestAnalysis(id);
			return new SnippetResponse(id.toString(), snippet.name(), snippet.description(), snippet.language(),
					snippet.version(), language.extension(), request.content(), snippet.compliance(),
					authorOf(snippet.author(), users.emails(List.of(snippet.author()))), List.of());
		} catch (RuntimeException e) {
			deleteQuietly(contentKey, e);
			throw e;
		}
	}

	// Lista los snippets del usuario (US-5): los propios, los compartidos o
	// todos, filtrados por nombre y ordenados por nombre. page empieza en 0.
	public SnippetPage list(int page, int pageSize, String name, String relation) {
		List<UUID> ids = permissions.accessibleSnippets(relation);
		if (ids.isEmpty()) {
			return new SnippetPage(page, pageSize, 0, List.of());
		}
		var found = repository.findByIdInAndNameContainingIgnoreCase(ids, name == null ? "" : name,
				PageRequest.of(page, pageSize, Sort.by("name").ascending().and(Sort.by("id"))));
		// Los emails de los autores de la página, en un solo pedido a Auth0.
		var emails = users.emails(found.getContent().stream().map(Snippet::author).toList());
		var snippets = found.getContent().stream().map(snippet -> toResponse(snippet, emails)).toList();
		return new SnippetPage(page, pageSize, found.getTotalElements(), snippets);
	}

	// Un snippet (US-6). Lo ven el owner y los usuarios con quienes se
	// compartió. Primero se busca: si no existe es 404, no 403.
	public SnippetResponse get(UUID id) {
		var snippet = repository.findById(id).orElseThrow(() -> new SnippetNotFoundException(id));
		var access = permissions.access(id);
		if (access == null || !access.canRead()) {
			throw new SnippetAccessDeniedException(id);
		}
		return toResponse(snippet, users.emails(List.of(snippet.author())));
	}

	// Actualiza un snippet (US-2, US-4). Solo el owner. Se valida igual que al
	// crear, con el lenguaje y la versión nuevos si cambian: si el código no es
	// válido, no se cambia nada. El archivo se pisa con la misma clave.
	// Después corren los tests del snippet (US-16).
	public SnippetResponse update(UUID id, UpdateSnippetRequest request) {
		var snippet = repository.findById(id).orElseThrow(() -> new SnippetNotFoundException(id));
		var access = permissions.access(id);
		if (access == null || !access.canWrite()) {
			throw new SnippetAccessDeniedException(id);
		}
		requireCode(request.content());

		var languageName = request.language() == null ? snippet.language() : request.language();
		// Si cambia el lenguaje y no viene la versión, se usa la versión por
		// defecto del lenguaje nuevo: la anterior puede no existir en él.
		var version = request.version() != null ? request.version()
				: languageName.equals(snippet.language()) ? snippet.version() : null;
		var language = languages.resolve(languageName, version);
		var diagnostics = language.language().validate(request.content(), language.version());
		if (!diagnostics.isEmpty()) {
			throw new InvalidSnippetException(diagnostics);
		}

		// El archivo se guarda dentro de la transacción: si falla, los metadatos
		// no cambian.
		var updated = transaction.execute(status -> {
			snippet.update(request.name() == null ? snippet.name() : request.name(),
					request.description() == null ? snippet.description() : request.description(),
					language.name(), language.version());
			var saved = repository.saveAndFlush(snippet);
			storage.put(snippet.contentKey(), request.content());
			return saved;
		});
		requestAnalysis(id);
		runTests(updated, request.content());
		return new SnippetResponse(id.toString(), updated.name(), updated.description(), updated.language(),
				updated.version(), language.extension(), request.content(), Compliance.PENDING,
				authorOf(updated.author(), users.emails(List.of(updated.author()))), List.of());
	}

	// Descarga un snippet (US-13): el código original, o la versión formateada
	// que guardó el formateo en segundo plano (#23). La descargan el owner y
	// los usuarios con acceso compartido.
	public SnippetFile download(UUID id, boolean formatted) {
		var snippet = repository.findById(id).orElseThrow(() -> new SnippetNotFoundException(id));
		var access = permissions.access(id);
		if (access == null || !access.canRead()) {
			throw new SnippetAccessDeniedException(id);
		}
		var extension = languages.resolve(snippet.language(), snippet.version()).extension();
		if (!formatted) {
			return new SnippetFile(snippet.name() + "." + extension,
					storage.get(snippet.contentKey()).orElse(""));
		}
		if (snippet.formattedKey() == null) {
			throw new FormattedVersionNotFoundException(id);
		}
		var content = storage.get(snippet.formattedKey()).orElseThrow(() -> new FormattedVersionNotFoundException(id));
		return new SnippetFile(snippet.name() + "-formateado." + extension, content);
	}

	// Formatea el código del editor con las reglas de formateo del owner (US-11),
	// sin guardar nada. Lo pueden usar el owner y los usuarios con acceso
	// compartido: un snippet se formatea siempre con las reglas de su owner.
	// Si el formateador falla, 400 con los diagnostics, como un código inválido.
	public FormatResponse format(UUID id, String content) {
		var snippet = repository.findById(id).orElseThrow(() -> new SnippetNotFoundException(id));
		var access = permissions.access(id);
		if (access == null || !access.canRead()) {
			throw new SnippetAccessDeniedException(id);
		}
		var language = languages.resolve(snippet.language(), snippet.version());
		var formatted = language.language().format(content, language.version(),
				rules.activeRules(snippet.author(), RuleKind.FORMAT));
		if (formatted.failed()) {
			throw new InvalidSnippetException(formatted.diagnostics());
		}
		return new FormatResponse(formatted.content());
	}

	// Pide lintear y formatear el snippet en segundo plano. Si falla, el
	// snippet ya está guardado: queda en pending y se registra.
	private void requestAnalysis(UUID id) {
		try {
			analysis.request(List.of(id), java.util.EnumSet.allOf(AnalysisTask.class));
		} catch (RuntimeException e) {
			log.warn("No se pudo pedir el lint y el formateo del snippet {}", id, e);
		}
	}

	// Testing automático (US-16): con el cambio ya guardado, corre todos los
	// tests del snippet y guarda sus resultados. Como pide la US-16, el
	// resultado no bloquea ni revierte el update: si algo falla, solo se
	// registra.
	private void runTests(Snippet snippet, String code) {
		try {
			testCases.runAll(snippet, code);
		} catch (RuntimeException e) {
			log.warn("No se pudieron correr los tests del snippet {}, que ya se actualizó", snippet.id(), e);
		}
	}

	// Borra un snippet. Solo el owner. La fila y el archivo se borran en una
	// transacción: si falla el Asset Service, el snippet queda como estaba.
	// Después se borran sus permisos. Si eso falla, el snippet queda borrado
	// igual: los permisos sueltos no dan acceso a nada, porque el snippet ya no
	// existe.
	public void delete(UUID id) {
		var snippet = repository.findById(id).orElseThrow(() -> new SnippetNotFoundException(id));
		var access = permissions.access(id);
		if (access == null || !access.canWrite()) {
			throw new SnippetAccessDeniedException(id);
		}
		transaction.executeWithoutResult(status -> {
			repository.delete(snippet);
			repository.flush();
			storage.delete(snippet.contentKey());
			// La versión formateada también, si había.
			if (snippet.formattedKey() != null) {
				storage.delete(snippet.formattedKey());
			}
		});
		try {
			permissions.deleteAll(id);
		} catch (RuntimeException e) {
			log.warn("No se pudieron borrar los permisos del snippet {}, que ya se borró", id, e);
		}
	}

	// El listado lleva el código de cada snippet, porque el Snippet de la UI lo
	// tiene: se lee del Snippet Storage.
	private SnippetResponse toResponse(Snippet snippet, Map<String, String> emails) {
		var extension = languages.resolve(snippet.language(), snippet.version()).extension();
		var content = storage.get(snippet.contentKey()).orElse("");
		return new SnippetResponse(snippet.id().toString(), snippet.name(), snippet.description(),
				snippet.language(), snippet.version(), extension, content, snippet.compliance(),
				authorOf(snippet.author(), emails), snippet.lintViolations());
	}

	// La UI muestra el email del autor. En la base se guarda solo su id: el
	// email se le pide a Auth0 (módulo User). Si no se consigue, el id.
	private static String authorOf(String userId, Map<String, String> emails) {
		return emails.getOrDefault(userId, userId);
	}

	// Un snippet sin código no se guarda: el Asset Service no acepta archivos
	// vacíos, y PrintScript lo daría por válido.
	private static void requireCode(String content) {
		if (content.isBlank()) {
			throw new EmptySnippetException();
		}
	}

	// Si borrar el archivo también falla, se responde con el error original: es
	// el que explica qué pasó.
	private void deleteQuietly(String contentKey, RuntimeException cause) {
		try {
			storage.delete(contentKey);
		} catch (RuntimeException deleteFailed) {
			cause.addSuppressed(deleteFailed);
		}
	}
}
