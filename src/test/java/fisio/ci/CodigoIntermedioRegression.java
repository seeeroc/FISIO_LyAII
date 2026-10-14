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

        var reasignaciones = generar("fn main() { let mut x = 2; x = x + 3; let y = x * 2; }");
        igual(2, reasignaciones.expresiones().size(), "Expresiones tras reasignación");
        igual("5", reasignaciones.expresiones().get(0).resultadoFinal(), "Resultado de reasignación");
        igual("10", reasignaciones.expresiones().get(1).resultadoFinal(), "Propagación lineal segura");

        ExpresionIntermedia tipoReasignado = unica("let mut x: f64 = 5.0; x = 5; let y = x / 2;");
        igual("2.5", tipoReasignado.resultadoFinal(), "La reasignación conserva el tipo decimal declarado");

        ExpresionIntermedia compuesta = unica("let mut x = 2; x += 3;");
        igual("x + 3", compuesta.infija(), "Asignación compuesta");
        igual("5", compuesta.resultadoFinal(), "Resultado de asignación compuesta");

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

        System.out.println("CI PASS: " + comprobaciones + " comprobaciones");
    }
}
