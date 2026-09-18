package fisio.semantic;

import fisio.ast.*;
import fisio.token.CategoriaToken;
import fisio.token.TokenFisio;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dos pasadas: firmas globales, luego cuerpos con ambitos lexicos.
 * No ejecuta el programa ni intenta implementar ownership o genericos de Rust.
 */
final class AnalizadorSemantico {
    private record Tipo(String nombre, Tipo elemento) {
        Tipo(String nombre) { this(nombre, null); }
        boolean entero() { return ENTEROS.contains(nombre); }
        boolean decimal() { return nombre.equals("f32") || nombre.equals("f64"); }
        boolean numerico() { return entero() || decimal(); }
        boolean rango() { return elemento != null; }
        @Override public String toString() { return rango() ? "rango<" + elemento + ">" : nombre; }
    }
    private record Variable(Tipo tipo, boolean mutable) {}
    private record Firma(List<Tipo> parametros, Tipo retorno) {}

    private static final Set<String> ENTEROS = Set.of(
        "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize");
    private static final Tipo I32 = new Tipo("i32"), F64 = new Tipo("f64"), BOOL = new Tipo("bool"),
        STRING = new Tipo("String"), UNIDAD = new Tipo("()"), ERROR = new Tipo("<error>"),
        NUNCA = new Tipo("!");

    private final List<ErrorSemantico> errores = new ArrayList<>();
    private final Map<String, Firma> funciones = new LinkedHashMap<>();
    private final Map<NodoFuncion, Firma> firmas = new IdentityHashMap<>();
    private final Deque<Map<String, Variable>> ambitos = new ArrayDeque<>();
    private Tipo retornoActual;

    ResultadoSemantico analizar(NodoPrograma programa) {
        for (NodoAST nodo : programa.sentencias()) {
            if (!(nodo instanceof NodoFuncion f)) {
                error(nodo, "ESTRUCTURA", "Solo se admiten funciones en el nivel superior.");
                continue;
            }
            List<Tipo> parametros = new ArrayList<>();
            for (var p : f.parametros()) parametros.add(tipoAnotado(p.tipo()));
            Firma firma = new Firma(List.copyOf(parametros), f.tipoRetorno() == null ? UNIDAD : tipoAnotado(f.tipoRetorno()));
            firmas.put(f, firma);
            if (funciones.putIfAbsent(f.nombre().lexema(), firma) != null) {
                error(f, "FUNCION_DUPLICADA", "La funcion '" + f.nombre().lexema() + "' ya esta declarada.");
            }
        }
        for (NodoAST nodo : programa.sentencias()) {
            if (!(nodo instanceof NodoFuncion f)) continue;
            Firma firma = firmas.get(f);
            retornoActual = firma.retorno();
            ambitos.push(new LinkedHashMap<>());
            for (int i = 0; i < f.parametros().size(); i++) {
                var p = f.parametros().get(i);
                String nombre = p.nombre().lexema();
                if (!nombre.equals("_") && ambitos.peek().putIfAbsent(nombre, new Variable(firma.parametros().get(i), false)) != null) {
                    error(p, "PARAMETRO_DUPLICADO", "El parametro '" + nombre + "' esta repetido.");
                }
            }
            Tipo cuerpo = bloque(f.cuerpo(), retornoActual);
            exigir(f, retornoActual, cuerpo, "RETORNO", "Retorno de '" + f.nombre().lexema() + "'");
            ambitos.pop();
        }
        return new ResultadoSemantico(true, errores, "");
    }

    private Tipo tipoAnotado(TokenFisio token) {
        Tipo tipo = new Tipo(token.lexema());
        if (tipo.numerico() || tipo.equals(BOOL) || tipo.equals(STRING)) return tipo;
        errores.add(new ErrorSemantico("TIPO_DESCONOCIDO", "Tipo no soportado: '" + token.lexema() + "'.",
            token.linea(), token.columna()));
        return ERROR;
    }

    private Variable buscar(String nombre) {
        for (var ambito : ambitos) {
            Variable variable = ambito.get(nombre);
            if (variable != null) return variable;
        }
        return null;
    }

