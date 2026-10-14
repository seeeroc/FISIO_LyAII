package fisio.ci;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import fisio.ast.*;
import fisio.token.TokenFisio;

/** Genera notaciones, cuádruplos y una resolución numérica acotada desde el AST. */
public final class GeneradorCodigoIntermedio {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private final List<ExpresionIntermedia> expresiones = new ArrayList<>();
    private final List<ProblemaCodigoIntermedio> problemas = new ArrayList<>();

    private GeneradorCodigoIntermedio() {
    }

    public static ResultadoCodigoIntermedio generar(NodoPrograma programa) {
        Objects.requireNonNull(programa, "programa");
        GeneradorCodigoIntermedio generador = new GeneradorCodigoIntermedio();
        generador.procesarPrograma(programa);
        return new ResultadoCodigoIntermedio(true, generador.expresiones, generador.problemas, "");
    }

    private void procesarPrograma(NodoPrograma programa) {
        Ambito global = new Ambito(null);
        for (NodoAST sentencia : programa.sentencias()) {
            procesarNodo(sentencia, global, "Expresión de nivel superior");
        }
    }

    private void procesarNodo(NodoAST nodo, Ambito ambito, String contexto) {
        if (nodo == null) return;
        if (nodo instanceof NodoFuncion funcion) {
            Ambito local = new Ambito(ambito);
            funcion.parametros().forEach(p -> local.declarar(p.nombre().lexema(), null, tipoEntero(p.tipo().lexema())));
            procesarBloque(funcion.cuerpo(), local, "Función " + funcion.nombre().lexema());
        } else if (nodo instanceof NodoBloque bloque) {
            procesarBloque(bloque, ambito, contexto);
        } else if (nodo instanceof NodoDeclaracion declaracion) {
            String nombre = declaracion.identificador().lexema();
            procesarValor(declaracion.expresion(), ambito, "Inicializador de " + nombre);
            ValorNumerico valor = evaluar(declaracion.expresion(), ambito);
            String tipo = declaracion.tipo() == null ? null : declaracion.tipo().lexema();
            ValorNumerico ajustado = ajustarTipo(valor, tipo);
            ambito.declarar(nombre, ajustado, tipo == null ? tipoDe(ajustado) : tipoEntero(tipo));
        } else if (nodo instanceof NodoAsignacion asignacion) {
            String nombre = asignacion.lugar() instanceof NodoIdentificador id ? id.token().lexema() : "destino";
            if (!"=".equals(asignacion.operador().lexema()) && asignacion.lugar() instanceof NodoIdentificador) {
                TokenFisio compuesto = asignacion.operador();
                TokenFisio simple = new TokenFisio(compuesto.lexema().substring(0, 1), compuesto.categoria(),
                    compuesto.idFisio(), compuesto.linea(), compuesto.columna());
                procesarValor(new NodoOperacion(asignacion.linea(), asignacion.columna(), simple,
                    asignacion.lugar(), asignacion.expresion()), ambito, "Asignación compuesta a " + nombre);
            } else {
                procesarValor(asignacion.expresion(), ambito, "Asignación a " + nombre);
            }
            actualizarAsignacion(asignacion, ambito);
        } else if (nodo instanceof NodoReturn retorno) {
            procesarValor(retorno.expresion(), ambito, "Valor de retorno");
        } else if (nodo instanceof NodoIf condicional) {
            procesarValor(condicional.condicion(), ambito, "Condición de if");
            procesarBloque(condicional.cuerpoIf(), ambito.copia(), "Bloque if");
            procesarNodo(condicional.cuerpoElse(), ambito.copia(), "Bloque else");
            ambito.invalidarValores();
        } else if (nodo instanceof NodoWhile ciclo) {
            procesarValor(ciclo.condicion(), ambito, "Condición de while");
            procesarBloque(ciclo.cuerpo(), ambito.sinValores(), "Cuerpo de while");
            ambito.invalidarValores();
        } else if (nodo instanceof NodoFor ciclo) {
            procesarValor(ciclo.iterable(), ambito, "Iterable de for");
            Ambito local = ambito.sinValores();
            local.declarar(ciclo.variable().lexema(), null, null);
            procesarBloque(ciclo.cuerpo(), local, "Cuerpo de for");
            ambito.invalidarValores();
        } else if (nodo instanceof NodoLoop ciclo) {
            procesarBloque(ciclo.cuerpo(), ambito.sinValores(), "Cuerpo de loop");
            ambito.invalidarValores();
        } else {
            procesarValor(nodo, ambito, contexto);
        }
    }

