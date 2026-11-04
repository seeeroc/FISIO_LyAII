package fisio.ci;

import java.math.BigDecimal;
import java.math.BigInteger;
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
        List<NodoConstante> constantes = programa.sentencias().stream()
            .filter(NodoConstante.class::isInstance).map(NodoConstante.class::cast).toList();
        List<NodoEstatico> estaticos = programa.sentencias().stream()
            .filter(NodoEstatico.class::isInstance).map(NodoEstatico.class::cast).toList();
        for (NodoConstante constante : constantes) {
            global.declarar(constante.nombre().lexema(), null, tipoEntero(constante.tipo().lexema()),
                constante.tipo().lexema());
        }
        for (NodoEstatico estatico : estaticos) {
            global.declarar(estatico.nombre().lexema(), null, tipoEntero(estatico.tipo().lexema()),
                estatico.tipo().lexema());
        }
        // Resolver referencias adelantadas entre constantes sin emitir expresiones duplicadas.
        for (int intento = 0; intento < constantes.size(); intento++) {
            boolean cambio = false;
            for (NodoConstante constante : constantes) {
                ValorNumerico valor = ajustarTipo(evaluar(constante.expresion(), global), constante.tipo().lexema());
                if (valor != null && global.obtener(constante.nombre().lexema()) == null) {
                    global.asignar(constante.nombre().lexema(), valor);
                    cambio = true;
                }
            }
            if (!cambio) break;
        }
        for (NodoConstante constante : constantes) {
            procesarValor(constante.expresion(), global, "Constante global " + constante.nombre().lexema());
        }
        for (NodoEstatico estatico : estaticos) {
            ValorNumerico valor = ajustarTipo(evaluar(estatico.expresion(), global), estatico.tipo().lexema());
            global.asignar(estatico.nombre().lexema(), valor);
            procesarValor(estatico.expresion(), global, "Static global " + estatico.nombre().lexema());
        }
        for (NodoAST sentencia : programa.sentencias()) {
            if (!(sentencia instanceof NodoConstante) && !(sentencia instanceof NodoEstatico)
                    && !(sentencia instanceof NodoAliasTipo))
                procesarNodo(sentencia, global, "Expresión de nivel superior");
        }
    }

    private void procesarNodo(NodoAST nodo, Ambito ambito, String contexto) {
        if (nodo == null) return;
        if (nodo instanceof NodoTrait) return;
        if (nodo instanceof NodoFuncion funcion) {
            Ambito local = new Ambito(ambito);
            funcion.parametros().forEach(p -> local.declarar(p.nombre().lexema(), null,
                tipoEntero(p.tipo().lexema()), p.tipo().lexema()));
            procesarBloque(funcion.cuerpo(), local, "Función " + funcion.nombre().lexema());
        } else if (nodo instanceof NodoImpl bloque) {
            for (NodoFuncion metodo : bloque.metodos()) procesarNodo(metodo, ambito,
                "Método " + (bloque.rasgo() == null ? "" : bloque.rasgo().lexema() + " for ")
                    + bloque.tipo().lexema() + "::" + metodo.nombre().lexema());
        } else if (nodo instanceof NodoBloque bloque) {
            procesarBloque(bloque, ambito, contexto);
        } else if (nodo instanceof NodoDeclaracion declaracion) {
            String nombre = declaracion.identificador().lexema();
            procesarValor(declaracion.expresion(), ambito, "Inicializador de " + nombre);
            ValorNumerico valor = evaluar(declaracion.expresion(), ambito);
            String tipo = declaracion.tipo() == null ? null : declaracion.tipo().lexema();
            ValorNumerico ajustado = ajustarTipo(valor, tipo);
            ambito.declarar(nombre, ajustado, tipo == null ? tipoDe(ajustado) : tipoEntero(tipo), tipo);
        } else if (nodo instanceof NodoLetElse declaracion) {
            procesarValor(declaracion.expresion(), ambito, "Valor de let else");
            procesarBloque(declaracion.cuerpoElse(), ambito.copia(), "Bloque else de let else");
            declararPatron(declaracion.patron(), ambito);
        } else if (nodo instanceof NodoConstante constante) {
            String nombre = constante.nombre().lexema();
            procesarValor(constante.expresion(), ambito, "Constante local " + nombre);
            ValorNumerico valor = ajustarTipo(evaluar(constante.expresion(), ambito), constante.tipo().lexema());
            ambito.declarar(nombre, valor, tipoEntero(constante.tipo().lexema()), constante.tipo().lexema());
        } else if (nodo instanceof NodoEstatico estatico) {
            String nombre = estatico.nombre().lexema();
            procesarValor(estatico.expresion(), ambito, "Static local " + nombre);
            ValorNumerico valor = ajustarTipo(evaluar(estatico.expresion(), ambito), estatico.tipo().lexema());
            ambito.declarar(nombre, valor, tipoEntero(estatico.tipo().lexema()), estatico.tipo().lexema());
        } else if (nodo instanceof NodoAsignacion asignacion) {
            String nombre = infija(asignacion.lugar());
            if (!"=".equals(asignacion.operador().lexema())) {
                TokenFisio compuesto = asignacion.operador();
                String operadorSimple = compuesto.lexema().substring(0, compuesto.lexema().length() - 1);
                TokenFisio simple = new TokenFisio(operadorSimple, compuesto.categoria(),
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
        } else if (nodo instanceof NodoIfLet condicional) {
            procesarValor(condicional.expresion(), ambito, "Valor de if let");
            Ambito rama = ambito.copia();
            declararPatron(condicional.patron(), rama);
            procesarValor(condicional.guarda(), rama, "Guarda de if let");
            procesarBloque(condicional.cuerpoIf(), rama, "Bloque if let");
            procesarNodo(condicional.cuerpoElse(), ambito.copia(), "Bloque else");
            ambito.invalidarValores();
        } else if (nodo instanceof NodoWhile ciclo) {
            procesarValor(ciclo.condicion(), ambito, "Condición de while");
            procesarBloque(ciclo.cuerpo(), ambito.sinValores(), "Cuerpo de while");
            ambito.invalidarValores();
        } else if (nodo instanceof NodoWhileLet ciclo) {
            procesarValor(ciclo.expresion(), ambito, "Valor de while let");
            Ambito local = ambito.sinValores();
            declararPatron(ciclo.patron(), local);
            procesarValor(ciclo.guarda(), local, "Guarda de while let");
            procesarBloque(ciclo.cuerpo(), local, "Cuerpo de while let");
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
        else if (nodo instanceof NodoReferencia referencia) procesarValor(referencia.expresion(), ambito, contexto);
        else if (nodo instanceof NodoDesreferencia referencia) procesarValor(referencia.referencia(), ambito, contexto);
        else if (nodo instanceof NodoConversion conversion) procesarValor(conversion.expresion(), ambito, contexto);
        else if (nodo instanceof NodoBloque bloque) procesarBloque(bloque, ambito, contexto);
        else if (nodo instanceof NodoIf || nodo instanceof NodoIfLet) procesarNodo(nodo, ambito, contexto);
        else if (nodo instanceof NodoLiteralStruct literal) {
            literal.campos().forEach(c -> procesarValor(c.valor(), ambito,
                "Campo " + c.nombre().lexema() + " de " + literal.nombre().lexema()));
            procesarValor(literal.base(), ambito, "Base de actualización de " + literal.nombre().lexema());
        } else if (nodo instanceof NodoLiteralVariante literal) {
            literal.campos().forEach(c -> procesarValor(c.valor(), ambito,
                "Campo " + c.nombre().lexema() + " de " + literal.tipo().lexema()
                    + "::" + literal.variante().lexema()));
        } else if (nodo instanceof NodoAccesoCampo acceso) {
            procesarValor(acceso.objeto(), ambito, contexto);
        } else if (nodo instanceof NodoArreglo arreglo) {
            arreglo.elementos().forEach(e -> procesarValor(e, ambito, "Elemento de arreglo"));
        } else if (nodo instanceof NodoTupla tupla) {
            tupla.elementos().forEach(e -> procesarValor(e, ambito, "Elemento de tupla"));
        } else if (nodo instanceof NodoIndice acceso) {
            procesarValor(acceso.objeto(), ambito, contexto);
            procesarValor(acceso.indice(), ambito, "Índice de arreglo");
        } else if (nodo instanceof NodoAccesoTupla acceso) {
            procesarValor(acceso.objeto(), ambito, contexto);
        } else if (nodo instanceof NodoMatch seleccion) {
            procesarValor(seleccion.expresion(), ambito, "Valor de match");
            seleccion.brazos().forEach(b -> {
                Ambito brazo = ambito.copia();
                declararPatron(PatronVariante.desde(b), brazo);
                procesarValor(b.guarda(), brazo, "Guarda de match");
                procesarValor(b.resultado(), brazo, "Brazo de match");
            });
            ambito.invalidarValores();
        } else if (nodo instanceof NodoOperacionRelacional operacion) {
            procesarValor(operacion.izquierda(), ambito, contexto);
            procesarValor(operacion.derecha(), ambito, contexto);
        } else if (nodo instanceof NodoOperacionUnaria unaria) {
            procesarValor(unaria.operando(), ambito, contexto);
        } else if (nodo instanceof NodoRango rango) {
            procesarValor(rango.inicio(), ambito, contexto);
            procesarValor(rango.fin(), ambito, contexto);
        } else if (nodo instanceof NodoLlamadaFuncion llamada) {
            llamada.argumentos().forEach(a -> procesarValor(a, ambito, "Argumento de " + llamada.nombre().lexema()));
        } else if (nodo instanceof NodoLlamadaAsociada llamada) {
            llamada.argumentos().forEach(a -> procesarValor(a, ambito,
                "Argumento de " + llamada.tipo().lexema() + "::" + llamada.nombre().lexema()));
        } else if (nodo instanceof NodoLlamadaMetodo llamada) {
            procesarValor(llamada.receptor(), ambito, "Receptor de " + llamada.nombre().lexema());
            llamada.argumentos().forEach(a -> procesarValor(a, ambito,
                "Argumento de método " + llamada.nombre().lexema()));
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

    private static void declararPatron(PatronVariante patron, Ambito ambito) {
        patron.elementos().forEach(e -> {
            if (e.esAnidado()) declararPatron(e.patron(), ambito);
            else if (!e.nombre().lexema().equals("_")) ambito.declarar(e.nombre().lexema(), null, null);
        });
        patron.campos().forEach(c -> {
            if (c.esAnidado()) declararPatron(c.patron(), ambito);
            else if (!c.vinculacion().lexema().equals("_"))
                ambito.declarar(c.vinculacion().lexema(), null, null);
        });
    }

    private Resolucion resolver(List<Cuadruplo> cuadruplos, String simboloFinal, Ambito ambito) {
        Map<String, ValorNumerico> temporales = new LinkedHashMap<>();
        List<PasoResolucion> pasos = new ArrayList<>();
        String problema = null;
        for (Cuadruplo cuadruplo : cuadruplos) {
            ValorNumerico primero = resolverOperando(cuadruplo.argumento1(), temporales, ambito);
            ValorNumerico segundo = cuadruplo.argumento2().isBlank()
                ? null : resolverOperando(cuadruplo.argumento2(), temporales, ambito);
            boolean unaria = cuadruplo.argumento2().isBlank();
            String operacion = unaria
                ? cuadruplo.resultado() + " = " + cuadruplo.operador() + " " + cuadruplo.argumento1()
                : cuadruplo.resultado() + " = " + cuadruplo.argumento1() + " "
                    + cuadruplo.operador() + " " + cuadruplo.argumento2();
            if (primero == null || (!unaria && segundo == null)) {
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
            String sustitucion = unaria
                ? cuadruplo.resultado() + " = " + cuadruplo.operador() + " " + primero.texto()
                    + " = " + valor.texto()
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
        if ("true".equals(simbolo)) return new ValorNumerico(BigDecimal.ONE, true);
        if ("false".equals(simbolo)) return new ValorNumerico(BigDecimal.ZERO, true);
        if (simbolo.length() >= 3 && simbolo.startsWith("'") && simbolo.endsWith("'")) {
            Integer codigo = codigoCaracter(simbolo);
            if (codigo != null) return new ValorNumerico(BigDecimal.valueOf(codigo), true);
        }
        try {
            return new ValorNumerico(new BigDecimal(simbolo), !simbolo.contains("."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static ValorNumerico evaluar(NodoAST nodo, Ambito ambito) {
        if (nodo == null) return null;
        if (nodo instanceof NodoNumero numero) {
            return valorLiteral(numero.token().lexema());
        }
        if (nodo instanceof NodoIdentificador identificador) return ambito.obtener(identificador.token().lexema());
        if (nodo instanceof NodoAgrupacion grupo) return evaluar(grupo.expresion(), ambito);
        if (nodo instanceof NodoReferencia referencia) return evaluar(referencia.expresion(), ambito);
        if (nodo instanceof NodoDesreferencia referencia) return evaluar(referencia.referencia(), ambito);
        if (nodo instanceof NodoConversion conversion) {
            ValorNumerico valor = evaluar(conversion.expresion(), ambito);
            return valor == null ? null : convertir(valor, conversion.tipo().lexema());
        }
        if (nodo instanceof NodoOperacionUnaria unaria
                && ("-".equals(unaria.operador().lexema()) || "!".equals(unaria.operador().lexema()))) {
            ValorNumerico valor = evaluar(unaria.operando(), ambito);
            if (valor == null) return null;
            return "-".equals(unaria.operador().lexema())
                ? new ValorNumerico(valor.valor.negate(PRECISION), valor.entero)
                : complementarBits(valor);
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
        if ("REF".equals(operador) || "REF_MUT".equals(operador) || "DEREF".equals(operador)) return a;
        if ("NEG".equals(operador)) return new ValorNumerico(a.valor.negate(PRECISION), a.entero, a.tipo);
        if ("BIT_NOT".equals(operador)) return complementarBits(a);
        if (operador.startsWith("CAST_")) return convertir(a, operador.substring("CAST_".length()));
        boolean entero = a.entero && b.entero;
        BigDecimal valor = switch (operador) {
            case "+" -> a.valor.add(b.valor, PRECISION);
            case "-" -> a.valor.subtract(b.valor, PRECISION);
            case "*" -> a.valor.multiply(b.valor, PRECISION);
            case "/" -> entero ? a.valor.divideToIntegralValue(b.valor) : a.valor.divide(b.valor, PRECISION);
            case "%" -> a.valor.remainder(b.valor, PRECISION);
            case "<<" -> new BigDecimal(a.valor.toBigInteger().shiftLeft(b.valor.intValue()));
            case ">>" -> new BigDecimal(a.valor.toBigInteger().shiftRight(b.valor.intValue()));
            case "&" -> new BigDecimal(a.valor.toBigInteger().and(b.valor.toBigInteger()));
            case "|" -> new BigDecimal(a.valor.toBigInteger().or(b.valor.toBigInteger()));
            case "^" -> new BigDecimal(a.valor.toBigInteger().xor(b.valor.toBigInteger()));
            default -> throw new IllegalArgumentException("Operador aritmético no soportado: " + operador);
        };
        String tipo = a.tipo != null ? a.tipo : b.tipo;
        return new ValorNumerico(valor, entero, tipo);
    }

    private static ValorNumerico complementarBits(ValorNumerico valor) {
        ValorNumerico complemento = new ValorNumerico(
            new BigDecimal(valor.valor.toBigInteger().not()), true);
        return valor.tipo == null ? complemento : convertir(complemento, valor.tipo);
    }

    private static ValorNumerico convertir(ValorNumerico valor, String tipo) {
        Boolean entero = tipoEntero(tipo);
        if (entero == null) return valor;
        if (!entero) {
            BigDecimal convertido = "f32".equals(tipo)
                ? new BigDecimal(Float.toString(valor.valor.floatValue()))
                : BigDecimal.valueOf(valor.valor.doubleValue());
            return new ValorNumerico(convertido, false, tipo);
        }
        int bits = tipo.endsWith("size") ? 64 : Integer.parseInt(tipo.substring(1));
        boolean sinSigno = tipo.startsWith("u");
        BigInteger numero = valor.valor.toBigInteger();
        BigInteger modulo = BigInteger.ONE.shiftLeft(bits);
        if (valor.entero) {
            numero = numero.mod(modulo);
            if (!sinSigno && numero.testBit(bits - 1)) numero = numero.subtract(modulo);
        } else {
            BigInteger minimo = sinSigno ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
            BigInteger maximo = BigInteger.ONE.shiftLeft(sinSigno ? bits : bits - 1).subtract(BigInteger.ONE);
            if (numero.compareTo(minimo) < 0) numero = minimo;
            else if (numero.compareTo(maximo) > 0) numero = maximo;
        }
        return new ValorNumerico(new BigDecimal(numero), true, tipo);
    }

    private static Integer codigoCaracter(String literal) {
        String contenido = literal.substring(1, literal.length() - 1);
        if (contenido.length() == 1) return (int) contenido.charAt(0);
        if (contenido.length() == 2 && Character.isSurrogatePair(contenido.charAt(0), contenido.charAt(1))) {
            return Character.toCodePoint(contenido.charAt(0), contenido.charAt(1));
        }
        if (contenido.length() != 2 || contenido.charAt(0) != '\\') return null;
        return switch (contenido.charAt(1)) {
            case 'n' -> (int) '\n';
            case 'r' -> (int) '\r';
            case 't' -> (int) '\t';
            case '0' -> 0;
            case '\\' -> (int) '\\';
            case '\'' -> (int) '\'';
            default -> null;
        };
    }

    private static ValorNumerico valorLiteral(String texto) {
        if ("true".equals(texto)) return new ValorNumerico(BigDecimal.ONE, true);
        if ("false".equals(texto)) return new ValorNumerico(BigDecimal.ZERO, true);
        if (texto.length() >= 3 && texto.startsWith("'") && texto.endsWith("'")) {
            Integer codigo = codigoCaracter(texto);
            return codigo == null ? null : new ValorNumerico(BigDecimal.valueOf(codigo), true);
        }
        try {
            return new ValorNumerico(new BigDecimal(texto), !texto.contains("."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static ValorNumerico ajustarTipo(ValorNumerico valor, String tipo) {
        if (valor == null || tipo == null) return valor;
        Boolean entero = tipoEntero(tipo);
        return entero == null ? valor : new ValorNumerico(valor.valor, entero, tipo);
    }

    private static Boolean tipoEntero(String tipo) {
        if (tipo == null) return null;
        if (tipo.startsWith("&mut ")) return tipoEntero(tipo.substring(5));
        if (tipo.startsWith("&")) return tipoEntero(tipo.substring(1));
        if (tipo.startsWith("i") || tipo.startsWith("u")) return true;
        if (tipo.startsWith("f")) return false;
        return null;
    }

    private static Boolean tipoDe(ValorNumerico valor) {
        return valor == null ? null : valor.entero;
    }

    private static void actualizarAsignacion(NodoAsignacion asignacion, Ambito ambito) {
        NodoIdentificador id;
        if (asignacion.lugar() instanceof NodoIdentificador directo) id = directo;
        else if (asignacion.lugar() instanceof NodoDesreferencia desreferencia
                && desreferencia.referencia() instanceof NodoIdentificador referencia) id = referencia;
        else return;
        String nombre = id.token().lexema();
        ValorNumerico derecha = evaluar(asignacion.expresion(), ambito);
        String operador = asignacion.operador().lexema();
        if ("=".equals(operador)) {
            Boolean tipo = ambito.tipo(nombre);
            if (derecha != null && tipo != null) derecha = new ValorNumerico(derecha.valor, tipo, derecha.tipo);
            ambito.asignar(nombre, derecha);
            return;
        }
        ValorNumerico izquierda = ambito.obtener(nombre);
        String simple = operador.substring(0, operador.length() - 1);
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
        if (nodo instanceof NodoConversion) return true;
        return nodo instanceof NodoAgrupacion grupo && esRaizAritmetica(grupo.expresion());
    }

    private static boolean esOperadorAritmetico(String operador) {
        return "+".equals(operador) || "-".equals(operador) || "*".equals(operador)
            || "/".equals(operador) || "%".equals(operador) || "<<".equals(operador)
            || ">>".equals(operador) || "&".equals(operador) || "|".equals(operador) || "^".equals(operador);
    }

    private static String infija(NodoAST nodo) {
        return infija(nodo, 0, false, "");
    }

    private static String infija(NodoAST nodo, int precedenciaPadre, boolean derecho, String operadorPadre) {
        if (nodo instanceof NodoNumero numero) return numero.token().lexema();
        if (nodo instanceof NodoIdentificador identificador) return identificador.token().lexema();
        if (nodo instanceof NodoAgrupacion grupo) return "(" + infija(grupo.expresion(), 0, false, "") + ")";
        if (nodo instanceof NodoReferencia referencia) {
            String operador = referencia.mutable() ? "&mut " : "&";
            String texto = operador + infija(referencia.expresion(), 8, true, operador);
            return 8 < precedenciaPadre ? "(" + texto + ")" : texto;
        }
        if (nodo instanceof NodoDesreferencia referencia) {
            String texto = "*" + infija(referencia.referencia(), 8, true, "*");
            return 8 < precedenciaPadre ? "(" + texto + ")" : texto;
        }
        if (nodo instanceof NodoAccesoCampo acceso) {
            return infija(acceso.objeto(), 9, false, ".") + "." + acceso.campo().lexema();
        }
        if (nodo instanceof NodoArreglo arreglo) {
            return "[" + arreglo.elementos().stream().map(GeneradorCodigoIntermedio::infija)
                .collect(java.util.stream.Collectors.joining(", ")) + "]";
        }
        if (nodo instanceof NodoTupla tupla) {
            String elementos = tupla.elementos().stream().map(GeneradorCodigoIntermedio::infija)
                .collect(java.util.stream.Collectors.joining(", "));
            return "(" + elementos + (tupla.elementos().size() == 1 ? "," : "") + ")";
        }
        if (nodo instanceof NodoIndice acceso) {
            return infija(acceso.objeto(), 9, false, "[]") + "[" + infija(acceso.indice()) + "]";
        }
        if (nodo instanceof NodoAccesoTupla acceso) {
            return infija(acceso.objeto(), 9, false, ".") + "." + acceso.indice().lexema();
        }
        if (nodo instanceof NodoVarianteEnum variante) {
            return variante.tipo().lexema() + "::" + variante.variante().lexema();
        }
        if (nodo instanceof NodoLiteralStruct literal) return literal.nombre().lexema() + " { ... }";
        if (nodo instanceof NodoLiteralVariante literal)
            return literal.tipo().lexema() + "::" + literal.variante().lexema() + " { ... }";
        if (nodo instanceof NodoLlamadaFuncion llamada) return llamada.nombre().lexema() + "(...)";
        if (nodo instanceof NodoLlamadaAsociada llamada)
            return llamada.tipo().lexema() + "::" + llamada.nombre().lexema() + "(...)";
        if (nodo instanceof NodoLlamadaMetodo llamada)
            return infija(llamada.receptor(), 9, false, ".") + "." + llamada.nombre().lexema() + "(...)";
        if (nodo instanceof NodoMatch seleccion) return "match@" + seleccion.linea() + ":" + seleccion.columna();
        if (nodo instanceof NodoConversion conversion) {
            String texto = infija(conversion.expresion(), 7, false, "as") + " as " + conversion.tipo().lexema();
            return 7 < precedenciaPadre ? "(" + texto + ")" : texto;
        }
        if (nodo instanceof NodoOperacionUnaria unaria) {
            String operador = unaria.operador().lexema();
            String texto = operador + infija(unaria.operando(), 8, true, operador);
            return 8 < precedenciaPadre ? "(" + texto + ")" : texto;
        }
        if (nodo instanceof NodoOperacion operacion) {
            String operador = operacion.operador().lexema();
            int precedencia = precedencia(operador);
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
            || "<<".equals(padre) || ">>".equals(padre)
            || ("+".equals(padre) && "-".equals(hijo))
            || ("*".equals(padre) && ("/".equals(hijo) || "%".equals(hijo)));
    }

    private static int precedencia(String operador) {
        return switch (operador) {
            case "|" -> 1;
            case "^" -> 2;
            case "&" -> 3;
            case "<<", ">>" -> 4;
            case "+", "-" -> 5;
            case "*", "/", "%" -> 6;
            default -> 0;
        };
    }

    private static String postfija(NodoAST nodo) {
        if (nodo instanceof NodoNumero numero) return numero.token().lexema();
        if (nodo instanceof NodoIdentificador identificador) return identificador.token().lexema();
        if (nodo instanceof NodoAgrupacion grupo) return postfija(grupo.expresion());
        if (nodo instanceof NodoReferencia referencia)
            return postfija(referencia.expresion()) + (referencia.mutable() ? " REF_MUT" : " REF");
        if (nodo instanceof NodoDesreferencia referencia) return postfija(referencia.referencia()) + " DEREF";
        if (nodo instanceof NodoAccesoCampo || nodo instanceof NodoAccesoTupla || nodo instanceof NodoIndice
                || nodo instanceof NodoArreglo || nodo instanceof NodoTupla || nodo instanceof NodoVarianteEnum
                || nodo instanceof NodoLiteralStruct || nodo instanceof NodoLiteralVariante || nodo instanceof NodoLlamadaFuncion
                || nodo instanceof NodoLlamadaAsociada || nodo instanceof NodoLlamadaMetodo
                || nodo instanceof NodoMatch) return infija(nodo);
        if (nodo instanceof NodoConversion conversion) {
            return postfija(conversion.expresion()) + " CAST_" + conversion.tipo().lexema();
        }
        if (nodo instanceof NodoOperacionUnaria unaria) {
            return postfija(unaria.operando()) + ("-".equals(unaria.operador().lexema()) ? " NEG" : " BIT_NOT");
        }
        if (nodo instanceof NodoOperacion operacion) {
            return postfija(operacion.izquierda()) + " " + postfija(operacion.derecha()) + " " + operacion.operador().lexema();
        }
        return "?";
    }

    private static String prefija(NodoAST nodo) {
        if (nodo instanceof NodoNumero numero) return numero.token().lexema();
        if (nodo instanceof NodoIdentificador identificador) return identificador.token().lexema();
        if (nodo instanceof NodoAgrupacion grupo) return prefija(grupo.expresion());
        if (nodo instanceof NodoReferencia referencia)
            return (referencia.mutable() ? "REF_MUT " : "REF ") + prefija(referencia.expresion());
        if (nodo instanceof NodoDesreferencia referencia) return "DEREF " + prefija(referencia.referencia());
        if (nodo instanceof NodoAccesoCampo || nodo instanceof NodoAccesoTupla || nodo instanceof NodoIndice
                || nodo instanceof NodoArreglo || nodo instanceof NodoTupla || nodo instanceof NodoVarianteEnum
                || nodo instanceof NodoLiteralStruct || nodo instanceof NodoLiteralVariante || nodo instanceof NodoLlamadaFuncion
                || nodo instanceof NodoLlamadaAsociada || nodo instanceof NodoLlamadaMetodo
                || nodo instanceof NodoMatch) return infija(nodo);
        if (nodo instanceof NodoConversion conversion) {
            return "CAST_" + conversion.tipo().lexema() + " " + prefija(conversion.expresion());
        }
        if (nodo instanceof NodoOperacionUnaria unaria) {
            return ("-".equals(unaria.operador().lexema()) ? "NEG " : "BIT_NOT ") + prefija(unaria.operando());
        }
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
            if (nodo instanceof NodoReferencia referencia) {
                return agregar(referencia.mutable() ? "REF_MUT" : "REF", construir(referencia.expresion()), "");
            }
            if (nodo instanceof NodoDesreferencia referencia) {
                return agregar("DEREF", construir(referencia.referencia()), "");
            }
            if (nodo instanceof NodoAccesoCampo || nodo instanceof NodoAccesoTupla || nodo instanceof NodoIndice
                    || nodo instanceof NodoArreglo || nodo instanceof NodoTupla || nodo instanceof NodoVarianteEnum
                    || nodo instanceof NodoLiteralStruct || nodo instanceof NodoLiteralVariante || nodo instanceof NodoLlamadaFuncion
                    || nodo instanceof NodoLlamadaAsociada || nodo instanceof NodoLlamadaMetodo
                    || nodo instanceof NodoMatch) return infija(nodo);
            if (nodo instanceof NodoConversion conversion) {
                return agregar("CAST_" + conversion.tipo().lexema(), construir(conversion.expresion()), "");
            }
            if (nodo instanceof NodoOperacionUnaria unaria) {
                String operando = construir(unaria.operando());
                return agregar("-".equals(unaria.operador().lexema()) ? "NEG" : "BIT_NOT", operando, "");
            }
            if (nodo instanceof NodoOperacion operacion) {
                String izquierda = construir(operacion.izquierda());
                String derecha = construir(operacion.derecha());
                return agregar(operacion.operador().lexema(), izquierda, derecha);
            }
            return "<expr@" + nodo.linea() + ":" + nodo.columna() + ">";
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
            else if (nodo instanceof NodoReferencia referencia) recolectarIdentificadores(referencia.expresion(), nombres);
            else if (nodo instanceof NodoDesreferencia referencia) recolectarIdentificadores(referencia.referencia(), nombres);
            else if (nodo instanceof NodoLlamadaAsociada llamada)
                llamada.argumentos().forEach(a -> recolectarIdentificadores(a, nombres));
            else if (nodo instanceof NodoLlamadaMetodo llamada) {
                recolectarIdentificadores(llamada.receptor(), nombres);
                llamada.argumentos().forEach(a -> recolectarIdentificadores(a, nombres));
            }
            else if (nodo instanceof NodoAccesoCampo acceso) recolectarIdentificadores(acceso.objeto(), nombres);
            else if (nodo instanceof NodoAccesoTupla acceso) recolectarIdentificadores(acceso.objeto(), nombres);
            else if (nodo instanceof NodoIndice acceso) {
                recolectarIdentificadores(acceso.objeto(), nombres);
                recolectarIdentificadores(acceso.indice(), nombres);
            } else if (nodo instanceof NodoArreglo arreglo) {
                arreglo.elementos().forEach(e -> recolectarIdentificadores(e, nombres));
            } else if (nodo instanceof NodoTupla tupla) {
                tupla.elementos().forEach(e -> recolectarIdentificadores(e, nombres));
            }
            else if (nodo instanceof NodoLiteralStruct literal) {
                literal.campos().forEach(c -> recolectarIdentificadores(c.valor(), nombres));
                recolectarIdentificadores(literal.base(), nombres);
            } else if (nodo instanceof NodoLiteralVariante literal) {
                literal.campos().forEach(c -> recolectarIdentificadores(c.valor(), nombres));
            } else if (nodo instanceof NodoMatch seleccion) {
                recolectarIdentificadores(seleccion.expresion(), nombres);
                seleccion.brazos().forEach(b -> {
                    recolectarIdentificadores(b.guarda(), nombres);
                    recolectarIdentificadores(b.resultado(), nombres);
                });
            }
            else if (nodo instanceof NodoConversion conversion) {
                recolectarIdentificadores(conversion.expresion(), nombres);
            }
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
        void declarar(String nombre, ValorNumerico valor, Boolean entero) {
            declarar(nombre, valor, entero, null);
        }
        void declarar(String nombre, ValorNumerico valor, Boolean entero, String tipo) {
            valores.put(nombre, new Vinculo(valor, entero, tipo));
        }
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
                if (valor != null && anterior.tipo != null) valor = ajustarTipo(valor, anterior.tipo);
                else if (valor != null && anterior.entero != null)
                    valor = new ValorNumerico(valor.valor, anterior.entero, valor.tipo);
                valores.put(nombre, new Vinculo(valor, anterior.entero, anterior.tipo));
            } else if (padre == null) {
                valores.put(nombre, new Vinculo(valor, tipoDe(valor), valor == null ? null : valor.tipo));
            } else padre.asignar(nombre, valor);
        }
        Ambito copia() {
            Ambito copia = new Ambito(null);
            copiarEn(copia.valores);
            return copia;
        }
        Ambito sinValores() {
            Ambito copia = copia();
            copia.valores.replaceAll((nombre, vinculo) -> new Vinculo(null, vinculo.entero, vinculo.tipo));
            return copia;
        }
        void invalidarValores() {
            valores.replaceAll((nombre, vinculo) -> new Vinculo(null, vinculo.entero, vinculo.tipo));
            if (padre != null) padre.invalidarValores();
        }
        private void copiarEn(Map<String, Vinculo> destino) {
            if (padre != null) padre.copiarEn(destino);
            destino.putAll(valores);
        }
    }

    private record Vinculo(ValorNumerico valor, Boolean entero, String tipo) {
    }

    private record ValorNumerico(BigDecimal valor, boolean entero, String tipo) {
        ValorNumerico(BigDecimal valor, boolean entero) {
            this(valor, entero, null);
        }

        String texto() {
            BigDecimal normalizado = valor.stripTrailingZeros();
            return normalizado.compareTo(BigDecimal.ZERO) == 0 ? "0" : normalizado.toPlainString();
        }
    }

    private record Resolucion(ValorNumerico valor, List<PasoResolucion> pasos, String detalle, String problema) {
    }
}
