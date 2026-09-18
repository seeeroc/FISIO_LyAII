# FISIO JavaCC

## Análisis semántico actual (Rust-like)

La gramática activa es el subconjunto Rust-like de `FISIO.jj`. El nuevo paquete
`fisio.semantic` valida su AST sin ejecutar el programa. Las secciones históricas
posteriores describen principalmente el FISIO original de física, no toda la
gramática actual.

En la GUI: **F5 → F6 → F7**. La pestaña **Semántica** incluye el botón
**Analizar semántica [F7]**, mensajes con línea/columna y códigos de error.
F7 exige análisis léxico y sintáctico sin errores del mismo texto; editar,
abrir, limpiar o cambiar de ejemplo invalida el resultado. Una fase bloqueada
no se muestra como un análisis exitoso.

Reglas implementadas:

- Ámbitos de función/bloque, uso antes de declarar y nombres inexistentes.
  Se permite volver a declarar con `let` (shadowing, como en Rust); se rechazan
  funciones y parámetros duplicados. El inicializador se resuelve antes de
  introducir el nuevo nombre. `_` descarta el enlace y no es una variable.
- Mutabilidad: asignación simple/compuesta exige `let mut`. Parámetros y
  variables de iteración son inmutables en la gramática actual.
- Tipos: `i8/i16/i32/i64/i128/isize`, `u8/u16/u32/u64/u128/usize`, `f32/f64`,
  `bool` y `String`. `isize/usize` se comprueban con 64 bits en este modelo.
  Sin anotación, los literales numéricos usan `i32/f64`; una anotación, llamada
  u operando tipado puede contextualizar un literal de la misma familia.
  No hay conversiones implícitas entre tipos ni inferencia global posterior.
  Se verifican los límites de literales numéricos.
- Operadores aritméticos y compuestos: operandos numéricos del mismo tipo.
  `&&/||/!` requieren booleanos; `-` admite números con signo y decimales.
  Igualdad entre tipos compatibles y orden entre números o cadenas.
- Condiciones `if/while` booleanas. `for` requiere un rango de enteros del
  mismo tipo y limita el ámbito de su variable al cuerpo.
- Firmas registradas antes de recorrer cuerpos: llamadas adelantadas y
  recursión, existencia, número/tipo de argumentos, retornos explícitos,
  expresión final y compatibilidad de ramas. Sin `->`, el retorno es `()`.
  No se exige `main`: también pueden analizarse archivos de funciones.
- `println!`: formato literal, marcadores `{}`, escapes de llaves `{{/}}`,
  cantidad de valores y tipos imprimibles (números, booleanos y cadenas).
  `println!()` es válido. Otras macros y formatos avanzados se diagnostican
  como no soportados. Una llamada `println(...)` es una función normal y
  debe estar declarada.
- División/resto por cero cuando el divisor es una expresión constante
  reconocida (literales, agrupación, negación, suma, resta y multiplicación).
  Se acumulan errores independientes y se evitan cascadas de tipos desconocidos.

Ejemplo: `let mut contador = 0; contador += "hola";` produce `OPERADOR` por
incompatibilidad `i32/String`. Con `contador += texto;` también se comprueba
que `texto` exista. `contador += ;` sigue siendo un error **sintáctico** y
bloquea F7 hasta corregirlo.

API sin GUI: `FisioSemanticRunner.analizar(String)` devuelve
`ResultadoSemantico` (`analizado`, `errores`, `motivo`, `exitoso()`). La
sobrecarga con resultados previos requiere que pertenezcan al mismo texto.

No es el compilador Rust: no implementa ownership, borrowing, traits,
inferencia global, evaluación de variables ni detección de todos los errores
en ejecución (por ejemplo, división por una variable que llegue a cero u
overflow de operaciones). Usa las estructuras que conserva el AST actual;
este todavía no distingue la inclusividad de `..` frente a `..=`.
Los ejemplos anteriores solo sintácticos pueden ahora generar errores
semánticos reales: el inicial usa un entero como iterable, y el de negativos
mezcla `f64` con `i32`. No se alteraron para ocultar esos diagnósticos.

