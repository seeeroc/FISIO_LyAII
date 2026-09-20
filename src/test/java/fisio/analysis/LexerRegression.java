package fisio.analysis;

import fisio.FisioParserConstants;
import fisio.token.CategoriaToken;
import fisio.token.MapeoTokens;
import fisio.semantic.FisioSemanticRunner;
import java.util.List;

/** Regresión léxica ejecutable sin bibliotecas externas. */
public final class LexerRegression {
    private static int casos;
    private static void verificar(boolean valor, String mensaje) {
        casos++;
        if (!valor) throw new AssertionError(mensaje);
    }
    private static void tokens(String codigo, String... esperados) {
        var r = FisioLexerRunner.tokenize(codigo);
        verificar(r.errores().isEmpty(), codigo + ": " + r.errores());
        verificar(r.tokens().stream().map(t -> t.lexema()).toList().equals(List.of(esperados)), codigo + ": " + r.tokens());
    }
    private static void error(String codigo, String mensaje) {
        var r = FisioLexerRunner.tokenize(codigo);
        verificar(r.errores().size() == 1 && r.errores().get(0).mensaje().contains(mensaje), codigo + ": " + r);
    }
    public static void main(String[] args) {
        // Cada literal declarado debe conservar su categoría e ID públicos.
        for (int kind = 1; kind < FisioParserConstants.tokenImage.length; kind++) {
            String imagen = FisioParserConstants.tokenImage[kind];
            var info = MapeoTokens.obtenerInfo(kind);
            if (!imagen.startsWith("\"") || info.categoria == CategoriaToken.ERROR) continue;
            String literal = imagen.substring(1, imagen.length() - 1);
            var r = FisioLexerRunner.tokenize(literal);
            verificar(r.errores().isEmpty() && r.tokens().size() == 1, "Literal: " + literal);
            verificar(r.tokens().get(0).idFisio().equals(info.idTok), "Mapeo: " + literal);
        }
        tokens("fn fnx let let2 _ _x x_1 FN", "fn", "fnx", "let", "let2", "_", "_x", "x_1", "FN");
        tokens("0 00 12 1.25 -5 0..10 1..=3", "0", "00", "12", "1.25", "-", "5", "0", "..", "10", "1", "..=", "3");
        tokens("<<= << <= < >>= >> >= > != ! :: : && &= &", "<<=", "<<", "<=", "<", ">>=", ">>", ">=", ">", "!=", "!", "::", ":", "&&", "&=", "&");
        tokens("// final");
        tokens("// línea\r\nfn".replace("\\r", "\r").replace("\\n", "\n"), "fn");
        tokens("/**/fn/* a /* b */ c */main", "fn", "main");
        tokens("/* // \" $ */ + /", "+", "/");
        tokens("\"\" \"hola\"", "\"\"", "\"hola\"");
        tokens("\"a\\\"b\\\\c\\n\\t\\r\\0\"", "\"a\\\"b\\\\c\\n\\t\\r\\0\"");
        tokens("\"á漢😀\"", "\"á漢😀\"");
        error("/*", "sin cerrar");
        error("/* uno /* dos */", "sin cerrar");
        error("\"sin cierre", "Cadena");
        error("\"mal\\q\"", "Cadena");
        error("\"fin\\", "Cadena");
        for (String numero : new String[]{"12abc", "3.5x", "1_000", "0xff", "1e3", "5i32"}) error(numero, "Número");
        error("$", "Carácter");
        error("漢", "Carácter");
        tokens("\"primera\nsegunda\"", "\"primera\nsegunda\"");
        var posiciones = FisioLexerRunner.tokenize("a\t b\r\nc\nd\re").tokens();
        verificar(posiciones.get(1).columna() == 4 && posiciones.get(2).linea() == 2
            && posiciones.get(3).linea() == 3 && posiciones.get(4).linea() == 4, "Tabulaciones y CR/LF");
        var varios = FisioLexerRunner.tokenize("$ 12abc \"mal\\q\" fn");
        verificar(varios.errores().size() == 3 && varios.tokens().get(0).lexema().equals("fn"), "Errores acumulados");
        var recuperado = FisioLexerRunner.tokenize("\"mal\\q\"; let x = 2;");
        verificar(recuperado.errores().size() == 1 && recuperado.tokens().get(1).lexema().equals("let"), "Recuperación cadena");
        var posicion = FisioLexerRunner.tokenize("\n  /* abierto");
        verificar(posicion.errores().get(0).linea() == 2 && posicion.errores().get(0).columna() == 3, "Posición comentario");
        var unicode = FisioLexerRunner.tokenize("\"á漢😀\"");
        verificar(unicode.errores().isEmpty() && unicode.tokens().get(0).lexema().equals("\"á漢😀\""), "Unicode");
        verificar(!FisioSemanticRunner.analizar("fn main() {} /*").analizado(), "Comentario debe bloquear semántica");
        verificar(FisioSemanticRunner.analizar("fn main() { /* a /* b */ c */ }").exitoso(), "Comentario anidado en parser");
        System.out.println("LEXER PASS: " + casos + " comprobaciones");
    }
}