    private void procesarBloque(NodoBloque bloque, Ambito padre, String contexto) {
        if (bloque == null) return;
        Ambito local = new Ambito(padre);
        for (NodoAST sentencia : bloque.sentencias()) procesarNodo(sentencia, local, contexto);
        procesarValor(bloque.expresionFinal(), local, "Expresión final de " + contexto.toLowerCase());
    }

    private void procesarValor(NodoAST nodo, Ambito ambito, String contexto) {
        if (nodo == null) return;
        if (esRaizAritmetica(nodo)) {
            generarExpresion(nodo, ambito, contexto);
            return;
        }
        if (nodo instanceof NodoAgrupacion grupo) procesarValor(grupo.expresion(), ambito, contexto);
        else if (nodo instanceof NodoOperacionRelacional operacion) {
            procesarValor(operacion.izquierda(), ambito, contexto);
            procesarValor(operacion.derecha(), ambito, contexto);
        } else if (nodo instanceof NodoOperacionUnaria unaria) {
            procesarValor(unaria.operando(), ambito, contexto);
        } else if (nodo instanceof NodoRango rango) {
            procesarValor(rango.inicio(), ambito, contexto);
            procesarValor(rango.fin(), ambito, contexto);
        } else if (nodo instanceof NodoLlamadaFuncion llamada) {
            llamada.argumentos().forEach(a -> procesarValor(a, ambito, "Argumento de " + llamada.nombre().lexema()));
        } else if (nodo instanceof NodoInvocacionMacro macro) {
            macro.argumentos().forEach(a -> procesarValor(a, ambito, "Argumento de " + macro.nombre().lexema() + "!"));
        }
    }

    private void generarExpresion(NodoAST nodo, Ambito ambito, String contexto) {
        ConstructorCuadruplos constructor = new ConstructorCuadruplos(nodo);
        String simboloFinal = constructor.construir(nodo);
        Resolucion resolucion = resolver(constructor.cuadruplos, simboloFinal, ambito);
        if (resolucion.problema != null) {
            problemas.add(new ProblemaCodigoIntermedio(nodo.linea(), nodo.columna(), resolucion.problema));
        }
        expresiones.add(new ExpresionIntermedia(
            contexto, nodo.linea(), nodo.columna(), infija(nodo), postfija(nodo), prefija(nodo),
            constructor.cuadruplos, resolucion.pasos,
            resolucion.valor == null ? null : resolucion.valor.texto(), resolucion.detalle));
    }

    private Resolucion resolver(List<Cuadruplo> cuadruplos, String simboloFinal, Ambito ambito) {
        Map<String, ValorNumerico> temporales = new LinkedHashMap<>();
        List<PasoResolucion> pasos = new ArrayList<>();
        String problema = null;
        for (Cuadruplo cuadruplo : cuadruplos) {
            ValorNumerico primero = resolverOperando(cuadruplo.argumento1(), temporales, ambito);
            ValorNumerico segundo = cuadruplo.argumento2().isBlank()
                ? null : resolverOperando(cuadruplo.argumento2(), temporales, ambito);
            String operacion = "NEG".equals(cuadruplo.operador())
                ? cuadruplo.resultado() + " = NEG " + cuadruplo.argumento1()
                : cuadruplo.resultado() + " = " + cuadruplo.argumento1() + " "
                    + cuadruplo.operador() + " " + cuadruplo.argumento2();
            if (primero == null || (!"NEG".equals(cuadruplo.operador()) && segundo == null)) {
                String faltantes = operandosDesconocidos(cuadruplo, primero, segundo);
                pasos.add(new PasoResolucion(cuadruplo.numero(), operacion,
                    "No se puede resolver todavía: " + faltantes + " no tiene un valor numérico conocido."));
                temporales.put(cuadruplo.resultado(), null);
                continue;
            }
            if (("/".equals(cuadruplo.operador()) || "%".equals(cuadruplo.operador()))
                    && segundo.valor.compareTo(BigDecimal.ZERO) == 0) {
                problema = ("/".equals(cuadruplo.operador()) ? "División" : "Módulo") + " entre cero en " + operacion + ".";
                pasos.add(new PasoResolucion(cuadruplo.numero(), operacion, problema));
                temporales.put(cuadruplo.resultado(), null);
                continue;
            }
            ValorNumerico valor = operar(cuadruplo.operador(), primero, segundo);
            temporales.put(cuadruplo.resultado(), valor);
            String sustitucion = "NEG".equals(cuadruplo.operador())
                ? cuadruplo.resultado() + " = -" + primero.texto() + " = " + valor.texto()
                : cuadruplo.resultado() + " = " + primero.texto() + " " + cuadruplo.operador() + " "
                    + segundo.texto() + " = " + valor.texto();
            pasos.add(new PasoResolucion(cuadruplo.numero(), operacion, "Sustitución: " + sustitucion));
        }
        ValorNumerico finalCalculado = resolverOperando(simboloFinal, temporales, ambito);
        String detalle;
        if (problema != null) detalle = problema;
        else if (finalCalculado != null) detalle = "Resultado numérico obtenido con los valores conocidos: " + finalCalculado.texto() + ".";
        else detalle = "No es posible obtener un resultado numérico definitivo con los valores conocidos en este punto.";
        return new Resolucion(finalCalculado, pasos, detalle, problema);
    }

