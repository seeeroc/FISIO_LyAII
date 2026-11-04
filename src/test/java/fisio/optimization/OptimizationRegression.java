package fisio.optimization;

import fisio.semantic.FisioSemanticRunner;

public final class OptimizationRegression {
    private static int comprobaciones;

    private OptimizationRegression() {
    }

    private static void comprobar(boolean condicion, String mensaje) {
        comprobaciones++;
        if (!condicion) throw new AssertionError(mensaje);
    }

    public static void main(String[] args) {
        String codigo = """
            fn efecto() -> i32 {
                7
            }

            fn main() {
                // El comentario se elimina.
                let sin_usar = 99;
                let llamada = efecto();
                let mut total = 2 + 3 * 4;
                total = total + 0;
                println!("https://fisio.test/{}", total);
                /* El bloque también se elimina,
                   pero conserva la estructura de líneas. */
                return;
                let inalcanzable = 50;
                println!("{}", inalcanzable);
            }
            """;

        ResultadoOptimizacion resultado = OptimizadorCodigo.optimizar(codigo);
        comprobar(resultado.exitoso(), "La optimización válida no debe bloquearse: " + resultado.motivo());
        comprobar(resultado.codigoOptimizado().contains("let llamada = efecto();"),
            "Una llamada cuyo resultado no se usa debe conservarse por sus posibles efectos");
        comprobar(!resultado.codigoOptimizado().contains("sin_usar"), "Variable no utilizada conservada");
        comprobar(!resultado.codigoOptimizado().contains("inalcanzable"), "Código inalcanzable conservado");
        comprobar(!resultado.codigoOptimizado().contains("El comentario"), "Comentario de línea conservado");
        comprobar(!resultado.codigoOptimizado().contains("El bloque"), "Comentario de bloque conservado");
        comprobar(resultado.codigoOptimizado().contains("https://fisio.test/{}"),
            "Los símbolos de comentario dentro de una cadena no deben modificarse");
        comprobar(resultado.codigoOptimizado().contains("let mut total = 14;"),
            "No se plegó la expresión constante");
        comprobar(!resultado.codigoOptimizado().contains("total = total"),
            "La simplificación no retiró la autoasignación resultante");
        comprobar(FisioSemanticRunner.analizar(resultado.codigoOptimizado()).exitoso(),
            "El resultado debe volver a superar el análisis semántico");

        for (TecnicaOptimizacion tecnica : TecnicaOptimizacion.values()) {
            comprobar(resultado.metricas().cambiosPorTecnica().getOrDefault(tecnica, 0) > 0,
                "Falta cobertura para " + tecnica);
        }
        comprobar(resultado.metricas().bytesOptimizados() < resultado.metricas().bytesOriginales(),
            "La limpieza debe reducir el tamaño UTF-8");
        comprobar(resultado.metricas().cuadruplosOptimizados() < resultado.metricas().cuadruplosOriginales(),
            "Las optimizaciones deben reducir los cuádruplos de este caso");
        comprobar(resultado.reporteTexto().contains("REPORTE DE OPTIMIZACIÓN FISIO")
                && resultado.reporteTexto().contains("Antes:")
                && resultado.reporteTexto().contains("Después:"),
            "El reporte TXT debe incluir encabezado y detalle de cambios");
        comprobar(!resultado.lineaOriginalPorOptimizada().isEmpty(), "Falta el mapa de líneas");

        ResultadoOptimizacion division = OptimizadorCodigo.optimizar("""
            fn main() {
                let divisor = 0;
                let sin_usar = 10 / divisor;
            }
            """);
        comprobar(division.exitoso(), "El caso conservador debe analizarse");
        comprobar(division.codigoOptimizado().contains("10 / divisor"),
            "Una división potencialmente fallida no debe eliminarse");

        ResultadoOptimizacion ambitos = OptimizadorCodigo.optimizar("""
            fn uno() -> i32 { let repetida = 10; return 1; }
            fn dos() -> i32 { let repetida = 20; return 2; }
            fn main() { println!("{} {}", uno(), dos()); }
            """);
        comprobar(ambitos.exitoso() && !ambitos.codigoOptimizado().contains("let repetida"),
            "Cada variable no utilizada debe evaluarse dentro de su propio ámbito");

        ResultadoOptimizacion cadenaSinUso = OptimizadorCodigo.optimizar("""
            fn main() {
                let primero = 1;
                let segundo = primero + 1;
            }
            """);
        comprobar(cadenaSinUso.exitoso() && !cadenaSinUso.codigoOptimizado().contains("let primero")
                && !cadenaSinUso.codigoOptimizado().contains("let segundo"),
            "La eliminación debe propagarse por cadenas completas de variables no utilizadas");

        ResultadoOptimizacion precedencia = OptimizadorCodigo.optimizar("""
            fn main() {
                let a = 5;
                let b = 3;
                let expresion = a + b * 0;
                println!("{}", expresion);
            }
            """);
        comprobar(precedencia.exitoso() && precedencia.codigoOptimizado().contains("a + b * 0"),
            "Una regla algebraica no debe ignorar la precedencia del AST");

        ResultadoOptimizacion efectos = OptimizadorCodigo.optimizar("""
            fn efecto() -> i32 { 7 }
            fn main() {
                let observado = efecto() * 0;
                println!("{}", observado);
            }
            """);
        comprobar(efectos.exitoso() && efectos.codigoOptimizado().contains("efecto() * 0"),
            "La simplificación no debe eliminar llamadas con posibles efectos");

        ResultadoOptimizacion comentariosAnidados = OptimizadorCodigo.optimizar("""
            fn main() {
                /* comentario exterior /* comentario interior */ cierre */
                println!("https://fisio.test/*ruta*/");
            }
            """);
        comprobar(comentariosAnidados.exitoso()
                && !comentariosAnidados.codigoOptimizado().contains("comentario exterior")
                && comentariosAnidados.codigoOptimizado().contains("https://fisio.test/*ruta*/"),
            "Los comentarios anidados deben eliminarse sin alterar cadenas");

        ResultadoOptimizacion flujoBucle = OptimizadorCodigo.optimizar("""
            fn main() {
                loop {
                    break;
                    let despues_break = 10;
                }
            }
            """);
        comprobar(flujoBucle.exitoso() && !flujoBucle.codigoOptimizado().contains("despues_break"),
            "El código posterior a break debe eliminarse dentro del bucle");

        ResultadoOptimizacion invalido = OptimizadorCodigo.optimizar("fn main( {");
        comprobar(!invalido.exitoso() && invalido.codigoOptimizado().equals(invalido.codigoOriginal()),
            "El código inválido debe bloquearse y conservarse");

        System.out.println("OPTIMIZATION PASS: " + comprobaciones + " comprobaciones");
    }
}
