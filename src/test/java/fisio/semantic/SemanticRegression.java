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
        caso("complemento entero", programa("let x = !1;"));
        caso("comparacion incompatible", programa("let x = 1 == \"1\";"), "OPERADOR");
        caso("orden incompatible", programa("let x = true < false;"), "OPERADOR");
        caso("aritmetica mixta", programa("let x = 1 + 2.0;"), "OPERADOR");
        caso("tipos numericos distintos", programa("let a: i64 = 1; let b: i32 = 2; let x = a + b;"), "OPERADOR");
        caso("contexto literal", programa("let a: i64 = 1; let b = 2 + a; let c: i64 = b;"));
        caso("compuestas", programa("let mut x = 0; x += 1; x -= 2; x *= 3; x /= 4; x %= 5;"));
        caso("operadores de bits", programa("let a: u8 = 5; let b = (a << 1) | 1; let c = b ^ a; let d = c & b;"));
        caso("bits booleanos", programa("let x = true & false; let y = x | true; let z = y ^ false;"));
        caso("tipos incompatibles en bits", programa("let a: i32 = 1; let b: u8 = 2; let x = a & b;"), "OPERADOR");
        caso("asignaciones compuestas de bits", programa("let mut x: u8 = 3; x <<= 1; x >>= 1; x &= 7; x |= 8; x ^= 2;"));
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
        caso("break y continue en bucles", programa("let mut x = 0; loop { x += 1; if x < 2 { continue; } break; }"));
        caso("break fuera de bucle", programa("break;"), "CONTROL_FLUJO");
        caso("continue fuera de bucle", programa("continue;"), "CONTROL_FLUJO");
        caso("break vuelve finito a loop", "fn f() -> i32 { loop { break; } }", "RETORNO");
        caso("break vuelve finito a while true", "fn f() -> i32 { while true { break; } }", "RETORNO");
        caso("continue conserva loop divergente", "fn f() -> i32 { loop { continue; } }");
        caso("break inalcanzable no vuelve finito a loop", "fn f() -> i32 { loop { continue; break; } }");
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
        caso("tipo char", programa("let letra: char = 'R'; let otra = 'S'; let orden = letra < otra; println!(\"{} {}\", letra, orden);"));
        caso("char incompatible", programa("let letra: char = \"R\";"), "TIPO");
        caso("conversiones explicitas", programa("let byte: u8 = 65; let letra: char = byte as char; let codigo: i32 = letra as i32; let bandera: i32 = true as i32; let decimal: f64 = codigo as f64;"));
        caso("conversion no permitida", programa("let x = \"texto\" as i32;"), "CONVERSION");
        caso("tipo de conversion desconocido", programa("let x = 1 as Desconocido;"), "TIPO_DESCONOCIDO");
        caso("if como expresion", programa("let x: i32 = if true { 1 } else { 2 };"));
        caso("bloque como expresion", programa("let x: i32 = { let base = 2; base * 3 };"));
        caso("if expresion sin else", programa("let x = if true { 1 };"), "RAMAS");
        String tiposCompuestos = "struct Punto { x: i32, y: i32 } enum Estado { Activo, Inactivo } ";
        caso("struct y acceso a campos", tiposCompuestos
            + "fn suma(p: Punto) -> i32 { p.x + p.y } fn main() { let p: Punto = Punto { x: 2, y: 3 }; let n = suma(p); }");
        caso("enum y match exhaustivo", tiposCompuestos
            + "fn valor(e: Estado) -> i32 { match e { Estado::Activo => 1, Estado::Inactivo => 0, } }");
        caso("match con comodin", "enum Estado { Activo, Inactivo } fn valor(e: Estado) -> i32 { "
            + "match e { Estado::Activo => 1, _ => 0, } }");
        caso("tipos de usuario adelantados", "struct Contenedor { estado: Estado } enum Estado { Activo } "
            + "fn main() { let c = Contenedor { estado: Estado::Activo }; }");
        caso("tipo de usuario duplicado", "struct Dato {} enum Dato { Uno } fn main() {}", "TIPO_DUPLICADO");
        caso("tipo primitivo no redefinible", "struct i32 {} fn main() {}", "TIPO_DUPLICADO");
        caso("campo de tipo desconocido", "struct Dato { valor: Nada } fn main() {}", "TIPO_DESCONOCIDO");
        caso("struct recursivo directo", "struct Nodo { siguiente: Nodo } fn main() {}", "TIPO_RECURSIVO");
        caso("structs recursivos indirectos", "struct A { b: B } struct B { a: A } fn main() {}", "TIPO_RECURSIVO");
        caso("campo definido dos veces", "struct Dato { valor: i32, valor: i32 } fn main() {}", "CAMPO_DUPLICADO");
        caso("variante definida dos veces", "enum Estado { Activo, Activo } fn main() {}", "VARIANTE_DUPLICADA");
        caso("campo faltante", "struct Punto { x: i32, y: i32 } fn main() { let p = Punto { x: 1 }; }", "CAMPO_FALTANTE");
        caso("campo desconocido en literal", "struct Punto { x: i32 } fn main() { let p = Punto { x: 1, z: 2 }; }", "CAMPO_DESCONOCIDO");
        caso("campo duplicado en literal", "struct Punto { x: i32 } fn main() { let p = Punto { x: 1, x: 2 }; }", "CAMPO_DUPLICADO");
        caso("tipo de campo incompatible", "struct Punto { x: i32 } fn main() { let p = Punto { x: \"uno\" }; }", "CAMPO");
        caso("lectura de campo inexistente", "struct Punto { x: i32 } fn main() { let p = Punto { x: 1 }; let y = p.y; }", "CAMPO_DESCONOCIDO");
        caso("acceso sobre escalar", programa("let x = 1; let y = x.valor;"), "ACCESO_CAMPO");
        caso("asignacion a campo mutable", "struct Punto { x: i32 } fn main() { let mut p = Punto { x: 1 }; p.x = 2; p.x += 3; }");
        caso("asignacion a campo inmutable", "struct Punto { x: i32 } fn main() { let p = Punto { x: 1 }; p.x = 2; }", "INMUTABLE");
        caso("tipo incompatible en campo", "struct Punto { x: i32 } fn main() { let mut p = Punto { x: 1 }; p.x = false; }", "TIPO");
        caso("campo destino inexistente", "struct Punto { x: i32 } fn main() { let mut p = Punto { x: 1 }; p.y = 2; }", "CAMPO_DESCONOCIDO");
        caso("raiz de campo inexistente", programa("fantasma.x = 1;"), "VARIABLE_NO_DECLARADA");
        caso("variante inexistente", "enum Estado { Activo } fn main() { let e = Estado::Inactivo; }", "VARIANTE_DESCONOCIDA");
        caso("match no exhaustivo", "enum Estado { Activo, Inactivo } fn f(e: Estado) -> i32 { "
            + "match e { Estado::Activo => 1, } }", "MATCH_NO_EXHAUSTIVO");
        caso("patron duplicado", "enum Estado { Activo, Inactivo } fn f(e: Estado) -> i32 { match e { "
            + "Estado::Activo => 1, Estado::Activo => 2, Estado::Inactivo => 0, } }", "PATRON_DUPLICADO");
        caso("brazo despues de comodin", "enum Estado { Activo } fn f(e: Estado) -> i32 { "
            + "match e { _ => 0, Estado::Activo => 1, } }", "PATRON_INALCANZABLE");
        caso("tipos distintos en match", "enum Estado { Activo, Inactivo } fn main() { let x = match Estado::Activo { "
            + "Estado::Activo => 1, Estado::Inactivo => false, }; }", "RAMAS");
        caso("match requiere enum", "enum Estado { Activo } fn main() { let x = match 1 { Estado::Activo => 1, _ => 0, }; }", "MATCH_TIPO");
        caso("arreglo inferido", programa("let valores = [1, 2, 3]; let primero: i32 = valores[0];"));
        caso("arreglo anotado", programa("let valores: [u8; 3] = [1, 2, 3]; let ultimo: u8 = valores[2];"));
        caso("arreglo vacio anotado", programa("let vacio: [i32; 0] = [];"));
        caso("arreglo vacio sin tipo", programa("let vacio = [];"), "ARREGLO_VACIO");
        caso("arreglo heterogeneo", programa("let valores = [1, true];"), "ELEMENTO_ARREGLO");
        caso("elemento arreglo incompatible", programa("let valores: [i32; 2] = [1, false];"), "ELEMENTO_ARREGLO");
        caso("longitud arreglo incompatible", programa("let valores: [i32; 3] = [1, 2];"), "LONGITUD_ARREGLO");
        caso("indice arreglo no entero", programa("let valores = [1, 2]; let x = valores[\"0\"];"), "INDICE_TIPO");
        caso("indexar escalar", programa("let valor = 1; let x = valor[0];"), "INDICE");
        caso("indice fuera de rango", programa("let valores = [1, 2]; let x = valores[2];"), "INDICE_FUERA_DE_RANGO");
        caso("indice negativo", programa("let valores = [1, 2]; let x = valores[-1];"), "INDICE_FUERA_DE_RANGO");
        caso("asignacion a indice mutable", programa("let mut valores = [1, 2]; valores[0] = 3; valores[1] += 4;"));
        caso("asignacion a indice inmutable", programa("let valores = [1, 2]; valores[0] = 3;"), "INMUTABLE");
        caso("tipo incompatible en indice", programa("let mut valores = [1, 2]; valores[0] = false;"), "TIPO");
        caso("for sobre arreglo", programa("let valores: [u8; 3] = [1, 2, 3]; for valor in valores { let copia: u8 = valor; }"));
        caso("tupla inferida y accesos", programa("let datos = (1, true, 'F'); let n: i32 = datos.0; let b: bool = datos.1; let c: char = datos.2;"));
        caso("tupla anotada", programa("let datos: (u8, bool) = (1, true);"));
        caso("tupla unitaria", programa("let dato: (i32,) = (1,); let n: i32 = dato.0;"));
        caso("unidad literal", programa("let unidad: () = ();"));
        caso("elemento tupla incompatible", programa("let datos: (i32, bool) = (true, false);"), "ELEMENTO_TUPLA");
        caso("longitud tupla incompatible", programa("let datos: (i32, bool) = (1, true, 'F');"), "LONGITUD_TUPLA");
        caso("indice tupla fuera de rango", programa("let datos = (1, true); let x = datos.2;"), "INDICE_TUPLA");
        caso("acceso tupla sobre escalar", programa("let dato = 1; let x = dato.0;"), "ACCESO_TUPLA");
        caso("asignacion a tupla mutable", programa("let mut datos = (1, true); datos.0 += 2; datos.1 = false;"));
        caso("asignacion a tupla inmutable", programa("let datos = (1, true); datos.0 = 2;"), "INMUTABLE");
        caso("firma con arreglos y tuplas", "fn combinar(a: [i32; 2], t: (i32, bool)) -> [i32; 2] { [a[0] + t.0, a[1]] } "
            + "fn main() { let r: [i32; 2] = combinar([1, 2], (3, true)); }");
        caso("struct con tipos compuestos", "struct Datos { valores: [i32; 2], estado: (bool, char) } fn main() { "
            + "let d = Datos { valores: [1, 2], estado: (true, 'F') }; let x = d.valores[1]; let c = d.estado.1; }");
        caso("struct recursivo mediante arreglo", "struct Nodo { hijos: [Nodo; 1] } fn main() {}", "TIPO_RECURSIVO");
        caso("macro no muestra arreglo directamente", programa("let valores = [1, 2]; println!(\"{}\", valores);"), "FORMATO_TIPO");
        caso("constante global", "const BASE: i32 = 10; fn main() { let x: i32 = BASE; }");
        caso("constante global adelantada", "const DOBLE: i32 = BASE * 2; const BASE: i32 = 10; "
            + "fn main() { let x: i32 = DOBLE; }");
        caso("constante local", programa("const FACTOR: i32 = 2; let x = FACTOR * 5;"));
        caso("constante compuesta", "const DATOS: ([i32; 2], (bool, char)) = ([1, 2], (true, 'F')); "
            + "fn main() { let n: i32 = DATOS.0[1]; let c: char = (DATOS.1).1; }");
        caso("constante condicional", "const VALOR: i32 = if true { 1 } else { 2 }; fn main() { let x = VALOR; }");
        caso("tipo de constante incompatible", "const TEXTO: i32 = \"hola\"; fn main() {}", "TIPO_CONSTANTE");
        caso("constante no captura variable", programa("let base = 10; const DOBLE: i32 = base * 2;"),
            "CONSTANTE_NO_EVALUABLE");
        caso("constante no llama funcion", "fn obtener() -> i32 { 1 } const VALOR: i32 = obtener(); fn main() {}",
            "CONSTANTE_NO_EVALUABLE");
        caso("bloque no constante", "const VALOR: i32 = { let x = 1; x }; fn main() {}", "CONSTANTE_NO_EVALUABLE");
        caso("constante global inmutable", "const BASE: i32 = 10; fn main() { BASE = 20; }", "INMUTABLE");
        caso("constante local inmutable", programa("const BASE: i32 = 10; BASE += 1;"), "INMUTABLE");
        caso("constante global duplicada", "const BASE: i32 = 1; const BASE: i32 = 2; fn main() {}",
            "CONSTANTE_DUPLICADA");
        caso("constante local duplicada", programa("const BASE: i32 = 1; const BASE: i32 = 2;"),
            "CONSTANTE_DUPLICADA");
        caso("constante ciclica directa", "const A: i32 = A; fn main() {}", "CONSTANTE_CICLICA");
        caso("constantes ciclicas indirectas", "const A: i32 = B; const B: i32 = A; fn main() {}",
            "CONSTANTE_CICLICA");
        caso("constante inexistente", "const A: i32 = FANTASMA; fn main() {}", "VARIABLE_NO_DECLARADA");
        caso("alcance constante local", programa("if true { const LOCAL: i32 = 1; } let x = LOCAL;"),
            "VARIABLE_NO_DECLARADA");
        caso("constante local sombrea global", "const BASE: i32 = 1; fn main() { const BASE: bool = true; let x: bool = BASE; }");
        caso("colision constante y funcion", "const valor: i32 = 1; fn valor() -> i32 { 2 } fn main() {}",
            "SIMBOLO_DUPLICADO");
        caso("items y campos publicos", "pub const BASE: i32 = 1; pub struct Dato { pub valor: i32 } "
            + "pub enum Estado { Activo } pub fn leer(d: Dato) -> i32 { d.valor + BASE } "
            + "fn main() { let d = Dato { valor: 2 }; let x = leer(d); let e = Estado::Activo; }");
        caso("alias simple transparente", "type Entero = i32; fn identidad(x: Entero) -> i32 { x } "
            + "fn main() { let x: Entero = identidad(1); }");
        caso("alias adelantado y encadenado", "type A = B; type B = C; type C = i32; "
            + "fn main() { let x: A = 1; let y: i32 = x; }");
        caso("alias compuesto", "type Entero = i32; type Vector = [Entero; 2]; type Par = (Entero, bool); "
            + "fn f(v: Vector, p: Par) -> Entero { v[0] + p.0 } fn main() { let x = f([1, 2], (3, true)); }");
        caso("alias de struct adelantado", "type PuntoAlias = Punto; struct Punto { x: i32 } "
            + "fn main() { let p: PuntoAlias = Punto { x: 1 }; let x = p.x; }");
        caso("alias publico", "pub type Entero = i32; pub const BASE: Entero = 1; fn main() { let x: Entero = BASE; }");
        caso("alias desconocido", "type Dato = Desconocido; fn main() {}", "TIPO_DESCONOCIDO");
        caso("alias ciclico directo", "type A = A; fn main() {}", "ALIAS_CICLICO");
        caso("alias ciclico indirecto", "type A = B; type B = A; fn main() {}", "ALIAS_CICLICO");
        caso("alias ciclico compuesto", "type A = [B; 1]; type B = (A,); fn main() {}", "ALIAS_CICLICO");
        caso("alias duplicado", "type A = i32; type A = bool; fn main() {}", "TIPO_DUPLICADO");
        caso("alias colisiona con struct", "struct A {} type A = i32; fn main() {}", "TIPO_DUPLICADO");
        caso("alias no redefine primitivo", "type i32 = bool; fn main() {}", "TIPO_DUPLICADO");
        caso("struct recursivo mediante alias", "type Otro = Nodo; struct Nodo { siguiente: Otro } fn main() {}",
            "TIPO_RECURSIVO");
        caso("static global", "static VERSION: i32 = 1; fn main() { let x: i32 = VERSION; }");
        caso("pub static con alias", "type Entero = i32; pub static VERSION: Entero = 1; "
            + "fn main() { let x: i32 = VERSION; }");
        caso("static usa constante adelantada", "static MAXIMO: i32 = BASE * 10; const BASE: i32 = 10; "
            + "fn main() { let x = MAXIMO; }");
        caso("static compuesto", "static DATOS: ([i32; 2], bool) = ([1, 2], true); "
            + "fn main() { let x: i32 = DATOS.0[1]; }");
        caso("static local", programa("static AJUSTE: i32 = 5; let x = AJUSTE + 1;"));
        caso("static tipo incompatible", "static TEXTO: i32 = \"hola\"; fn main() {}", "TIPO_STATIC");
        caso("static no captura variable", programa("let base = 1; static VALOR: i32 = base;"),
            "STATIC_NO_CONSTANTE");
        caso("static no depende de static", "static A: i32 = 1; static B: i32 = A + 1; fn main() {}",
            "STATIC_NO_CONSTANTE");
        caso("static mut requiere unsafe", "static mut CONTADOR: i32 = 0; fn main() {}", "STATIC_MUT_UNSAFE");
        caso("static inmutable", "static BASE: i32 = 1; fn main() { BASE = 2; }", "INMUTABLE");
        caso("static duplicado", "static BASE: i32 = 1; static BASE: i32 = 2; fn main() {}", "STATIC_DUPLICADO");
        caso("static colisiona constante", "const BASE: i32 = 1; static BASE: i32 = 2; fn main() {}", "STATIC_DUPLICADO");
        caso("static colisiona funcion", "static valor: i32 = 1; fn valor() -> i32 { 2 } fn main() {}",
            "SIMBOLO_DUPLICADO");
        caso("alcance static local", programa("if true { static LOCAL: i32 = 1; } let x = LOCAL;"),
            "VARIABLE_NO_DECLARADA");
        caso("referencia compartida", programa("let valor: i32 = 4; let r: &i32 = &valor; let copia: i32 = *r;"));
        caso("referencia mutable", programa("let mut valor: i32 = 4; let r: &mut i32 = &mut valor; *r += 2;"));
        caso("referencias en funciones", "fn leer(x: &i32) -> i32 { *x } "
            + "fn incrementar(x: &mut i32) { *x += 1; } "
            + "fn main() { let mut n = 1; let r = &n; let x = leer(r); let m = &mut n; incrementar(m); }");
        caso("referencia a elemento mutable", programa("let mut datos = [1, 2]; let r = &mut datos[0]; *r = 3;"));
        caso("struct recursivo mediante referencia", "struct Nodo { siguiente: &Nodo } fn main() {}");
        caso("tipo de referencia incompatible", programa("let valor = 1; let r: &bool = &valor;"), "TIPO");
        caso("referencia mutable a inmutable", programa("let valor = 1; let r = &mut valor;"), "REFERENCIA_MUTABLE");
        caso("escritura mediante referencia compartida", programa("let mut valor = 1; let r = &valor; *r = 2;"),
            "REFERENCIA_INMUTABLE");
        caso("desreferenciar escalar", programa("let valor = 1; let copia = *valor;"), "DESREFERENCIA");
        caso("referencia requiere lugar", programa("let r = &(1 + 2);"), "REFERENCIA_DESTINO");
        caso("asignacion incompatible mediante referencia", programa(
            "let mut valor = 1; let r = &mut valor; *r = false;"), "TIPO");
        caso("alcance de referencia", programa("if true { let valor = 1; let r = &valor; } let copia = *r;"),
            "VARIABLE_NO_DECLARADA");
        caso("impl con función asociada", "struct Punto { x: i32 } impl Punto { "
            + "fn nuevo(x: i32) -> Punto { Punto { x: x } } } "
            + "fn main() { let p: Punto = Punto::nuevo(2); }");
        caso("método con self compartido", "struct Punto { x: i32 } impl Punto { "
            + "fn valor(&self) -> i32 { self.x } } fn main() { let p = Punto { x: 2 }; let x = p.valor(); }");
        caso("método con self mutable", "struct Punto { x: i32 } impl Punto { "
            + "fn cambiar(&mut self, x: i32) { self.x = x; } } "
            + "fn main() { let mut p = Punto { x: 1 }; p.cambiar(3); }");
        caso("método sobre referencia mutable", "struct Punto { x: i32 } impl Punto { "
            + "fn cambiar(&mut self, x: i32) { self.x = x; } } "
            + "fn main() { let mut p = Punto { x: 1 }; let r = &mut p; r.cambiar(3); }");
        caso("método de enum", "enum Estado { Activo } impl Estado { fn es_activo(&self) -> bool { true } } "
            + "fn main() { let e = Estado::Activo; let activo = e.es_activo(); }");
        caso("impl de tipo inexistente", "impl Fantasma { fn crear() -> i32 { 1 } } fn main() {}", "IMPL_TIPO");
        caso("método duplicado", "struct Dato {} impl Dato { fn f(&self) {} } "
            + "impl Dato { fn f(&self) {} } fn main() {}", "METODO_DUPLICADO");
        caso("método inexistente", "struct Dato {} fn main() { let d = Dato {}; d.f(); }", "METODO_NO_DECLARADO");
        caso("función asociada inexistente", "struct Dato {} fn main() { let d = Dato::crear(); }",
            "FUNCION_ASOCIADA_NO_DECLARADA");
        caso("método llamado con dos puntos", "struct Dato {} impl Dato { fn f(&self) {} } "
            + "fn main() { Dato::f(); }", "METODO_REQUIERE_RECEPTOR");
        caso("asociada llamada como método", "struct Dato {} impl Dato { fn crear() -> Dato { Dato {} } } "
            + "fn main() { let d = Dato {}; let x = d.crear(); }", "FUNCION_ASOCIADA_SIN_RECEPTOR");
        caso("receptor mutable requerido", "struct Dato { x: i32 } impl Dato { "
            + "fn cambiar(&mut self) { self.x = 2; } } fn main() { let d = Dato { x: 1 }; d.cambiar(); }",
            "RECEPTOR_MUTABLE");
        caso("self compartido no modifica", "struct Dato { x: i32 } impl Dato { "
            + "fn cambiar(&self) { self.x = 2; } } fn main() {}", "INMUTABLE");
        caso("argumento de método incompatible", "struct Dato {} impl Dato { fn f(&self, x: i32) {} } "
            + "fn main() { let d = Dato {}; d.f(false); }", "ARGUMENTO");
        caso("aridad de método", "struct Dato {} impl Dato { fn f(&self, x: i32) {} } "
            + "fn main() { let d = Dato {}; d.f(); }", "ARIDAD");
        caso("self fuera de primera posición", "struct Dato {} impl Dato { fn f(x: i32, self) {} } fn main() {}",
            "RECEPTOR_SELF");
        caso("trait implementado", "trait Medible { fn crear(x: i32) -> Self; fn valor(&self) -> i32; } "
            + "struct Dato { x: i32 } impl Medible for Dato { "
            + "fn crear(x: i32) -> Dato { Dato { x: x } } fn valor(&self) -> i32 { self.x } } "
            + "fn main() { let d = Dato::crear(2); let x = d.valor(); }");
        caso("trait para enum", "trait Activo { fn activo(&self) -> bool; } enum Estado { Si } "
            + "impl Activo for Estado { fn activo(&self) -> bool { true } } "
            + "fn main() { let e = Estado::Si; let x = e.activo(); }");
        caso("trait con Self compuesto", "trait Copiable { fn copiar(&self, otro: &Self) -> (Self, Self); } "
            + "struct Dato {} impl Copiable for Dato { "
            + "fn copiar(&self, otro: &Dato) -> (Dato, Dato) { (Dato {}, Dato {}) } } fn main() {}");
        caso("trait inexistente", "struct Dato {} impl Fantasma for Dato { fn f() {} } fn main() {}",
            "TRAIT_NO_DECLARADO");
        caso("método faltante de trait", "trait T { fn a(&self); fn b(&self); } struct Dato {} "
            + "impl T for Dato { fn a(&self) {} } fn main() {}", "METODO_TRAIT_FALTANTE");
        caso("método extra de trait", "trait T { fn a(&self); } struct Dato {} "
            + "impl T for Dato { fn a(&self) {} fn b(&self) {} } fn main() {}", "METODO_TRAIT_EXTRA");
        caso("firma de trait incompatible", "trait T { fn f(&self, x: i32) -> i32; } struct Dato {} "
            + "impl T for Dato { fn f(&self, x: bool) -> bool { x } } fn main() {}", "FIRMA_TRAIT");
        caso("mutabilidad self incompatible", "trait T { fn f(&self); } struct Dato {} "
            + "impl T for Dato { fn f(&mut self) {} } fn main() {}", "FIRMA_TRAIT");
        caso("implementación trait duplicada", "trait T { fn f(&self); } struct Dato {} "
            + "impl T for Dato { fn f(&self) {} } impl T for Dato { fn f(&self) {} } fn main() {}",
            "IMPL_TRAIT_DUPLICADO");
        caso("método trait duplicado", "trait T { fn f(&self); fn f(&self); } struct Dato {} fn main() {}",
            "METODO_TRAIT_DUPLICADO");
        caso("trait duplicado", "trait T {} trait T {} fn main() {}", "TIPO_DUPLICADO");
        caso("trait colisiona struct", "struct T {} trait T {} fn main() {}", "TIPO_DUPLICADO");
        caso("método trait no lleva pub", "trait T { fn f(&self); } struct Dato {} "
            + "impl T for Dato { pub fn f(&self) {} } fn main() {}", "VISIBILIDAD_TRAIT");
        caso("struct de tupla", "struct Punto(i32, bool); fn main() { let mut p = Punto(1, true); "
            + "let x: i32 = p.0; let b: bool = p.1; p.0 += 2; }");
        caso("struct de tupla en firma", "struct Punto(i32, i32); fn sumar(p: Punto) -> i32 { p.0 + p.1 } "
            + "fn main() { let x = sumar(Punto(2, 3)); }");
        caso("constructor struct tupla aridad", "struct Punto(i32, i32); fn main() { let p = Punto(1); }", "ARIDAD");
        caso("constructor struct tupla tipo", "struct Punto(i32, bool); fn main() { let p = Punto(1, 2); }", "ARGUMENTO");
        caso("struct tupla con llaves", "struct Punto(i32); fn main() { let p = Punto { x: 1 }; }", "FORMA_STRUCT");
        caso("struct nombrado como función", "struct Punto { x: i32 } fn main() { let p = Punto(1); }", "FORMA_STRUCT");
        caso("campo nombrado en struct tupla", "struct Punto(i32); fn main() { let p = Punto(1); let x = p.x; }",
            "ACCESO_CAMPO");
        caso("posición inexistente struct tupla", "struct Punto(i32); fn main() { let p = Punto(1); let x = p.1; }",
            "INDICE_TUPLA");
        caso("enum con datos", "enum Opcion { Nada, Valor(i32), Par(i32, bool) } fn main() { "
            + "let a = Opcion::Nada; let b = Opcion::Valor(2); let c = Opcion::Par(3, true); }");
        caso("match extrae datos", "enum Opcion { Nada, Valor(i32), Par(i32, i32) } "
            + "fn valor(o: Opcion) -> i32 { match o { Opcion::Nada => 0, Opcion::Valor(x) => x, "
            + "Opcion::Par(a, b) => a + b, } } fn main() { let x = valor(Opcion::Valor(3)); }");
        caso("constructor variante aridad", "enum Opcion { Valor(i32) } fn main() { let x = Opcion::Valor(); }", "ARIDAD");
        caso("constructor variante tipo", "enum Opcion { Valor(i32) } fn main() { let x = Opcion::Valor(false); }",
            "ARGUMENTO");
        caso("variante requiere datos", "enum Opcion { Valor(i32) } fn main() { let x = Opcion::Valor; }",
            "VARIANTE_REQUIERE_DATOS");
        caso("patrón variante aridad", "enum Opcion { Valor(i32) } fn f(o: Opcion) -> i32 { "
            + "match o { Opcion::Valor => 1, } }", "PATRON_ARIDAD");
        caso("vinculación duplicada", "enum Par { Valor(i32, i32) } fn f(p: Par) -> i32 { "
            + "match p { Par::Valor(x, x) => x, } }", "VINCULACION_DUPLICADA");
        caso("alcance de vinculación", "enum Opcion { Valor(i32) } fn main() { "
            + "let y = match Opcion::Valor(1) { Opcion::Valor(x) => x, }; let z = x; }",
            "VARIABLE_NO_DECLARADA");
        caso("enum recursivo directo", "enum Lista { Fin, Siguiente(Lista) } fn main() {}", "TIPO_RECURSIVO");
        caso("enums recursivos indirectos", "enum A { B(B) } enum B { A(A) } fn main() {}", "TIPO_RECURSIVO");
        caso("ciclo struct enum", "struct A { b: B } enum B { A(A) } fn main() {}", "TIPO_RECURSIVO");
        caso("enum recursivo por referencia", "enum Lista { Fin, Siguiente(&Lista) } fn main() {} ");
        caso("constante algebraica", "struct Punto(i32, i32); enum Opcion { Valor(Punto) } "
            + "const P: Punto = Punto(1, 2); const O: Opcion = Opcion::Valor(P); fn main() {}");
        caso("variante con campos nombrados", "enum Evento { Punto { x: i32, y: i32 }, Nada } "
            + "fn suma(e: Evento) -> i32 { match e { Evento::Punto { x, y: otro } => x + otro, "
            + "Evento::Nada => 0, } } fn main() { let x = 2; "
            + "let e = Evento::Punto { x, y: 3 }; let total = suma(e); }");
        caso("guarda accede a vinculaciones", "enum E { Valor(i32), Nada } fn f(e: E) -> i32 { match e { "
            + "E::Valor(x) if x > 0 => x, E::Valor(_) => 0, E::Nada => 0, } } fn main() {}");
        caso("guardas repetidas no duplican patrón", "enum E { V(i32) } fn f(e: E) -> i32 { match e { "
            + "E::V(x) if x > 10 => 2, E::V(x) if x > 0 => 1, E::V(_) => 0, } } fn main() {}");
        caso("campo desconocido al construir variante", "enum E { V { x: i32 } } fn main() { "
            + "let e = E::V { z: 1 }; }", "CAMPO_DESCONOCIDO", "CAMPO_FALTANTE");
        caso("campo faltante al construir variante", "enum E { V { x: i32, y: bool } } fn main() { "
            + "let e = E::V { x: 1 }; }", "CAMPO_FALTANTE");
        caso("campo duplicado al construir variante", "enum E { V { x: i32 } } fn main() { "
            + "let e = E::V { x: 1, x: 2 }; }", "CAMPO_DUPLICADO");
        caso("tipo de campo en variante", "enum E { V { x: i32 } } fn main() { "
            + "let e = E::V { x: false }; }", "CAMPO");
        caso("forma nombrada como tupla", "enum E { V { x: i32 } } fn main() { let e = E::V(1); }",
            "FORMA_VARIANTE");
        caso("forma tupla como nombrada", "enum E { V(i32) } fn main() { let e = E::V { x: 1 }; }",
            "FORMA_VARIANTE");
        caso("forma de patrón nombrado", "enum E { V { x: i32 } } fn f(e: E) -> i32 { "
            + "match e { E::V => 0, } }", "FORMA_PATRON");
        caso("campo desconocido en patrón", "enum E { V { x: i32 } } fn f(e: E) -> i32 { "
            + "match e { E::V { z } => 0, } }", "CAMPO_DESCONOCIDO", "CAMPO_PATRON_FALTANTE");
        caso("campo faltante en patrón", "enum E { V { x: i32, y: i32 } } fn f(e: E) -> i32 { "
            + "match e { E::V { x } => x, } }", "CAMPO_PATRON_FALTANTE");
        caso("campo duplicado en patrón", "enum E { V { x: i32 } } fn f(e: E) -> i32 { "
            + "match e { E::V { x, x: _ } => x, } }", "CAMPO_DUPLICADO");
        caso("guarda debe ser bool", "enum E { V(i32) } fn f(e: E) -> i32 { match e { "
            + "E::V(x) if x => 1, E::V(_) => 0, } }", "GUARDA_MATCH");
        caso("guarda no aporta exhaustividad", "enum E { V(i32) } fn f(e: E) -> i32 { match e { "
            + "E::V(x) if x > 0 => x, } }", "MATCH_NO_EXHAUSTIVO");
        caso("patrón de otro enum no aporta exhaustividad", "enum E { A } enum F { A } "
            + "fn f(e: E) -> i32 { match e { F::A => 1, } }", "PATRON_TIPO", "MATCH_NO_EXHAUSTIVO");
        caso("enum nombrado recursivo", "enum Lista { Fin, Sig { resto: Lista } } fn main() {}",
            "TIPO_RECURSIVO");
        caso("enum nombrado recursivo por referencia", "enum Lista { Fin, Sig { resto: &Lista } } fn main() {}");
        caso("constante de variante nombrada", "enum E { V { x: i32 } } const DATO: E = E::V { x: 1 }; fn main() {}");
        caso("campos abreviados de struct", "struct P { x: i32, y: i32 } fn main() { "
            + "let x = 1; let y = 2; let p = P { x, y }; }");
        caso("actualización funcional de struct", "struct P { x: i32, y: i32, activo: bool } fn main() { "
            + "let base = P { x: 1, y: 2, activo: true }; let x = 5; let nuevo = P { x, ..base }; }");
        caso("base de actualización incompatible", "struct A { x: i32 } struct B { x: i32 } fn main() { "
            + "let b = B { x: 1 }; let a = A { ..b }; }", "BASE_STRUCT");
        caso("base de actualización inexistente", "struct P { x: i32 } fn main() { "
            + "let p = P { ..fantasma }; }", "VARIABLE_NO_DECLARADA");
        caso("campo desconocido aun con base", "struct P { x: i32 } fn main() { "
            + "let base = P { x: 1 }; let p = P { z: 2, ..base }; }", "CAMPO_DESCONOCIDO");
        caso("shorthand requiere variable", "struct P { x: i32 } fn main() { let p = P { x }; }",
            "VARIABLE_NO_DECLARADA");
        caso("patrón nombrado parcial", "enum E { V { x: i32, y: bool, z: String } } "
            + "fn f(e: E) -> i32 { match e { E::V { x, .. } => x, } } fn main() {}");
        caso("patrón posicional parcial", "enum E { V(i32, bool, String) } "
            + "fn f(e: E) -> i32 { match e { E::V(x, ..) => x, } } fn main() {}");
        caso("patrón ignora todos los datos", "enum E { A(i32, bool), B { x: i32 } } "
            + "fn f(e: E) -> i32 { match e { E::A(..) => 1, E::B { .. } => 2, } } fn main() {}");
        caso("demasiadas vinculaciones antes del resto", "enum E { V(i32) } "
            + "fn f(e: E) -> i32 { match e { E::V(a, b, ..) => 0, } }", "PATRON_ARIDAD");
        caso("constante con actualización", "struct P { x: i32, y: i32 } "
            + "const BASE: P = P { x: 1, y: 2 }; const NUEVO: P = P { x: 3, ..BASE }; fn main() {}");
        caso("if let posicional", "enum E { Valor(i32), Nada } fn leer(e: E) -> i32 { "
            + "if let E::Valor(x) = e { x } else { 0 } } fn main() {}");
        caso("if let con campos y resto", "enum E { Dato { x: i32, activo: bool }, Nada } "
            + "fn leer(e: E) -> i32 { if let E::Dato { x, .. } = e { x } else { 0 } } fn main() {}");
        caso("cadena else if let", "enum E { A(i32), B(i32), C } fn leer(e: E) -> i32 { "
            + "if let E::A(x) = e { x } else if let E::B(y) = e { y } else { 0 } } fn main() {}");
        caso("alcance de if let", "enum E { V(i32) } fn main() { "
            + "if let E::V(x) = E::V(1) { let y = x; } let z = x; }", "VARIABLE_NO_DECLARADA");
        caso("if let requiere enum", "enum E { V(i32) } fn main() { "
            + "if let E::V(x) = 1 { let y = x; } }", "PATRON_TIPO");
        caso("if let patrón de otro enum", "enum E { V(i32) } enum F { V(i32) } fn main() { "
            + "let e = E::V(1); if let F::V(x) = e { let y = x; } }", "PATRON_TIPO");
        caso("if let variante inexistente", "enum E { V(i32) } fn main() { "
            + "let e = E::V(1); if let E::Nada = e {} }", "VARIANTE_DESCONOCIDA");
        caso("if let aridad de patrón", "enum E { V(i32, bool) } fn main() { "
            + "let e = E::V(1, true); if let E::V(x) = e {} }", "PATRON_ARIDAD");
        caso("while let y reasignación", "enum E { V(i32), Fin } fn main() { let mut e = E::V(2); "
            + "while let E::V(x) = e { if x > 0 { e = E::Fin; } } }");
        caso("alcance de while let", "enum E { V(i32), Fin } fn main() { let mut e = E::V(1); "
            + "while let E::V(x) = e { e = E::Fin; } let y = x; }", "VARIABLE_NO_DECLARADA");
        caso("while let requiere enum", "enum E { V(i32) } fn main() { "
            + "while let E::V(x) = 1 { break; } }", "PATRON_TIPO");
        caso("guarda de if let usa vinculacion", "enum E { V(i32), N } fn f(e: E) -> i32 { "
            + "if let E::V(x) = e && x > 0 { x } else { 0 } } fn main() {}");
        caso("guarda de while let usa vinculacion", "enum E { V(i32), N } fn main() { "
            + "let mut e = E::V(2); while let E::V(x) = e && x > 0 { e = E::N; } }");
        caso("guarda if let estrictamente booleana", "enum E { V(i32) } fn f(e: E) { "
            + "if let E::V(x) = e && x {} }", "CONDICION");
        caso("guarda while let estrictamente booleana", "enum E { V(i32) } fn f(e: E) { "
            + "while let E::V(x) = e && x { break; } }", "CONDICION");
        caso("alcance guarda if let", "enum E { V(i32) } fn f(e: E) { "
            + "if let E::V(x) = e && fantasma > x {} }", "VARIABLE_NO_DECLARADA");
        caso("if let comodín irrefutable", "fn main() { if let _ = 1 { let x = 2; } }");
        caso("while let comodín divergente", "fn f() -> i32 { while let _ = 1 {} } fn main() {}");
        caso("while let comodín con guarda no es divergente", "fn f() -> i32 { "
            + "while let _ = 1 && false {} } fn main() {}", "RETORNO");
        caso("ramas incompatibles de if let", "enum E { V(i32) } fn f(e: E) -> i32 { "
            + "if let E::V(x) = e { x } else { false } }", "RAMAS");
        caso("let else posicional y alcance posterior", "enum E { V(i32, bool), N } "
            + "fn f(e: E) -> i32 { let E::V(x, ..) = e else { return 0; }; x } fn main() {}");
        caso("let else nombrado y alias", "enum E { V { x: i32, activo: bool }, N } "
            + "fn f(e: E) -> i32 { let E::V { x: valor, .. } = e else { return 0; }; valor } fn main() {}");
        caso("let else permite shadowing", "enum E { V(i32), N } fn f(e: E) -> i32 { "
            + "let x = true; let E::V(x) = e else { return 0; }; x } fn main() {}");
        caso("let else exige divergencia", "enum E { V(i32), N } fn f(e: E) { "
            + "let E::V(x) = e else { let y = 0; }; println!(\"{}\", x); }", "LET_ELSE_NO_DIVERGE");
        caso("let else requiere enum", "enum E { V(i32) } fn f() { "
            + "let E::V(x) = 1 else { return; }; }", "PATRON_TIPO");
        caso("let else patrón de otro enum", "enum E { V(i32) } enum F { V(i32) } fn f(e: E) { "
            + "let F::V(x) = e else { return; }; }", "PATRON_TIPO");
        caso("let else retorno incompatible", "enum E { V(i32) } fn f(e: E) { "
            + "let E::V(x) = e else { return 1; }; }", "RETORNO");
        caso("match con patrón posicional anidado", "enum I { V(i32), N } enum O { P(I), Fin } "
            + "fn f(o: O) -> i32 { match o { O::P(I::V(x)) => x, _ => 0, } } fn main() {}");
        caso("match con patrón nombrado anidado", "enum I { V { x: i32 }, N } "
            + "enum O { P { dato: I, activo: bool }, Fin } fn f(o: O) -> i32 { match o { "
            + "O::P { dato: I::V { x }, .. } => x, _ => 0, } } fn main() {}");
        caso("if let con patrón anidado y guarda", "enum I { V(i32), N } enum O { P(I) } "
            + "fn f(o: O) -> i32 { if let O::P(I::V(x)) = o && x > 0 { x } else { 0 } } fn main() {}");
        caso("while let con patrón anidado", "enum I { V(i32), N } enum O { P(I), Fin } "
            + "fn main() { let mut o = O::P(I::V(1)); while let O::P(I::V(x)) = o { o = O::Fin; } }");
        caso("let else con patrón anidado", "enum I { V(i32), N } enum O { P(I) } "
            + "fn f(o: O) -> i32 { let O::P(I::V(x)) = o else { return 0; }; x } fn main() {}");
        caso("tipo enum anidado incompatible", "enum A { X(i32) } enum B { Y(i32) } enum O { V(A) } "
            + "fn f(o: O) { if let O::V(B::Y(x)) = o {} }", "PATRON_TIPO");
        caso("patrón anidado sobre escalar", "enum A { X(i32) } enum O { V(i32) } "
            + "fn f(o: O) { if let O::V(A::X(x)) = o {} }", "PATRON_TIPO");
        caso("variante anidada desconocida", "enum A { X(i32) } enum O { V(A) } "
            + "fn f(o: O) { if let O::V(A::Z(x)) = o {} }", "VARIANTE_DESCONOCIDA");
        caso("aridad anidada incorrecta", "enum A { X(i32) } enum O { V(A) } "
            + "fn f(o: O) { if let O::V(A::X(x, y)) = o {} }", "PATRON_ARIDAD");
        caso("vinculación duplicada entre niveles", "enum A { X(i32) } enum O { V(i32, A) } "
            + "fn f(o: O) { if let O::V(x, A::X(x)) = o {} }", "VINCULACION_DUPLICADA");
        caso("patrón anidado tras cobertura amplia", "enum A { X(i32) } enum O { V(A) } "
            + "fn f(o: O) -> i32 { match o { O::V(valor) => 0, O::V(A::X(x)) => x, } }",
            "PATRON_INALCANZABLE");
        caso("exhaustividad anidada posicional", "enum I { A, B } enum O { P(I), Fin } "
            + "fn f(o: O) -> i32 { match o { O::P(I::A) => 1, O::P(I::B) => 2, O::Fin => 0, } }");
        caso("exhaustividad anidada nombrada cartesiana", "enum Bit { Cero, Uno } "
            + "enum Par { P { a: Bit, b: Bit } } fn f(p: Par) -> i32 { match p { "
            + "Par::P { a: Bit::Cero, b: Bit::Cero } => 0, "
            + "Par::P { a: Bit::Cero, b: Bit::Uno } => 1, "
            + "Par::P { a: Bit::Uno, b: Bit::Cero } => 2, "
            + "Par::P { a: Bit::Uno, b: Bit::Uno } => 3, } }");
        caso("exhaustividad anidada con resto", "enum Bit { Cero, Uno } enum Par { P(Bit, bool) } "
            + "fn f(p: Par) -> i32 { match p { Par::P(Bit::Cero, ..) => 0, "
            + "Par::P(Bit::Uno, ..) => 1, } }");
        caso("combinación anidada faltante", "enum Bit { Cero, Uno } enum Par { P { a: Bit, b: Bit } } "
            + "fn f(p: Par) -> i32 { match p { "
            + "Par::P { a: Bit::Cero, b: Bit::Cero } => 0, "
            + "Par::P { a: Bit::Cero, b: Bit::Uno } => 1, "
            + "Par::P { a: Bit::Uno, b: Bit::Cero } => 2, } }", "MATCH_NO_EXHAUSTIVO");
        caso("patrón anidado redundante", "enum I { A, B } enum O { P(I) } "
            + "fn f(o: O) -> i32 { match o { O::P(I::A) => 1, O::P(I::B) => 2, "
            + "O::P(valor) => 3, } }", "PATRON_INALCANZABLE");
        caso("guarda anidada no aporta cobertura", "enum I { A, B } enum O { P(I) } "
            + "fn f(o: O) -> i32 { match o { O::P(I::A) if true => 1, O::P(I::B) => 2, } }",
            "MATCH_NO_EXHAUSTIVO");
        caso("enum vacío tiene match exhaustivo", "enum Nunca {} fn f(n: Nunca) -> i32 { match n {} }");
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
        caso("ejemplo enums nombrados", EjemplosFisio.codigoDe("Rust-like - enums nombrados y guardas"));
        caso("ejemplo actualización y patrones", EjemplosFisio.codigoDe("Rust-like - actualización y patrones parciales"));
        caso("ejemplo if let y while let", EjemplosFisio.codigoDe("Rust-like - if let y while let"));
        caso("ejemplo let else", EjemplosFisio.codigoDe("Rust-like - let else"));
        caso("ejemplo patrones anidados", EjemplosFisio.codigoDe("Rust-like - patrones anidados"));
        caso("ejemplo ampliado", EjemplosFisio.codigoDe("Rust-like - características ampliadas"));
        caso("ejemplo tipos compuestos", EjemplosFisio.codigoDe("Rust-like - structs, enums y match"));
        caso("ejemplo arreglos y tuplas", EjemplosFisio.codigoDe("Rust-like - arreglos y tuplas"));
        caso("ejemplo constantes", EjemplosFisio.codigoDe("Rust-like - constantes"));
        caso("ejemplo visibilidad pub", EjemplosFisio.codigoDe("Rust-like - visibilidad pub"));
        caso("ejemplo alias de tipos", EjemplosFisio.codigoDe("Rust-like - alias de tipos"));
        caso("ejemplo elementos static", EjemplosFisio.codigoDe("Rust-like - elementos static"));
        caso("ejemplo referencias", EjemplosFisio.codigoDe("Rust-like - referencias"));
        caso("ejemplo impl y metodos", EjemplosFisio.codigoDe("Rust-like - impl y metodos"));
        caso("ejemplo traits", EjemplosFisio.codigoDe("Rust-like - traits"));
        caso("ejemplo datos algebraicos", EjemplosFisio.codigoDe("Rust-like - datos algebraicos"));
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
