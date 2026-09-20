package fisio.analysis;

import fisio.FisioParser;
import fisio.FisioParserConstants;
import fisio.TokenMgrError;
import fisio.token.CategoriaToken;
import fisio.token.ErrorLexico;
import fisio.token.MapeoTokens;
import fisio.token.TokenFisio;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Objects;

/** Reconoce el subconjunto Rust-like y mantiene separados tokens y diagnósticos. */
public final class FisioLexerRunner {
    private FisioLexerRunner() {}

    public static ResultadoLexico tokenize(String codigoFuente) {
        Objects.requireNonNull(codigoFuente, "codigoFuente");
        var tokens = new ArrayList<TokenFisio>();
        var errores = new ArrayList<ErrorLexico>();
        var parser = new FisioParser(new StringReader(codigoFuente));
        try {
            while (true) {
                var token = parser.getNextToken();
                if (token.kind == FisioParserConstants.EOF) break;
                var info = MapeoTokens.obtenerInfo(token.kind);
                if (info.categoria == CategoriaToken.ERROR) {
                    String mensaje = "Carácter no reconocido";
                    if (token.kind == FisioParserConstants.CADENA_INVALIDA)
                        mensaje = "Cadena sin cerrar o con escape no admitido";
                    else if (token.kind == FisioParserConstants.NUMERO_INVALIDO)
                        mensaje = "Número mal formado o formato numérico no admitido";
                    errores.add(new ErrorLexico(token.image, mensaje, token.beginLine, token.beginColumn));
                } else {
                    tokens.add(new TokenFisio(token.image, info.categoria, info.idTok, token.beginLine, token.beginColumn));
                }
            }
        } catch (TokenMgrError error) {
            if (parser.token_source.profundidadComentario == 0) throw error;
            errores.add(new ErrorLexico("/*", "Comentario de bloque sin cerrar",
                parser.token_source.lineaComentario, parser.token_source.columnaComentario));
        }
        return new ResultadoLexico(tokens, errores);
    }
}
