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
                "'R'", "let letra: char = 'R';", "let x = 1 as u8;", "let x = if true { 1 } else { 2 };",
                "let x = { let y = 1; y + 1 };", "if true {} else if false {} else {}", "while true { break; }",
                "for i in 0..10 { continue; }", "loop { break; }", "break;", "continue;", "return;", "return 1;"))
            valido("fn f() {" + cuerpo + "}");
        for (String cuerpo : List.of("1 2", "1 let x = 2;", "x y", "x = 1", "let = 1;", "let x 1;",
                "let x = ;", "x += ;", "f(,);", "f(1,);", "f(1 2);", "f(1;", "(1 + 2;",
                "if {}", "while x > {}", "for i in 0.. {}", "return 1", "struct A {}", "let x = 1 + ;"))
            invalido("fn f() {" + cuerpo + "}");
        valido("fn suma(a: i32, b: i32) -> i32 { a + b } fn main() { suma(1, 2); }");
        String tiposCompuestos = "struct Punto { x: i32, y: i32, } enum Estado { Activo, Inactivo, } "
            + "fn f(p: Punto, e: Estado) -> i32 { let copia = Punto { x: p.x, y: p.y }; "
            + "match e { Estado::Activo => copia.x, Estado::Inactivo => 0, } }";
        var astCompuesto = analizar(tiposCompuestos);
        comprobar(astCompuesto.errores().isEmpty(), astCompuesto.errores().toString());
        comprobar(astCompuesto.ast().sentencias().size() == 3
            && astCompuesto.ast().sentencias().get(0) instanceof NodoStruct
            && astCompuesto.ast().sentencias().get(1) instanceof NodoEnum, "Items compuestos de nivel superior");
        var funcionCompuesta = (NodoFuncion) astCompuesto.ast().sentencias().get(2);
        var matchFinal = (NodoMatch) funcionCompuesta.cuerpo().sentencias()
            .get(funcionCompuesta.cuerpo().sentencias().size() - 1);
        comprobar(matchFinal != null, "Match como expresión final");
        var acceso = (NodoAccesoCampo) matchFinal.brazos().get(0).resultado();
        comprobar(acceso.campo().lexema().equals("x"), "Acceso a campo perdido");
        valido("struct Unidad {} fn main() { let unidad = Unidad {}; }");
        valido("enum Opcion { Uno, Dos } fn main() { let x = match Opcion::Uno { Opcion::Uno => 1, _ => 0, }; }");
        String colecciones = "fn identidad(a: [i32; 3], t: (i32, bool)) -> [i32; 3] { a } "
            + "fn main() { let mut a: [i32; 3] = [1, 2, 3]; a[1] += 4; "
            + "let t: (i32, bool) = (a[0], true); let x = t.0; let u: () = (); let uno: (i32,) = (1,); }";
        var astColecciones = analizar(colecciones);
        comprobar(astColecciones.errores().isEmpty(), astColecciones.errores().toString());
        var mainColecciones = (NodoFuncion) astColecciones.ast().sentencias().get(1);
        var declaracionArreglo = (NodoDeclaracion) mainColecciones.cuerpo().sentencias().get(0);
        comprobar(declaracionArreglo.tipo().lexema().equals("[i32;3]")
            && declaracionArreglo.expresion() instanceof NodoArreglo arreglo && arreglo.elementos().size() == 3,
            "Tipo o literal arreglo perdido");
        var asignacionIndice = (NodoAsignacion) mainColecciones.cuerpo().sentencias().get(1);
        comprobar(asignacionIndice.lugar() instanceof NodoIndice, "Destino indexado perdido");
        var declaracionTupla = (NodoDeclaracion) mainColecciones.cuerpo().sentencias().get(2);
        comprobar(declaracionTupla.tipo().lexema().equals("(i32,bool)")
            && declaracionTupla.expresion() instanceof NodoTupla, "Tupla tipada perdida");
        var accesoTupla = (NodoDeclaracion) mainColecciones.cuerpo().sentencias().get(3);
        comprobar(accesoTupla.expresion() instanceof NodoAccesoTupla accesoPosicional
            && accesoPosicional.indice().lexema().equals("0"),
            "Acceso posicional de tupla perdido");
        valido("struct Datos { valores: [i32; 2], par: (bool, char) } fn main() { "
            + "let d = Datos { valores: [1, 2], par: (true, 'F') }; }");
        var astConstantes = analizar("const BASE: i32 = 10; const DOBLE: i32 = BASE * 2; "
            + "fn main() { const LOCAL: [i32; 2] = [BASE, DOBLE]; let x = LOCAL[0]; }");
        comprobar(astConstantes.errores().isEmpty(), astConstantes.errores().toString());
        comprobar(astConstantes.ast().sentencias().get(0) instanceof NodoConstante constante
            && constante.nombre().lexema().equals("BASE") && constante.tipo().lexema().equals("i32"),
            "Constante global perdida");
        var funcionConstantes = (NodoFuncion) astConstantes.ast().sentencias().get(2);
        comprobar(funcionConstantes.cuerpo().sentencias().get(0) instanceof NodoConstante local
            && local.tipo().lexema().equals("[i32;2]"), "Constante local perdida");
        var astPublico = analizar("pub const LIMITE: i32 = 10; pub struct Dato { pub valor: i32, privado: bool } "
            + "pub enum Estado { Activo } pub fn valor(d: Dato) -> i32 { d.valor } fn main() {}");
        comprobar(astPublico.errores().isEmpty(), astPublico.errores().toString());
        comprobar(((NodoConstante) astPublico.ast().sentencias().get(0)).publico(), "pub const perdido");
        var structPublico = (NodoStruct) astPublico.ast().sentencias().get(1);
        comprobar(structPublico.publico() && structPublico.campos().get(0).publico()
            && !structPublico.campos().get(1).publico(), "Visibilidad de struct o campos perdida");
        comprobar(((NodoEnum) astPublico.ast().sentencias().get(2)).publico()
            && ((NodoFuncion) astPublico.ast().sentencias().get(3)).publico()
            && !((NodoFuncion) astPublico.ast().sentencias().get(4)).publico(), "Visibilidad de items perdida");
        var astAlias = analizar("pub type Entero = i32; type Vector = [Entero; 3]; "
            + "fn suma(v: Vector) -> Entero { v[0] + v[1] + v[2] }");
        comprobar(astAlias.errores().isEmpty(), astAlias.errores().toString());
        comprobar(astAlias.ast().sentencias().get(0) instanceof NodoAliasTipo alias
            && alias.publico() && alias.nombre().lexema().equals("Entero")
            && alias.tipo().lexema().equals("i32"), "Alias público perdido");
        comprobar(((NodoAliasTipo) astAlias.ast().sentencias().get(1)).tipo().lexema().equals("[Entero;3]"),
            "Tipo compuesto del alias perdido");
        var astStatic = analizar("pub static VERSION: i32 = 1; static mut GLOBAL: i32 = 0; "
            + "fn main() { static LOCAL: [i32; 2] = [1, 2]; }");
        comprobar(astStatic.errores().isEmpty(), astStatic.errores().toString());
        comprobar(astStatic.ast().sentencias().get(0) instanceof NodoEstatico publicoStatic
            && publicoStatic.publico() && !publicoStatic.mutable(), "pub static perdido");
        comprobar(((NodoEstatico) astStatic.ast().sentencias().get(1)).mutable(), "static mut perdido");
        var mainStatic = (NodoFuncion) astStatic.ast().sentencias().get(2);
        comprobar(mainStatic.cuerpo().sentencias().get(0) instanceof NodoEstatico localStatic
            && localStatic.tipo().lexema().equals("[i32;2]"), "static local perdido");
        var astReferencias = analizar("fn leer(x: &i32) -> i32 { *x } "
            + "fn cambiar(x: &mut i32) { *x = 2; } "
            + "fn main() { let mut n = 1; let r: &i32 = &n; let m: &mut i32 = &mut n; *m += *r; }");
        comprobar(astReferencias.errores().isEmpty(), astReferencias.errores().toString());
        var leerReferencia = (NodoFuncion) astReferencias.ast().sentencias().get(0);
        comprobar(leerReferencia.parametros().get(0).tipo().lexema().equals("&i32")
            && leerReferencia.cuerpo().expresionFinal() instanceof NodoDesreferencia,
            "Parámetro o desreferenciación compartida perdida");
        var cambiarReferencia = (NodoFuncion) astReferencias.ast().sentencias().get(1);
        comprobar(cambiarReferencia.parametros().get(0).tipo().lexema().equals("&mut i32")
            && ((NodoAsignacion) cambiarReferencia.cuerpo().sentencias().get(0)).lugar() instanceof NodoDesreferencia,
            "Referencia mutable o destino desreferenciado perdido");
        var mainReferencias = (NodoFuncion) astReferencias.ast().sentencias().get(2);
        comprobar(((NodoDeclaracion) mainReferencias.cuerpo().sentencias().get(1)).expresion()
                instanceof NodoReferencia compartida && !compartida.mutable()
            && ((NodoDeclaracion) mainReferencias.cuerpo().sentencias().get(2)).expresion()
                instanceof NodoReferencia mutable && mutable.mutable(),
            "Creación de referencias perdida");
        valido("struct Nodo { siguiente: &Nodo } fn main() {}");
        var astImpl = analizar("struct Punto { x: i32 } impl Punto { "
            + "pub fn nuevo(x: i32) -> Punto { Punto { x: x } } "
            + "fn leer(&self) -> i32 { self.x } fn cambiar(&mut self, x: i32) { self.x = x; } } "
            + "fn main() { let mut p = Punto::nuevo(1); let x = p.leer(); p.cambiar(2); }");
        comprobar(astImpl.errores().isEmpty(), astImpl.errores().toString());
        comprobar(astImpl.ast().sentencias().get(1) instanceof NodoImpl bloqueImpl
            && bloqueImpl.tipo().lexema().equals("Punto") && bloqueImpl.metodos().size() == 3,
            "Bloque impl perdido");
        var metodoLeer = ((NodoImpl) astImpl.ast().sentencias().get(1)).metodos().get(1);
        comprobar(metodoLeer.parametros().get(0).nombre().lexema().equals("self")
            && metodoLeer.parametros().get(0).tipo().lexema().equals("&Punto"), "Receptor &self perdido");
        var metodoCambiar = ((NodoImpl) astImpl.ast().sentencias().get(1)).metodos().get(2);
        comprobar(metodoCambiar.parametros().get(0).tipo().lexema().equals("&mut Punto"),
            "Receptor &mut self perdido");
        var mainImpl = (NodoFuncion) astImpl.ast().sentencias().get(2);
        comprobar(((NodoDeclaracion) mainImpl.cuerpo().sentencias().get(0)).expresion()
                instanceof NodoLlamadaAsociada asociada && asociada.nombre().lexema().equals("nuevo"),
            "Llamada asociada perdida");
        comprobar(((NodoDeclaracion) mainImpl.cuerpo().sentencias().get(1)).expresion()
                instanceof NodoLlamadaMetodo metodo && metodo.nombre().lexema().equals("leer"),
            "Llamada de método perdida");
        var astTrait = analizar("pub trait Medible { fn crear(x: i32) -> Self; "
            + "fn valor(&self) -> i32; fn cambiar(&mut self, x: i32); } "
            + "struct Dato { x: i32 } impl Medible for Dato { "
            + "fn crear(x: i32) -> Dato { Dato { x: x } } fn valor(&self) -> i32 { self.x } "
            + "fn cambiar(&mut self, x: i32) { self.x = x; } }");
        comprobar(astTrait.errores().isEmpty(), astTrait.errores().toString());
        comprobar(astTrait.ast().sentencias().get(0) instanceof NodoTrait trait
            && trait.publico() && trait.nombre().lexema().equals("Medible") && trait.metodos().size() == 3,
            "Definición de trait perdida");
        var firmaValor = ((NodoTrait) astTrait.ast().sentencias().get(0)).metodos().get(1);
        comprobar(firmaValor.parametros().get(0).tipo().lexema().equals("&Self")
            && firmaValor.tipoRetorno().lexema().equals("i32"), "Firma de trait perdida");
        comprobar(astTrait.ast().sentencias().get(2) instanceof NodoImpl implTrait
            && implTrait.rasgo().lexema().equals("Medible") && implTrait.tipo().lexema().equals("Dato"),
            "Implementación de trait perdida");
        var astDatos = analizar("pub struct Punto(pub i32, i32); enum Opcion { Nada, Valor(i32), Par(i32, bool), } "
            + "fn f(o: Opcion) -> i32 { match o { Opcion::Nada => 0, Opcion::Valor(x) => x, "
            + "Opcion::Par(x, _) => x, } } fn main() { let p = Punto(1, 2); let o = Opcion::Valor(p.0); }");
        comprobar(astDatos.errores().isEmpty(), astDatos.errores().toString());
        comprobar(astDatos.ast().sentencias().get(0) instanceof NodoStruct puntoTupla
            && puntoTupla.tupla() && puntoTupla.publico() && puntoTupla.campos().size() == 2
            && puntoTupla.campos().get(0).publico() && puntoTupla.campos().get(0).nombre().lexema().equals("0"),
            "Struct de tupla perdido");
        var opcionDatos = (NodoEnum) astDatos.ast().sentencias().get(1);
        comprobar(opcionDatos.variantes().get(1).lexema().equals("Valor")
            && opcionDatos.variantes().get(1).tipos().get(0).lexema().equals("i32")
            && opcionDatos.variantes().get(2).tipos().size() == 2, "Datos de variante enum perdidos");
        var funcionDatos = (NodoFuncion) astDatos.ast().sentencias().get(2);
        var matchDatos = (NodoMatch) funcionDatos.cuerpo().sentencias()
            .get(funcionDatos.cuerpo().sentencias().size() - 1);
        comprobar(matchDatos.brazos().get(1).vinculaciones().get(0).lexema().equals("x")
            && matchDatos.brazos().get(2).vinculaciones().size() == 2,
            "Vinculaciones del patrón match perdidas");
        var astNombrado = analizar("enum Mensaje { Mover { x: i32, y: i32 }, Cerrar } "
            + "fn f(m: Mensaje) -> i32 { match m { Mensaje::Mover { x, y: otro } if x > 0 => x + otro, "
            + "Mensaje::Mover { x: _, y } => y, Mensaje::Cerrar => 0, } } "
            + "fn main() { let x = 1; let m = Mensaje::Mover { x, y: 2 }; }");
        comprobar(astNombrado.errores().isEmpty(), astNombrado.errores().toString());
        var mensaje = (NodoEnum) astNombrado.ast().sentencias().get(0);
        comprobar(mensaje.variantes().get(0).forma() == NodoEnum.Forma.ESTRUCTURA
            && mensaje.variantes().get(0).campos().size() == 2
            && mensaje.variantes().get(0).campos().get(1).nombre().lexema().equals("y"),
            "Campos nombrados de variante perdidos");
        var funcionNombrada = (NodoFuncion) astNombrado.ast().sentencias().get(1);
        var matchNombrado = (NodoMatch) funcionNombrada.cuerpo().sentencias()
            .get(funcionNombrada.cuerpo().sentencias().size() - 1);
        comprobar(matchNombrado.brazos().get(0).forma() == NodoMatch.FormaPatron.ESTRUCTURA
            && matchNombrado.brazos().get(0).campos().get(1).vinculacion().lexema().equals("otro")
            && matchNombrado.brazos().get(0).guarda() instanceof NodoOperacionRelacional,
            "Patrón nombrado o guarda de match perdidos");
        var mainNombrado = (NodoFuncion) astNombrado.ast().sentencias().get(2);
        comprobar(((NodoDeclaracion) mainNombrado.cuerpo().sentencias().get(1)).expresion()
            instanceof NodoLiteralVariante literal && literal.campos().size() == 2
            && literal.campos().get(0).valor() instanceof NodoIdentificador,
            "Construcción nombrada o shorthand perdida");
        var astParcial = analizar("struct Config { x: i32, y: i32 } enum E { Punto { x: i32, y: i32 }, "
            + "Color(i32, i32, i32) } fn f(e: E) -> i32 { match e { "
            + "E::Punto { x, .. } => x, E::Color(r, ..) => r, } } fn main() { "
            + "let base = Config { x: 1, y: 2 }; let x = 3; let nuevo = Config { x, ..base }; }");
        comprobar(astParcial.errores().isEmpty(), astParcial.errores().toString());
        var funcionParcial = (NodoFuncion) astParcial.ast().sentencias().get(2);
        var matchParcial = (NodoMatch) funcionParcial.cuerpo().sentencias()
            .get(funcionParcial.cuerpo().sentencias().size() - 1);
        comprobar(matchParcial.brazos().get(0).resto() && matchParcial.brazos().get(0).campos().size() == 1
            && matchParcial.brazos().get(1).resto() && matchParcial.brazos().get(1).vinculaciones().size() == 1,
            "Resto '..' perdido en patrones parciales");
        var mainParcial = (NodoFuncion) astParcial.ast().sentencias().get(3);
        var actualizacion = (NodoLiteralStruct) ((NodoDeclaracion) mainParcial.cuerpo().sentencias().get(2)).expresion();
        comprobar(actualizacion.base() instanceof NodoIdentificador
            && actualizacion.campos().get(0).valor() instanceof NodoIdentificador,
            "Base o campo abreviado perdido en actualización de struct");
        var astLet = analizar("enum E { V(i32), P { x: i32, y: i32 }, N } "
            + "fn f(e: E) -> i32 { if let E::V(x) = e && x > 0 { x } "
            + "else if let E::P { x, .. } = e && x >= 0 { x } else { 0 } } "
            + "fn main() { let mut e = E::V(2); while let E::V(x) = e && x > 0 { e = E::N; } }");
        comprobar(astLet.errores().isEmpty(), astLet.errores().toString());
        var funcionLet = (NodoFuncion) astLet.ast().sentencias().get(1);
        var ifLet = (NodoIfLet) funcionLet.cuerpo().sentencias().get(funcionLet.cuerpo().sentencias().size() - 1);
        comprobar(ifLet.patron().vinculaciones().get(0).lexema().equals("x")
            && ifLet.guarda() instanceof NodoOperacionRelacional
            && ifLet.cuerpoElse() instanceof NodoIfLet segundo
            && segundo.patron().forma() == NodoMatch.FormaPatron.ESTRUCTURA && segundo.patron().resto()
            && segundo.guarda() instanceof NodoOperacionRelacional,
            "AST de if let o else if let incompleto");
        var mainLet = (NodoFuncion) astLet.ast().sentencias().get(2);
        comprobar(mainLet.cuerpo().sentencias().get(1) instanceof NodoWhileLet cicloLet
            && cicloLet.patron().tipo().lexema().equals("E")
            && cicloLet.patron().variante().lexema().equals("V")
            && cicloLet.guarda() instanceof NodoOperacionRelacional, "AST de while let incompleto");
        var astLetElse = analizar("enum E { V { x: i32, y: bool }, N } "
            + "fn f(e: E) -> i32 { let E::V { x: valor, .. } = e else { return 0; }; valor }");
        comprobar(astLetElse.errores().isEmpty(), astLetElse.errores().toString());
        var funcionLetElse = (NodoFuncion) astLetElse.ast().sentencias().get(1);
        comprobar(funcionLetElse.cuerpo().sentencias().get(0) instanceof NodoLetElse declaracionLetElse
            && declaracionLetElse.patron().campos().get(0).vinculacion().lexema().equals("valor")
            && declaracionLetElse.patron().resto()
            && declaracionLetElse.cuerpoElse().sentencias().get(0) instanceof NodoReturn,
            "AST de let else incompleto");
        var astAnidado = analizar("enum I { V(i32), N } enum O { P(I), C { dato: I, activo: bool } } "
            + "fn f(o: O) -> i32 { match o { O::P(I::V(x)) => x, "
            + "O::C { dato: I::V(y), .. } => y, _ => 0, } }");
        comprobar(astAnidado.errores().isEmpty(), astAnidado.errores().toString());
        var funcionAnidada = (NodoFuncion) astAnidado.ast().sentencias().get(2);
        var matchAnidado = (NodoMatch) funcionAnidada.cuerpo().sentencias().get(0);
        var patronPosicional = PatronVariante.desde(matchAnidado.brazos().get(0));
        var patronNombrado = PatronVariante.desde(matchAnidado.brazos().get(1));
        comprobar(patronPosicional.elementos().get(0).esAnidado()
            && patronPosicional.elementos().get(0).patron().variante().lexema().equals("V")
            && patronPosicional.elementos().get(0).patron().vinculaciones().get(0).lexema().equals("x"),
            "Patrón posicional anidado perdido");
        comprobar(patronNombrado.campos().get(0).esAnidado()
            && patronNombrado.campos().get(0).patron().tipo().lexema().equals("I")
            && patronNombrado.resto(), "Patrón nombrado anidado perdido");
        for (String codigo : List.of("fn f() { let x: [i32 3] = [1,2,3]; }",
                "fn f() { let x: [i32; 3 = [1,2,3]; }", "fn f() { let x = [1 2]; }",
                "fn f() { let x = (1 2); }", "fn f() { let x = [1, 2; }",
                "fn f() { let x = (1, 2; }", "fn f() { let x = (1,); x.0.5 = 2; }")) invalido(codigo);
        for (String codigo : List.of("fn f(x: &) {}", "fn f(x: &mut) {}", "fn f() { let r = &; }",
                "fn f() { let r = &mut; }", "fn f() { * = 1; }")) invalido(codigo);
        for (String codigo : List.of("impl {}", "impl Punto fn f() {}", "impl Punto { let x = 1; }",
                "pub impl Punto {}", "struct Punto {} impl Punto { fn f(&otro) {} }",
                "struct Punto {} impl Punto { fn f(&mut otro) {} }")) invalido(codigo);
        for (String codigo : List.of("trait {}", "trait T fn f();", "trait T { fn f() {} }",
                "trait T { fn f() }", "trait T { pub fn f(); }", "impl T for { fn f() {} }")) invalido(codigo);
        for (String codigo : List.of("struct Punto(i32, bool)", "struct Punto(i32 bool);",
                "enum Opcion { Valor(i32 bool) }", "enum Opcion { Valor(i32 }",
                "enum E { V { x i32 } }", "enum E { V { pub x: i32 } }",
                "fn f(o: Opcion) { match o { Opcion::Valor(x y) => x, } }",
                "enum E { V { x: i32 } } fn f(e: E) { match e { E::V { x y } => x, } }",
                "enum E { V } fn f(e: E) { match e { E::V if => 1, } }")) invalido(codigo);
        for (String codigo : List.of(
                "struct P { x: i32 } fn main() { let p = P { .. }; }",
                "struct P { x: i32, y: i32 } fn main() { let p = P { ..base, x: 1 }; }",
                "enum E { V(i32, i32) } fn f(e: E) { match e { E::V(.., x) => x, } }",
                "enum E { V { x: i32 } } fn f(e: E) { match e { E::V { .., x } => x, } }"))
            invalido(codigo);
        valido("enum E { V } fn main() { if E::V { let x = 1; } else { let x = 2; } "
            + "while E::V { break; } }");
        for (String codigo : List.of(
                "enum E { V } fn main() { if let E::V E::V {} }",
                "enum E { V } fn main() { if let = E::V {} }",
                "enum E { V } fn main() { if let E::V = {} }",
                "enum E { V } fn main() { while let E::V E::V {} }",
                "enum E { V(i32) } fn main() { while let E::V(x y) = E::V(1) {} }",
                "enum E { V(i32) } fn main() { if let E::V(x) = E::V(1) && else {} }",
                "enum E { V(i32) } fn main() { while let E::V(x) = E::V(1) && else {} }",
                "enum I { V(i32) } enum O { P(I) } fn main() { if let O::P(I::V(x) = O::P(I::V(1)) {} }",
                "enum I { V(i32) } enum O { P(I) } fn main() { if let O::P(I::V(x),) extra) = O::P(I::V(1)) {} }",
                "enum I { V(i32) } enum O { C { dato: I } } fn main() { if let O::C { dato: I::V(x) extra } = O::C { dato: I::V(1) } {} }",
                "enum E { V } fn main() { let E::V E::V else { return; }; }",
                "enum E { V } fn main() { let E::V = E::V { return; }; }",
                "enum E { V } fn main() { let E::V = E::V else { return; } }"))
            invalido(codigo);
        for (String codigo : List.of("const: i32 = 1; fn main() {}", "const X = 1; fn main() {}",
                "const X: = 1; fn main() {}", "const X: i32 1; fn main() {}",
                "const X: i32 = ; fn main() {}", "const X: i32 = 1 fn main() {}",
                "fn main() { const X: i32 = 1 }")) invalido(codigo);
        for (String codigo : List.of("pub", "pub let X = 1; fn main() {}", "pub pub fn main() {}",
                "fn main() { pub const X: i32 = 1; }", "struct D { pub x i32 }")) invalido(codigo);
        for (String codigo : List.of("type = i32; fn main() {}", "type Entero i32; fn main() {}",
                "type Entero = ; fn main() {}", "type Entero = i32 fn main() {}",
                "fn main() { type Local = i32; }")) invalido(codigo);
        for (String codigo : List.of("static: i32 = 1; fn main() {}", "static X = 1; fn main() {}",
                "static X: i32 1; fn main() {}", "static X: i32 = ; fn main() {}",
                "static X: i32 = 1 fn main() {}")) invalido(codigo);
        for (String codigo : List.of("fn", "fn () {}", "fn f(a) {}", "fn f(a:) {}", "fn f(a: i32,) {}",
                "fn f( {}", "fn f() -> {}", "fn f()", "fn f() {", "fn f() {} basura",
                "struct Punto { x i32 }", "enum Estado { Activo Inactivo }",
                "enum E { A } fn f() { match E::A { E::A 1, } }")) invalido(codigo);
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
        var bits = (NodoOperacion) bloque("1 | 2 ^ 3 & 4 << 1 + 1").expresionFinal();
        comprobar(bits.operador().lexema().equals("|")
            && ((NodoOperacion) bits.derecha()).operador().lexema().equals("^")
            && ((NodoOperacion) ((NodoOperacion) bits.derecha()).derecha()).operador().lexema().equals("&"),
            "Precedencia de operadores de bits");
        var sumaConConversion = (NodoOperacion) bloque("1 + 2 as u8").expresionFinal();
        var conversion = (NodoConversion) sumaConConversion.derecha();
        comprobar(conversion.tipo().lexema().equals("u8") && conversion.expresion() instanceof NodoNumero,
            "Conversión explícita y su precedencia");
        for (String op : List.of("=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=")) {
            var asignacion = (NodoAsignacion) bloque("x " + op + " 1;").sentencias().get(0);
            comprobar(asignacion.operador().lexema().equals(op), "Operador de asignación perdido");
        }
        var asignacionCampo = (NodoAsignacion) bloque("p.posicion.x += 1;").sentencias().get(0);
        comprobar(asignacionCampo.lugar() instanceof NodoAccesoCampo accesoCampo
            && accesoCampo.objeto() instanceof NodoAccesoCampo
            && accesoCampo.campo().lexema().equals("x"), "Destino de campo anidado perdido");
        // Entradas léxicamente válidas y sintácticamente arbitrarias: recuperación sin excepciones.
        var azar = new java.util.Random(731);
        String[] piezas = {"let", "mut", "const", "x", "1", "fn", "(", ")", "[", "]", "{", "}", ";", ",", ":", "=", "+",
            "if", "else", "match", "struct", "enum", "=>", "::", ".", "return", "for", "in", "while", "!", ".."};
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
