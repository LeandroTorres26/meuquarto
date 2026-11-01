package com.meuquarto.identity.infra;

/**
 * The reset e-mail in pt-BR: a plain-text part and a small HTML part (inline styles, no images, no
 * remote resources). The link is the only variable; it is built from SITE_URL and a URL-safe token,
 * and is HTML-escaped anyway.
 */
record PasswordResetEmail(String link) {

    static final String SUBJECT = "Redefinir sua senha do meuquarto";

    String text() {
        return """
                Olá,

                Recebemos um pedido para redefinir a senha da sua conta no meuquarto.
                Para escolher uma nova senha, abra o link abaixo. Ele vale por 30 minutos e só pode ser usado uma vez:

                %s

                Se não foi você, pode ignorar este e-mail: sua senha continua a mesma.

                meuquarto
                """.formatted(link);
    }

    String html() {
        String href = escape(link);
        return """
                <!doctype html>
                <html lang="pt-BR">
                <body style="margin:0;padding:24px;background:#f6f3ee;font-family:Arial,Helvetica,sans-serif;color:#222;">
                <div style="max-width:480px;margin:0 auto;background:#ffffff;padding:24px;border-radius:8px;">
                <h1 style="font-size:20px;margin:0 0 16px;">Redefinir sua senha</h1>
                <p style="font-size:15px;line-height:1.5;margin:0 0 16px;">Recebemos um pedido para redefinir a senha da sua conta no meuquarto. O link vale por 30 minutos e só pode ser usado uma vez.</p>
                <p style="margin:0 0 16px;"><a href="%s" style="display:inline-block;background:#222;color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none;font-size:15px;">Escolher nova senha</a></p>
                <p style="font-size:13px;line-height:1.5;margin:0 0 8px;color:#555;">Se o botão não abrir, copie este endereço no navegador:</p>
                <p style="font-size:13px;line-height:1.5;margin:0 0 16px;word-break:break-all;"><a href="%s" style="color:#222;">%s</a></p>
                <p style="font-size:13px;line-height:1.5;margin:0;color:#555;">Se não foi você, ignore este e-mail: sua senha continua a mesma.</p>
                </div>
                </body>
                </html>
                """.formatted(href, href, href);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