    private Tipo bloque(NodoBloque bloque, Tipo esperado) {
        ambitos.push(new LinkedHashMap<>());
        Tipo valor = UNIDAD;
        boolean termina = false;
        for (int i = 0; i < bloque.sentencias().size(); i++) {
            NodoAST sentencia = bloque.sentencias().get(i);
            boolean ultima = i == bloque.sentencias().size() - 1 && bloque.expresionFinal() == null;
            Tipo actual = sentencia(sentencia, ultima ? esperado : UNIDAD);
            // Las llamadas/operaciones en sentencias llevan ';': no son un retorno implicito.
            if (ultima && sentencia instanceof NodoIf) valor = actual;
            if (actual.equals(NUNCA)) termina = true;
        }
        if (bloque.expresionFinal() != null) valor = expresion(bloque.expresionFinal(), esperado);
        ambitos.pop();
        return termina ? NUNCA : valor;
    }

    private Tipo sentencia(NodoAST nodo, Tipo esperado) {
        if (nodo instanceof NodoDeclaracion n) {
            Tipo anotado = n.tipo() == null ? null : tipoAnotado(n.tipo());
            // Inicializador antes de declarar: permite shadowing y detecta autorreferencia.
            Tipo valor = expresion(n.expresion(), anotado);
            if (anotado != null) exigir(n, anotado, valor, "TIPO", "Inicializador de '" + n.identificador().lexema() + "'");
            if (!n.identificador().lexema().equals("_")) {
                ambitos.peek().put(n.identificador().lexema(), new Variable(anotado == null ? valor : anotado, n.mutable()));
            }
            return valor.equals(NUNCA) ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoAsignacion n) {
            if (!(n.lugar() instanceof NodoIdentificador id)) {
                error(n, "DESTINO", "El destino no es una variable asignable.");
                expresion(n.expresion(), null);
                return UNIDAD;
            }
            String nombre = id.token().lexema();
            Variable variable = buscar(nombre);
            Tipo valor = expresion(n.expresion(), variable == null ? null : variable.tipo());
            if (variable == null) error(id, "VARIABLE_NO_DECLARADA", "Variable no declarada: '" + nombre + "'.");
            else {
                if (!variable.mutable()) error(n, "INMUTABLE", "No se puede modificar '" + nombre + "': se declaro sin 'mut'.");
                if (n.operador().lexema().equals("=")) exigir(n, variable.tipo(), valor, "TIPO", "Asignacion a '" + nombre + "'");
                else {
                    operador(n, n.operador().lexema(), variable.tipo(), valor);
                    comprobarCero(n, n.operador().lexema(), n.expresion());
                }
            }
            return UNIDAD;
        }
        if (nodo instanceof NodoReturn n) {
            Tipo valor = n.expresion() == null ? UNIDAD : expresion(n.expresion(), retornoActual);
            exigir(n, retornoActual, valor, "RETORNO", "Sentencia return");
            return NUNCA;
        }
        if (nodo instanceof NodoIf n) return condicional(n, esperado);
        if (nodo instanceof NodoWhile n) {
            exigir(n.condicion(), BOOL, expresion(n.condicion(), BOOL), "CONDICION", "Condicion de while");
            exigir(n.cuerpo(), UNIDAD, bloque(n.cuerpo(), UNIDAD), "TIPO", "Cuerpo de while");
            return esTrue(n.condicion()) ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoFor n) {
            Tipo iterable = expresion(n.iterable(), null);
            if (!iterable.equals(ERROR) && !iterable.rango()) error(n.iterable(), "ITERABLE", "for necesita un rango entero; se encontro " + iterable + ".");
            ambitos.push(new LinkedHashMap<>());
            if (!n.variable().lexema().equals("_")) ambitos.peek().put(n.variable().lexema(), new Variable(iterable.rango() ? iterable.elemento() : ERROR, false));
            exigir(n.cuerpo(), UNIDAD, bloque(n.cuerpo(), UNIDAD), "TIPO", "Cuerpo de for");
            ambitos.pop();
            return UNIDAD;
        }
        if (nodo instanceof NodoLoop n) {
            exigir(n.cuerpo(), UNIDAD, bloque(n.cuerpo(), UNIDAD), "TIPO", "Cuerpo de loop");
            return NUNCA; // La gramatica no admite break: loop no continua normalmente.
        }
        return expresion(nodo, null);
    }

