package com.snippetsearcher.snippets.clients;

// Qué puede hacer el usuario con un snippet, según Permission Service.
public record Access(boolean canRead, boolean canWrite) {
}
