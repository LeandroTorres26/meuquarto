Toast is a short, self-dismissing confirmation or error message, inverted so it reads over any theme.

Provide one sentence that starts with a bold status word ("Pronto.", "Erro."). Use `role="status"` for success and `role="alert"` for failures. Place it bottom-center on mobile and bottom-right on desktop, and dismiss after 5 seconds unless it is an error.

Do not: stack more than two toasts, or put actions inside one; use a dialog for that.