    private static String operandosDesconocidos(Cuadruplo c, ValorNumerico primero, ValorNumerico segundo) {
        if (primero == null && segundo == null && !c.argumento2().isBlank()) return "'" + c.argumento1() + "' y '" + c.argumento2() + "'";
        return "'" + (primero == null ? c.argumento1() : c.argumento2()) + "'";
    }

    private static ValorNumerico resolverOperando(String simbolo, Map<String, ValorNumerico> temporales, Ambito ambito) {
        if (temporales.containsKey(simbolo)) return temporales.get(simbolo);
        if (ambito.contiene(simbolo)) return ambito.obtener(simbolo);
        try {
            return new ValorNumerico(new BigDecimal(simbolo), !simbolo.contains("."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static ValorNumerico evaluar(NodoAST nodo, Ambito ambito) {
        if (nodo == null) return null;
        if (nodo instanceof NodoNumero numero) {
            String texto = numero.token().lexema();
            try { return new ValorNumerico(new BigDecimal(texto), !texto.contains(".")); }
            catch (NumberFormatException ex) { return null; }
        }
        if (nodo instanceof NodoIdentificador identificador) return ambito.obtener(identificador.token().lexema());
        if (nodo instanceof NodoAgrupacion grupo) return evaluar(grupo.expresion(), ambito);
        if (nodo instanceof NodoOperacionUnaria unaria && "-".equals(unaria.operador().lexema())) {
            ValorNumerico valor = evaluar(unaria.operando(), ambito);
            return valor == null ? null : new ValorNumerico(valor.valor.negate(PRECISION), valor.entero);
        }
        if (nodo instanceof NodoOperacion operacion && esOperadorAritmetico(operacion.operador().lexema())) {
            ValorNumerico izquierda = evaluar(operacion.izquierda(), ambito);
            ValorNumerico derecha = evaluar(operacion.derecha(), ambito);
            if (izquierda == null || derecha == null) return null;
            if (("/".equals(operacion.operador().lexema()) || "%".equals(operacion.operador().lexema()))
                    && derecha.valor.compareTo(BigDecimal.ZERO) == 0) return null;
            return operar(operacion.operador().lexema(), izquierda, derecha);
        }
        return null;
    }

    private static ValorNumerico operar(String operador, ValorNumerico a, ValorNumerico b) {
        if ("NEG".equals(operador)) return new ValorNumerico(a.valor.negate(PRECISION), a.entero);
        boolean entero = a.entero && b.entero;
        BigDecimal valor = switch (operador) {
            case "+" -> a.valor.add(b.valor, PRECISION);
            case "-" -> a.valor.subtract(b.valor, PRECISION);
            case "*" -> a.valor.multiply(b.valor, PRECISION);
            case "/" -> entero ? a.valor.divideToIntegralValue(b.valor) : a.valor.divide(b.valor, PRECISION);
            case "%" -> a.valor.remainder(b.valor, PRECISION);
            default -> throw new IllegalArgumentException("Operador aritmético no soportado: " + operador);
        };
        return new ValorNumerico(valor, entero);
    }

    private static ValorNumerico ajustarTipo(ValorNumerico valor, String tipo) {
        if (valor == null || tipo == null) return valor;
        Boolean entero = tipoEntero(tipo);
        return entero == null ? valor : new ValorNumerico(valor.valor, entero);
    }

    private static Boolean tipoEntero(String tipo) {
        if (tipo == null) return null;
        if (tipo.startsWith("i") || tipo.startsWith("u")) return true;
        if (tipo.startsWith("f")) return false;
        return null;
    }

    private static Boolean tipoDe(ValorNumerico valor) {
        return valor == null ? null : valor.entero;
    }

    private static void actualizarAsignacion(NodoAsignacion asignacion, Ambito ambito) {
        if (!(asignacion.lugar() instanceof NodoIdentificador id)) return;
        String nombre = id.token().lexema();
        ValorNumerico derecha = evaluar(asignacion.expresion(), ambito);
        String operador = asignacion.operador().lexema();
        if ("=".equals(operador)) {
            Boolean tipo = ambito.tipo(nombre);
            if (derecha != null && tipo != null) derecha = new ValorNumerico(derecha.valor, tipo);
            ambito.asignar(nombre, derecha);
            return;
        }
        ValorNumerico izquierda = ambito.obtener(nombre);
        String simple = operador.substring(0, 1);
        if (izquierda == null || derecha == null
                || (("/".equals(simple) || "%".equals(simple)) && derecha.valor.compareTo(BigDecimal.ZERO) == 0)) {
            ambito.asignar(nombre, null);
        } else {
            ambito.asignar(nombre, operar(simple, izquierda, derecha));
        }
    }

    private static boolean esRaizAritmetica(NodoAST nodo) {
        if (nodo instanceof NodoOperacion operacion) return esOperadorAritmetico(operacion.operador().lexema());
        if (nodo instanceof NodoOperacionUnaria unaria) return "-".equals(unaria.operador().lexema());
        return nodo instanceof NodoAgrupacion grupo && esRaizAritmetica(grupo.expresion());
    }

    private static boolean esOperadorAritmetico(String operador) {
        return "+".equals(operador) || "-".equals(operador) || "*".equals(operador)
            || "/".equals(operador) || "%".equals(operador);
    }

    private static String infija(NodoAST nodo) {
        return infija(nodo, 0, false, "");
    }

    private static String infija(NodoAST nodo, int precedenciaPadre, boolean derecho, String operadorPadre) {
        if (nodo instanceof NodoNumero numero) return numero.token().lexema();
        if (nodo instanceof NodoIdentificador identificador) return identificador.token().lexema();
        if (nodo instanceof NodoAgrupacion grupo) return "(" + infija(grupo.expresion(), 0, false, "") + ")";
        if (nodo instanceof NodoOperacionUnaria unaria) {
            String texto = "-" + infija(unaria.operando(), 3, true, "NEG");
            return 3 < precedenciaPadre ? "(" + texto + ")" : texto;
        }
        if (nodo instanceof NodoOperacion operacion) {
            String operador = operacion.operador().lexema();
            int precedencia = ("*".equals(operador) || "/".equals(operador) || "%".equals(operador)) ? 2 : 1;
            String texto = infija(operacion.izquierda(), precedencia, false, operador) + " " + operador + " "
                + infija(operacion.derecha(), precedencia, true, operador);
            boolean parentesis = precedencia < precedenciaPadre
                || (derecho && precedencia == precedenciaPadre && requiereParentesisDerecho(operadorPadre, operador));
            return parentesis ? "(" + texto + ")" : texto;
        }
        return "?";
    }

    private static boolean requiereParentesisDerecho(String padre, String hijo) {
        return "-".equals(padre) || "/".equals(padre) || "%".equals(padre)
            || ("+".equals(padre) && "-".equals(hijo))
            || ("*".equals(padre) && ("/".equals(hijo) || "%".equals(hijo)));
    }

    private static String postfija(NodoAST nodo) {
        if (nodo instanceof NodoNumero numero) return numero.token().lexema();
        if (nodo instanceof NodoIdentificador identificador) return identificador.token().lexema();
        if (nodo instanceof NodoAgrupacion grupo) return postfija(grupo.expresion());
        if (nodo instanceof NodoOperacionUnaria unaria) return postfija(unaria.operando()) + " NEG";
        if (nodo instanceof NodoOperacion operacion) {
            return postfija(operacion.izquierda()) + " " + postfija(operacion.derecha()) + " " + operacion.operador().lexema();
        }
        return "?";
    }

    private static String prefija(NodoAST nodo) {
        if (nodo instanceof NodoNumero numero) return numero.token().lexema();
        if (nodo instanceof NodoIdentificador identificador) return identificador.token().lexema();
        if (nodo instanceof NodoAgrupacion grupo) return prefija(grupo.expresion());
        if (nodo instanceof NodoOperacionUnaria unaria) return "NEG " + prefija(unaria.operando());
        if (nodo instanceof NodoOperacion operacion) {
            return operacion.operador().lexema() + " " + prefija(operacion.izquierda()) + " " + prefija(operacion.derecha());
        }
        return "?";
    }

    private static final class ConstructorCuadruplos {
        private final List<Cuadruplo> cuadruplos = new ArrayList<>();
        private final Set<String> nombresReservados = new HashSet<>();
        private int siguienteTemporal = 1;

        ConstructorCuadruplos(NodoAST raiz) {
            recolectarIdentificadores(raiz, nombresReservados);
        }

        String construir(NodoAST nodo) {
            if (nodo instanceof NodoNumero numero) return numero.token().lexema();
            if (nodo instanceof NodoIdentificador identificador) return identificador.token().lexema();
            if (nodo instanceof NodoAgrupacion grupo) return construir(grupo.expresion());
            if (nodo instanceof NodoOperacionUnaria unaria) {
                String operando = construir(unaria.operando());
                return agregar("NEG", operando, "");
            }
            if (nodo instanceof NodoOperacion operacion) {
                String izquierda = construir(operacion.izquierda());
                String derecha = construir(operacion.derecha());
                return agregar(operacion.operador().lexema(), izquierda, derecha);
            }
            throw new IllegalArgumentException("El nodo no es una expresión aritmética procesable");
        }

        private String agregar(String operador, String argumento1, String argumento2) {
            int numero = cuadruplos.size() + 1;
            String temporal;
            do {
                temporal = "t" + siguienteTemporal++;
            } while (nombresReservados.contains(temporal));
            cuadruplos.add(new Cuadruplo(numero, operador, argumento1, argumento2, temporal));
            return temporal;
        }

        private static void recolectarIdentificadores(NodoAST nodo, Set<String> nombres) {
            if (nodo instanceof NodoIdentificador identificador) nombres.add(identificador.token().lexema());
            else if (nodo instanceof NodoAgrupacion grupo) recolectarIdentificadores(grupo.expresion(), nombres);
            else if (nodo instanceof NodoOperacion operacion) {
                recolectarIdentificadores(operacion.izquierda(), nombres);
                recolectarIdentificadores(operacion.derecha(), nombres);
            } else if (nodo instanceof NodoOperacionUnaria unaria) {
                recolectarIdentificadores(unaria.operando(), nombres);
            }
        }
    }

    private static final class Ambito {
        private final Ambito padre;
        private final Map<String, Vinculo> valores = new LinkedHashMap<>();

        Ambito(Ambito padre) { this.padre = padre; }
        void declarar(String nombre, ValorNumerico valor, Boolean entero) { valores.put(nombre, new Vinculo(valor, entero)); }
        boolean contiene(String nombre) { return valores.containsKey(nombre) || padre != null && padre.contiene(nombre); }
        ValorNumerico obtener(String nombre) {
            if (valores.containsKey(nombre)) return valores.get(nombre).valor;
            return padre == null ? null : padre.obtener(nombre);
        }
        Boolean tipo(String nombre) {
            if (valores.containsKey(nombre)) return valores.get(nombre).entero;
            return padre == null ? null : padre.tipo(nombre);
        }
        void asignar(String nombre, ValorNumerico valor) {
            if (valores.containsKey(nombre)) {
                Vinculo anterior = valores.get(nombre);
                valores.put(nombre, new Vinculo(valor, anterior.entero));
            } else if (padre == null) {
                valores.put(nombre, new Vinculo(valor, tipoDe(valor)));
            } else padre.asignar(nombre, valor);
        }
        Ambito copia() {
            Ambito copia = new Ambito(null);
            copiarEn(copia.valores);
            return copia;
        }
        Ambito sinValores() {
            Ambito copia = copia();
            copia.valores.replaceAll((nombre, vinculo) -> new Vinculo(null, vinculo.entero));
            return copia;
        }
        void invalidarValores() {
            valores.replaceAll((nombre, vinculo) -> new Vinculo(null, vinculo.entero));
            if (padre != null) padre.invalidarValores();
        }
        private void copiarEn(Map<String, Vinculo> destino) {
            if (padre != null) padre.copiarEn(destino);
            destino.putAll(valores);
        }
    }

    private record Vinculo(ValorNumerico valor, Boolean entero) {
    }

    private record ValorNumerico(BigDecimal valor, boolean entero) {
        String texto() {
            BigDecimal normalizado = valor.stripTrailingZeros();
            return normalizado.compareTo(BigDecimal.ZERO) == 0 ? "0" : normalizado.toPlainString();
        }
    }

    private record Resolucion(ValorNumerico valor, List<PasoResolucion> pasos, String detalle, String problema) {
    }
}