Pruebas sin dependencias nuevas, desde PowerShell:

```powershell
.\scripts\test-semantico.ps1
```

El script ejecuta `test-compile` y la suite Java con comprobaciones explícitas,
incluida la integración Swing. También puede ejecutarse sin probar ventanas:

```powershell
.\mvnw.cmd test-compile
java -Djava.awt.headless=true -cp "target/classes;target/test-classes" fisio.semantic.SemanticRegression
```

`mvn test` por sí solo no ejecuta esta suite `main`; usar el script anterior.

## Descripción

FISIO es un lenguaje de dominio específico orientado a describir problemas y
cálculos de física clásica (MRU, MRUA, caída libre, tiro parabólico) con una
sintaxis simple de asignaciones, comandos y expresiones aritméticas.

Este proyecto es una **reimplementación de FISIO en Java, usando JavaCC**
como generador del analizador léxico y sintáctico, a partir de una versión
de referencia ya existente escrita en Python (Tkinter). El objetivo fue
conservar el comportamiento observable de esa versión original: mismo
alfabeto, misma gramática, mismos mensajes de error (léxicos y sintácticos),
misma recuperación ante múltiples errores, y una interfaz gráfica
equivalente — no rediseñarlo ni ampliar el lenguaje.

## Objetivo académico

Proyecto desarrollado en el contexto de la materia **Lenguajes y Autómatas
II**, como ejercicio práctico de construcción de un analizador léxico y
sintáctico completo con herramientas generadas (JavaCC) en lugar de un
analizador manual, cubriendo:

- especificación léxica (tokens, palabras reservadas, operadores, signos) y
  clasificación de errores léxicos;
- gramática BNF y su traducción a producciones JavaCC, con la precedencia y
  asociatividad de operadores resueltas por la propia estructura de la
  gramática;
- construcción de un **AST** (árbol de sintaxis abstracta) manual, sin
  JJTree;
- **recuperación de errores sintácticos** en modo pánico, permitiendo
  reportar múltiples errores en una sola pasada en vez de detenerse en el
  primero;
- integración de todo lo anterior en una **interfaz gráfica Swing**.

## Alcance actual

**Implementado:**

- editor de código con numeración de líneas y resaltado por tipo de token;
- análisis léxico completo (tokens, palabras reservadas, operadores, signos,
  identificadores, números) y clasificación de errores léxicos;
- análisis sintáctico completo con recuperación de múltiples errores y
  construcción de AST (completo o parcial);
- tabla de tokens, panel de resumen/estadísticas y panel de resultado
  sintáctico (AST y errores) en la GUI;
- abrir/guardar archivos `.fisio`, ejemplos precargados, limpiar;
- atajos de teclado F5 (léxico) / F6 (sintáctico).

**NO implementado (fuera de alcance de este proyecto):**

- semántica completa de Rust (ver el módulo del subconjunto descrito arriba);
- intérprete o ejecución real del programa;
- simulación física real (los comandos `mru`, `mrua`, `caida`, `parabolico`,
  `graficar`, `simular`, `despejar`, etc. se reconocen y validan
  sintácticamente, pero no calculan ni simulan nada);
- gráficas reales;
- resolución real de `despejar`.

## Tecnologías

- **Java 17** (compilado con `--release 17`; probado en este entorno con un
  JDK 21)
- **JavaCC 7.0.13** (motor generador, vía plugin de Maven)
- **Maven 3.9.9** (a través del Maven Wrapper incluido)
- **org.codehaus.mojo:javacc-maven-plugin 3.2.0**
- **Swing** (incluido en el JDK; sin librerías gráficas externas)

## Arquitectura