    private Tipo condicional(NodoIf n, Tipo esperado) {
        exigir(n.condicion(), BOOL, expresion(n.condicion(), BOOL), "CONDICION", "Condicion de if");
        Tipo si = bloque(n.cuerpoIf(), esperado);
        Tipo no = n.cuerpoElse() instanceof NodoIf otro ? condicional(otro, esperado)
            : n.cuerpoElse() instanceof NodoBloque b ? bloque(b, esperado) : UNIDAD;
        Tipo resultado;
        if (si.equals(NUNCA)) resultado = no;
        else if (no.equals(NUNCA)) resultado = si;
        else if (si.equals(ERROR) || no.equals(ERROR)) resultado = ERROR;
        else if (!si.equals(no)) {
            error(n, "RAMAS", "Las ramas de if/else producen tipos distintos: " + si + " y " + no + ".");
            resultado = ERROR;
        } else resultado = si;
        if (UNIDAD.equals(esperado)) exigir(n, UNIDAD, resultado, "TIPO", "Condicional usado como sentencia");
        return resultado;
    }

    private Tipo expresion(NodoAST nodo, Tipo esperado) {
        if (nodo instanceof NodoNumero n) return literal(n, esperado, false);
        if (nodo instanceof NodoIdentificador n) {
            Variable variable = buscar(n.token().lexema());
            if (variable != null) return variable.tipo();
            error(n, "VARIABLE_NO_DECLARADA", "Variable no declarada: '" + n.token().lexema() + "'.");
            return ERROR;
        }
        if (nodo instanceof NodoAgrupacion n) return expresion(n.expresion(), esperado);
        if (nodo instanceof NodoOperacionUnaria n) {
            String op = n.operador().lexema();
            NodoAST operando = n.operando();
            while (operando instanceof NodoAgrupacion grupo) operando = grupo.expresion();
            if (op.equals("-") && operando instanceof NodoNumero literal && literal.token().categoria() == CategoriaToken.NUM) {
                Tipo tipo = literal(literal, esperado, true);
                return tipo;
            }
            Tipo tipo = expresion(n.operando(), op.equals("!") ? BOOL : esperado);
            if (tipo.equals(ERROR)) return ERROR;
            if (op.equals("!") && tipo.equals(BOOL)) return BOOL;
            if (op.equals("-") && tipo.numerico() && !tipo.nombre().startsWith("u")) return tipo;
            error(n, "OPERADOR", "El operador '" + op + "' no admite " + tipo + ".");
            return ERROR;
        }
        if (nodo instanceof NodoOperacion n) {
            Tipo contexto = n.operador().lexema().equals("&&") || n.operador().lexema().equals("||") ? BOOL : esperado;
            Tipo[] tipos = operandos(n.izquierda(), n.derecha(), contexto);
            comprobarCero(n, n.operador().lexema(), n.derecha());
            return operador(n, n.operador().lexema(), tipos[0], tipos[1]);
        }
        if (nodo instanceof NodoOperacionRelacional n) {
            Tipo[] tipos = operandos(n.izquierda(), n.derecha(), null);
            return operador(n, n.operador().lexema(), tipos[0], tipos[1]);
        }
        if (nodo instanceof NodoRango n) {
            Tipo[] tipos = operandos(n.inicio(), n.fin(), esperado != null && esperado.rango() ? esperado.elemento() : null);
            if (tipos[0].equals(ERROR) || tipos[1].equals(ERROR)) return ERROR;
            if (!tipos[0].entero() || !tipos[0].equals(tipos[1])) {
                error(n, "RANGO", "Los extremos del rango deben ser enteros del mismo tipo; se encontro " + tipos[0] + " y " + tipos[1] + ".");
                return ERROR;
            }
            return new Tipo("rango", tipos[0]);
        }
        if (nodo instanceof NodoLlamadaFuncion n) return llamada(n);
        if (nodo instanceof NodoInvocacionMacro n) return macro(n);
        error(nodo, "ESTRUCTURA", "Nodo no soportado por el analizador semantico: " + nodo.getClass().getSimpleName() + ".");
        return ERROR;
    }

    /** Contexto solo para literales sin sufijo, nunca convierte variables de otro tipo. */
    private Tipo[] operandos(NodoAST izquierda, NodoAST derecha, Tipo esperado) {
        Tipo contexto = pista(izquierda);
        if (contexto == null) contexto = pista(derecha);
        if (contexto == null) contexto = esperado;
        Tipo a = expresion(izquierda, contexto);
        Tipo b = expresion(derecha, a.equals(ERROR) ? contexto : a);
        return new Tipo[]{a, b};
    }

