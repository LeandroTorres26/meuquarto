Toast is a short confirmation or error, inverted (`ink` background) so it reads over any theme.

React: `<Meuquarto.Toast tone title onClose>sentence</Meuquarto.Toast>`. Plain HTML: `div.mq-toast` (`mq-toast--error`) with an icon, `p.mq-toast__text` starting with a `strong` status word, and for errors `button.mq-toast__close`. Stack them inside `div.mq-toasts`: bottom center on phones, bottom right from 600px.

| Tone | Behavior |
| --- | --- |
| success | `role="status"`, check icon, leaves after 5 seconds. The status word repeats the action's verb ("Salvar perfil" → "Salvo."). |
| error | `role="alert"`, alert icon and a `danger` stripe on the left, stays until closed. Says what failed and what happens next. |

Do not: stack more than two, put actions inside (use a dialog), or rely on the stripe color alone: the word "Erro." is required.
