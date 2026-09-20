package fisio.semantic;

import fisio.analysis.FisioLexerRunner;
import fisio.analysis.FisioParserRunner;
import fisio.gui.EjemplosFisio;
import java.util.Arrays;
import java.util.List;

/** Regresion ejecutable sin dependencias externas; falla con exit code no cero. */
public final class SemanticRegression {
    private static int casos;
    private static String programa(String cuerpo) { return "fn main() {\n" + cuerpo + "\n}"; }

    private static void caso(String nombre, String codigo, String... esperados) {
        var resultado = FisioSemanticRunner.analizar(codigo);
        if (!resultado.analizado()) throw new AssertionError(nombre + ": bloqueado: " + resultado.motivo()
            + " " + FisioParserRunner.parse(codigo).errores());
        List<String> actuales = resultado.errores().stream().map(ErrorSemantico::codigo).sorted().toList();
        if (!actuales.equals(Arrays.stream(esperados).sorted().toList())) {
            throw new AssertionError(nombre + ": esperaba " + Arrays.toString(esperados) + ", obtuvo " + resultado.errores());
        }
        for (var error : resultado.errores()) if (error.linea() < 1 || error.columna() < 1) throw new AssertionError(error);
        System.out.println("OK " + nombre + " -> " + resultado.errores());
        casos++;
    }

    private static void casoMensaje(String nombre, String codigo, String esperado, String fragmento) {
        caso(nombre, codigo, esperado);
        var error = FisioSemanticRunner.analizar(codigo).errores().get(0);
        if (!error.mensaje().contains(fragmento)) throw new AssertionError(nombre + ": " + error.mensaje());
    }