    private Tipo pista(NodoAST nodo) {
        if (nodo instanceof NodoIdentificador n) { var v = buscar(n.token().lexema()); return v == null ? null : v.tipo(); }
        if (nodo instanceof NodoLlamadaFuncion n) { var f = funciones.get(n.nombre().lexema()); return f == null ? null : f.retorno(); }
        if (nodo instanceof NodoAgrupacion n) return pista(n.expresion());
        if (nodo instanceof NodoOperacionUnaria n) return n.operador().lexema().equals("!") ? BOOL : pista(n.operando());
        if (nodo instanceof NodoOperacion n) { Tipo t = pista(n.izquierda()); return t == null ? pista(n.derecha()) : t; }
        return null;
    }

    private Tipo literal(NodoNumero n, Tipo esperado, boolean negativo) {
        TokenFisio token = n.token();
        if (token.categoria() == CategoriaToken.STRING) return STRING;
        if (token.lexema().equals("true") || token.lexema().equals("false")) return BOOL;
        if (token.lexema().contains(".")) {
            Tipo tipo = esperado != null && esperado.decimal() ? esperado : F64;
            double valor = tipo.nombre().equals("f32") ? Float.parseFloat(token.lexema()) : Double.parseDouble(token.lexema());
            if (!Double.isFinite(valor)) error(n, "LITERAL_FUERA_DE_RANGO", "Literal fuera del rango de " + tipo + ".");
            return tipo;
        }
        Tipo tipo = esperado != null && esperado.entero() ? esperado : I32;
        BigInteger valor = new BigInteger(token.lexema());
        if (negativo) valor = valor.negate();
        boolean unsigned = tipo.nombre().startsWith("u");
        int bits = tipo.nombre().endsWith("size") ? 64 : Integer.parseInt(tipo.nombre().substring(1));
        BigInteger max = BigInteger.ONE.shiftLeft(unsigned ? bits : bits - 1).subtract(BigInteger.ONE);
        BigInteger min = unsigned ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
        if (valor.compareTo(min) < 0 || valor.compareTo(max) > 0 || (negativo && unsigned)) {
            error(n, "LITERAL_FUERA_DE_RANGO", "Literal fuera del rango de " + tipo + ".");
        }
        return tipo;
    }

    private Tipo operador(NodoAST nodo, String op, Tipo a, Tipo b) {
        if (a.equals(ERROR) || b.equals(ERROR)) return ERROR;
        if (a.equals(NUNCA) || b.equals(NUNCA)) return NUNCA;
        boolean iguales = a.equals(b);
        String operacion = Set.of("+=", "-=", "*=", "/=", "%=").contains(op) ? op.substring(0, 1) : op;
        switch (operacion) {
            case "+", "-", "*", "/", "%": if (iguales && a.numerico()) return a; break;
            case "&&", "||": if (a.equals(BOOL) && b.equals(BOOL)) return BOOL; break;
            case "==", "!=": if (iguales && (a.numerico() || a.equals(BOOL) || a.equals(STRING) || a.equals(UNIDAD))) return BOOL; break;
            case "<", ">", "<=", ">=": if (iguales && (a.numerico() || a.equals(STRING))) return BOOL; break;
            default: break;
        }
        error(nodo, "OPERADOR", "El operador '" + op + "' no admite " + a + " y " + b + ".");
        return ERROR;
    }

    private Tipo llamada(NodoLlamadaFuncion n) {
        Firma firma = funciones.get(n.nombre().lexema());
        Variable variable = buscar(n.nombre().lexema());
        if (variable != null) {
            error(n, "NO_INVOCABLE", "'" + n.nombre().lexema() + "' es una variable, no una funcion.");
            firma = null;
        } else if (firma == null) error(n, "FUNCION_NO_DECLARADA", "Funcion no declarada: '" + n.nombre().lexema() + "'.");
        if (firma != null && n.argumentos().size() != firma.parametros().size()) {
            error(n, "ARIDAD", "'" + n.nombre().lexema() + "' espera " + firma.parametros().size() + " argumento(s), pero recibio " + n.argumentos().size() + ".");
        }
        for (int i = 0; i < n.argumentos().size(); i++) {
            Tipo esperado = firma != null && i < firma.parametros().size() ? firma.parametros().get(i) : null;
            Tipo actual = expresion(n.argumentos().get(i), esperado);
            if (esperado != null) exigir(n.argumentos().get(i), esperado, actual, "ARGUMENTO", "Argumento " + (i + 1) + " de '" + n.nombre().lexema() + "'");
        }
        return firma == null ? ERROR : firma.retorno();
    }

