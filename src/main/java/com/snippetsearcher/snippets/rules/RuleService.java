package com.snippetsearcher.snippets.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snippetsearcher.snippets.analysis.AnalysisRequests;
import com.snippetsearcher.snippets.analysis.AnalysisTask;
import com.snippetsearcher.snippets.clients.Language.CatalogRule;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.security.CurrentUser;

// Las reglas de lint y formateo de cada usuario (US-11, US-14). Las reglas que
// existen y sus valores por defecto salen del catálogo del lenguaje; en la base
// se guarda solo lo que el usuario cambió. Ver "Reglas: catálogo, valores por
// defecto y validación" en Decisiones técnicas.
@Service
public class RuleService {

	private static final Logger log = LoggerFactory.getLogger(RuleService.class);

	private final UserRuleRepository repository;
	private final AnalysisRequests analysis;
	private final LanguageCatalog languages;
	private final CurrentUser currentUser;
	private final String language;

	public RuleService(UserRuleRepository repository, AnalysisRequests analysis, LanguageCatalog languages,
			CurrentUser currentUser, @Value("${rules.language}") String language) {
		this.repository = repository;
		this.analysis = analysis;
		this.languages = languages;
		this.currentUser = currentUser;
		this.language = language;
	}

	// Las reglas del usuario que pide.
	public List<RuleResponse> list(RuleKind kind) {
		return rulesOf(currentUser.id(), kind);
	}

	// Guarda las reglas que manda la UI (puede ser la lista completa o solo
	// algunas) y devuelve todas. Si algo no sirve, no guarda nada (400).
	@Transactional
	public List<RuleResponse> update(RuleKind kind, List<RuleRequest> requested) {
		var userId = currentUser.id();
		var catalog = catalog(kind);
		var current = new LinkedHashMap<String, RuleResponse>();
		rulesOf(userId, kind).forEach(rule -> current.put(rule.name(), rule));
		for (var request : requested) {
			var rule = catalog.get(request.ruleName());
			if (rule == null) {
				throw new InvalidRulesException("No existe la regla de " + kind.value() + " '" + request.ruleName() + "'");
			}
			var value = request.value() == null ? current.get(rule.name()).value() : valueOf(rule, request.value());
			current.put(rule.name(), new RuleResponse(rule.name(), rule.name(), request.isActive(), value));
		}
		requireNoOpposites(catalog, current);
		repository.deleteByUserIdAndKind(userId, kind.value());
		repository.flush();
		current.values().forEach(rule -> repository.save(new UserRule(userId, kind, rule.name(), rule.isActive(),
				rule.value() == null ? null : rule.value().toString())));
		log.info("reglas de {} guardadas", kind.value());
		// Los snippets del usuario se vuelven a lintear o formatear con las
		// reglas nuevas, en segundo plano (US-12, US-15).
		analysis.requestForAuthor(userId, kind == RuleKind.LINT ? AnalysisTask.LINT : AnalysisTask.FORMAT);
		return List.copyOf(current.values());
	}

	// Las reglas activas de un usuario, como las pide el lenguaje: nombre →
	// valor, con true en las de sí o no. Para lintear y formatear sus snippets.
	public Map<String, Object> activeRules(String userId, RuleKind kind) {
		var active = new LinkedHashMap<String, Object>();
		for (var rule : rulesOf(userId, kind)) {
			if (rule.isActive()) {
				active.put(rule.name(), rule.value() == null ? Boolean.TRUE : rule.value());
			}
		}
		return active;
	}

	private List<RuleResponse> rulesOf(String userId, RuleKind kind) {
		var saved = repository.findByUserIdAndKind(userId, kind.value()).stream()
				.collect(Collectors.toMap(UserRule::name, Function.identity()));
		return catalog(kind).values().stream()
				.map(rule -> {
					var mine = saved.get(rule.name());
					if (mine == null) {
						return new RuleResponse(rule.name(), rule.name(), rule.defaultValue().isActive(),
								rule.defaultValue().value());
					}
					return new RuleResponse(rule.name(), rule.name(), mine.active(),
							mine.value() == null ? null : valueOf(rule, mine.value()));
				})
				.toList();
	}

	private Map<String, CatalogRule> catalog(RuleKind kind) {
		var catalog = languages.resolve(language, null).language().rules();
		var rules = kind == RuleKind.LINT ? catalog.lint() : catalog.format();
		var byName = new LinkedHashMap<String, CatalogRule>();
		rules.forEach(rule -> byName.put(rule.name(), rule));
		return byName;
	}

	// El valor con el tipo que dice el catálogo. 400 si no sirve.
	private static Object valueOf(CatalogRule rule, Object value) {
		return switch (rule.type()) {
			case "text" -> {
				var text = value.toString().trim();
				if (rule.options() != null && !rule.options().contains(text)) {
					throw new InvalidRulesException("La regla '" + rule.name() + "' acepta " + String.join(" o ",
							rule.options()) + ", no '" + text + "'");
				}
				yield text;
			}
			case "number" -> {
				int number;
				try {
					number = value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString().trim());
				} catch (NumberFormatException e) {
					throw new InvalidRulesException("La regla '" + rule.name() + "' pide un número, no '" + value + "'");
				}
				if (number < 0) {
					throw new InvalidRulesException("La regla '" + rule.name() + "' no acepta números negativos");
				}
				yield number;
			}
			default -> null;
		};
	}

	private static void requireNoOpposites(Map<String, CatalogRule> catalog, Map<String, RuleResponse> rules) {
		for (var rule : rules.values()) {
			var opposite = catalog.get(rule.name()).opposite();
			if (rule.isActive() && opposite != null && rules.containsKey(opposite) && rules.get(opposite).isActive()) {
				throw new InvalidRulesException("Las reglas '" + rule.name() + "' y '" + opposite
						+ "' son opuestas: activá solo una");
			}
		}
	}
}
