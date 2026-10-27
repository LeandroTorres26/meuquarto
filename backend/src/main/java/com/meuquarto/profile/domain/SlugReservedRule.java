package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.Slug.Problem;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.Optional;
import java.util.Set;

/** Routes and names a profile must never shadow (BR-01). */
final class SlugReservedRule implements ValidationRule<String, Problem> {

    static final Set<String> RESERVED = Set.of(
            // app routes
            "api", "painel", "entrar", "sair", "cadastro", "esqueci-senha", "redefinir-senha",
            "termos", "privacidade", "loja", "compras", "conta", "quarto", "item", "obra", "busca",
            // framework and infrastructure paths
            "static", "assets", "public", "favicon", "robots", "sitemap", "manifest", "health", "status",
            // identity and support
            "meuquarto", "admin", "administrador", "root", "suporte", "ajuda", "sobre", "contato",
            "login", "logout", "signup", "signin", "register", "settings", "config", "dashboard",
            "www", "mail", "email", "blog", "app", "null", "undefined");

    @Override
    public Optional<Problem> check(String value) {
        return RESERVED.contains(value) ? Optional.of(Problem.RESERVED) : Optional.empty();
    }
}
