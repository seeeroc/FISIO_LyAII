package fisio.analysis;

import fisio.ast.*;
import java.util.List;

/** Regresión sintáctica independiente del análisis de tipos. */
public final class ParserRegression {
    private static int casos;
    private static void comprobar(boolean condicion, String mensaje) {
        casos++;
        if (!condicion) throw new AssertionError(mensaje);
    }
    private static ResultadoSintactico analizar(String codigo) {
        comprobar(FisioLexerRunner.tokenize(codigo).errores().isEmpty(), "Caso no léxico: " + codigo);
        return FisioParserRunner.parse(codigo);
    }
    private static void valido(String codigo) {
        var r = analizar(codigo);
        comprobar(r.errores().isEmpty(), codigo + ": " + r.errores());
    }
    private static void invalido(String codigo) {
        var r = analizar(codigo);
        comprobar(!r.errores().isEmpty(), "Aceptado incorrectamente: " + codigo);
    }
    private static NodoBloque bloque(String cuerpo) {
        var r = analizar("fn f() {" + cuerpo + "}");
        comprobar(r.errores().isEmpty(), r.errores().toString());
        return ((NodoFuncion) r.ast().sentencias().get(0)).cuerpo();
    }
    public static void main(String[] args) {
        for (String cuerpo : List.of("", "let x = 1;", "let mut x: i32 = 1; x += 2;", "f();", "println!(\"{}\", 1);",
                "1", "x", "x + (1)", "-f(1)", "1;", "(1 + 2);", "!true;", "f(1 + 2, g(3));",
                "if true {} else if false {} else {}", "while true {}", "for i in 0..10 {}", "loop {}", "return;", "return 1;"))
            valido("fn f() {" + cuerpo + "}");
        for (String cuerpo : List.of("1 2", "1 let x = 2;", "x y", "x = 1", "let = 1;", "let x 1;",
                "let x = ;", "x += ;", "f(,);", "f(1,);", "f(1 2);", "f(1;", "(1 + 2;",
                "if {}", "while x > {}", "for i in 0.. {}", "return 1", "break;", "struct A {}", "let x = 1 + ;"))
            invalido("fn f() {" + cuerpo + "}");
        valido("fn suma(a: i32, b: i32) -> i32 { a + b } fn main() { suma(1, 2); }");
        for (String codigo : List.of("fn", "fn () {}", "fn f(a) {}", "fn f(a:) {}", "fn f(a: i32,) {}",
                "fn f( {}", "fn f() -> {}", "fn f()", "fn f() {", "fn f() {} basura")) invalido(codigo);
        var expresiones = bloque("1; 2; 3");
        comprobar(expresiones.sentencias().size() == 2 && expresiones.expresionFinal() instanceof NodoNumero, "Expresiones descartadas");
        var aritmetica = (NodoOperacion) bloque("1 + 2 * 3").expresionFinal();
        comprobar(aritmetica.operador().lexema().equals("+") && aritmetica.derecha() instanceof NodoOperacion,
            "Precedencia multiplicativa");
        var asociacion = (NodoOperacion) bloque("8 - 3 - 1").expresionFinal();
        comprobar(asociacion.izquierda() instanceof NodoOperacion, "Asociatividad izquierda");
        for (String prefijo : List.of("basura ", "fn rota( ", "fn rota() { let = 1; ", "fn rota(a) { if true {} } ")) {
            var r = analizar(prefijo + "fn sana() {}");
            comprobar(!r.errores().isEmpty() && r.ast().sentencias().stream().anyMatch(n ->
                n instanceof NodoFuncion f && f.nombre().lexema().equals("sana")), "Se perdió función siguiente: " + prefijo);
        }
        var r = analizar("fn f() {\n  let x = 1;\n\n");
        var ultimo = r.errores().get(r.errores().size() - 1);
        comprobar(ultimo.linea() == 4 && ultimo.columna() == 1, "Posición EOF: " + ultimo);
        comprobar(!((NodoRango) bloque("0..3").expresionFinal()).inclusivo(), "Rango exclusivo");
        comprobar(((NodoRango) bloque("0..=3").expresionFinal()).inclusivo(), "Rango inclusivo perdido");
        var recuperado = analizar("fn f() { let x = 1 let y = 2; return; }");
        comprobar(!recuperado.errores().isEmpty() && ((NodoFuncion) recuperado.ast().sentencias().get(0)).cuerpo().sentencias().size() == 3,
            "Se perdió declaración tras punto y coma ausente");
        var logica = (NodoOperacion) bloque("true || false && !true").expresionFinal();
        comprobar(logica.operador().lexema().equals("||") && ((NodoOperacion) logica.derecha()).operador().lexema().equals("&&"), "Precedencia lógica");
        for (String op : List.of("=", "+=", "-=", "*=", "/=", "%=")) {
            var asignacion = (NodoAsignacion) bloque("x " + op + " 1;").sentencias().get(0);
            comprobar(asignacion.operador().lexema().equals(op), "Operador de asignación perdido");
        }
        // Entradas léxicamente válidas y sintácticamente arbitrarias: recuperación sin excepciones.
        var azar = new java.util.Random(731);
        String[] piezas = {"let", "mut", "x", "1", "fn", "(", ")", "{", "}", ";", ",", ":", "=", "+", "if", "else", "return", "for", "in", "while", "!", ".."};
        for (int i = 0; i < 1000; i++) {
            StringBuilder codigo = new StringBuilder("fn f() { ");
            for (int j = 0; j < 18; j++) codigo.append(piezas[azar.nextInt(piezas.length)]).append(' ');
            codigo.append("} fn conservada() {}");
            var resultado = analizar(codigo.toString());
            comprobar(resultado.ast().sentencias().stream().anyMatch(n -> n instanceof NodoFuncion f
                && f.nombre().lexema().equals("conservada")), "Recuperación aleatoria: " + codigo);
        }
        System.out.println("PARSER PASS: " + casos + " comprobaciones");
    }
}
