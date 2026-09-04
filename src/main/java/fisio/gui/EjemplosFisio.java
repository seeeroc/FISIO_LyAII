package fisio.gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los 6 ejemplos precargados de gui.py (diccionario EJEMPLOS), trasladados
 * tal cual -- mismo nombre, mismo codigo caracter por caracter, mismo
 * orden. Se usa LinkedHashMap para preservar el orden de insercion (es el
 * orden en que EJEMPLOS.keys() los entrega en Python, y el orden en que
 * aparecen en el JComboBox de gui.py).
 */
public final class EjemplosFisio {

    private static final Map<String, String> EJEMPLOS = crearEjemplos();

    private EjemplosFisio() {
    }

    /** Nombre -> codigo, en el mismo orden que gui.py. Inmutable. */
    public static Map<String, String> mapa() {
        return EJEMPLOS;
    }

    public static String codigoDe(String nombre) {
        return EJEMPLOS.getOrDefault(nombre, "");
    }

    private static Map<String, String> crearEjemplos() {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("MRU completo",
              "mru(velocidad := 20, tiempo := 5);\n"
            + "posicion := velocidad * tiempo;\n"
            + "graficar(posicion);\n");

        m.put("MRUA + caída libre",
              "mrua(aceleracion := 3, velocidad := 0, tiempo := 10);\n"
            + "caida(gravedad := 9.81);\n"
            + "altura := 150.5 - 0.5 * gravedad * tiempo ^ 2;\n"
            + "velocidadFinal := velocidad + aceleracion * tiempo;\n"
            + "simular(altura);\n");

        m.put("Tiro parabólico",
              "parabolico(alcance := 45, altura := 12.0);\n"
            + "raiz(posicion);\n"
            + "seno(angulo);\n"
            + "coseno(angulo);\n"
            + "magnitud(velocidad);\n"
            + "despejar(alcance);\n");

        m.put("Operadores y signos",
              "x := 10;\n"
            + "y := x + 5;\n"
            + "z := y - 3;\n"
            + "w := z * 2;\n"
            + "v := w / 4;\n"
            + "p := v ^ 2;\n"
            + "ok := x <= y;\n"
            + "nok := x != y;\n"
            + "lista[0];\n");

        m.put("Errores léxicos",
              "a := .5;\n"
            + "b := 5.;\n"
            + "c := 1..5;\n"
            + "d := 01;\n"
            + "e := 12a;\n"
            + "f := 9.8x;\n"
            + "g := 5@2;\n"
            + "h := 3#14;\n"
            + "i := 5 ! 3;\n"
            + "1variable := 10;\n");

        m.put("Errores de alfabeto (Reales)",
              "; Todos estos símbolos están fuera del alfabeto FISIO\n"
            + "x := 5 @ 2;\n"
            + "y := 3 # 14;\n"
            + "precio := 100$;\n"
            + "porcentaje := 50%;\n"
            + "y_comercial := a & b;\n"
            + "mi_variable := 25;\n"
            + "conjunto := { 1, 2, 3 };\n"
            + "mensaje := \"hola\";\n"
            + "operador_invalido := a \\ b | c;\n"
            + "pregunta := ¿test?;\n"
            + "info := ¡alerta!;\n");

        return Collections.unmodifiableMap(m);
    }
}
