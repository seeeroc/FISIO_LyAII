package fisio.token;

import java.util.Map;

import fisio.FISIOParserConstants;
import fisio.Token;

/**
 * Traduce Token.kind (generado por JavaCC) hacia la categoria y el ID
 * FISIO originales de token_types.py — la capa que evita que cualquier
 * consumidor (GUI futura incluida) dependa de los enteros generados por
 * JavaCC (5, 6, 7...). Solo cubre los 39 tokens "validos" (palabras
 * reservadas, operadores, signos, ID, NUM); LEXEMA_INVALIDO/OP_INCOMPLETO
 * se manejan aparte en fisio.analysis (siempre se convierten en
 * ErrorLexico, nunca en TokenFisio).
 */
public final class MapeoTokens {

    private record InfoToken(CategoriaToken categoria, String idFisio) {
    }

    private static final Map<Integer, InfoToken> TABLA = Map.ofEntries(
        // Palabras reservadas (PR_01..PR_20) — mismo orden que PALABRAS_RESERVADAS en token_types.py
        Map.entry(FISIOParserConstants.MRU,         new InfoToken(CategoriaToken.PR, "PR_01")),
        Map.entry(FISIOParserConstants.MRUA,        new InfoToken(CategoriaToken.PR, "PR_02")),
        Map.entry(FISIOParserConstants.CAIDA,       new InfoToken(CategoriaToken.PR, "PR_03")),
        Map.entry(FISIOParserConstants.PARABOLICO,  new InfoToken(CategoriaToken.PR, "PR_04")),
        Map.entry(FISIOParserConstants.DESPEJAR,    new InfoToken(CategoriaToken.PR, "PR_05")),
        Map.entry(FISIOParserConstants.GRAFICAR,    new InfoToken(CategoriaToken.PR, "PR_06")),
        Map.entry(FISIOParserConstants.SIMULAR,     new InfoToken(CategoriaToken.PR, "PR_07")),
        Map.entry(FISIOParserConstants.RAIZ,        new InfoToken(CategoriaToken.PR, "PR_08")),
        Map.entry(FISIOParserConstants.SENO,        new InfoToken(CategoriaToken.PR, "PR_09")),
        Map.entry(FISIOParserConstants.COSENO,      new InfoToken(CategoriaToken.PR, "PR_10")),
        Map.entry(FISIOParserConstants.MAGNITUD,    new InfoToken(CategoriaToken.PR, "PR_11")),
        Map.entry(FISIOParserConstants.GRAVEDAD,    new InfoToken(CategoriaToken.PR, "PR_12")),
        Map.entry(FISIOParserConstants.POSICION,    new InfoToken(CategoriaToken.PR, "PR_13")),
        Map.entry(FISIOParserConstants.VELOCIDAD,   new InfoToken(CategoriaToken.PR, "PR_14")),
        Map.entry(FISIOParserConstants.ACELERACION, new InfoToken(CategoriaToken.PR, "PR_15")),
        Map.entry(FISIOParserConstants.TIEMPO,      new InfoToken(CategoriaToken.PR, "PR_16")),
        Map.entry(FISIOParserConstants.ALTURA,      new InfoToken(CategoriaToken.PR, "PR_17")),
        Map.entry(FISIOParserConstants.ALCANCE,     new InfoToken(CategoriaToken.PR, "PR_18")),
        Map.entry(FISIOParserConstants.INICIO,      new InfoToken(CategoriaToken.PR, "PR_19")),
        Map.entry(FISIOParserConstants.FIN,         new InfoToken(CategoriaToken.PR, "PR_20")),

        // Operadores matematicos (OPM_01..OPM_05)
        Map.entry(FISIOParserConstants.SUMA,           new InfoToken(CategoriaToken.OPM, "OPM_01")),
        Map.entry(FISIOParserConstants.RESTA,          new InfoToken(CategoriaToken.OPM, "OPM_02")),
        Map.entry(FISIOParserConstants.MULTIPLICACION, new InfoToken(CategoriaToken.OPM, "OPM_03")),
        Map.entry(FISIOParserConstants.DIVISION,       new InfoToken(CategoriaToken.OPM, "OPM_04")),
        Map.entry(FISIOParserConstants.POTENCIA,       new InfoToken(CategoriaToken.OPM, "OPM_05")),

        // Operadores relacionales / asignacion (OPR_01..OPR_07)
        Map.entry(FISIOParserConstants.ASIGNACION,  new InfoToken(CategoriaToken.OPR, "OPR_01")),
        Map.entry(FISIOParserConstants.IGUALDAD,    new InfoToken(CategoriaToken.OPR, "OPR_02")),
        Map.entry(FISIOParserConstants.MENOR,       new InfoToken(CategoriaToken.OPR, "OPR_03")),
        Map.entry(FISIOParserConstants.MAYOR,       new InfoToken(CategoriaToken.OPR, "OPR_04")),
        Map.entry(FISIOParserConstants.MENOR_IGUAL, new InfoToken(CategoriaToken.OPR, "OPR_05")),
        Map.entry(FISIOParserConstants.MAYOR_IGUAL, new InfoToken(CategoriaToken.OPR, "OPR_06")),
        Map.entry(FISIOParserConstants.DISTINTO,    new InfoToken(CategoriaToken.OPR, "OPR_07")),

        // Signos (SIG_01..SIG_07)
        Map.entry(FISIOParserConstants.PARENTESIS_ABRE,   new InfoToken(CategoriaToken.SIG, "SIG_01")),
        Map.entry(FISIOParserConstants.PARENTESIS_CIERRA, new InfoToken(CategoriaToken.SIG, "SIG_02")),
        Map.entry(FISIOParserConstants.CORCHETE_ABRE,     new InfoToken(CategoriaToken.SIG, "SIG_03")),
        Map.entry(FISIOParserConstants.CORCHETE_CIERRA,   new InfoToken(CategoriaToken.SIG, "SIG_04")),
        Map.entry(FISIOParserConstants.COMA,              new InfoToken(CategoriaToken.SIG, "SIG_05")),
        Map.entry(FISIOParserConstants.PUNTO,             new InfoToken(CategoriaToken.SIG, "SIG_06")),
        Map.entry(FISIOParserConstants.PUNTO_Y_COMA,      new InfoToken(CategoriaToken.SIG, "SIG_07")),

        // Identificador y numero
        Map.entry(FISIOParserConstants.ID,  new InfoToken(CategoriaToken.ID, "ID")),
        Map.entry(FISIOParserConstants.NUM, new InfoToken(CategoriaToken.NUM, "NUM"))
    );

    private MapeoTokens() {
    }

    public static boolean esTokenValido(int kind) {
        return TABLA.containsKey(kind);
    }

    /** Categoria FISIO de un kind valido, o null si no esta en la tabla (LEXEMA_INVALIDO/OP_INCOMPLETO). */
    public static CategoriaToken categoriaDe(int kind) {
        InfoToken info = TABLA.get(kind);
        return info == null ? null : info.categoria();
    }

    public static TokenFisio crear(Token token) {
        InfoToken info = TABLA.get(token.kind);
        if (info == null) {
            throw new IllegalArgumentException(
                "MapeoTokens.crear() no aplica a LEXEMA_INVALIDO/OP_INCOMPLETO (kind=" + token.kind + ")");
        }
        return new TokenFisio(token.image, info.categoria(), info.idFisio(), token.beginLine, token.beginColumn);
    }
}