    public static void main(String[] args) throws Exception {
        caso("entero += cadena literal", programa("let mut contador = 0; contador += \"hola\";"), "OPERADOR");
        caso("entero += cadena declarada", programa("let mut contador = 0; let texto = \"hola\"; contador += texto;"), "OPERADOR");
        caso("operando no declarado", programa("let mut contador = 0; contador += texto;"), "VARIABLE_NO_DECLARADA");
        caso("entero += entero", programa("let mut contador = 0; let incremento = 2; contador += incremento;"));
        caso("asignacion incompatible", programa("let mut contador = 0; contador = \"hola\";"), "TIPO");
        caso("destino no declarado", programa("contador = 1;"), "VARIABLE_NO_DECLARADA");
        caso("inmutable", programa("let contador = 0; contador += 1;"), "INMUTABLE");
        caso("parametro inmutable", "fn f(x: i32) { x = 1; }", "INMUTABLE");
        caso("autorreferencia", programa("let x = x;"), "VARIABLE_NO_DECLARADA");
        caso("uso antes de declarar", programa("let y = x; let x = 1;"), "VARIABLE_NO_DECLARADA");
        caso("shadowing", programa("let x = 1; let x = x + 1; let x = \"hola\"; println!(\"{}\", x);"));
        caso("ambito de bloque", programa("if true { let x = 1; } println!(\"{}\", x);"), "VARIABLE_NO_DECLARADA");
        caso("aislamiento funciones", "fn f() { let x = 1; } fn g() { println!(\"{}\", x); }", "VARIABLE_NO_DECLARADA");
        caso("ambito exterior restaurado", programa("let mut x = 1; if true { let x = \"texto\"; println!(\"{}\", x); } x += 1;"));
        caso("tipo desconocido", programa("let x: Numero = 1;"), "TIPO_DESCONOCIDO");
        caso("tipo inicializador", programa("let x: i32 = true;"), "TIPO");
        caso("sin conversion implicita", programa("let x: f64 = 1;"), "TIPO");
        caso("booleanos", programa("let x = true && !false; let y = x || false;"));
        caso("logicos numericos", programa("let x = 1 && 2;"), "OPERADOR");
        caso("unario cadena", programa("let x = -\"hola\";"), "OPERADOR");
        caso("negacion numerica", programa("let x = !1;"), "OPERADOR");
        caso("comparacion incompatible", programa("let x = 1 == \"1\";"), "OPERADOR");
        caso("orden incompatible", programa("let x = true < false;"), "OPERADOR");
        caso("aritmetica mixta", programa("let x = 1 + 2.0;"), "OPERADOR");
        caso("tipos numericos distintos", programa("let a: i64 = 1; let b: i32 = 2; let x = a + b;"), "OPERADOR");
        caso("contexto literal", programa("let a: i64 = 1; let b = 2 + a; let c: i64 = b;"));
        caso("compuestas", programa("let mut x = 0; x += 1; x -= 2; x *= 3; x /= 4; x %= 5;"));
        caso("division cero", programa("let x = 10 / 0;"), "DIVISION_CERO");
        caso("resto cero constante", programa("let mut x = 10; x %= (2 - 2);"), "DIVISION_CERO");
        caso("limites i8", programa("let a: i8 = -128; let b: i8 = 127;"));
        caso("limite negativo agrupado", programa("let a: i8 = -(128);"));
        caso("overflow literal", programa("let a: i8 = 128;"), "LITERAL_FUERA_DE_RANGO");
        caso("unsigned negativo", programa("let a: u8 = -1;"), "LITERAL_FUERA_DE_RANGO");
        caso("condicion if", programa("if 1 {}"), "CONDICION");
        caso("condicion while", programa("while \"hola\" {}"), "CONDICION");
        caso("rango valido", programa("for i in 0..10 { println!(\"{}\", i); }"));
        caso("rango inclusivo", programa("for i in 0..=10 { println!(\"{}\", i); }"));
        caso("rango variable", programa("let r = 0..10; for i in r { let x: i32 = i; }"));
        caso("for escalar", programa("for i in 0.10 {}"), "ITERABLE");
        caso("rango decimal", programa("for i in 0.0..10.0 {}"), "RANGO");
        caso("rango desigual", programa("for i in 0..true {}"), "RANGO");
        caso("indice fuera ambito", programa("for i in 0..10 {} println!(\"{}\", i);"), "VARIABLE_NO_DECLARADA");
        caso("indice inmutable", programa("for i in 0..10 { i = 1; }"), "INMUTABLE");
        caso("funcion posterior", programa("let x = suma(1, 2);") + "fn suma(a: i32, b: i32) -> i32 { a + b }");
        caso("recursion", "fn f(n: i32) -> i32 { if n == 0 { return 0; } return f(n - 1); }");
        caso("funcion inexistente", programa("foo(1);"), "FUNCION_NO_DECLARADA");
        caso("variable no invocable", programa("let foo = 1; foo();"), "NO_INVOCABLE");
        caso("aridad", programa("f(1);") + "fn f(a: i32, b: i32) {}", "ARIDAD");
        caso("tipo argumento", programa("f(\"hola\");") + "fn f(a: i32) {}", "ARGUMENTO");
        caso("argumento inexistente", programa("f(x);") + "fn f(a: i32) {}", "VARIABLE_NO_DECLARADA");
        caso("funcion duplicada", "fn f() {} fn f() {}", "FUNCION_DUPLICADA");
        caso("parametro duplicado", "fn f(a: i32, a: i32) {}", "PARAMETRO_DUPLICADO");
        caso("retorno explicito", "fn f() -> i32 { return 1; }");
        caso("retorno implicito", "fn f() -> i32 { 1 }");
        caso("retorno incompatible", "fn f() -> i32 { return \"hola\"; }", "RETORNO");
        caso("retorno implicito incompatible", "fn f() -> i32 { \"hola\" }", "RETORNO");
        caso("retorno faltante", "fn f() -> i32 { let x = 1; }", "RETORNO");
        caso("return vacio incompatible", "fn f() -> i32 { return; }", "RETORNO");
        caso("funcion sin retorno anotado", programa("return 5;"), "RETORNO");
        caso("retornos todas ramas", "fn f(x: bool) -> i32 { if x { return 1; } else { return 2; } }");
        caso("rama sin retorno", "fn f(x: bool) -> i32 { if x { return 1; } }", "RETORNO");
        caso("valor if final", "fn f(x: bool) -> i32 { if x { 1 } else { 2 } }");
        caso("ramas incompatibles", "fn f(x: bool) -> i32 { if x { 1 } else { false } }", "RAMAS");
        caso("return en while no garantizado", "fn f(x: bool) -> i32 { while x { return 1; } }", "RETORNO");
        caso("loop divergente", "fn f() -> i32 { loop {} }");
        caso("while true divergente", "fn f() -> i32 { while true {} }");
        caso("macro vacia", programa("println!();"));
        caso("formato correcto", programa("println!(\"hola {} {{}}\", 1);"));
        caso("macro desconocida", programa("foo!();"), "MACRO_DESCONOCIDA");
        caso("formato no cadena", programa("println!(123);"), "FORMATO");
        caso("formato variable", programa("let s = \"hola\"; println!(s);"), "FORMATO");
        caso("formato incompleto", programa("println!(\"{\");"), "FORMATO");
        caso("marcador sin argumento", programa("println!(\"{}\");"), "FORMATO_ARIDAD");
        caso("argumento sin marcador", programa("println!(\"hola\", 1);"), "FORMATO_ARIDAD");
        caso("macro identificador inexistente", programa("println!(\"{}\", x);"), "VARIABLE_NO_DECLARADA");
        caso("macro valor unidad", programa("println!(\"{}\", f());") + "fn f() {}", "FORMATO_TIPO");
        caso("macro valor rango", programa("println!(\"{}\", 0..10);"), "FORMATO_TIPO");
        for (String tipo : new String[]{"i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize"}) {
            caso("tipo " + tipo, "fn f(x: " + tipo + ") -> " + tipo + " { x + 1 }");
        }
        caso("f32", "fn f(x: f32) -> f32 { x + 1.5 }");
        caso("f64", "fn f(x: f64) -> f64 { x + 1.5 }");
        caso("String", "fn f(x: String) -> String { x }");
        caso("retorno bool", "fn f(x: i32) -> bool { x > 0 }");
        caso("errores independientes", programa("let x = 1; x = true; y = 2; if 0 {}"), "INMUTABLE", "TIPO", "VARIABLE_NO_DECLARADA", "CONDICION");
        caso("sin cascada", programa("let x = desconocida; let y = x + 1;"), "VARIABLE_NO_DECLARADA");

        for (String codigo : new String[]{programa("let = 10;"), programa("contador += ;"), programa("println!(\"hola\";"), programa("let x = $;"), programa("if x > {}")}) {
            var resultado = FisioSemanticRunner.analizar(codigo);
            if (resultado.analizado() || resultado.exitoso() || resultado.motivo().isBlank()) throw new AssertionError("No bloqueo: " + codigo);
            casos++;
        }
        String multi = """
            fn main() {
                let = 10;
                let mut contador 0;
                contador += ;
                println!("Hola {}", contador;
                let resultado = sumar(10, );
                for i in 0.. { let valor = i; }
                if contador > { contador += 1; }
                while contador < { contador -= 1; }
                return 5
            }
            fn sumar(a: i32, b: i32) -> i32 { a + b }
            """;
        var sintaxis = FisioParserRunner.parse(multi);
        if (sintaxis.errores().size() != 9 || sintaxis.ast().sentencias().size() != 2
            || FisioLexerRunner.tokenize(multi).tokens().size() != 84
            || FisioSemanticRunner.analizar(multi).analizado()) throw new AssertionError("Regresion del fix sintactico previo");
        casos++;
        caso("ejemplo extenso", EjemplosFisio.codigoDe("Rust-like - programa extenso (matematicas)"));
        caso("ejemplo funciones", EjemplosFisio.codigoDe("Rust-like - funciones y println!"));
        caso("ejemplo rangos", EjemplosFisio.codigoDe("Rust-like - rangos y for"));
        caso("ejemplo operadores", EjemplosFisio.codigoDe("Rust-like - operadores"));
        caso("ejemplo inicial ahora detecta for escalar", EjemplosFisio.codigoDe("Rust-like - declaraciones y control"), "ITERABLE");
        caso("negativos y tipos detecta mezcla y retorno", EjemplosFisio.codigoDe("Fase 2: negativos y tipos"), "OPERADOR");
        caso("rango cambia inclusividad", programa("let mut r = 0..3; r = 0..=3;"), "TIPO");
        caso("rango inclusivo cambia a exclusivo", programa("let mut r = 0..=3; r = 0..3;"), "TIPO");
        caso("rango inclusivo reasignable", programa("let mut r = 0..=3; r = 1..=4; for i in r {}"));
        caso("rango conserva tipo extremo", programa("let fin: u8 = 3; let mut r = 0..=fin; r = 1..=4; for i in r { let x: u8 = i; }"));
        caso("descarte asignacion", programa("_ = 1; _ = \"texto\";"));
        caso("descarte valida valor", programa("_ = noExiste;"), "VARIABLE_NO_DECLARADA");
        caso("descarte compuesto invalido", programa("_ += 1;"), "DESTINO");
        caso("division entera genera cero", programa("let x = 5 / (1 / 2);"), "DIVISION_CERO");
        caso("resto genera cero", programa("let x = 5 / (4 % 2);"), "DIVISION_CERO");
        caso("division entera trunca hacia cero", programa("let x = 5 / (-1 / 2);"), "DIVISION_CERO");
        caso("division decimal no trunca", programa("let x = 5.0 / (1.0 / 2.0);"));
        caso("division decimal periodica", programa("let x = 5.0 / (1.0 / 3.0);"));
        caso("compuesta divisor entero cero", programa("let mut x = 5; x /= (1 / 2);"), "DIVISION_CERO");
        caso("operador invalido sin falso cero", programa("let x = \"hola\" / 0;"), "OPERADOR");
        caso("variable inexistente sin falso cero", programa("let x = desconocida / 0;"), "VARIABLE_NO_DECLARADA");
        caso("error constante interno no duplicado", programa("let x = 5 / (1 / 0);"), "DIVISION_CERO");
        caso("comparacion unaria contextual", "fn f(x: i8) -> bool { -(128) < x }");
        caso("argumento contextual anidado", programa("f(1 + (2 * 3));") + "fn f(x: u8) {} ");
        caso("aridad mas argumento desconocido", programa("f(noExiste, 2);") + "fn f(x: i32) {}", "ARIDAD", "VARIABLE_NO_DECLARADA");
        caso("funcion oculta por variable", programa("let f = 1; f();") + "fn f() {}", "NO_INVOCABLE");
        caso("retorno no implica valor sentencia", "fn f() -> i32 { 1; }", "RETORNO");
        caso("for no garantiza retorno", "fn f() -> i32 { for i in 0..=1 { return i; } }", "RETORNO");
        caso("errores tras return", programa("return; noExiste();"), "FUNCION_NO_DECLARADA");
        caso("for sombrea sin alterar exterior", programa("let mut i = true; for i in 0..3 { let n: i32 = i; } i = false;"));
        caso("parametros descarte repetidos", "fn f(_: i32, _: bool) {}");
        caso("descarte no crea variable", programa("let _ = 1; println!(\"{}\", _);"), "VARIABLE_NO_DECLARADA");
        caso("condicional descarta valores invalidos", programa("if true { 1 } else { 2 } let x = 0;"), "TIPO");
        caso("macro escapes comillas", programa("println!(\"dijo \\\"{}\\\"\", 1);"));
        for (String tipo : new String[]{"i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize"}) {
            boolean unsigned = tipo.startsWith("u");
            int bits = tipo.endsWith("size") ? 64 : Integer.parseInt(tipo.substring(1));
            var max = java.math.BigInteger.ONE.shiftLeft(unsigned ? bits : bits - 1).subtract(java.math.BigInteger.ONE);
            var min = unsigned ? java.math.BigInteger.ZERO : java.math.BigInteger.ONE.shiftLeft(bits - 1).negate();
            caso("limites completos " + tipo, programa("let min: " + tipo + " = " + min + "; let max: " + tipo + " = " + max + ";"));
            caso("supera maximo " + tipo, programa("let x: " + tipo + " = " + max.add(java.math.BigInteger.ONE) + ";"), "LITERAL_FUERA_DE_RANGO");
            caso("bajo minimo " + tipo, programa("let x: " + tipo + " = " + min.subtract(java.math.BigInteger.ONE) + ";"), "LITERAL_FUERA_DE_RANGO");
        }
        caso("redondeo f64 no es cero exacto", programa("let x = 1.0 / (0.1 + 0.2 - 0.3);"));
        caso("redondeo f32 produce cero", programa("let x: f32 = 1.0 / (16777216.0 + 1.0 - 16777216.0);"), "DIVISION_CERO");
        caso("misma expresion f64 no cero", programa("let x: f64 = 1.0 / (16777216.0 + 1.0 - 16777216.0);"));
        caso("constante decimal negativa agrupada", programa("let x = 1.0 / (-0.5 + 0.5);"), "DIVISION_CERO");
        caso("overflow intermedio no deduce cero", programa("let x: u8 = 1 / (255 + 1);"));
        caso("valor fuera de rango no deduce cero", programa("let x: u8 = 1 / (256 - 256);"), "LITERAL_FUERA_DE_RANGO", "LITERAL_FUERA_DE_RANGO");
        caso("desborde literal f32", programa("let x: f32 = 99999999999999999999999999999999999999999999999.0;"), "LITERAL_FUERA_DE_RANGO");
        caso("desborde literal f64", programa("let x: f64 = " + "9".repeat(320) + ".0;"), "LITERAL_FUERA_DE_RANGO");
        caso("cadenas igualdad", programa("let a = \"a\" == \"b\"; let b = \"a\" < \"b\";"));
        caso("resto cadenas rechazado", programa("let x = \"a\" % \"b\";"), "OPERADOR");
        caso("logico cadena rechazado", programa("let x = true && \"b\";"), "OPERADOR");
        caso("asignacion unidad incompatible", programa("let mut x = 1; x = f();") + "fn f() {}", "TIPO");
        caso("tipo desconocido sin cascada", programa("let x: Desconocido = 1; let y = x + 2;"), "TIPO_DESCONOCIDO");
        caso("funciones duplicadas comprueban cuerpos", "fn f() -> i32 { 1 } fn f() -> bool { 2 }", "FUNCION_DUPLICADA", "RETORNO");
        caso("parametro oculto en bloque", "fn f(x: i32) -> i32 { if true { let x = false; } x }");
        caso("rama divergente conserva retorno", "fn f(x: bool) -> i32 { if x { loop {} } else { 2 } }");
        caso("bucle condicional no garantiza retorno", "fn f(x: bool) -> i32 { while x {} }", "RETORNO");
        for (String fuente : List.of("fn f() {} /*", "fn f() { let x = 1e3; }", "fn f() { let x = ; }")) {
            var r = FisioSemanticRunner.analizar(fuente);
            if (r.analizado() || r.exitoso() || r.motivo().isBlank()) throw new AssertionError("Fase previa no bloqueó: " + fuente);
            casos++;
        }
        var posicion = FisioSemanticRunner.analizar("fn f() {\n  x = 1;\n}");
        if (posicion.errores().get(0).linea() != 2 || posicion.errores().get(0).columna() != 3)
            throw new AssertionError("Posición semántica incorrecta");
        if (!posicion.equals(FisioSemanticRunner.analizar("fn f() {\n  x = 1;\n}"))
                || !FisioSemanticRunner.analizar("fn f() {}").exitoso()) throw new AssertionError("Estado compartido entre ejecuciones");
        casos += 2;
        // Lista de comprobación solicitada: códigos y mensajes visibles al usuario.
        caso("check declaración estricta", programa("let cantidad: i32 = \"diez\";"), "TIPO");
        caso("check reasignación estricta", programa("let mut cantidad = 10; cantidad = \"diez\";"), "TIPO");
        casoMensaje("check fantasma operación", programa("let x = fantasma + 1;"), "VARIABLE_NO_DECLARADA", "no está declarada en este ámbito");
        casoMensaje("check fantasma macro", programa("println!(\"{}\", fantasma);"), "VARIABLE_NO_DECLARADA", "no está declarada en este ámbito");
        caso("check colisión binaria", programa("let x = 10 + \"texto\";"), "OPERADOR");
        caso("check condiciones booleanas", programa("if 1 {} while \"texto\" {}"), "CONDICION", "CONDICION");
        caso("check firma retorno", "fn f() -> i32 { return true; }", "RETORNO");
        casoMensaje("check retorno vacío", "fn f() -> i32 { return; }", "RETORNO", "Se encontró un retorno vacío");
        casoMensaje("check retorno con valor sin tipo", "fn f() { return 5; }", "RETORNO", "La función no puede devolver un valor");
        casoMensaje("check retorno implícito sin tipo", "fn f() { 5 }", "RETORNO", "La función no puede devolver un valor");
        caso("check mutabilidad simple y compuesta", programa("let x = 1; x = 2; x += 1;"), "INMUTABLE", "INMUTABLE");
        casoMensaje("check scope lectura", programa("if true { let local = 1; } println!(\"{}\", local);"), "VARIABLE_NO_DECLARADA", "no es accesible desde él");
        casoMensaje("check scope asignación", programa("if true { let mut local = 1; } local = 2;"), "VARIABLE_NO_DECLARADA", "no es accesible desde él");
        casoMensaje("check scope entre funciones", "fn a() { let local = 1; } fn b() { println!(\"{}\", local); }", "VARIABLE_NO_DECLARADA", "no está declarada en este ámbito");
        caso("check retornos vacíos válidos", "fn f() { return; } fn g() { return f(); }");
        caso("check variable exterior accesible", programa("let mut x = 1; if true { x += 1; } println!(\"{}\", x);"));
        caso("check retorno desconocido sin cascada", "fn f() { return fantasma; }", "VARIABLE_NO_DECLARADA");
        if (args.length > 0 && args[0].equals("--gui")) probarGui();
        System.out.println("PASS: " + casos + " casos semanticos");
    }

    private static void probarGui() throws Exception {
        fisio.gui.GuiRegression.main(new String[0]);
    }
}
