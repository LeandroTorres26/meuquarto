package com.meuquarto.shared.domain;

import java.util.List;
import java.util.Optional;

/**
 * One rule that a value of type {@code T} must obey. {@code check} answers with the problem of type
 * {@code P} when the rule is broken, or empty when it is respected (ADR 0018).
 * <p>
 * Two type parameters, like {@code Dupla<S, W>} from the course: the rule does not care what the
 * value or the problem are, so each caller picks them (a {@code String} and a {@code Slug.Problem},
 * for example).
 */
public interface ValidationRule<T, P> {

    Optional<P> check(T value);

    /** Default method: every rule can answer yes or no without each class repeating this. */
    default boolean accepts(T value) {
        return check(value).isEmpty();
    }

    /**
     * Static method of the interface: asks each rule in order and returns the first problem found.
     * The list holds different classes, but all are used as {@code ValidationRule}; which
     * {@code check} runs is decided at run time (subtype polymorphism, like the PetShop).
     */
    static <T, P> Optional<P> firstProblem(List<? extends ValidationRule<T, P>> rules, T value) {
        for (ValidationRule<T, P> rule : rules) {
            Optional<P> problem = rule.check(value);
            if (problem.isPresent()) {
                return problem;
            }
        }
        return Optional.empty();
    }
}