Pipeline de análisis, igual para F5 y F6 salvo que F6 vuelve a tokenizar
internamente (no reutiliza los tokens de F5):

```
Código .fisio
    ↓
JavaCC TokenManager (generado desde FISIO.jj)
    ↓
Tokens / errores léxicos          → fisio.token, fisio.analysis
    ↓
Parser JavaCC (generado desde FISIO.jj)
    ↓
AST / errores sintácticos         → fisio.ast, fisio.analysis
    ↓
GUI Swing                          → fisio.gui
```

Paquetes principales:

- `fisio.token` — modelo de dominio de un token/error léxico y su mapeo
  desde los tipos generados por JavaCC.
- `fisio.analysis` — `FisioLexerRunner` (F5) y `FisioParserRunner` (F6):
  puntos de entrada que la GUI usa para ejecutar el análisis sobre un
  `String` de código.
- `fisio.ast` — los 12 tipos de nodo del árbol de sintaxis abstracta.
- `fisio.gui` — la interfaz Swing completa.

## Estructura del proyecto

```
FISIO-JavaCC/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
│   └── wrapper/
├── .gitignore
├── examples/
│   └── programa_fisio.fisio
└── src/
    └── main/
        ├── javacc/
        │   └── fisio/
        │       └── FISIO.jj
        └── java/
            └── fisio/
                ├── analysis/   (FisioLexerRunner, FisioParserRunner, resultados, clasificación de errores)
                ├── ast/        (12 tipos de nodo del AST)
                ├── token/      (TokenFisio, ErrorLexico, categorías, mapeo)
                └── gui/        (ventana principal y paneles Swing)
```

`target/` no forma parte del proyecto: es la salida de Maven (clases
compiladas y el lexer/parser generado por JavaCC) y se regenera por completo
en cada compilación.

## Gramática

`src/main/javacc/fisio/FISIO.jj` contiene, en un solo archivo (formato
estándar de JavaCC):

- las especificaciones léxicas (`TOKEN`/`SKIP`): alfabeto, palabras
  reservadas, operadores, signos, identificadores, números y los tokens
  auxiliares usados para agrupar lexemas inválidos;
- la gramática sintáctica, expresada como producciones BNF de JavaCC
  (elecciones, repeticiones y opcionales reales — no si/while manuales
  simulando la gramática);
- bloques de acción en Java, usados puntualmente para construir el AST,
  registrar mensajes de error personalizados (en español, con el texto
  exacto ya validado contra la versión Python) y sincronizar la
  recuperación tras un error.

## Analizador léxico

Reconoce 20 palabras reservadas, 5 operadores matemáticos, 7 operadores
relacionales/de asignación, 7 signos, identificadores y números. Un lexema
que no encaja en ninguna categoría válida se agrupa como un único error
léxico (no carácter por carácter) y se clasifica en uno de varios mensajes
posibles (número mal formado, carácter fuera del alfabeto, operador
relacional incompleto, etc.).

Punto de entrada: `fisio.analysis.FisioLexerRunner.tokenize(String codigo)`,
usado por el botón/atajo **F5**.

## Analizador sintáctico

Gramática de 14 producciones (asignación, comandos de movimiento, comandos
de función, cálculos, expresión aritmética/relacional con su jerarquía de
precedencia, agrupación, acceso a arreglo, variables físicas, parámetros,
programa). La precedencia y asociatividad de operadores quedan resueltas
por la propia estructura de la gramática (no por tablas de precedencia
aparte). Ante un error, el parser registra un mensaje específico, sincroniza
hasta el siguiente punto seguro y continúa, permitiendo reportar **varios
errores en una sola pasada** en vez de detenerse en el primero.

Punto de entrada: `fisio.analysis.FisioParserRunner.parse(String codigo)`,
usado por el botón/atajo **F6** (re-tokeniza internamente; no reutiliza los
tokens de F5).

## AST

