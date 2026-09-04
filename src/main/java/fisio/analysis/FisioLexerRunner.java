package fisio.analysis;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import fisio.FISIOParserConstants;
import fisio.FISIOParserTokenManager;
import fisio.SimpleCharStream;
import fisio.Token;
import fisio.TokenMgrError;
import fisio.token.CategoriaToken;
import fisio.token.ErrorLexico;
import fisio.token.MapeoTokens;
import fisio.token.TokenFisio;

/**
 * Ejecuta unicamente el TokenManager generado por JavaCC sobre un String de
 * codigo FISIO, sin instanciar FISIOParser — equivalente funcional de
 * Lexer(codigo).analizar() en el proyecto Python (F5).
 *
 * Paso 3.1 — cierre de paridad lexica: agrega una pasada de normalizacion
 * por CONTIGUIDAD sobre los tokens crudos de JavaCC. Es necesaria porque
 * lexer.py, al leer un identificador/palabra reservada/numero, sigue
 * probando si el caracter siguiente (":" o "!") continua o no el mismo
 * lexema segun el mismo criterio que ya usa LEXEMA_INVALIDO (delimitador
 * solo si va seguido de "="), incluso cuando ese caracter por si solo no
 * calza con ninguna alternativa de LEXEMA_INVALIDO (porque lo que sigue a
 * ese ":"/"!" es a su vez un delimitador real o el fin del archivo). JavaCC,
 * al tokenizar cada posicion de forma independiente, ya deja separados esos
 * casos (p. ej. ID("abc") + OP_INCOMPLETO(":") para la entrada "abc:") —
 * esta pasada los vuelve a unir cuando son fisicamente contiguos (misma
 * linea, columnas consecutivas), exactamente como hace lexer.py.
 */
public final class FisioLexerRunner {

    private FisioLexerRunner() {
    }

    public static ResultadoLexico tokenize(String codigo) {
        List<Token> crudos = leerTokensCrudos(codigo);
        return fusionarYClasificar(crudos);
    }

    private static List<Token> leerTokensCrudos(String codigo) {
        List<Token> crudos = new ArrayList<>();

        SimpleCharStream charStream = new SimpleCharStream(new StringReader(codigo));
        FISIOParserTokenManager tokenManager = new FISIOParserTokenManager(charStream);

        try {
            Token tok = tokenManager.getNextToken();
            while (tok.kind != FISIOParserConstants.EOF) {
                crudos.add(tok);
                tok = tokenManager.getNextToken();
            }
        } catch (TokenMgrError e) {
            // No se oculta: si esto ocurre, LEXEMA_INVALIDO/OP_INCOMPLETO no
            // cubrieron una entrada que deberian haber capturado. Debe
            // investigarse y corregirse FISIO.jj, no silenciarse como un
            // ErrorLexico mas.
            throw new IllegalStateException(
                "TokenMgrError inesperado del TokenManager de FISIO — revisar FISIO.jj: " + e.getMessage(), e);
        }

        return crudos;
    }

    private static ResultadoLexico fusionarYClasificar(List<Token> crudos) {
        List<TokenFisio> tokens = new ArrayList<>();
        List<ErrorLexico> errores = new ArrayList<>();

        int i = 0;
        while (i < crudos.size()) {
            Token actual = crudos.get(i);

            if (esErrorCrudo(actual.kind)) {
                // Arranca un run de error sin prefijo valido: "::", "@", etc.
                // (el escaneo de continuacion empieza en i+1, no en i: "actual"
                // ya es el primer elemento del run).
                i = agregarRunDeError(crudos, i + 1, actual, tokens, errores);
                continue;
            }

            CategoriaToken categoria = MapeoTokens.categoriaDe(actual.kind);
            boolean puedeIniciarFusion = categoria == CategoriaToken.PR
                || categoria == CategoriaToken.ID
                || categoria == CategoriaToken.NUM;

            if (puedeIniciarFusion
                    && i + 1 < crudos.size()
                    && esErrorCrudo(crudos.get(i + 1).kind)
                    && sonContiguos(actual, crudos.get(i + 1))) {
                // "abc:", "12:", "mru!", "9.8:"... — el token valido se
                // descarta y se funde con el/los error(es) que le siguen
                // pegados, igual que _leer_identificador_o_reservada /
                // _leer_numero en lexer.py.
                i = agregarRunDeError(crudos, i + 1, actual, tokens, errores);
                continue;
            }

            tokens.add(MapeoTokens.crear(actual));
            i++;
        }

        return new ResultadoLexico(tokens, errores);
    }

    /**
     * Acumula el lexema comenzando en {@code primerToken} (que puede ser un
     * token valido ya decidido a fusionarse, o directamente un error crudo)
     * y sigue absorbiendo tokens LEXEMA_INVALIDO/OP_INCOMPLETO mientras cada
     * uno sea fisicamente contiguo al anterior. Devuelve el indice de
     * {@code crudos} en el que debe continuar el recorrido principal.
     */
    private static int agregarRunDeError(
            List<Token> crudos, int siguienteIndice, Token primerToken,
            List<TokenFisio> tokens, List<ErrorLexico> errores) {

        StringBuilder lexema = new StringBuilder(primerToken.image);
        Token ultimoAbsorbido = primerToken;
        int j = siguienteIndice;

        while (j < crudos.size()
                && esErrorCrudo(crudos.get(j).kind)
                && sonContiguos(ultimoAbsorbido, crudos.get(j))) {
            ultimoAbsorbido = crudos.get(j);
            lexema.append(ultimoAbsorbido.image);
            j++;
        }

        String lexemaCompleto = lexema.toString();
        String mensaje = ClasificadorErrorLexico.clasificar(lexemaCompleto);
        tokens.add(new TokenFisio(lexemaCompleto, CategoriaToken.ERROR, "ERROR",
            primerToken.beginLine, primerToken.beginColumn));
        errores.add(new ErrorLexico(lexemaCompleto, mensaje, primerToken.beginLine, primerToken.beginColumn));

        return j;
    }

    private static boolean esErrorCrudo(int kind) {
        return kind == FISIOParserConstants.LEXEMA_INVALIDO || kind == FISIOParserConstants.OP_INCOMPLETO;
    }

    private static boolean sonContiguos(Token anterior, Token siguiente) {
        return anterior.beginLine == siguiente.beginLine
            && anterior.beginColumn + anterior.image.length() == siguiente.beginColumn;
    }
}