    private Tipo macro(NodoInvocacionMacro n) {
        List<Tipo> tipos = new ArrayList<>();
        for (NodoAST argumento : n.argumentos()) tipos.add(expresion(argumento, null));
        if (!n.nombre().lexema().equals("println")) {
            error(n, "MACRO_DESCONOCIDA", "Macro no soportada: '" + n.nombre().lexema() + "!'.");
            return ERROR;
        }
        if (n.argumentos().isEmpty()) return UNIDAD;
        for (int i = 1; i < tipos.size(); i++) {
            Tipo tipo = tipos.get(i);
            if (!tipo.equals(ERROR) && !tipo.equals(NUNCA) && !(tipo.numerico() || tipo.equals(BOOL) || tipo.equals(STRING))) {
                error(n.argumentos().get(i), "FORMATO_TIPO", "println! no puede mostrar un valor de tipo " + tipo + " con {}.");
            }
        }
        NodoAST formato = n.argumentos().get(0);
        if (!(formato instanceof NodoNumero literal) || literal.token().categoria() != CategoriaToken.STRING) {
            error(formato, "FORMATO", "El primer argumento de println! debe ser una cadena literal de formato.");
            return UNIDAD;
        }
        String texto = literal.token().lexema();
        int huecos = 0;
        for (int i = 1; i < texto.length() - 1; i++) {
            char c = texto.charAt(i);
            if (c != '{' && c != '}') continue;
            if (i + 1 < texto.length() - 1 && texto.charAt(i + 1) == c) { i++; continue; }
            if (c == '{' && i + 1 < texto.length() - 1 && texto.charAt(i + 1) == '}') { huecos++; i++; continue; }
            error(formato, "FORMATO", "Formato invalido o no soportado: usa {}, {{ o }} en println!.");
            return UNIDAD;
        }
        if (huecos != n.argumentos().size() - 1) error(n, "FORMATO_ARIDAD", "println! contiene " + huecos + " marcador(es) {} y recibio " + (n.argumentos().size() - 1) + " valor(es).");
        return UNIDAD;
    }

    private void comprobarCero(NodoAST nodo, String operador, NodoAST divisor) {
        if (!(operador.equals("/") || operador.equals("%") || operador.equals("/=") || operador.equals("%="))) return;
        BigDecimal constante = constante(divisor);
        if (constante != null && constante.signum() == 0) error(nodo, "DIVISION_CERO", "Division o resto entre una expresion constante igual a cero.");
    }

    /** Solo expresiones constantes: no simula variables, llamadas ni ciclos. */
    private BigDecimal constante(NodoAST nodo) {
        if (nodo instanceof NodoNumero n && n.token().categoria() == CategoriaToken.NUM) return new BigDecimal(n.token().lexema());
        if (nodo instanceof NodoAgrupacion n) return constante(n.expresion());
        if (nodo instanceof NodoOperacionUnaria n && n.operador().lexema().equals("-")) { var v = constante(n.operando()); return v == null ? null : v.negate(); }
        if (nodo instanceof NodoOperacion n) {
            BigDecimal a = constante(n.izquierda()), b = constante(n.derecha());
            if (a == null || b == null) return null;
            return switch (n.operador().lexema()) {
                case "+" -> a.add(b); case "-" -> a.subtract(b); case "*" -> a.multiply(b); default -> null;
            };
        }
        return null;
    }

    private boolean esTrue(NodoAST nodo) {
        if (nodo instanceof NodoAgrupacion n) return esTrue(n.expresion());
        return nodo instanceof NodoNumero n && n.token().lexema().equals("true");
    }

    private void exigir(NodoAST nodo, Tipo esperado, Tipo actual, String codigo, String contexto) {
        if (esperado.equals(ERROR) || actual.equals(ERROR) || actual.equals(NUNCA) || esperado.equals(actual)) return;
        error(nodo, codigo, contexto + ": se esperaba " + esperado + ", se encontro " + actual + ".");
    }

    private void error(NodoAST nodo, String codigo, String mensaje) {
        errores.add(new ErrorSemantico(codigo, mensaje, nodo.linea(), nodo.columna()));
    }
}