Doce tipos de nodo, todos como `record` de Java (inmutables) implementando
la interfaz sellada `NodoAST`:

`NodoPrograma`, `NodoAsignacion`, `NodoComando`, `NodoCalculo`,
`NodoParametro`, `NodoOperacion`, `NodoOperacionRelacional`,
`NodoAgrupacion`, `NodoNumero`, `NodoIdentificador`, `NodoAccesoArreglo`,
`NodoVariableFisica`.

## Interfaz gráfica

- **F5 — Léxico**: tokeniza el código actual y llena la tabla de tokens, el
  panel de resumen y el resaltado del editor; habilita F6 solo si no hubo
  errores léxicos.
- **F6 — Sintáctico**: solo puede ejecutarse tras un F5 exitoso sobre el
  código actual; llena el panel de sintaxis con el AST (o AST parcial) y los
  errores sintácticos.
- **Abrir / Guardar**: archivos `.fisio` en UTF-8.
- **Ejemplos…**: 6 ejemplos precargados (idénticos a los de la versión
  Python).
- **Limpiar**: vacía el editor y todos los resultados.
- Pestañas: **Tokens**, **Resumen**, **Sintaxis**, **Alfabeto** (esta última
  con la especificación léxica completa del lenguaje).

Editar el código después de F5 o F6 invalida automáticamente el análisis
anterior (F6 se deshabilita hasta volver a ejecutar F5).

## Requisitos

- JDK 17 o superior.
- Desarrollado y probado en Windows. Al usar únicamente Swing/JDK estándar
  (sin dependencias nativas), debería funcionar igual en Linux/macOS con un
  JDK compatible, pero no se probó en esos sistemas.

## Compilar

Windows:

```
.\mvnw.cmd clean compile
```

Unix/macOS:

```
./mvnw clean compile
```

No requiere tener Maven ni JavaCC instalados por separado: el Maven Wrapper
descarga Maven, y `javacc-maven-plugin` descarga y ejecuta JavaCC
automáticamente durante la compilación.

## Ejecutar

Por ahora (sin un `.jar` empaquetado todavía), después de compilar:

```
java -cp target/classes fisio.gui.AplicacionFisio
```

## Flujo de uso

1. Escribir código FISIO en el editor, o cargar un ejemplo / abrir un
   archivo `.fisio`.
2. Presionar **F5** (léxico).
3. Revisar la tabla de tokens y el resumen; corregir errores léxicos si los
   hay.
4. Si el léxico fue exitoso, presionar **F6** (sintáctico).
5. Revisar el AST o los errores sintácticos en la pestaña Sintaxis.

## Archivo de ejemplo

`examples/programa_fisio.fisio` es un programa FISIO completo, usado como
caso de regresión principal durante todo el desarrollo (sin modificar
respecto al proyecto Python original).

## Validación / paridad

La versión Java se comparó de forma directa y repetida contra la versión
Python de referencia (mismo código de entrada, misma salida esperada), no
solo una vez al final. La comparación principal, sobre el archivo de
ejemplo completo:

| | Python | Java |
|---|---|---|
| Tokens | 1548 | 1548 |
| Errores léxicos | 0 | 0 |
| Sentencias | 183 | 183 |
| Errores sintácticos | 0 | 0 |

con el AST estructuralmente equivalente entre ambas implementaciones.

También se compararon, con el mismo criterio de cero diferencias, casos con
errores léxicos, un único error sintáctico, múltiples errores sintácticos
en una sola entrada, y el caso de fin de archivo inesperado (EOF).

## Limitaciones

- El análisis semántico cubre el subconjunto documentado arriba, no Rust completo.
- No hay intérprete: el código no se ejecuta.
- No hay simulación física real ni cálculos numéricos de los comandos
  (`mru`, `mrua`, `caida`, `parabolico`, etc.).
- No hay gráficas reales.
- `despejar` se reconoce sintácticamente pero no despeja nada.
