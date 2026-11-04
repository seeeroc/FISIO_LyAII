package fisio.ci;

import fisio.analysis.FisioParserRunner;
import fisio.gui.EjemplosFisio;
import fisio.semantic.FisioSemanticRunner;

/** Regresión ejecutable sin dependencias externas para el generador aritmético. */
public final class CodigoIntermedioRegression {
    private static int comprobaciones;

    private static ResultadoCodigoIntermedio generar(String codigo) {
        var sintaxis = FisioParserRunner.parse(codigo);
        comprobar(sintaxis.errores().isEmpty(), "El caso de prueba debe ser sintácticamente válido: " + sintaxis.errores());
        return GeneradorCodigoIntermedio.generar(sintaxis.ast());
    }

    private static ExpresionIntermedia unica(String cuerpo) {
        var resultado = generar("fn main() { " + cuerpo + " }");
        comprobar(resultado.expresiones().size() == 1,
            "Se esperaba una expresión y se obtuvieron " + resultado.expresiones().size());
        return resultado.expresiones().get(0);
    }

    private static void comprobar(boolean condicion, String mensaje) {
        comprobaciones++;
        if (!condicion) throw new AssertionError(mensaje);
    }

    private static void igual(Object esperado, Object real, String mensaje) {
        comprobar(java.util.Objects.equals(esperado, real), mensaje + ": " + real + " != " + esperado);
    }

