package com.snippetsearcher.snippets.language;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.snippetsearcher.snippets.language.LanguageCatalog.FileType;

@RestController
public class LanguageController {

	private final LanguageCatalog catalog;

	public LanguageController(LanguageCatalog catalog) {
		this.catalog = catalog;
	}

	// getFileTypes() de la UI: con esto arma el selector de lenguaje y reconoce
	// los archivos que se suben por su extensión.
	@GetMapping("/languages")
	public List<FileType> languages() {
		return catalog.fileTypes();
	}
}