    public static void main(String[] args) {
        ExpresionIntermedia completa = unica(
            "let a = 10; let b = 5; let resultado = (a + b) * 2 - 8 / 4;");
        igual("(a + b) * 2 - 8 / 4", completa.infija(), "Infija completa");
        igual("a b + 2 * 8 4 / -", completa.postfija(), "Postfija completa");
        igual("- * + a b 2 / 8 4", completa.prefija(), "Prefija completa");
        igual(4, completa.cuadruplos().size(), "Cantidad de cuádruplos");
        igual(new Cuadruplo(1, "+", "a", "b", "t1"), completa.cuadruplos().get(0), "Cuádruplo 1");
        igual(new Cuadruplo(2, "*", "t1", "2", "t2"), completa.cuadruplos().get(1), "Cuádruplo 2");
        igual(new Cuadruplo(3, "/", "8", "4", "t3"), completa.cuadruplos().get(2), "Cuádruplo 3");
        igual(new Cuadruplo(4, "-", "t2", "t3", "t4"), completa.cuadruplos().get(3), "Cuádruplo 4");
        igual("28", completa.resultadoFinal(), "Resultado matemático completo");
        comprobar(completa.pasos().get(0).detalle().contains("10 + 5 = 15"), "Falta sustitución de variables");
        comprobar(completa.pasos().get(3).detalle().contains("30 - 2 = 28"), "Falta resolución final");

        ExpresionIntermedia precedencia = unica("let x = 2 + 3 * 4;");
        igual("2 3 4 * +", precedencia.postfija(), "Precedencia postfija");
        igual("+ 2 * 3 4", precedencia.prefija(), "Precedencia prefija");
        igual("14", precedencia.resultadoFinal(), "Precedencia numérica");

        ExpresionIntermedia agrupada = unica("let x = (2 + 3) * 4;");
        igual("(2 + 3) * 4", agrupada.infija(), "Agrupación infija");
        igual("20", agrupada.resultadoFinal(), "Agrupación numérica");

        igual("5", unica("let x = 10 - 3 - 2;").resultadoFinal(), "Resta asociativa");
        igual("2.5", unica("let x = 10.0 / 4.0;").resultadoFinal(), "División decimal");
        igual("1", unica("let x = 10 % 3;").resultadoFinal(), "Módulo");
        igual("-6", unica("let x = -(2 + 4);").resultadoFinal(), "Negación unaria");

        ExpresionIntermedia bits = unica("let x = (5 << 1) | 3;");
        igual("(5 << 1) | 3", bits.infija(), "Infija con bits");
        igual("5 1 << 3 |", bits.postfija(), "Postfija con bits");
        igual("11", bits.resultadoFinal(), "Resolución de bits");

        ExpresionIntermedia conversion = unica("let x = 'A' as i32 + 1;");
        igual("'A' as i32 + 1", conversion.infija(), "Infija con conversión");
        igual("'A' CAST_i32 1 +", conversion.postfija(), "Postfija con conversión");
        igual(new Cuadruplo(1, "CAST_i32", "'A'", "", "t1"), conversion.cuadruplos().get(0), "Cuádruplo de conversión");
        igual("66", conversion.resultadoFinal(), "Resolución de conversión char");
        igual("44", unica("let x = 300 as u8;").resultadoFinal(), "Conversión entera respeta el ancho de u8");
        igual("128512", unica("let x = '😀' as i32;").resultadoFinal(), "Conversión de carácter Unicode");
        ExpresionIntermedia complemento = unica("let x = !1 + 2;");
        igual("1 BIT_NOT 2 +", complemento.postfija(), "Postfija con complemento de bits");
        igual("0", complemento.resultadoFinal(), "Resolución de complemento de bits");
        igual("254", unica("let a: u8 = 1; let x: u8 = !a; let y = x + 0;").resultadoFinal(),
            "El complemento conserva el ancho del tipo u8");

        var reasignaciones = generar("fn main() { let mut x = 2; x = x + 3; let y = x * 2; }");
        igual(2, reasignaciones.expresiones().size(), "Expresiones tras reasignación");
        igual("5", reasignaciones.expresiones().get(0).resultadoFinal(), "Resultado de reasignación");
        igual("10", reasignaciones.expresiones().get(1).resultadoFinal(), "Propagación lineal segura");

        ExpresionIntermedia tipoReasignado = unica("let mut x: f64 = 5.0; x = 5; let y = x / 2;");
        igual("2.5", tipoReasignado.resultadoFinal(), "La reasignación conserva el tipo decimal declarado");

        ExpresionIntermedia compuesta = unica("let mut x = 2; x += 3;");
        igual("x + 3", compuesta.infija(), "Asignación compuesta");
        igual("5", compuesta.resultadoFinal(), "Resultado de asignación compuesta");
        igual("8", unica("let mut x = 2; x <<= 2;").resultadoFinal(), "Asignación compuesta de desplazamiento");

        ExpresionIntermedia temporalSinColision = unica("let t1 = 4; let x = (2 + 3) + t1;");
        igual("t2", temporalSinColision.cuadruplos().get(0).resultado(), "El temporal no colisiona con identificadores");
        igual("9", temporalSinColision.resultadoFinal(), "Resolución con nombre parecido a temporal");

        var desconocida = generar("fn calcular(a: i32) { let x = a + 1; }").expresiones().get(0);
        comprobar(!desconocida.resultadoDisponible(), "Un parámetro no debe recibir un valor inventado");
        comprobar(desconocida.detalleResultado().contains("No es posible"), "Falta explicación para valor desconocido");
        igual(1, desconocida.cuadruplos().size(), "Se conserva CI con operando desconocido");

        var flujo = generar("fn main() { let mut x = 1; while x < 3 { x += 1; } let y = x + 2; }");
        igual(2, flujo.expresiones().size(), "Se genera CI dentro y después de un ciclo");
        comprobar(!flujo.expresiones().get(0).resultadoDisponible(), "No se debe simular una iteración del ciclo");
        comprobar(!flujo.expresiones().get(1).resultadoDisponible(), "El valor posterior al ciclo no es definitivo");

        var divisionCero = generar("fn main() { let cero = 0; let x = 5 / cero; }");
        igual(1, divisionCero.problemas().size(), "División entre cero");
        comprobar(divisionCero.problemas().get(0).mensaje().contains("División entre cero"), "Mensaje de división entre cero");
        comprobar(!divisionCero.expresiones().get(0).resultadoDisponible(), "División entre cero sin resultado válido");

        var moduloCero = generar("fn main() { let cero = 2 - 2; let x = 5 % cero; }");
        igual(2, moduloCero.expresiones().size(), "Expresiones del caso módulo cero");
        igual(1, moduloCero.problemas().size(), "Módulo entre cero");
        comprobar(moduloCero.problemas().get(0).mensaje().contains("Módulo entre cero"), "Mensaje de módulo entre cero");

        var vacio = generar("fn main() { let x = 1; println!(\"{}\", x); }");
        comprobar(vacio.generado(), "El generador debe finalizar aunque no encuentre operaciones");
        comprobar(vacio.expresiones().isEmpty(), "No debe inventar expresiones");

        String codigoEjemplo = EjemplosFisio.codigoDe("Rust-like - código intermedio (CI)");
        comprobar(!codigoEjemplo.isBlank(), "El ejemplo de CI debe estar disponible en el selector");
        comprobar(FisioSemanticRunner.analizar(codigoEjemplo).exitoso(), "El ejemplo de CI debe ser semánticamente válido");
        var ejemplo = generar(codigoEjemplo);
        igual(4, ejemplo.expresiones().size(), "Expresiones del ejemplo de CI");
        igual("28", ejemplo.expresiones().get(0).resultadoFinal(), "Resultado principal del ejemplo de CI");
        igual("34", ejemplo.expresiones().get(3).resultadoFinal(), "Resultado final del ejemplo de CI");

        String codigoCompuesto = EjemplosFisio.codigoDe("Rust-like - structs, enums y match");
        comprobar(FisioSemanticRunner.analizar(codigoCompuesto).exitoso(), "El ejemplo compuesto debe ser válido");
        var compuesto = generar(codigoCompuesto);
        igual(1, compuesto.expresiones().size(), "Aritmética encontrada dentro de función con struct");
        igual("punto.x * punto.x + punto.y * punto.y", compuesto.expresiones().get(0).infija(),
            "Accesos a campos conservados en CI");
        comprobar(!compuesto.expresiones().get(0).resultadoDisponible(),
            "El CI no debe inventar valores para campos de parámetros");

        var brazosMatch = generar("enum E { A, B } fn main() { let e = E::A; let x = match e { "
            + "E::A => 1 + 2, E::B => 3 * 4, }; }");
        igual(2, brazosMatch.expresiones().size(), "CI dentro de brazos de match");
        igual("3", brazosMatch.expresiones().get(0).resultadoFinal(), "Primer brazo de match");
        igual("12", brazosMatch.expresiones().get(1).resultadoFinal(), "Segundo brazo de match");

        var enumNombrado = generar("enum E { V { x: i32 }, N } fn f(e: E) -> i32 { match e { "
            + "E::V { x } if x + 1 > 0 => x * 2, E::V { x } => x, E::N => 0, } } "
            + "fn main() { let e = E::V { x: 1 + 2 }; }");
        igual(3, enumNombrado.expresiones().size(), "CI en guarda, brazo y campo de variante nombrada");
        igual("x + 1", enumNombrado.expresiones().get(0).infija(), "Aritmética de guarda preservada");
        igual("x * 2", enumNombrado.expresiones().get(1).infija(), "Aritmética del brazo preservada");
        igual("3", enumNombrado.expresiones().get(2).resultadoFinal(), "Campo de variante evaluado");

        var sombraPatron = generar("enum E { V(i32) } fn f(e: E) -> i32 { let x = 100; "
            + "match e { E::V(x) => x + 1, } } fn main() {}");
        igual(1, sombraPatron.expresiones().size(), "Expresión con vinculación que sombrea variable exterior");
        comprobar(!sombraPatron.expresiones().get(0).resultadoDisponible(),
            "El CI no debe reutilizar el valor exterior de una vinculación del patrón");

        var actualizacionStruct = generar("struct P { x: i32, y: i32 } "
            + "fn crear(x: i32) -> P { P { x, y: 0 } } fn main() { "
            + "let p = P { x: 1 + 2, ..crear(3 + 4) }; }");
        igual(2, actualizacionStruct.expresiones().size(),
            "CI en campos y expresión base de actualización struct");
        igual("3", actualizacionStruct.expresiones().get(0).resultadoFinal(),
            "Campo explícito de actualización evaluado");
        igual("7", actualizacionStruct.expresiones().get(1).resultadoFinal(),
            "Argumento de la expresión base evaluado");

        var letPatrones = generar("enum E { V(i32), Fin } fn main() { let exterior = 100; "
            + "let mut e = E::V(2 + 3); let r = if let E::V(exterior) = e { exterior * 2 } "
            + "else { 1 + 1 }; while let E::V(x) = e { let y = x + 1; e = E::Fin; } }");
        igual(4, letPatrones.expresiones().size(), "CI dentro de if let y while let");
        igual("5", letPatrones.expresiones().get(0).resultadoFinal(), "Argumento del enum evaluado");
        comprobar(!letPatrones.expresiones().get(1).resultadoDisponible(),
            "La vinculación de if let debe ocultar la variable exterior en CI");
        igual("2", letPatrones.expresiones().get(2).resultadoFinal(), "Rama else de if let evaluada");
        comprobar(!letPatrones.expresiones().get(3).resultadoDisponible(),
            "La vinculación de while let no debe inventar un valor en CI");

        var letElse = generar("enum E { V(i32), N } fn f(e: E) -> i32 { "
            + "let E::V(x) = e else { return 10 + 5; }; let doble = x * 2; doble } fn main() {}");
        igual(2, letElse.expresiones().size(), "CI dentro de let else y después de su patrón");
        igual("15", letElse.expresiones().get(0).resultadoFinal(), "Rama divergente de let else evaluada");
        comprobar(!letElse.expresiones().get(1).resultadoDisponible(),
            "La vinculación de let else debe existir sin inventar un valor numérico");

        var guardasLet = generar("enum E { V(i32), N } fn f(e: E) { "
            + "if let E::V(x) = e && x + 1 > 0 { let a = x * 2; } "
            + "while let E::V(y) = e && y - 1 > 0 { let b = y + 3; break; } } fn main() {}");
        igual(4, guardasLet.expresiones().size(), "CI de guardas y cuerpos de if let/while let");
        comprobar(guardasLet.expresiones().stream().noneMatch(ExpresionIntermedia::resultadoDisponible),
            "Las guardas no deben reutilizar valores externos para vinculaciones de patrón");

        var patronesAnidados = generar("enum I { V(i32), N } enum O { P(I) } fn f(o: O) -> i32 { "
            + "if let O::P(I::V(x)) = o && x + 1 > 0 { let y = x * 2; } "
            + "let O::P(I::V(z)) = o else { return 2 + 3; }; let q = z - 1; q } fn main() {}");
        igual(4, patronesAnidados.expresiones().size(), "CI de patrones anidados");
        igual("5", patronesAnidados.expresiones().get(2).resultadoFinal(),
            "Rama else del patrón anidado evaluada");
        comprobar(!patronesAnidados.expresiones().get(0).resultadoDisponible()
            && !patronesAnidados.expresiones().get(1).resultadoDisponible()
            && !patronesAnidados.expresiones().get(3).resultadoDisponible(),
            "El CI no debe inventar valores para vinculaciones anidadas");

        var campoCompuesto = generar("struct Punto { x: i32 } fn main() { "
            + "let mut p = Punto { x: 1 }; p.x += 2; }");
        igual(1, campoCompuesto.expresiones().size(), "Asignación compuesta sobre campo");
        igual("p.x + 2", campoCompuesto.expresiones().get(0).infija(), "Destino de campo preservado en CI");
        comprobar(!campoCompuesto.expresiones().get(0).resultadoDisponible(),
            "El CI no debe inventar el contenido de un campo");

        var colecciones = generar("fn main() { let mut a = [1 + 2, 3 * 4]; "
            + "let t = (a[0] + 5, true); a[1] += t.0; }");
        igual(4, colecciones.expresiones().size(), "Aritmética dentro de arreglos, tuplas e índices");
        igual("1 + 2", colecciones.expresiones().get(0).infija(), "Elemento de arreglo en CI");
        igual("3 * 4", colecciones.expresiones().get(1).infija(), "Segundo elemento de arreglo en CI");
        igual("a[0] + 5", colecciones.expresiones().get(2).infija(), "Índice preservado en CI");
        igual("a[1] + t.0", colecciones.expresiones().get(3).infija(), "Accesos compuestos preservados en CI");
        comprobar(!colecciones.expresiones().get(2).resultadoDisponible()
            && !colecciones.expresiones().get(3).resultadoDisponible(),
            "El CI no debe inventar valores de arreglos o tuplas");

        String codigoColecciones = EjemplosFisio.codigoDe("Rust-like - arreglos y tuplas");
        comprobar(FisioSemanticRunner.analizar(codigoColecciones).exitoso(),
            "El ejemplo de arreglos y tuplas debe ser semánticamente válido");
        var ejemploColecciones = generar(codigoColecciones);
        comprobar(ejemploColecciones.generado(), "El CI debe recorrer el ejemplo de colecciones sin fallar");

        var constantes = generar("const DOBLE: i32 = BASE * 2; const BASE: i32 = 10; "
            + "fn main() { const AJUSTE: i32 = 3; let x = DOBLE + AJUSTE; }");
        igual(2, constantes.expresiones().size(), "CI de constantes globales y locales");
        igual("20", constantes.expresiones().get(0).resultadoFinal(),
            "Propagación de constante global adelantada");
        igual("23", constantes.expresiones().get(1).resultadoFinal(),
            "Propagación de constante global y local");

        String codigoConstantes = EjemplosFisio.codigoDe("Rust-like - constantes");
        comprobar(FisioSemanticRunner.analizar(codigoConstantes).exitoso(),
            "El ejemplo de constantes debe ser semánticamente válido");
        comprobar(generar(codigoConstantes).generado(), "El CI debe recorrer el ejemplo de constantes");

        String codigoAlias = EjemplosFisio.codigoDe("Rust-like - alias de tipos");
        comprobar(FisioSemanticRunner.analizar(codigoAlias).exitoso(),
            "El ejemplo de alias debe ser semánticamente válido");
        comprobar(generar(codigoAlias).generado(), "El CI debe ignorar los alias sin perder expresiones");

        var estaticos = generar("const BASE: i32 = 10; static MAXIMO: i32 = BASE * 2; "
            + "fn main() { static AJUSTE: i32 = 3; let x = MAXIMO + AJUSTE; }");
        igual(2, estaticos.expresiones().size(), "CI de elementos static");
        igual("20", estaticos.expresiones().get(0).resultadoFinal(), "Static global resuelto");
        igual("23", estaticos.expresiones().get(1).resultadoFinal(), "Propagación de static global y local");

        String codigoStatic = EjemplosFisio.codigoDe("Rust-like - elementos static");
        comprobar(FisioSemanticRunner.analizar(codigoStatic).exitoso(),
            "El ejemplo de static debe ser semánticamente válido");
        comprobar(generar(codigoStatic).generado(), "El CI debe recorrer elementos static");

        var referencias = generar("fn main() { let mut x = 4; let r: &i32 = &x; "
            + "let m: &mut i32 = &mut x; *m += 2; let y = *m + *r; }");
        igual(2, referencias.expresiones().size(), "CI de lectura y escritura mediante referencias");
        igual("6", referencias.expresiones().get(0).resultadoFinal(),
            "Asignación compuesta mediante referencia mutable");
        igual("10", referencias.expresiones().get(1).resultadoFinal(),
            "Desreferenciaciones resueltas dentro de aritmética");
        comprobar(referencias.expresiones().get(1).cuadruplos().stream()
                .filter(c -> c.operador().equals("DEREF")).count() == 2,
            "El CI debe representar cada desreferenciación con un cuádruplo");

        String codigoReferencias = EjemplosFisio.codigoDe("Rust-like - referencias");
        comprobar(FisioSemanticRunner.analizar(codigoReferencias).exitoso(),
            "El ejemplo de referencias debe ser semánticamente válido");
        comprobar(generar(codigoReferencias).generado(), "El CI debe recorrer referencias sin fallar");

        var metodos = generar("struct Dato { x: i32 } impl Dato { "
            + "fn nuevo(x: i32) -> Dato { Dato { x: x } } fn sumar(&self, n: i32) -> i32 { self.x + n } } "
            + "fn main() { let d = Dato::nuevo(1 + 2); let x = d.sumar(3 * 4); }");
        igual(3, metodos.expresiones().size(), "CI dentro de métodos y argumentos de sus llamadas");
        igual("self.x + n", metodos.expresiones().get(0).infija(), "Cuerpo de método recorrido");
        igual("3", metodos.expresiones().get(1).resultadoFinal(), "Argumento de función asociada resuelto");
        igual("12", metodos.expresiones().get(2).resultadoFinal(), "Argumento de método resuelto");

        String codigoImpl = EjemplosFisio.codigoDe("Rust-like - impl y metodos");
        comprobar(FisioSemanticRunner.analizar(codigoImpl).exitoso(),
            "El ejemplo de impl debe ser semánticamente válido");
        comprobar(generar(codigoImpl).generado(), "El CI debe recorrer bloques impl sin fallar");

        var traits = generar("trait Calculable { fn calcular(&self, n: i32) -> i32; } "
            + "struct Dato { x: i32 } impl Calculable for Dato { "
            + "fn calcular(&self, n: i32) -> i32 { self.x * 2 + n } } "
            + "fn main() { let d = Dato { x: 3 }; let x = d.calcular(4 + 1); }");
        igual(2, traits.expresiones().size(), "CI de implementación de trait");
        igual("self.x * 2 + n", traits.expresiones().get(0).infija(), "Cuerpo de método trait recorrido");
        igual("5", traits.expresiones().get(1).resultadoFinal(), "Argumento de método trait resuelto");

        String codigoTraits = EjemplosFisio.codigoDe("Rust-like - traits");
        comprobar(FisioSemanticRunner.analizar(codigoTraits).exitoso(),
            "El ejemplo de traits debe ser semánticamente válido");
        comprobar(generar(codigoTraits).generado(), "El CI debe recorrer implementaciones de trait");

        var algebraicos = generar("struct Punto(i32, i32); enum Opcion { Valor(i32), Par(i32, i32) } "
            + "fn extraer(o: Opcion) -> i32 { match o { Opcion::Valor(x) => x, Opcion::Par(a, b) => a + b, } } "
            + "fn main() { let p = Punto(1 + 2, 3 * 4); let o = Opcion::Par(p.0 + 1, p.1); }");
        igual(4, algebraicos.expresiones().size(), "CI de structs de tupla y variantes con datos");
        igual("a + b", algebraicos.expresiones().get(0).infija(), "Aritmética del patrón match recorrida");
        igual("3", algebraicos.expresiones().get(1).resultadoFinal(), "Primer campo del constructor resuelto");
        igual("12", algebraicos.expresiones().get(2).resultadoFinal(), "Segundo campo del constructor resuelto");
        comprobar(algebraicos.expresiones().get(3).infija().equals("p.0 + 1"),
            "Argumento de variante conservado en CI");

        String codigoAlgebraico = EjemplosFisio.codigoDe("Rust-like - datos algebraicos");
        comprobar(FisioSemanticRunner.analizar(codigoAlgebraico).exitoso(),
            "El ejemplo de datos algebraicos debe ser semánticamente válido");
        comprobar(generar(codigoAlgebraico).generado(), "El CI debe recorrer datos algebraicos");

        System.out.println("CI PASS: " + comprobaciones + " comprobaciones");
    }
}
