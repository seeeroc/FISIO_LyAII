package fisio.semantic;

import fisio.ast.*;
import fisio.token.CategoriaToken;
import fisio.token.TokenFisio;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dos pasadas: firmas globales, luego cuerpos con ambitos lexicos.
 * No ejecuta el programa ni intenta implementar ownership o genericos de Rust.
 */
final class AnalizadorSemantico {
    private record Tipo(String nombre, Tipo elemento, List<Tipo> componentes, int longitud) {
        Tipo {
            componentes = List.copyOf(componentes);
        }
        Tipo(String nombre) { this(nombre, null, List.of(), -1); }
        Tipo(String nombre, Tipo elemento) { this(nombre, elemento, List.of(), -1); }
        static Tipo arreglo(Tipo elemento, int longitud) {
            return new Tipo("arreglo", elemento, List.of(), longitud);
        }
        static Tipo tupla(List<Tipo> componentes) {
            return new Tipo("tupla", null, componentes, -1);
        }
        static Tipo referencia(Tipo elemento, boolean mutable) {
            return new Tipo(mutable ? "referencia_mut" : "referencia", elemento);
        }
        boolean entero() { return ENTEROS.contains(nombre); }
        boolean decimal() { return nombre.equals("f32") || nombre.equals("f64"); }
        boolean numerico() { return entero() || decimal(); }
        boolean rango() { return nombre.equals("rango") || nombre.equals("rango_inclusivo"); }
        boolean arreglo() { return nombre.equals("arreglo"); }
        boolean tupla() { return nombre.equals("tupla"); }
        boolean referencia() { return nombre.equals("referencia") || nombre.equals("referencia_mut"); }
        boolean referenciaMutable() { return nombre.equals("referencia_mut"); }
        @Override public String toString() {
            if (rango()) return nombre + "<" + elemento + ">";
            if (arreglo()) return "[" + elemento + "; " + longitud + "]";
            if (tupla()) {
                if (componentes.isEmpty()) return "()";
                String texto = String.join(", ", componentes.stream().map(Tipo::toString).toList());
                return "(" + texto + (componentes.size() == 1 ? "," : "") + ")";
            }
            if (referencia()) return (referenciaMutable() ? "&mut " : "&") + elemento;
            return nombre;
        }
    }
    private record Variable(Tipo tipo, boolean mutable, boolean constante) {
        Variable(Tipo tipo, boolean mutable) { this(tipo, mutable, false); }
    }
    private record Firma(List<Tipo> parametros, Tipo retorno) {}
    private record DefStruct(Map<String, Tipo> campos, boolean tupla) {
        DefStruct { campos = mapaInmutableOrdenado(campos); }
    }
    private record DefVariante(List<Tipo> posicionales, Map<String, Tipo> campos, NodoEnum.Forma forma) {
        DefVariante {
            posicionales = List.copyOf(posicionales);
            campos = mapaInmutableOrdenado(campos);
        }
    }
    private record DefEnum(Map<String, DefVariante> variantes) {
        DefEnum { variantes = mapaInmutableOrdenado(variantes); }
    }
    /** Patrón normalizado para el algoritmo de utilidad/exhaustividad. Variante nula significa comodín. */
    private record PatronCobertura(String variante, List<PatronCobertura> datos) {
        PatronCobertura {
            datos = List.copyOf(datos);
        }
        static PatronCobertura comodin() { return new PatronCobertura(null, List.of()); }
        boolean esComodin() { return variante == null; }
    }
    private record DependenciaTipo(String destino, TokenFisio ubicacion) {}
    private static final class ContextoBucle { boolean contieneBreak; }

    private static <K, V> Map<K, V> mapaInmutableOrdenado(Map<K, V> origen) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(origen));
    }

    private static final Set<String> ENTEROS = Set.of(
        "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize");
    private static final Tipo I32 = new Tipo("i32"), F64 = new Tipo("f64"), BOOL = new Tipo("bool"),
        STRING = new Tipo("String"), CHAR = new Tipo("char"), UNIDAD = new Tipo("()"), ERROR = new Tipo("<error>"),
        NUNCA = new Tipo("!");

    private final List<ErrorSemantico> errores = new ArrayList<>();
    private final Map<String, Firma> funciones = new LinkedHashMap<>();
    private final Map<String, Firma> metodos = new LinkedHashMap<>();
    private final Map<String, NodoFuncion> nodosMetodo = new LinkedHashMap<>();
    private final Map<NodoFuncion, Firma> firmas = new IdentityHashMap<>();
    private final Map<NodoFuncion, String> propietariosMetodo = new IdentityHashMap<>();
    private final List<NodoImpl> bloquesImpl = new ArrayList<>();
    private final Map<String, NodoStruct> nodosStruct = new LinkedHashMap<>();
    private final Map<String, NodoEnum> nodosEnum = new LinkedHashMap<>();
    private final Map<String, NodoTrait> nodosTrait = new LinkedHashMap<>();
    private final Map<String, NodoAliasTipo> nodosAlias = new LinkedHashMap<>();
    private final Map<String, NodoConstante> nodosConstante = new LinkedHashMap<>();
    private final Map<String, NodoEstatico> nodosEstatico = new LinkedHashMap<>();
    private final Map<String, DefStruct> structs = new LinkedHashMap<>();
    private final Map<String, DefEnum> enums = new LinkedHashMap<>();
    private final Map<String, Map<String, Firma>> traits = new LinkedHashMap<>();
    private final Set<String> implementacionesTrait = new LinkedHashSet<>();
    private final Map<String, Tipo> aliases = new LinkedHashMap<>();
    private final Map<String, Variable> constantesGlobales = new LinkedHashMap<>();
    private final Deque<Map<String, Variable>> ambitos = new ArrayDeque<>();
    private final Deque<ContextoBucle> bucles = new ArrayDeque<>();
    private Tipo retornoActual;
    private final Map<NodoAST, Tipo> tiposExpresion = new IdentityHashMap<>();

    ResultadoSemantico analizar(NodoPrograma programa) {
        // Primera pasada: registrar todos los nombres permite referencias adelantadas entre tipos.
        for (NodoAST nodo : programa.sentencias()) {
            if (nodo instanceof NodoStruct n) registrarStruct(n);
            else if (nodo instanceof NodoEnum n) registrarEnum(n);
            else if (nodo instanceof NodoTrait n) registrarTrait(n);
            else if (nodo instanceof NodoAliasTipo n) registrarAlias(n);
            else if (nodo instanceof NodoConstante n) registrarConstante(n);
            else if (nodo instanceof NodoEstatico n) registrarEstatico(n);
            else if (nodo instanceof NodoImpl n) bloquesImpl.add(n);
            else if (!(nodo instanceof NodoFuncion))
                error(nodo, "ESTRUCTURA", "Solo se admiten funciones, structs, enums, impl, alias, constantes y static en el nivel superior.");
        }
        resolverAliases();
        for (var entrada : nodosStruct.entrySet()) resolverStruct(entrada.getKey(), entrada.getValue());
        for (var entrada : nodosEnum.entrySet()) resolverEnum(entrada.getKey(), entrada.getValue());
        for (var entrada : nodosTrait.entrySet()) resolverTrait(entrada.getKey(), entrada.getValue());
        validarStructsRecursivos();
        for (var entrada : nodosConstante.entrySet()) {
            Tipo tipo = tipoAnotado(entrada.getValue().tipo());
            constantesGlobales.put(entrada.getKey(), new Variable(tipo, false, true));
        }
        for (var entrada : nodosEstatico.entrySet()) {
            Tipo tipo = tipoAnotado(entrada.getValue().tipo());
            constantesGlobales.put(entrada.getKey(), new Variable(tipo, entrada.getValue().mutable(), false));
        }

        // Segunda pasada: firmas de funciones, ahora con tipos de usuario disponibles.
        for (NodoAST nodo : programa.sentencias()) {
            if (!(nodo instanceof NodoFuncion f)) continue;
            Firma firma = crearFirma(f);
            if (funciones.putIfAbsent(f.nombre().lexema(), firma) != null) {
                error(f, "FUNCION_DUPLICADA", "La funcion '" + f.nombre().lexema() + "' ya esta declarada.");
            }
            if (nodosConstante.containsKey(f.nombre().lexema()) || nodosEstatico.containsKey(f.nombre().lexema())) {
                error(f, "SIMBOLO_DUPLICADO", "El nombre '" + f.nombre().lexema()
                    + "' ya pertenece a un elemento global.");
            }
        }
        for (NodoImpl bloque : bloquesImpl) registrarMetodos(bloque);
        ambitos.push(constantesGlobales);
        for (NodoConstante constante : nodosConstante.values()) validarConstante(constante, true);
        for (NodoEstatico estatico : nodosEstatico.values()) validarEstatico(estatico, true);
        validarCiclosConstantes();
        for (NodoAST nodo : programa.sentencias())
            if (nodo instanceof NodoFuncion f) validarFuncion(f);
        for (NodoImpl bloque : bloquesImpl)
            for (NodoFuncion metodo : bloque.metodos()) validarFuncion(metodo);
        ambitos.pop();
        return new ResultadoSemantico(true, errores, "");
    }

    private Firma crearFirma(NodoFuncion funcion) {
        List<Tipo> parametros = new ArrayList<>();
        for (var parametro : funcion.parametros()) parametros.add(tipoAnotado(parametro.tipo()));
        Firma firma = new Firma(List.copyOf(parametros),
            funcion.tipoRetorno() == null ? UNIDAD : tipoAnotado(funcion.tipoRetorno()));
        firmas.put(funcion, firma);
        return firma;
    }

    private void registrarMetodos(NodoImpl bloque) {
        String propietario = bloque.tipo().lexema();
        String nombreTrait = bloque.rasgo() == null ? null : bloque.rasgo().lexema();
        boolean implementacionTraitDuplicada = false;
        if (!structs.containsKey(propietario) && !enums.containsKey(propietario)) {
            error(bloque, "IMPL_TIPO", "No se puede implementar el tipo '" + propietario
                + "': solo se admiten structs y enums declarados en FISIO.");
        }
        Map<String, Firma> contrato = null;
        if (nombreTrait != null) {
            contrato = traits.get(nombreTrait);
            if (contrato == null) {
                error(bloque, "TRAIT_NO_DECLARADO", "No existe un trait llamado '" + nombreTrait + "'.");
            }
            String implementacion = nombreTrait + " for " + propietario;
            if (!implementacionesTrait.add(implementacion)) {
                error(bloque, "IMPL_TRAIT_DUPLICADO", "Ya existe una implementación de '" + implementacion + "'.");
                implementacionTraitDuplicada = true;
            }
        }
        Set<String> implementados = new LinkedHashSet<>();
        for (NodoFuncion metodo : bloque.metodos()) {
            Firma firma = crearFirma(metodo);
            propietariosMetodo.put(metodo, propietario);
            String clave = propietario + "::" + metodo.nombre().lexema();
            if (!implementacionTraitDuplicada && metodos.putIfAbsent(clave, firma) != null) {
                error(metodo, "METODO_DUPLICADO", "El método '" + metodo.nombre().lexema()
                    + "' ya está definido para '" + propietario + "'.");
            }
            if (!implementacionTraitDuplicada) nodosMetodo.putIfAbsent(clave, metodo);
            implementados.add(metodo.nombre().lexema());
            if (nombreTrait != null && metodo.publico()) {
                error(metodo, "VISIBILIDAD_TRAIT",
                    "Los métodos de una implementación de trait no se declaran con 'pub'.");
            }
            if (contrato != null) {
                Firma esperada = contrato.get(metodo.nombre().lexema());
                if (esperada == null) {
                    error(metodo, "METODO_TRAIT_EXTRA", "El método '" + metodo.nombre().lexema()
                        + "' no pertenece al trait '" + nombreTrait + "'.");
                } else {
                    Firma sustituida = sustituirSelf(esperada, new Tipo(propietario));
                    if (!sustituida.equals(firma)) {
                        error(metodo, "FIRMA_TRAIT", "La firma de '" + metodo.nombre().lexema()
                            + "' no coincide con el contrato del trait '" + nombreTrait + "'.");
                    }
                }
            }
            for (int i = 0; i < metodo.parametros().size(); i++) {
                NodoParametroFuncion parametro = metodo.parametros().get(i);
                if (!parametro.nombre().lexema().equals("self")) continue;
                if (i != 0) {
                    error(parametro, "RECEPTOR_SELF", "El receptor self debe ser el primer parámetro del método.");
                    continue;
                }
                Tipo receptor = firma.parametros().get(i);
                Tipo base = receptor.referencia() ? receptor.elemento() : receptor;
                if (!base.equals(new Tipo(propietario))) {
                    error(parametro, "RECEPTOR_SELF", "El receptor self de '" + propietario
                        + "' tiene un tipo incompatible: " + receptor + ".");
                }
            }
        }
        if (contrato != null) {
            Set<String> faltantes = new LinkedHashSet<>(contrato.keySet());
            faltantes.removeAll(implementados);
            if (!faltantes.isEmpty()) {
                error(bloque, "METODO_TRAIT_FALTANTE", "La implementación de '" + nombreTrait
                    + "' para '" + propietario + "' no define: " + String.join(", ", faltantes) + ".");
            }
        }
    }

    private Firma sustituirSelf(Firma firma, Tipo propietario) {
        return new Firma(firma.parametros().stream().map(t -> sustituirSelf(t, propietario)).toList(),
            sustituirSelf(firma.retorno(), propietario));
    }

    private Tipo sustituirSelf(Tipo tipo, Tipo propietario) {
        if (tipo.nombre().equals("Self")) return propietario;
        if (tipo.arreglo()) return Tipo.arreglo(sustituirSelf(tipo.elemento(), propietario), tipo.longitud());
        if (tipo.referencia()) return Tipo.referencia(sustituirSelf(tipo.elemento(), propietario), tipo.referenciaMutable());
        if (tipo.tupla()) return Tipo.tupla(tipo.componentes().stream()
            .map(t -> sustituirSelf(t, propietario)).toList());
        return tipo;
    }

    private void validarFuncion(NodoFuncion funcion) {
        Firma firma = firmas.get(funcion);
        if (firma == null) return;
        retornoActual = firma.retorno();
        ambitos.push(new LinkedHashMap<>());
        for (int i = 0; i < funcion.parametros().size(); i++) {
            var parametro = funcion.parametros().get(i);
            String nombre = parametro.nombre().lexema();
            if (!nombre.equals("_") && ambitos.peek().putIfAbsent(nombre,
                    new Variable(firma.parametros().get(i), false)) != null) {
                error(parametro, "PARAMETRO_DUPLICADO", "El parametro '" + nombre + "' esta repetido.");
            }
        }
        Tipo cuerpo = bloque(funcion.cuerpo(), retornoActual);
        comprobarRetorno(funcion, cuerpo, false, "Retorno de '" + funcion.nombre().lexema() + "'");
        ambitos.pop();
    }

    private void registrarConstante(NodoConstante nodo) {
        String nombre = nodo.nombre().lexema();
        if (nodosEstatico.containsKey(nombre) || nodosConstante.putIfAbsent(nombre, nodo) != null) {
            error(nodo, "CONSTANTE_DUPLICADA", "La constante global '" + nombre + "' ya está declarada.");
        }
    }

    private void registrarEstatico(NodoEstatico nodo) {
        String nombre = nodo.nombre().lexema();
        if (nodosConstante.containsKey(nombre) || nodosEstatico.putIfAbsent(nombre, nodo) != null) {
            error(nodo, "STATIC_DUPLICADO", "El elemento static '" + nombre + "' ya está declarado.");
        }
    }

    private void registrarAlias(NodoAliasTipo nodo) {
        String nombre = nodo.nombre().lexema();
        if (nombreTipoOcupado(nombre)) {
            error(nodo, "TIPO_DUPLICADO", "El tipo '" + nombre + "' ya está declarado o es un tipo primitivo.");
            return;
        }
        nodosAlias.put(nombre, nodo);
    }

    private void registrarStruct(NodoStruct nodo) {
        String nombre = nodo.nombre().lexema();
        if (nombreTipoOcupado(nombre)) {
            error(nodo, "TIPO_DUPLICADO", "El tipo '" + nombre + "' ya está declarado o es un tipo primitivo.");
            return;
        }
        nodosStruct.put(nombre, nodo);
    }

    private void registrarEnum(NodoEnum nodo) {
        String nombre = nodo.nombre().lexema();
        if (nombreTipoOcupado(nombre)) {
            error(nodo, "TIPO_DUPLICADO", "El tipo '" + nombre + "' ya está declarado o es un tipo primitivo.");
            return;
        }
        nodosEnum.put(nombre, nodo);
    }

    private void registrarTrait(NodoTrait nodo) {
        String nombre = nodo.nombre().lexema();
        if (nombreTipoOcupado(nombre)) {
            error(nodo, "TIPO_DUPLICADO", "El trait '" + nombre + "' ya está declarado o colisiona con otro tipo.");
            return;
        }
        nodosTrait.put(nombre, nodo);
    }

    private boolean nombreTipoOcupado(String nombre) {
        return tipoPrimitivo(new Tipo(nombre)) || nodosStruct.containsKey(nombre)
            || nodosEnum.containsKey(nombre) || nodosTrait.containsKey(nombre) || nodosAlias.containsKey(nombre);
    }

    private void resolverAliases() {
        Map<String, Integer> estado = new LinkedHashMap<>();
        for (String nombre : nodosAlias.keySet()) resolverAlias(nombre, estado);
    }

    private Tipo resolverAlias(String nombre, Map<String, Integer> estado) {
        if (aliases.containsKey(nombre)) return aliases.get(nombre);
        if (estado.getOrDefault(nombre, 0) == 1) {
            NodoAliasTipo nodo = nodosAlias.get(nombre);
            error(nodo, "ALIAS_CICLICO", "El alias de tipo '" + nombre + "' forma una dependencia cíclica.");
            aliases.put(nombre, ERROR);
            return ERROR;
        }
        estado.put(nombre, 1);
        NodoAliasTipo nodo = nodosAlias.get(nombre);
        Tipo tipo = expandirAlias(parsearTipo(nodo.tipo().lexema()), estado);
        if (contieneError(tipo)) {
            tipo = ERROR;
        } else if (tipo == null || !tipoSoportado(tipo)) {
            error(nodo.tipo(), "TIPO_DESCONOCIDO", "Tipo no soportado en el alias '" + nombre
                + "': '" + nodo.tipo().lexema() + "'.");
            tipo = ERROR;
        }
        aliases.put(nombre, tipo);
        estado.put(nombre, 2);
        return tipo;
    }

    private boolean contieneError(Tipo tipo) {
        if (tipo == null || tipo.equals(ERROR)) return true;
        return tipo.elemento() != null && contieneError(tipo.elemento())
            || tipo.componentes().stream().anyMatch(this::contieneError);
    }

    private Tipo expandirAlias(Tipo tipo, Map<String, Integer> estado) {
        if (tipo == null) return null;
        if (nodosAlias.containsKey(tipo.nombre())) return resolverAlias(tipo.nombre(), estado);
        if (tipo.arreglo()) return Tipo.arreglo(expandirAlias(tipo.elemento(), estado), tipo.longitud());
        if (tipo.referencia()) return Tipo.referencia(expandirAlias(tipo.elemento(), estado), tipo.referenciaMutable());
        if (tipo.tupla()) {
            List<Tipo> componentes = new ArrayList<>();
            for (Tipo componente : tipo.componentes()) componentes.add(expandirAlias(componente, estado));
            return Tipo.tupla(componentes);
        }
        return tipo;
    }

    private Tipo expandirAlias(Tipo tipo) {
        if (tipo == null) return null;
        if (aliases.containsKey(tipo.nombre())) return aliases.get(tipo.nombre());
        if (tipo.arreglo()) return Tipo.arreglo(expandirAlias(tipo.elemento()), tipo.longitud());
        if (tipo.referencia()) return Tipo.referencia(expandirAlias(tipo.elemento()), tipo.referenciaMutable());
        if (tipo.tupla()) return Tipo.tupla(tipo.componentes().stream().map(this::expandirAlias).toList());
        return tipo;
    }

    private void resolverStruct(String nombre, NodoStruct nodo) {
        Map<String, Tipo> campos = new LinkedHashMap<>();
        for (NodoStruct.Campo campo : nodo.campos()) {
            Tipo tipo = tipoAnotado(campo.tipo());
            if (campos.putIfAbsent(campo.nombre().lexema(), tipo) != null) {
                error(campo.nombre(), "CAMPO_DUPLICADO",
                    "El campo '" + campo.nombre().lexema() + "' está repetido en el struct '" + nombre + "'.");
            }
        }
        structs.put(nombre, new DefStruct(campos, nodo.tupla()));
    }

    private void resolverEnum(String nombre, NodoEnum nodo) {
        Map<String, DefVariante> variantes = new LinkedHashMap<>();
        for (NodoEnum.Variante variante : nodo.variantes()) {
            List<Tipo> datos = variante.tipos().stream().map(this::tipoAnotado).toList();
            Map<String, Tipo> campos = new LinkedHashMap<>();
            for (NodoEnum.Campo campo : variante.campos()) {
                Tipo tipo = tipoAnotado(campo.tipo());
                if (campos.putIfAbsent(campo.nombre().lexema(), tipo) != null) {
                    error(campo.nombre(), "CAMPO_DUPLICADO", "El campo '" + campo.nombre().lexema()
                        + "' está repetido en la variante '" + nombre + "::" + variante.lexema() + "'.");
                }
            }
            DefVariante definicion = new DefVariante(datos, campos, variante.forma());
            if (variantes.putIfAbsent(variante.lexema(), definicion) != null) {
                error(variante.nombre(), "VARIANTE_DUPLICADA",
                    "La variante '" + variante.lexema() + "' está repetida en el enum '" + nombre + "'.");
            }
        }
        enums.put(nombre, new DefEnum(variantes));
    }

    private void resolverTrait(String nombre, NodoTrait nodo) {
        Map<String, Firma> firmasTrait = new LinkedHashMap<>();
        for (NodoFirmaTrait metodo : nodo.metodos()) {
            List<Tipo> parametros = new ArrayList<>();
            for (NodoParametroFuncion parametro : metodo.parametros())
                parametros.add(tipoEnTrait(parametro.tipo()));
            Firma firma = new Firma(List.copyOf(parametros),
                metodo.tipoRetorno() == null ? UNIDAD : tipoEnTrait(metodo.tipoRetorno()));
            if (firmasTrait.putIfAbsent(metodo.nombre().lexema(), firma) != null) {
                error(metodo, "METODO_TRAIT_DUPLICADO", "El método '" + metodo.nombre().lexema()
                    + "' está repetido en el trait '" + nombre + "'.");
            }
            for (int i = 0; i < metodo.parametros().size(); i++) {
                if (metodo.parametros().get(i).nombre().lexema().equals("self") && i != 0) {
                    error(metodo.parametros().get(i), "RECEPTOR_SELF",
                        "El receptor self debe ser el primer parámetro del método del trait.");
                }
            }
        }
        traits.put(nombre, mapaInmutableOrdenado(firmasTrait));
    }

    private Tipo tipoEnTrait(TokenFisio token) {
        Tipo tipo = expandirAlias(parsearTipo(token.lexema()));
        if (tipo != null && tipoValidoEnTrait(tipo)) return tipo;
        error(token, "TIPO_DESCONOCIDO", "Tipo no soportado en la firma del trait: '" + token.lexema() + "'.");
        return ERROR;
    }

    private boolean tipoValidoEnTrait(Tipo tipo) {
        if (tipo == null) return false;
        if (tipo.nombre().equals("Self")) return true;
        if (tipo.arreglo()) return tipo.longitud() >= 0 && tipoValidoEnTrait(tipo.elemento());
        if (tipo.referencia()) return tipoValidoEnTrait(tipo.elemento());
        if (tipo.tupla()) return tipo.componentes().stream().allMatch(this::tipoValidoEnTrait);
        return tipoSoportado(tipo);
    }

    private void validarStructsRecursivos() {
        Map<String, Integer> estado = new LinkedHashMap<>();
        Set<String> nombres = new LinkedHashSet<>(structs.keySet());
        nombres.addAll(enums.keySet());
        for (String nombre : nombres)
            if (estado.getOrDefault(nombre, 0) == 0) visitarTipoDatos(nombre, estado);
    }

    private void visitarTipoDatos(String nombre, Map<String, Integer> estado) {
        estado.put(nombre, 1);
        List<DependenciaTipo> dependencias = new ArrayList<>();
        NodoStruct struct = nodosStruct.get(nombre);
        DefStruct definicionStruct = structs.get(nombre);
        if (struct != null && definicionStruct != null) {
            for (NodoStruct.Campo campo : struct.campos()) {
                Set<String> destinos = new LinkedHashSet<>();
                recolectarTiposDatos(definicionStruct.campos().get(campo.nombre().lexema()), destinos);
                for (String destino : destinos) dependencias.add(new DependenciaTipo(destino, campo.tipo()));
            }
        }
        NodoEnum enumNodo = nodosEnum.get(nombre);
        DefEnum definicionEnum = enums.get(nombre);
        if (enumNodo != null && definicionEnum != null) {
            for (NodoEnum.Variante variante : enumNodo.variantes()) {
                DefVariante definicionVariante = definicionEnum.variantes().get(variante.lexema());
                List<Tipo> tipos = definicionVariante == null ? List.of() : definicionVariante.posicionales();
                for (int i = 0; i < tipos.size(); i++) {
                    Set<String> destinos = new LinkedHashSet<>();
                    recolectarTiposDatos(tipos.get(i), destinos);
                    TokenFisio ubicacion = i < variante.tipos().size() ? variante.tipos().get(i) : variante.nombre();
                    for (String destino : destinos) dependencias.add(new DependenciaTipo(destino, ubicacion));
                }
                if (definicionVariante != null) {
                    for (NodoEnum.Campo campo : variante.campos()) {
                        Set<String> destinos = new LinkedHashSet<>();
                        recolectarTiposDatos(definicionVariante.campos().get(campo.nombre().lexema()), destinos);
                        for (String destino : destinos) dependencias.add(new DependenciaTipo(destino, campo.tipo()));
                    }
                }
            }
        }
        for (DependenciaTipo dependencia : dependencias) {
            int estadoDestino = estado.getOrDefault(dependencia.destino(), 0);
            if (estadoDestino == 1) {
                error(dependencia.ubicacion(), "TIPO_RECURSIVO", "El tipo '" + nombre
                    + "' contiene un ciclo de tamaño infinito a través de '" + dependencia.destino() + "'.");
            } else if (estadoDestino == 0) {
                visitarTipoDatos(dependencia.destino(), estado);
            }
        }
        estado.put(nombre, 2);
    }

    private void recolectarTiposDatos(Tipo tipo, Set<String> referencias) {
        if (tipo == null || tipo.equals(ERROR) || tipo.referencia()) return;
        if (structs.containsKey(tipo.nombre()) || enums.containsKey(tipo.nombre())) referencias.add(tipo.nombre());
        if (tipo.elemento() != null) recolectarTiposDatos(tipo.elemento(), referencias);
        for (Tipo componente : tipo.componentes()) recolectarTiposDatos(componente, referencias);
    }

    private Tipo tipoAnotado(TokenFisio token) {
        Tipo tipo = expandirAlias(parsearTipo(token.lexema()));
        if (tipo != null && tipoSoportado(tipo)) return tipo;
        errores.add(new ErrorSemantico("TIPO_DESCONOCIDO", "Tipo no soportado: '" + token.lexema() + "'.",
            token.linea(), token.columna()));
        return ERROR;
    }

    private Tipo parsearTipo(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        if (texto.startsWith("&mut ")) {
            Tipo elemento = parsearTipo(texto.substring(5));
            return elemento == null ? null : Tipo.referencia(elemento, true);
        }
        if (texto.startsWith("&")) {
            Tipo elemento = parsearTipo(texto.substring(1));
            return elemento == null ? null : Tipo.referencia(elemento, false);
        }
        if (texto.startsWith("[") && texto.endsWith("]")) {
            String interior = texto.substring(1, texto.length() - 1);
            int separador = buscarSeparadorSuperior(interior, ';');
            if (separador < 0) return null;
            Tipo elemento = parsearTipo(interior.substring(0, separador));
            try {
                int longitud = Integer.parseInt(interior.substring(separador + 1));
                return elemento == null || longitud < 0 ? null : Tipo.arreglo(elemento, longitud);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (texto.startsWith("(") && texto.endsWith(")")) {
            String interior = texto.substring(1, texto.length() - 1);
            if (interior.isEmpty()) return UNIDAD;
            List<String> partes = separarSuperior(interior, ',');
            boolean tuplaUnitaria = interior.endsWith(",");
            if (partes.size() == 1 && !tuplaUnitaria) return parsearTipo(partes.get(0));
            List<Tipo> componentes = new ArrayList<>();
            for (String parte : partes) {
                if (parte.isEmpty()) continue;
                Tipo componente = parsearTipo(parte);
                if (componente == null) return null;
                componentes.add(componente);
            }
            return Tipo.tupla(componentes);
        }
        return new Tipo(texto);
    }

    private int buscarSeparadorSuperior(String texto, char separador) {
        int corchetes = 0, parentesis = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '[') corchetes++;
            else if (c == ']') corchetes--;
            else if (c == '(') parentesis++;
            else if (c == ')') parentesis--;
            else if (c == separador && corchetes == 0 && parentesis == 0) return i;
        }
        return -1;
    }

    private List<String> separarSuperior(String texto, char separador) {
        List<String> partes = new ArrayList<>();
        int inicio = 0, corchetes = 0, parentesis = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '[') corchetes++;
            else if (c == ']') corchetes--;
            else if (c == '(') parentesis++;
            else if (c == ')') parentesis--;
            else if (c == separador && corchetes == 0 && parentesis == 0) {
                partes.add(texto.substring(inicio, i));
                inicio = i + 1;
            }
        }
        partes.add(texto.substring(inicio));
        return partes;
    }

    private boolean tipoSoportado(Tipo tipo) {
        if (tipo == null) return false;
        if (tipo.arreglo()) return tipo.longitud() >= 0 && tipoSoportado(tipo.elemento());
        if (tipo.referencia()) return tipoSoportado(tipo.elemento());
        if (tipo.tupla()) return tipo.componentes().stream().allMatch(this::tipoSoportado);
        return tipoPrimitivo(tipo) || nodosStruct.containsKey(tipo.nombre()) || nodosEnum.containsKey(tipo.nombre());
    }

    private boolean tipoPrimitivo(Tipo tipo) {
        return tipo.numerico() || tipo.equals(BOOL) || tipo.equals(STRING) || tipo.equals(CHAR)
            || tipo.equals(UNIDAD);
    }

    private Variable buscar(String nombre) {
        for (var ambito : ambitos) {
            Variable variable = ambito.get(nombre);
            if (variable != null) return variable;
        }
        return null;
    }

    private Tipo validarConstante(NodoConstante nodo, boolean global) {
        Tipo esperado = global ? constantesGlobales.get(nodo.nombre().lexema()).tipo() : tipoAnotado(nodo.tipo());
        Tipo actual = expresion(nodo.expresion(), esperado);
        exigir(nodo, esperado, actual, "TIPO_CONSTANTE", "Inicializador de la constante '"
            + nodo.nombre().lexema() + "'");
        if (!actual.equals(ERROR) && !esExpresionConstante(nodo.expresion())) {
            error(nodo.expresion(), "CONSTANTE_NO_EVALUABLE", "El inicializador de '"
                + nodo.nombre().lexema() + "' no es una expresión constante.");
        }
        if (!global) {
            Variable anterior = ambitos.peek().putIfAbsent(nodo.nombre().lexema(),
                new Variable(esperado, false, true));
            if (anterior != null) {
                error(nodo, "CONSTANTE_DUPLICADA", "El nombre '" + nodo.nombre().lexema()
                    + "' ya está declarado en este bloque.");
            }
        }
        return actual;
    }

    private Tipo validarEstatico(NodoEstatico nodo, boolean global) {
        Tipo esperado = global ? constantesGlobales.get(nodo.nombre().lexema()).tipo() : tipoAnotado(nodo.tipo());
        Tipo actual = expresion(nodo.expresion(), esperado);
        exigir(nodo, esperado, actual, "TIPO_STATIC", "Inicializador de static '" + nodo.nombre().lexema() + "'");
        if (!actual.equals(ERROR) && !esExpresionConstante(nodo.expresion())) {
            error(nodo.expresion(), "STATIC_NO_CONSTANTE", "El inicializador de static '"
                + nodo.nombre().lexema() + "' debe ser una expresión constante.");
        }
        if (nodo.mutable()) {
            error(nodo, "STATIC_MUT_UNSAFE", "static mut requiere acceso unsafe, característica aún no implementada en FISIO.");
        }
        if (!global) {
            Variable anterior = ambitos.peek().putIfAbsent(nodo.nombre().lexema(),
                new Variable(esperado, nodo.mutable(), false));
            if (anterior != null) error(nodo, "STATIC_DUPLICADO", "El nombre '" + nodo.nombre().lexema()
                + "' ya está declarado en este bloque.");
        }
        return actual;
    }

    private boolean esExpresionConstante(NodoAST nodo) {
        if (nodo instanceof NodoNumero || nodo instanceof NodoVarianteEnum) return true;
        if (nodo instanceof NodoIdentificador n) {
            Variable variable = buscar(n.token().lexema());
            return variable != null && variable.constante();
        }
        if (nodo instanceof NodoAgrupacion n) return esExpresionConstante(n.expresion());
        if (nodo instanceof NodoOperacionUnaria n) return esExpresionConstante(n.operando());
        if (nodo instanceof NodoConversion n) return esExpresionConstante(n.expresion());
        if (nodo instanceof NodoOperacion n)
            return esExpresionConstante(n.izquierda()) && esExpresionConstante(n.derecha());
        if (nodo instanceof NodoOperacionRelacional n)
            return esExpresionConstante(n.izquierda()) && esExpresionConstante(n.derecha());
        if (nodo instanceof NodoRango n)
            return esExpresionConstante(n.inicio()) && esExpresionConstante(n.fin());
        if (nodo instanceof NodoArreglo n) return n.elementos().stream().allMatch(this::esExpresionConstante);
        if (nodo instanceof NodoTupla n) return n.elementos().stream().allMatch(this::esExpresionConstante);
        if (nodo instanceof NodoIndice n)
            return esExpresionConstante(n.objeto()) && esExpresionConstante(n.indice());
        if (nodo instanceof NodoAccesoTupla n) return esExpresionConstante(n.objeto());
        if (nodo instanceof NodoAccesoCampo n) return esExpresionConstante(n.objeto());
        if (nodo instanceof NodoLiteralStruct n)
            return n.campos().stream().allMatch(c -> esExpresionConstante(c.valor()))
                && (n.base() == null || esExpresionConstante(n.base()));
        if (nodo instanceof NodoLiteralVariante n)
            return n.campos().stream().allMatch(c -> esExpresionConstante(c.valor()));
        if (nodo instanceof NodoLlamadaAsociada n) {
            DefEnum definicion = enums.get(n.tipo().lexema());
            return definicion != null && definicion.variantes().containsKey(n.nombre().lexema())
                && n.argumentos().stream().allMatch(this::esExpresionConstante);
        }
        if (nodo instanceof NodoLlamadaFuncion n) {
            DefStruct definicion = structs.get(n.nombre().lexema());
            return definicion != null && definicion.tupla()
                && n.argumentos().stream().allMatch(this::esExpresionConstante);
        }
        if (nodo instanceof NodoMatch n)
            return esExpresionConstante(n.expresion())
                && n.brazos().stream().allMatch(b -> (b.guarda() == null || esExpresionConstante(b.guarda()))
                    && esExpresionConstante(b.resultado()));
        if (nodo instanceof NodoIf n) {
            return esExpresionConstante(n.condicion()) && bloqueConstante(n.cuerpoIf())
                && (n.cuerpoElse() == null
                    || n.cuerpoElse() instanceof NodoIf otro && esExpresionConstante(otro)
                    || n.cuerpoElse() instanceof NodoIfLet otro && esExpresionConstante(otro)
                    || n.cuerpoElse() instanceof NodoBloque bloque && bloqueConstante(bloque));
        }
        if (nodo instanceof NodoIfLet n) {
            return esExpresionConstante(n.expresion()) && bloqueConstante(n.cuerpoIf())
                && (n.guarda() == null || esExpresionConstante(n.guarda()))
                && (n.cuerpoElse() == null
                    || n.cuerpoElse() instanceof NodoIf otro && esExpresionConstante(otro)
                    || n.cuerpoElse() instanceof NodoIfLet otro && esExpresionConstante(otro)
                    || n.cuerpoElse() instanceof NodoBloque bloque && bloqueConstante(bloque));
        }
        if (nodo instanceof NodoBloque n) return bloqueConstante(n);
        return false;
    }

    private boolean bloqueConstante(NodoBloque bloque) {
        return bloque.sentencias().isEmpty() && bloque.expresionFinal() != null
            && esExpresionConstante(bloque.expresionFinal());
    }

    private void validarCiclosConstantes() {
        Map<String, Integer> estado = new LinkedHashMap<>();
        for (String nombre : nodosConstante.keySet()) visitarConstante(nombre, estado);
    }

    private void visitarConstante(String nombre, Map<String, Integer> estado) {
        if (estado.getOrDefault(nombre, 0) != 0) return;
        estado.put(nombre, 1);
        Set<String> dependencias = new LinkedHashSet<>();
        recolectarIdentificadores(nodosConstante.get(nombre).expresion(), dependencias);
        for (String dependencia : dependencias) {
            if (!nodosConstante.containsKey(dependencia)) continue;
            int estadoDependencia = estado.getOrDefault(dependencia, 0);
            if (estadoDependencia == 1) {
                error(nodosConstante.get(nombre), "CONSTANTE_CICLICA", "La constante '" + nombre
                    + "' forma una dependencia cíclica mediante '" + dependencia + "'.");
            } else if (estadoDependencia == 0) visitarConstante(dependencia, estado);
        }
        estado.put(nombre, 2);
    }

    private void recolectarIdentificadores(NodoAST nodo, Set<String> nombres) {
        if (nodo == null) return;
        if (nodo instanceof NodoIdentificador n) nombres.add(n.token().lexema());
        else if (nodo instanceof NodoAgrupacion n) recolectarIdentificadores(n.expresion(), nombres);
        else if (nodo instanceof NodoOperacionUnaria n) recolectarIdentificadores(n.operando(), nombres);
        else if (nodo instanceof NodoReferencia n) recolectarIdentificadores(n.expresion(), nombres);
        else if (nodo instanceof NodoDesreferencia n) recolectarIdentificadores(n.referencia(), nombres);
        else if (nodo instanceof NodoConversion n) recolectarIdentificadores(n.expresion(), nombres);
        else if (nodo instanceof NodoOperacion n) {
            recolectarIdentificadores(n.izquierda(), nombres); recolectarIdentificadores(n.derecha(), nombres);
        } else if (nodo instanceof NodoOperacionRelacional n) {
            recolectarIdentificadores(n.izquierda(), nombres); recolectarIdentificadores(n.derecha(), nombres);
        } else if (nodo instanceof NodoRango n) {
            recolectarIdentificadores(n.inicio(), nombres); recolectarIdentificadores(n.fin(), nombres);
        } else if (nodo instanceof NodoArreglo n) n.elementos().forEach(e -> recolectarIdentificadores(e, nombres));
        else if (nodo instanceof NodoTupla n) n.elementos().forEach(e -> recolectarIdentificadores(e, nombres));
        else if (nodo instanceof NodoIndice n) {
            recolectarIdentificadores(n.objeto(), nombres); recolectarIdentificadores(n.indice(), nombres);
        } else if (nodo instanceof NodoAccesoTupla n) recolectarIdentificadores(n.objeto(), nombres);
        else if (nodo instanceof NodoAccesoCampo n) recolectarIdentificadores(n.objeto(), nombres);
        else if (nodo instanceof NodoLiteralStruct n) {
            n.campos().forEach(c -> recolectarIdentificadores(c.valor(), nombres));
            recolectarIdentificadores(n.base(), nombres);
        }
        else if (nodo instanceof NodoLiteralVariante n)
            n.campos().forEach(c -> recolectarIdentificadores(c.valor(), nombres));
        else if (nodo instanceof NodoMatch n) {
            recolectarIdentificadores(n.expresion(), nombres);
            n.brazos().forEach(b -> {
                recolectarIdentificadores(b.guarda(), nombres);
                recolectarIdentificadores(b.resultado(), nombres);
            });
        } else if (nodo instanceof NodoIf n) {
            recolectarIdentificadores(n.condicion(), nombres);
            recolectarIdentificadores(n.cuerpoIf(), nombres);
            recolectarIdentificadores(n.cuerpoElse(), nombres);
        } else if (nodo instanceof NodoIfLet n) {
            recolectarIdentificadores(n.expresion(), nombres);
            recolectarIdentificadores(n.guarda(), nombres);
            recolectarIdentificadores(n.cuerpoIf(), nombres);
            recolectarIdentificadores(n.cuerpoElse(), nombres);
        } else if (nodo instanceof NodoWhileLet n) {
            recolectarIdentificadores(n.expresion(), nombres);
            recolectarIdentificadores(n.guarda(), nombres);
            recolectarIdentificadores(n.cuerpo(), nombres);
        } else if (nodo instanceof NodoLetElse n) {
            recolectarIdentificadores(n.expresion(), nombres);
            recolectarIdentificadores(n.cuerpoElse(), nombres);
        } else if (nodo instanceof NodoBloque n) {
            n.sentencias().forEach(s -> recolectarIdentificadores(s, nombres));
            recolectarIdentificadores(n.expresionFinal(), nombres);
        } else if (nodo instanceof NodoConstante n) recolectarIdentificadores(n.expresion(), nombres);
        else if (nodo instanceof NodoEstatico n) recolectarIdentificadores(n.expresion(), nombres);
        else if (nodo instanceof NodoDeclaracion n) recolectarIdentificadores(n.expresion(), nombres);
        else if (nodo instanceof NodoLlamadaFuncion n)
            n.argumentos().forEach(a -> recolectarIdentificadores(a, nombres));
        else if (nodo instanceof NodoLlamadaAsociada n)
            n.argumentos().forEach(a -> recolectarIdentificadores(a, nombres));
        else if (nodo instanceof NodoLlamadaMetodo n) {
            recolectarIdentificadores(n.receptor(), nombres);
            n.argumentos().forEach(a -> recolectarIdentificadores(a, nombres));
        }
        else if (nodo instanceof NodoInvocacionMacro n)
            n.argumentos().forEach(a -> recolectarIdentificadores(a, nombres));
    }

    private Tipo bloque(NodoBloque bloque, Tipo esperado) {
        ambitos.push(new LinkedHashMap<>());
        Tipo valor = UNIDAD;
        boolean termina = false;
        for (int i = 0; i < bloque.sentencias().size(); i++) {
            NodoAST sentencia = bloque.sentencias().get(i);
            boolean ultima = i == bloque.sentencias().size() - 1 && bloque.expresionFinal() == null;
            ContextoBucle bucleActual = bucles.peek();
            boolean breakPrevio = bucleActual != null && bucleActual.contieneBreak;
            boolean eraInalcanzable = termina;
            Tipo actual = sentencia(sentencia, ultima ? esperado : UNIDAD);
            // Se siguen validando sentencias inalcanzables, pero no alteran el flujo del bucle.
            if (eraInalcanzable && bucleActual != null) bucleActual.contieneBreak = breakPrevio;
            // Las llamadas/operaciones en sentencias llevan ';': no son un retorno implicito.
            if (ultima && (sentencia instanceof NodoIf || sentencia instanceof NodoIfLet || sentencia instanceof NodoBloque
                    || sentencia instanceof NodoMatch)) valor = actual;
            if (actual.equals(NUNCA)) termina = true;
        }
        if (bloque.expresionFinal() != null) valor = expresion(bloque.expresionFinal(), esperado);
        ambitos.pop();
        return termina ? NUNCA : valor;
    }

    private Tipo sentencia(NodoAST nodo, Tipo esperado) {
        if (nodo instanceof NodoEstatico n) {
            Tipo valor = validarEstatico(n, false);
            return valor.equals(NUNCA) ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoConstante n) {
            Tipo valor = validarConstante(n, false);
            return valor.equals(NUNCA) ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoDeclaracion n) {
            Tipo anotado = n.tipo() == null ? null : tipoAnotado(n.tipo());
            // Inicializador antes de declarar: permite shadowing y detecta autorreferencia.
            Tipo valor = expresion(n.expresion(), anotado);
            if (anotado != null) exigir(n, anotado, valor, "TIPO", "Inicializador de '" + n.identificador().lexema() + "'");
            if (!n.identificador().lexema().equals("_")) {
                ambitos.peek().put(n.identificador().lexema(), new Variable(anotado == null ? valor : anotado, n.mutable()));
            }
            return valor.equals(NUNCA) ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoLetElse n) {
            Tipo valor = expresion(n.expresion(), null);
            validarTipoValorPatron(n.patron(), valor, n.expresion(), "let else");
            Map<String, Variable> vinculaciones = validarPatron(n.patron(), valor);
            Tipo tipoElse = bloque(n.cuerpoElse(), UNIDAD);
            if (!tipoElse.equals(NUNCA) && !tipoElse.equals(ERROR)) {
                error(n.cuerpoElse(), "LET_ELSE_NO_DIVERGE",
                    "El bloque else de let else debe finalizar el flujo con return, break o continue.");
            }
            // Rust permite shadowing: las vinculaciones se publican solo después de validar el inicializador y el else.
            ambitos.peek().putAll(vinculaciones);
            return valor.equals(NUNCA) ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoAsignacion n) {
            NodoIdentificador raiz = raizAsignacion(n.lugar());
            if (raiz == null) {
                error(n, "DESTINO", "El destino no es una variable asignable.");
                expresion(n.expresion(), null);
                return UNIDAD;
            }
            String nombre = raiz.token().lexema();
            if (nombre.equals("_") && n.lugar() instanceof NodoIdentificador) {
                expresion(n.expresion(), null);
                if (!n.operador().lexema().equals("="))
                    error(n, "DESTINO", "El descarte '_' solo admite asignacion simple; no almacena un valor.");
                return UNIDAD;
            }
            Variable variable = buscar(nombre);
            Tipo destino;
            if (n.lugar() instanceof NodoIdentificador) {
                destino = variable == null ? ERROR : variable.tipo();
            } else {
                destino = expresion(n.lugar(), null);
            }
            Tipo valor = expresion(n.expresion(), destino.equals(ERROR) ? null : destino);
            if (variable == null) {
                if (n.lugar() instanceof NodoIdentificador) {
                    error(raiz, "VARIABLE_NO_DECLARADA", "La variable '" + nombre
                        + "' no está declarada en este ámbito o no es accesible desde él.");
                }
            } else {
                NodoDesreferencia desreferencia = desreferenciaEnLugar(n.lugar());
                if (desreferencia != null) {
                    Tipo tipoReferencia = tiposExpresion.get(desreferencia.referencia());
                    if (tipoReferencia == null || !tipoReferencia.referenciaMutable()) {
                        error(n, "REFERENCIA_INMUTABLE",
                            "No se puede modificar el valor a través de una referencia compartida.");
                    }
                } else if (!variable.mutable()
                        && !(n.lugar() instanceof NodoIdentificador)
                        && !variable.tipo().referenciaMutable()) {
                    error(n, "INMUTABLE", "No se puede modificar '" + nombre + "': se declaro sin 'mut'.");
                } else if (!variable.mutable() && n.lugar() instanceof NodoIdentificador) {
                    error(n, "INMUTABLE", "No se puede modificar '" + nombre + "': se declaro sin 'mut'.");
                }
                String lugar = descripcionLugar(n.lugar());
                if (n.operador().lexema().equals("=")) exigir(n, destino, valor, "TIPO", "Asignacion a '" + lugar + "'");
                else {
                    Tipo resultado = operador(n, n.operador().lexema(), destino, valor);
                    if (!resultado.equals(ERROR)) comprobarCero(n, n.operador().lexema(), n.expresion());
                }
            }
            return UNIDAD;
        }
        if (nodo instanceof NodoReturn n) {
            Tipo valor = n.expresion() == null ? UNIDAD : expresion(n.expresion(), retornoActual);
            comprobarRetorno(n, valor, n.expresion() == null, "Sentencia return");
            return NUNCA;
        }
        if (nodo instanceof NodoIf n) return condicional(n, esperado);
        if (nodo instanceof NodoIfLet n) return condicionalLet(n, esperado);
        if (nodo instanceof NodoWhile n) {
            exigir(n.condicion(), BOOL, expresion(n.condicion(), BOOL), "CONDICION", "Condicion de while");
            ContextoBucle contexto = new ContextoBucle();
            bucles.push(contexto);
            exigir(n.cuerpo(), UNIDAD, bloque(n.cuerpo(), UNIDAD), "TIPO", "Cuerpo de while");
            bucles.pop();
            return esTrue(n.condicion()) && !contexto.contieneBreak ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoWhileLet n) {
            Tipo valor = expresion(n.expresion(), null);
            validarTipoValorPatron(n.patron(), valor, n.expresion(), "while let");
            abrirAmbitoPatron(n.patron(), valor);
            if (n.guarda() != null) {
                exigir(n.guarda(), BOOL, expresion(n.guarda(), BOOL), "CONDICION",
                    "Guarda de while let");
            }
            ContextoBucle contexto = new ContextoBucle();
            bucles.push(contexto);
            exigir(n.cuerpo(), UNIDAD, bloque(n.cuerpo(), UNIDAD), "TIPO", "Cuerpo de while let");
            bucles.pop();
            ambitos.pop();
            boolean guardaSiempre = n.guarda() == null || esTrue(n.guarda());
            return n.patron().comodin() && guardaSiempre && !contexto.contieneBreak ? NUNCA : UNIDAD;
        }
        if (nodo instanceof NodoFor n) {
            Tipo iterable = expresion(n.iterable(), null);
            if (!iterable.equals(ERROR) && !iterable.rango() && !iterable.arreglo())
                error(n.iterable(), "ITERABLE", "for necesita un rango o arreglo; se encontro " + iterable + ".");
            ambitos.push(new LinkedHashMap<>());
            if (!n.variable().lexema().equals("_")) ambitos.peek().put(n.variable().lexema(),
                new Variable(iterable.rango() || iterable.arreglo() ? iterable.elemento() : ERROR, false));
            bucles.push(new ContextoBucle());
            exigir(n.cuerpo(), UNIDAD, bloque(n.cuerpo(), UNIDAD), "TIPO", "Cuerpo de for");
            bucles.pop();
            ambitos.pop();
            return UNIDAD;
        }
        if (nodo instanceof NodoLoop n) {
            ContextoBucle contexto = new ContextoBucle();
            bucles.push(contexto);
            exigir(n.cuerpo(), UNIDAD, bloque(n.cuerpo(), UNIDAD), "TIPO", "Cuerpo de loop");
            bucles.pop();
            return contexto.contieneBreak ? UNIDAD : NUNCA;
        }
        if (nodo instanceof NodoBreak) {
            if (bucles.isEmpty()) error(nodo, "CONTROL_FLUJO", "'break' solo puede utilizarse dentro de un bucle.");
            else bucles.peek().contieneBreak = true;
            return NUNCA;
        }
        if (nodo instanceof NodoContinue) {
            if (bucles.isEmpty()) error(nodo, "CONTROL_FLUJO", "'continue' solo puede utilizarse dentro de un bucle.");
            return NUNCA;
        }
        return expresion(nodo, esperado);
    }

    private NodoIdentificador raizAsignacion(NodoAST lugar) {
        if (lugar instanceof NodoIdentificador id) return id;
        if (lugar instanceof NodoDesreferencia acceso) return raizAsignacion(acceso.referencia());
        if (lugar instanceof NodoAccesoCampo acceso) return raizAsignacion(acceso.objeto());
        if (lugar instanceof NodoIndice acceso) return raizAsignacion(acceso.objeto());
        if (lugar instanceof NodoAccesoTupla acceso) return raizAsignacion(acceso.objeto());
        return null;
    }

    private String descripcionLugar(NodoAST lugar) {
        if (lugar instanceof NodoIdentificador id) return id.token().lexema();
        if (lugar instanceof NodoDesreferencia acceso) return "*" + descripcionLugar(acceso.referencia());
        if (lugar instanceof NodoAccesoCampo acceso) return descripcionLugar(acceso.objeto()) + "." + acceso.campo().lexema();
        if (lugar instanceof NodoIndice acceso) return descripcionLugar(acceso.objeto()) + "[indice]";
        if (lugar instanceof NodoAccesoTupla acceso) return descripcionLugar(acceso.objeto()) + "." + acceso.indice().lexema();
        return "destino";
    }

    private NodoDesreferencia desreferenciaEnLugar(NodoAST lugar) {
        if (lugar instanceof NodoDesreferencia acceso) return acceso;
        if (lugar instanceof NodoAccesoCampo acceso) return desreferenciaEnLugar(acceso.objeto());
        if (lugar instanceof NodoIndice acceso) return desreferenciaEnLugar(acceso.objeto());
        if (lugar instanceof NodoAccesoTupla acceso) return desreferenciaEnLugar(acceso.objeto());
        return null;
    }

    private boolean esLugarReferenciable(NodoAST nodo) {
        return nodo instanceof NodoIdentificador || nodo instanceof NodoDesreferencia
            || nodo instanceof NodoAccesoCampo || nodo instanceof NodoIndice || nodo instanceof NodoAccesoTupla;
    }

    private Tipo condicional(NodoIf n, Tipo esperado) {
        exigir(n.condicion(), BOOL, expresion(n.condicion(), BOOL), "CONDICION", "Condicion de if");
        Tipo si = bloque(n.cuerpoIf(), esperado);
        Tipo no = n.cuerpoElse() instanceof NodoIf otro ? condicional(otro, esperado)
            : n.cuerpoElse() instanceof NodoIfLet otro ? condicionalLet(otro, esperado)
            : n.cuerpoElse() instanceof NodoBloque b ? bloque(b, esperado) : UNIDAD;
        return combinarRamasCondicionales(n, si, no, esperado);
    }

    private Tipo condicionalLet(NodoIfLet n, Tipo esperado) {
        Tipo valor = expresion(n.expresion(), null);
        validarTipoValorPatron(n.patron(), valor, n.expresion(), "if let");
        abrirAmbitoPatron(n.patron(), valor);
        if (n.guarda() != null) {
            exigir(n.guarda(), BOOL, expresion(n.guarda(), BOOL), "CONDICION", "Guarda de if let");
        }
        Tipo si = bloque(n.cuerpoIf(), esperado);
        ambitos.pop();
        Tipo no = n.cuerpoElse() instanceof NodoIf otro ? condicional(otro, esperado)
            : n.cuerpoElse() instanceof NodoIfLet otro ? condicionalLet(otro, esperado)
            : n.cuerpoElse() instanceof NodoBloque b ? bloque(b, esperado) : UNIDAD;
        return combinarRamasCondicionales(n, si, no, esperado);
    }

    private Tipo combinarRamasCondicionales(NodoAST nodo, Tipo si, Tipo no, Tipo esperado) {
        Tipo resultado;
        if (si.equals(NUNCA)) resultado = no;
        else if (no.equals(NUNCA)) resultado = si;
        else if (si.equals(ERROR) || no.equals(ERROR)) resultado = ERROR;
        else if (!si.equals(no)) {
            error(nodo, "RAMAS", "Las ramas de if/else producen tipos distintos: " + si + " y " + no + ".");
            resultado = ERROR;
        } else resultado = si;
        if (UNIDAD.equals(esperado)) exigir(nodo, UNIDAD, resultado, "TIPO", "Condicional usado como sentencia");
        return resultado;
    }

    private void validarTipoValorPatron(PatronVariante patron, Tipo valor, NodoAST expresion, String contexto) {
        if (!patron.comodin() && !valor.equals(ERROR) && !enums.containsKey(valor.nombre())) {
            error(expresion, "PATRON_TIPO", contexto + " requiere un valor enum; se encontró " + valor + ".");
        }
    }

    private Tipo expresion(NodoAST nodo, Tipo esperado) {
        Tipo tipo = evaluarExpresion(nodo, esperado);
        tiposExpresion.put(nodo, tipo);
        return tipo;
    }

    private Tipo evaluarExpresion(NodoAST nodo, Tipo esperado) {
        if (nodo instanceof NodoNumero n) return literal(n, esperado, false);
        if (nodo instanceof NodoIdentificador n) {
            Variable variable = buscar(n.token().lexema());
            if (variable != null) return variable.tipo();
            error(n, "VARIABLE_NO_DECLARADA", "La variable '" + n.token().lexema() + "' no está declarada en este ámbito o no es accesible desde él.");
            return ERROR;
        }
        if (nodo instanceof NodoAgrupacion n) return expresion(n.expresion(), esperado);
        if (nodo instanceof NodoArreglo n) return arreglo(n, esperado);
        if (nodo instanceof NodoTupla n) return tupla(n, esperado);
        if (nodo instanceof NodoIndice n) return indice(n);
        if (nodo instanceof NodoAccesoTupla n) return accesoTupla(n);
        if (nodo instanceof NodoBloque n) return bloque(n, esperado);
        if (nodo instanceof NodoIf n) return condicional(n, esperado);
        if (nodo instanceof NodoIfLet n) return condicionalLet(n, esperado);
        if (nodo instanceof NodoLiteralStruct n) return literalStruct(n);
        if (nodo instanceof NodoLiteralVariante n) return literalVariante(n);
        if (nodo instanceof NodoAccesoCampo n) return accesoCampo(n);
        if (nodo instanceof NodoVarianteEnum n) return varianteEnum(n);
        if (nodo instanceof NodoMatch n) return expresionMatch(n, esperado);
        if (nodo instanceof NodoLlamadaAsociada n) return llamadaAsociada(n);
        if (nodo instanceof NodoLlamadaMetodo n) return llamadaMetodo(n);
        if (nodo instanceof NodoReferencia n) {
            Tipo contexto = esperado != null && esperado.referencia() ? esperado.elemento() : null;
            Tipo elemento = expresion(n.expresion(), contexto);
            if (!esLugarReferenciable(n.expresion())) {
                error(n, "REFERENCIA_DESTINO", "Una referencia debe apuntar a una variable o a una parte accesible de ella.");
            }
            if (n.mutable()) {
                NodoIdentificador raiz = raizAsignacion(n.expresion());
                Variable variable = raiz == null ? null : buscar(raiz.token().lexema());
                NodoDesreferencia desreferencia = desreferenciaEnLugar(n.expresion());
                boolean mutable = desreferencia != null
                    ? tiposExpresion.get(desreferencia.referencia()) != null
                        && tiposExpresion.get(desreferencia.referencia()).referenciaMutable()
                    : variable != null && variable.mutable();
                if (!elemento.equals(ERROR) && !mutable) {
                    error(n, "REFERENCIA_MUTABLE",
                        "No se puede crear una referencia mutable a un valor que no fue declarado como mutable.");
                }
            }
            return elemento.equals(ERROR) ? ERROR : Tipo.referencia(elemento, n.mutable());
        }
        if (nodo instanceof NodoDesreferencia n) {
            Tipo referencia = expresion(n.referencia(), null);
            if (referencia.equals(ERROR)) return ERROR;
            if (referencia.referencia()) return referencia.elemento();
            error(n, "DESREFERENCIA", "El operador '*' requiere una referencia; se encontró " + referencia + ".");
            return ERROR;
        }
        if (nodo instanceof NodoConversion n) {
            Tipo origen = expresion(n.expresion(), null);
            Tipo destino = tipoAnotado(n.tipo());
            if (origen.equals(ERROR) || destino.equals(ERROR)) return ERROR;
            boolean permitida = origen.numerico() && destino.numerico()
                || origen.equals(CHAR) && destino.entero()
                || origen.equals(BOOL) && destino.entero()
                || origen.equals(new Tipo("u8")) && destino.equals(CHAR);
            if (permitida) return destino;
            error(n, "CONVERSION", "No se puede convertir " + origen + " a " + destino + " mediante 'as'.");
            return ERROR;
        }
        if (nodo instanceof NodoOperacionUnaria n) {
            String op = n.operador().lexema();
            NodoAST operando = n.operando();
            while (operando instanceof NodoAgrupacion grupo) operando = grupo.expresion();
            if (op.equals("-") && operando instanceof NodoNumero literal && literal.token().categoria() == CategoriaToken.NUM) {
                Tipo tipo = literal(literal, esperado, true);
                return tipo;
            }
            Tipo tipo = expresion(n.operando(), null);
            if (tipo.equals(ERROR)) return ERROR;
            if (op.equals("!") && (tipo.equals(BOOL) || tipo.entero())) return tipo;
            if (op.equals("-") && tipo.numerico() && !tipo.nombre().startsWith("u")) return tipo;
            error(n, "OPERADOR", "El operador '" + op + "' no admite " + tipo + ".");
            return ERROR;
        }
        if (nodo instanceof NodoOperacion n) {
            Tipo contexto = n.operador().lexema().equals("&&") || n.operador().lexema().equals("||") ? BOOL : esperado;
            Tipo[] tipos = operandos(n.izquierda(), n.derecha(), contexto);
            Tipo resultado = operador(n, n.operador().lexema(), tipos[0], tipos[1]);
            if (!resultado.equals(ERROR)) comprobarCero(n, n.operador().lexema(), n.derecha());
            return resultado;
        }
        if (nodo instanceof NodoOperacionRelacional n) {
            Tipo[] tipos = operandos(n.izquierda(), n.derecha(), null);
            return operador(n, n.operador().lexema(), tipos[0], tipos[1]);
        }
        if (nodo instanceof NodoRango n) {
            Tipo[] tipos = operandos(n.inicio(), n.fin(), esperado != null && esperado.rango() ? esperado.elemento() : null);
            if (tipos[0].equals(ERROR) || tipos[1].equals(ERROR)) return ERROR;
            if (!tipos[0].entero() || !tipos[0].equals(tipos[1])) {
                error(n, "RANGO", "Los extremos del rango deben ser enteros del mismo tipo; se encontro " + tipos[0] + " y " + tipos[1] + ".");
                return ERROR;
            }
            return new Tipo(n.inclusivo() ? "rango_inclusivo" : "rango", tipos[0]);
        }
        if (nodo instanceof NodoLlamadaFuncion n) return llamada(n);
        if (nodo instanceof NodoInvocacionMacro n) return macro(n);
        error(nodo, "ESTRUCTURA", "Nodo no soportado por el analizador semantico: " + nodo.getClass().getSimpleName() + ".");
        return ERROR;
    }

    private Tipo arreglo(NodoArreglo nodo, Tipo esperado) {
        Tipo esperadoArreglo = esperado != null && esperado.arreglo() ? esperado : null;
        if (esperadoArreglo != null && esperadoArreglo.longitud() != nodo.elementos().size()) {
            error(nodo, "LONGITUD_ARREGLO", "El arreglo declarado como " + esperadoArreglo
                + " necesita " + esperadoArreglo.longitud() + " elemento(s), pero recibió "
                + nodo.elementos().size() + ".");
        }
        if (nodo.elementos().isEmpty()) {
            if (esperadoArreglo != null) return esperadoArreglo;
            error(nodo, "ARREGLO_VACIO", "No se puede inferir el tipo de un arreglo vacío; agrega una anotación como [i32; 0].");
            return ERROR;
        }
        Tipo elementoEsperado = esperadoArreglo == null ? null : esperadoArreglo.elemento();
        Tipo elemento = expresion(nodo.elementos().get(0), elementoEsperado);
        if (elementoEsperado != null)
            exigir(nodo.elementos().get(0), elementoEsperado, elemento, "ELEMENTO_ARREGLO", "Elemento 1 del arreglo");
        Tipo referencia = elementoEsperado == null ? elemento : elementoEsperado;
        for (int i = 1; i < nodo.elementos().size(); i++) {
            Tipo actual = expresion(nodo.elementos().get(i), referencia);
            exigir(nodo.elementos().get(i), referencia, actual, "ELEMENTO_ARREGLO",
                "Elemento " + (i + 1) + " del arreglo");
        }
        if (esperadoArreglo != null) return esperadoArreglo;
        return referencia.equals(ERROR) ? ERROR : Tipo.arreglo(referencia, nodo.elementos().size());
    }

    private Tipo tupla(NodoTupla nodo, Tipo esperado) {
        if (nodo.elementos().isEmpty()) return UNIDAD;
        Tipo esperada = esperado != null && esperado.tupla() ? esperado : null;
        if (esperada != null && esperada.componentes().size() != nodo.elementos().size()) {
            error(nodo, "LONGITUD_TUPLA", "La tupla declarada como " + esperada + " necesita "
                + esperada.componentes().size() + " elemento(s), pero recibió " + nodo.elementos().size() + ".");
        }
        List<Tipo> componentes = new ArrayList<>();
        for (int i = 0; i < nodo.elementos().size(); i++) {
            Tipo componenteEsperado = esperada != null && i < esperada.componentes().size()
                ? esperada.componentes().get(i) : null;
            Tipo actual = expresion(nodo.elementos().get(i), componenteEsperado);
            if (componenteEsperado != null)
                exigir(nodo.elementos().get(i), componenteEsperado, actual, "ELEMENTO_TUPLA",
                    "Elemento " + i + " de la tupla");
            componentes.add(componenteEsperado == null ? actual : componenteEsperado);
        }
        if (esperada != null) return esperada;
        return componentes.contains(ERROR) ? ERROR : Tipo.tupla(componentes);
    }

    private Tipo indice(NodoIndice nodo) {
        Tipo objeto = expresion(nodo.objeto(), null);
        while (objeto.referencia()) objeto = objeto.elemento();
        Tipo tipoIndice = expresion(nodo.indice(), I32);
        if (!tipoIndice.equals(ERROR) && !tipoIndice.entero()) {
            error(nodo.indice(), "INDICE_TIPO", "El índice de un arreglo debe ser entero; se encontró " + tipoIndice + ".");
        }
        if (objeto.equals(ERROR)) return ERROR;
        if (!objeto.arreglo()) {
            error(nodo, "INDICE", "Solo se pueden indexar arreglos; se encontró " + objeto + ".");
            return ERROR;
        }
        BigDecimal valor = constante(nodo.indice());
        if (valor != null && valor.stripTrailingZeros().scale() <= 0) {
            BigInteger entero = valor.toBigIntegerExact();
            if (entero.signum() < 0 || entero.compareTo(BigInteger.valueOf(objeto.longitud())) >= 0) {
                error(nodo.indice(), "INDICE_FUERA_DE_RANGO", "El índice " + entero
                    + " está fuera del arreglo de longitud " + objeto.longitud() + ".");
            }
        }
        return objeto.elemento();
    }

    private Tipo accesoTupla(NodoAccesoTupla nodo) {
        Tipo objeto = expresion(nodo.objeto(), null);
        while (objeto.referencia()) objeto = objeto.elemento();
        if (objeto.equals(ERROR)) return ERROR;
        DefStruct structTupla = structs.get(objeto.nombre());
        if (structTupla != null && structTupla.tupla()) {
            Tipo campo = structTupla.campos().get(nodo.indice().lexema());
            if (campo == null) {
                error(nodo, "INDICE_TUPLA", "El struct de tupla " + objeto + " no tiene la posición "
                    + nodo.indice().lexema() + ".");
                return ERROR;
            }
            return campo;
        }
        if (!objeto.tupla()) {
            error(nodo, "ACCESO_TUPLA", "El acceso ." + nodo.indice().lexema()
                + " requiere una tupla; se encontró " + objeto + ".");
            return ERROR;
        }
        try {
            int indice = Integer.parseInt(nodo.indice().lexema());
            if (indice < 0 || indice >= objeto.componentes().size()) {
                error(nodo, "INDICE_TUPLA", "La tupla " + objeto + " no tiene la posición " + indice + ".");
                return ERROR;
            }
            return objeto.componentes().get(indice);
        } catch (NumberFormatException ex) {
            error(nodo, "INDICE_TUPLA", "El índice de tupla es demasiado grande.");
            return ERROR;
        }
    }

    private Tipo literalStruct(NodoLiteralStruct nodo) {
        String nombre = nodo.nombre().lexema();
        DefStruct definicion = structs.get(nombre);
        if (definicion == null) {
            String mensaje = enums.containsKey(nombre)
                ? "'" + nombre + "' es un enum, no un struct."
                : "No existe un struct llamado '" + nombre + "'.";
            error(nodo, "STRUCT_NO_DECLARADO", mensaje);
        }
        if (definicion != null && definicion.tupla()) {
            error(nodo, "FORMA_STRUCT", "El struct de tupla '" + nombre
                + "' debe construirse con paréntesis.");
            definicion = null;
        }
        Set<String> vistos = new LinkedHashSet<>();
        for (NodoLiteralStruct.CampoValor campo : nodo.campos()) {
            String campoNombre = campo.nombre().lexema();
            Tipo esperado = definicion == null ? null : definicion.campos().get(campoNombre);
            Tipo actual = expresion(campo.valor(), esperado);
            if (!vistos.add(campoNombre)) {
                error(campo.nombre(), "CAMPO_DUPLICADO",
                    "El campo '" + campoNombre + "' se inicializó más de una vez.");
            }
            if (definicion != null && esperado == null) {
                error(campo.nombre(), "CAMPO_DESCONOCIDO",
                    "El struct '" + nombre + "' no tiene un campo llamado '" + campoNombre + "'.");
            } else if (esperado != null) {
                exigir(campo.valor(), esperado, actual, "CAMPO", "Campo '" + campoNombre + "'");
            }
        }
        if (nodo.base() != null) {
            Tipo esperadoBase = definicion == null ? null : new Tipo(nombre);
            Tipo actualBase = expresion(nodo.base(), esperadoBase);
            if (esperadoBase != null)
                exigir(nodo.base(), esperadoBase, actualBase, "BASE_STRUCT", "Base de actualización de '" + nombre + "'");
        }
        if (definicion != null) {
            Set<String> faltantes = new LinkedHashSet<>(definicion.campos().keySet());
            faltantes.removeAll(vistos);
            if (nodo.base() == null && !faltantes.isEmpty()) {
                error(nodo, "CAMPO_FALTANTE", "Faltan campos al construir '" + nombre + "': "
                    + String.join(", ", faltantes) + ".");
            }
            return new Tipo(nombre);
        }
        return ERROR;
    }

    private Tipo literalVariante(NodoLiteralVariante nodo) {
        String tipo = nodo.tipo().lexema();
        String variante = nodo.variante().lexema();
        DefEnum definicionEnum = enums.get(tipo);
        if (definicionEnum == null) {
            for (NodoLiteralStruct.CampoValor campo : nodo.campos()) expresion(campo.valor(), null);
            error(nodo, "ENUM_NO_DECLARADO", "No existe un enum llamado '" + tipo + "'.");
            return ERROR;
        }
        DefVariante definicion = definicionEnum.variantes().get(variante);
        if (definicion == null) {
            for (NodoLiteralStruct.CampoValor campo : nodo.campos()) expresion(campo.valor(), null);
            error(nodo.variante(), "VARIANTE_DESCONOCIDA", "El enum '" + tipo
                + "' no tiene la variante '" + variante + "'.");
            return ERROR;
        }
        if (definicion.forma() != NodoEnum.Forma.ESTRUCTURA) {
            for (NodoLiteralStruct.CampoValor campo : nodo.campos()) expresion(campo.valor(), null);
            error(nodo, "FORMA_VARIANTE", "La variante '" + tipo + "::" + variante
                + "' no se construye con campos nombrados.");
            return ERROR;
        }
        validarCampos(nodo, nodo.campos(), definicion.campos(), tipo + "::" + variante);
        return new Tipo(tipo);
    }

    private void validarCampos(NodoAST nodo, List<NodoLiteralStruct.CampoValor> campos,
            Map<String, Tipo> esperados, String propietario) {
        Set<String> vistos = new LinkedHashSet<>();
        for (NodoLiteralStruct.CampoValor campo : campos) {
            String nombre = campo.nombre().lexema();
            Tipo esperado = esperados.get(nombre);
            Tipo actual = expresion(campo.valor(), esperado);
            if (!vistos.add(nombre)) {
                error(campo.nombre(), "CAMPO_DUPLICADO", "El campo '" + nombre
                    + "' se inicializó más de una vez.");
            } else if (esperado == null) {
                error(campo.nombre(), "CAMPO_DESCONOCIDO", "'" + propietario
                    + "' no tiene un campo llamado '" + nombre + "'.");
            } else {
                exigir(campo.valor(), esperado, actual, "CAMPO", "Campo '" + nombre + "'");
            }
        }
        Set<String> faltantes = new LinkedHashSet<>(esperados.keySet());
        faltantes.removeAll(vistos);
        if (!faltantes.isEmpty()) {
            error(nodo, "CAMPO_FALTANTE", "Faltan campos al construir '" + propietario + "': "
                + String.join(", ", faltantes) + ".");
        }
    }

    private Tipo accesoCampo(NodoAccesoCampo nodo) {
        Tipo tipoObjeto = expresion(nodo.objeto(), null);
        while (tipoObjeto.referencia()) tipoObjeto = tipoObjeto.elemento();
        if (tipoObjeto.equals(ERROR)) return ERROR;
        DefStruct definicion = structs.get(tipoObjeto.nombre());
        if (definicion == null) {
            error(nodo, "ACCESO_CAMPO", "El valor de tipo " + tipoObjeto + " no tiene campos accesibles.");
            return ERROR;
        }
        if (definicion.tupla()) {
            error(nodo, "ACCESO_CAMPO", "El struct de tupla '" + tipoObjeto
                + "' utiliza accesos posicionales como .0.");
            return ERROR;
        }
        Tipo campo = definicion.campos().get(nodo.campo().lexema());
        if (campo == null) {
            error(nodo, "CAMPO_DESCONOCIDO", "El struct '" + tipoObjeto + "' no tiene un campo llamado '"
                + nodo.campo().lexema() + "'.");
            return ERROR;
        }
        return campo;
    }

    private Tipo varianteEnum(NodoVarianteEnum nodo) {
        String nombre = nodo.tipo().lexema();
        DefEnum definicion = enums.get(nombre);
        if (definicion == null) {
            String mensaje = structs.containsKey(nombre)
                ? "'" + nombre + "' es un struct, no un enum."
                : "No existe un enum llamado '" + nombre + "'.";
            error(nodo, "ENUM_NO_DECLARADO", mensaje);
            return ERROR;
        }
        DefVariante datos = definicion.variantes().get(nodo.variante().lexema());
        if (datos == null) {
            error(nodo, "VARIANTE_DESCONOCIDA", "El enum '" + nombre + "' no tiene la variante '"
                + nodo.variante().lexema() + "'.");
            return ERROR;
        }
        if (datos.forma() != NodoEnum.Forma.UNITARIA) {
            error(nodo, "VARIANTE_REQUIERE_DATOS", "La variante '" + nombre + "::"
                + nodo.variante().lexema() + "' requiere datos en forma "
                + (datos.forma() == NodoEnum.Forma.TUPLA ? "posicional" : "de campos") + ".");
        }
        return new Tipo(nombre);
    }

    private Tipo expresionMatch(NodoMatch nodo, Tipo esperado) {
        Tipo tipoValor = expresion(nodo.expresion(), null);
        DefEnum definicion = tipoValor.equals(ERROR) ? null : enums.get(tipoValor.nombre());
        if (!tipoValor.equals(ERROR) && definicion == null) {
            error(nodo.expresion(), "MATCH_TIPO", "match requiere un valor enum; se encontró " + tipoValor + ".");
        }
        List<List<PatronCobertura>> cobertura = new ArrayList<>();
        Set<String> variantesCompletas = new LinkedHashSet<>();
        boolean coberturaConPatronInvalido = false;
        Tipo resultado = null;
        for (NodoMatch.Brazo brazo : nodo.brazos()) {
            PatronVariante patron = PatronVariante.desde(brazo);
            PatronCobertura normalizado = definicion == null ? null : normalizarPatron(patron, tipoValor);
            if (definicion != null && normalizado == null && !patron.comodin()
                    && patron.tipo().lexema().equals(tipoValor.nombre())
                    && definicion.variantes().containsKey(patron.variante().lexema())) {
                coberturaConPatronInvalido = true;
            }
            boolean util = normalizado == null
                || esPatronUtil(cobertura, List.of(normalizado), List.of(tipoValor));
            if (!util) {
                boolean duplicadoAmplio = !patron.comodin() && !patron.tieneAnidados()
                    && variantesCompletas.contains(patron.variante().lexema());
                error(patron.comodin() ? brazo.linea() : patron.variante().linea(),
                    patron.comodin() ? brazo.columna() : patron.variante().columna(),
                    duplicadoAmplio ? "PATRON_DUPLICADO" : "PATRON_INALCANZABLE",
                    duplicadoAmplio
                        ? "La variante '" + patron.variante().lexema() + "' ya tiene un brazo que cubre todos sus valores."
                        : "Este brazo no puede aceptar ningún valor que no haya sido cubierto anteriormente.");
            }
            if (brazo.guarda() == null && normalizado != null) {
                cobertura.add(List.of(normalizado));
                if (!patron.comodin() && !patron.tieneAnidados()) {
                    variantesCompletas.add(patron.variante().lexema());
                }
            }
            abrirAmbitoPatron(patron, tipoValor);
            if (brazo.guarda() != null) {
                Tipo tipoGuarda = expresion(brazo.guarda(), BOOL);
                exigir(brazo.guarda(), BOOL, tipoGuarda, "GUARDA_MATCH", "Guarda de match");
            }
            Tipo tipoBrazo = expresion(brazo.resultado(), esperado);
            ambitos.pop();
            if (resultado == null || resultado.equals(NUNCA)) resultado = tipoBrazo;
            else if (!tipoBrazo.equals(NUNCA) && !resultado.equals(ERROR) && !tipoBrazo.equals(ERROR)
                    && !resultado.equals(tipoBrazo)) {
                error(brazo.resultado(), "RAMAS", "Los brazos de match producen tipos distintos: "
                    + resultado + " y " + tipoBrazo + ".");
                resultado = ERROR;
            } else if (tipoBrazo.equals(ERROR)) resultado = ERROR;
        }
        if (definicion != null && !coberturaConPatronInvalido && esPatronUtil(cobertura,
                List.of(PatronCobertura.comodin()), List.of(tipoValor))) {
            List<String> faltantes = new ArrayList<>();
            for (var entrada : definicion.variantes().entrySet()) {
                PatronCobertura varianteCompleta = new PatronCobertura(entrada.getKey(),
                    comodines(tiposDatos(entrada.getValue()).size()));
                if (esPatronUtil(cobertura, List.of(varianteCompleta), List.of(tipoValor))) {
                    faltantes.add(entrada.getKey());
                }
            }
            error(nodo, "MATCH_NO_EXHAUSTIVO", "match no cubre completamente las variantes: "
                + String.join(", ", faltantes) + ".");
        }
        if (resultado == null) resultado = NUNCA;
        if (UNIDAD.equals(esperado)) exigir(nodo, UNIDAD, resultado, "TIPO", "Match usado como sentencia");
        return resultado;
    }

    /** Convierte el patrón del AST en una forma posicional uniforme para analizar su cobertura. */
    private PatronCobertura normalizarPatron(PatronVariante patron, Tipo esperado) {
        if (patron.comodin()) return PatronCobertura.comodin();
        DefEnum definicion = esperado.equals(ERROR) ? null : enums.get(esperado.nombre());
        if (definicion == null || !patron.tipo().lexema().equals(esperado.nombre())) return null;
        DefVariante variante = definicion.variantes().get(patron.variante().lexema());
        if (variante == null) return null;
        NodoMatch.FormaPatron forma = switch (variante.forma()) {
            case UNITARIA -> NodoMatch.FormaPatron.UNITARIO;
            case TUPLA -> NodoMatch.FormaPatron.TUPLA;
            case ESTRUCTURA -> NodoMatch.FormaPatron.ESTRUCTURA;
        };
        if (patron.forma() != forma) return null;

        List<PatronCobertura> datos = new ArrayList<>();
        if (forma == NodoMatch.FormaPatron.TUPLA) {
            List<Tipo> tipos = variante.posicionales();
            if (patron.elementos().size() > tipos.size()
                    || !patron.resto() && patron.elementos().size() != tipos.size()) return null;
            for (int i = 0; i < tipos.size(); i++) {
                if (i >= patron.elementos().size()) {
                    datos.add(PatronCobertura.comodin());
                    continue;
                }
                PatronVariante.Elemento elemento = patron.elementos().get(i);
                PatronCobertura convertido = elemento.esAnidado()
                    ? normalizarPatron(elemento.patron(), tipos.get(i))
                    : PatronCobertura.comodin();
                if (convertido == null) return null;
                datos.add(convertido);
            }
        } else if (forma == NodoMatch.FormaPatron.ESTRUCTURA) {
            Map<String, NodoMatch.VinculacionCampo> presentes = new LinkedHashMap<>();
            for (NodoMatch.VinculacionCampo campo : patron.campos()) {
                if (presentes.putIfAbsent(campo.campo().lexema(), campo) != null
                        || !variante.campos().containsKey(campo.campo().lexema())) return null;
            }
            if (!patron.resto() && presentes.size() != variante.campos().size()) return null;
            for (var campo : variante.campos().entrySet()) {
                NodoMatch.VinculacionCampo presente = presentes.get(campo.getKey());
                if (presente == null) {
                    datos.add(PatronCobertura.comodin());
                    continue;
                }
                PatronCobertura convertido = presente.esAnidado()
                    ? normalizarPatron(presente.patron(), campo.getValue())
                    : PatronCobertura.comodin();
                if (convertido == null) return null;
                datos.add(convertido);
            }
        }
        return new PatronCobertura(patron.variante().lexema(), datos);
    }

    /** Tipos de datos de una variante en el mismo orden usado por su patrón normalizado. */
    private List<Tipo> tiposDatos(DefVariante variante) {
        return variante.forma() == NodoEnum.Forma.ESTRUCTURA
            ? List.copyOf(variante.campos().values()) : variante.posicionales();
    }

    private List<PatronCobertura> comodines(int cantidad) {
        List<PatronCobertura> resultado = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) resultado.add(PatronCobertura.comodin());
        return resultado;
    }

    /** Algoritmo de utilidad de matrices de patrones, adaptado a los enums finitos de FISIO. */
    private boolean esPatronUtil(List<List<PatronCobertura>> matriz,
            List<PatronCobertura> vector, List<Tipo> tipos) {
        if (vector.isEmpty()) return matriz.isEmpty();
        PatronCobertura primero = vector.get(0);
        Tipo tipo = tipos.get(0);
        List<PatronCobertura> colaVector = new ArrayList<>(vector.subList(1, vector.size()));
        List<Tipo> colaTipos = new ArrayList<>(tipos.subList(1, tipos.size()));
        DefEnum definicion = enums.get(tipo.nombre());
        if (matriz.isEmpty()) {
            return !(primero.esComodin() && definicion != null && definicion.variantes().isEmpty());
        }
        if (matriz.stream().anyMatch(fila -> fila.size() == vector.size()
                && fila.stream().allMatch(PatronCobertura::esComodin))) return false;

        if (primero.esComodin()) {
            boolean primeraColumnaComodin = matriz.stream().allMatch(fila -> fila.get(0).esComodin());
            if (primeraColumnaComodin || definicion == null) {
                return esPatronUtil(matrizPorDefecto(matriz), colaVector, colaTipos);
            }
            for (var entrada : definicion.variantes().entrySet()) {
                List<Tipo> datos = tiposDatos(entrada.getValue());
                List<PatronCobertura> especializado = new ArrayList<>(comodines(datos.size()));
                especializado.addAll(colaVector);
                List<Tipo> tiposEspecializados = new ArrayList<>(datos);
                tiposEspecializados.addAll(colaTipos);
                if (esPatronUtil(especializar(matriz, entrada.getKey(), datos.size()),
                        especializado, tiposEspecializados)) return true;
            }
            return false;
        }

        if (definicion == null) return true;
        DefVariante variante = definicion.variantes().get(primero.variante());
        if (variante == null) return true;
        List<Tipo> datos = tiposDatos(variante);
        List<PatronCobertura> especializado = new ArrayList<>(primero.datos());
        especializado.addAll(colaVector);
        List<Tipo> tiposEspecializados = new ArrayList<>(datos);
        tiposEspecializados.addAll(colaTipos);
        return esPatronUtil(especializar(matriz, primero.variante(), datos.size()),
            especializado, tiposEspecializados);
    }

    /** Conserva las filas cuyo primer patrón no restringe el valor y elimina esa columna. */
    private List<List<PatronCobertura>> matrizPorDefecto(List<List<PatronCobertura>> matriz) {
        List<List<PatronCobertura>> resultado = new ArrayList<>();
        for (List<PatronCobertura> fila : matriz) {
            if (fila.get(0).esComodin()) resultado.add(List.copyOf(fila.subList(1, fila.size())));
        }
        return resultado;
    }

    /** Especializa una matriz para una variante concreta, expandiendo comodines según su aridad. */
    private List<List<PatronCobertura>> especializar(List<List<PatronCobertura>> matriz,
            String variante, int aridad) {
        List<List<PatronCobertura>> resultado = new ArrayList<>();
        for (List<PatronCobertura> fila : matriz) {
            PatronCobertura primero = fila.get(0);
            List<PatronCobertura> nueva = new ArrayList<>();
            if (primero.esComodin()) nueva.addAll(comodines(aridad));
            else if (primero.variante().equals(variante)) nueva.addAll(primero.datos());
            else continue;
            nueva.addAll(fila.subList(1, fila.size()));
            resultado.add(List.copyOf(nueva));
        }
        return resultado;
    }

    /** Valida el patrón contra el valor y abre el ámbito de sus vinculaciones. */
    private void abrirAmbitoPatron(PatronVariante patron, Tipo tipoValor) {
        ambitos.push(validarPatron(patron, tipoValor));
    }

    /** Valida un patrón y devuelve sus vinculaciones sin decidir dónde vivirán. */
    private Map<String, Variable> validarPatron(PatronVariante patron, Tipo tipoValor) {
        Map<String, Variable> vinculaciones = new LinkedHashMap<>();
        validarPatron(patron, tipoValor, new LinkedHashSet<>(), vinculaciones, false);
        return vinculaciones;
    }

    private void validarPatron(PatronVariante patron, Tipo tipoValor, Set<String> nombresPatron,
            Map<String, Variable> vinculaciones, boolean anidado) {
        if (patron.comodin()) return;
        String tipoPatron = patron.tipo().lexema();
        String variante = patron.variante().lexema();
        DefEnum definicionValor = tipoValor.equals(ERROR) ? null : enums.get(tipoValor.nombre());
        if (anidado && !tipoValor.equals(ERROR) && definicionValor == null) {
            error(patron.tipo(), "PATRON_TIPO", "El patrón anidado '" + tipoPatron + "::" + variante
                + "' requiere un valor enum, pero el dato exterior es " + tipoValor + ".");
        }
        if (definicionValor != null && !tipoPatron.equals(tipoValor.nombre())) {
            error(patron.tipo(), "PATRON_TIPO", "El patrón usa " + tipoPatron
                + " pero el valor es " + tipoValor + ".");
        }
        DefEnum enumPatron = enums.get(tipoPatron);
        DefVariante datosPatron = enumPatron == null ? null : enumPatron.variantes().get(variante);
        if (datosPatron == null) {
            error(patron.variante(), "VARIANTE_DESCONOCIDA", "No existe la variante '"
                + tipoPatron + "::" + variante + "'.");
        }
        NodoMatch.FormaPatron formaEsperada = datosPatron == null ? null : switch (datosPatron.forma()) {
            case UNITARIA -> NodoMatch.FormaPatron.UNITARIO;
            case TUPLA -> NodoMatch.FormaPatron.TUPLA;
            case ESTRUCTURA -> NodoMatch.FormaPatron.ESTRUCTURA;
        };
        if (formaEsperada != null && patron.forma() != formaEsperada) {
            String codigoForma = formaEsperada == NodoMatch.FormaPatron.TUPLA
                ? "PATRON_ARIDAD" : "FORMA_PATRON";
            error(patron.variante(), codigoForma, "La variante '" + tipoPatron + "::" + variante
                + "' requiere un patrón " + descripcionFormaPatron(formaEsperada) + ".");
        }
        if (patron.forma() == NodoMatch.FormaPatron.TUPLA) {
            List<Tipo> tipos = datosPatron == null ? List.of() : datosPatron.posicionales();
            boolean aridadInvalida = patron.resto()
                ? patron.elementos().size() > tipos.size()
                : patron.elementos().size() != tipos.size();
            if (datosPatron != null && aridadInvalida) {
                error(patron.variante(), "PATRON_ARIDAD", "El patrón '" + tipoPatron + "::" + variante
                    + "' necesita " + (patron.resto() ? "como máximo " : "") + tipos.size()
                    + " elemento(s), pero recibió " + patron.elementos().size() + ".");
            }
            for (int i = 0; i < patron.elementos().size(); i++) {
                PatronVariante.Elemento elemento = patron.elementos().get(i);
                Tipo tipo = i < tipos.size() ? tipos.get(i) : ERROR;
                if (elemento.esAnidado()) {
                    validarPatron(elemento.patron(), tipo, nombresPatron, vinculaciones, true);
                } else {
                    vincularPatron(elemento.nombre(), tipo, nombresPatron, vinculaciones);
                }
            }
        } else if (patron.forma() == NodoMatch.FormaPatron.ESTRUCTURA) {
            Map<String, Tipo> tipos = datosPatron == null ? Map.of() : datosPatron.campos();
            Set<String> camposVistos = new LinkedHashSet<>();
            for (NodoMatch.VinculacionCampo campo : patron.campos()) {
                String nombreCampo = campo.campo().lexema();
                if (!camposVistos.add(nombreCampo)) {
                    error(campo.campo(), "CAMPO_DUPLICADO", "El campo '" + nombreCampo
                        + "' está repetido en el patrón.");
                }
                Tipo tipo = tipos.get(nombreCampo);
                if (tipo == null && datosPatron != null) {
                    error(campo.campo(), "CAMPO_DESCONOCIDO", "La variante '" + tipoPatron + "::"
                        + variante + "' no tiene un campo llamado '" + nombreCampo + "'.");
                    tipo = ERROR;
                }
                if (tipo == null) tipo = ERROR;
                if (campo.esAnidado()) {
                    validarPatron(campo.patron(), tipo, nombresPatron, vinculaciones, true);
                } else {
                    vincularPatron(campo.vinculacion(), tipo, nombresPatron, vinculaciones);
                }
            }
            Set<String> faltantes = new LinkedHashSet<>(tipos.keySet());
            faltantes.removeAll(camposVistos);
            if (datosPatron != null && !patron.resto() && !faltantes.isEmpty()) {
                error(patron.variante(), "CAMPO_PATRON_FALTANTE", "Faltan campos en el patrón '"
                    + tipoPatron + "::" + variante + "': " + String.join(", ", faltantes) + ".");
            }
        }
    }

    private void vincularPatron(TokenFisio vinculacion, Tipo tipo, Set<String> nombresPatron,
            Map<String, Variable> vinculaciones) {
        if (vinculacion.lexema().equals("_")) return;
        if (!nombresPatron.add(vinculacion.lexema())) {
            error(vinculacion, "VINCULACION_DUPLICADA", "La vinculación '"
                + vinculacion.lexema() + "' está repetida en el patrón.");
        }
        vinculaciones.put(vinculacion.lexema(), new Variable(tipo, false));
    }

    private String descripcionFormaPatron(NodoMatch.FormaPatron forma) {
        return switch (forma) {
            case UNITARIO -> "unitario, sin paréntesis ni llaves";
            case TUPLA -> "posicional con paréntesis";
            case ESTRUCTURA -> "con campos entre llaves";
            case COMODIN -> "comodín";
        };
    }

    /** Contexto solo para literales sin sufijo, nunca convierte variables de otro tipo. */
    private Tipo[] operandos(NodoAST izquierda, NodoAST derecha, Tipo esperado) {
        Tipo contexto = pista(izquierda);
        if (contexto == null) contexto = pista(derecha);
        if (contexto == null) contexto = esperado;
        Tipo a = expresion(izquierda, contexto);
        Tipo b = expresion(derecha, a.equals(ERROR) ? contexto : a);
        return new Tipo[]{a, b};
    }

    private Tipo pista(NodoAST nodo) {
        if (nodo instanceof NodoIdentificador n) { var v = buscar(n.token().lexema()); return v == null ? null : v.tipo(); }
        if (nodo instanceof NodoLlamadaFuncion n) {
            DefStruct constructor = structs.get(n.nombre().lexema());
            if (constructor != null && constructor.tupla()) return new Tipo(n.nombre().lexema());
            var f = funciones.get(n.nombre().lexema());
            return f == null ? null : f.retorno();
        }
        if (nodo instanceof NodoLlamadaAsociada n) {
            DefEnum definicion = enums.get(n.tipo().lexema());
            DefVariante variante = definicion == null ? null : definicion.variantes().get(n.nombre().lexema());
            if (variante != null && variante.forma() == NodoEnum.Forma.TUPLA)
                return new Tipo(n.tipo().lexema());
            Firma firma = metodos.get(n.tipo().lexema() + "::" + n.nombre().lexema());
            return firma == null ? null : firma.retorno();
        }
        if (nodo instanceof NodoLlamadaMetodo n) {
            Tipo receptor = pista(n.receptor());
            while (receptor != null && receptor.referencia()) receptor = receptor.elemento();
            Firma firma = receptor == null ? null : metodos.get(receptor.nombre() + "::" + n.nombre().lexema());
            return firma == null ? null : firma.retorno();
        }
        if (nodo instanceof NodoAgrupacion n) return pista(n.expresion());
        if (nodo instanceof NodoOperacionUnaria n) return pista(n.operando());
        if (nodo instanceof NodoReferencia n) {
            Tipo elemento = pista(n.expresion());
            return elemento == null ? null : Tipo.referencia(elemento, n.mutable());
        }
        if (nodo instanceof NodoDesreferencia n) {
            Tipo referencia = pista(n.referencia());
            return referencia != null && referencia.referencia() ? referencia.elemento() : null;
        }
        if (nodo instanceof NodoOperacion n) { Tipo t = pista(n.izquierda()); return t == null ? pista(n.derecha()) : t; }
        if (nodo instanceof NodoArreglo n && !n.elementos().isEmpty()) {
            Tipo elemento = pista(n.elementos().get(0));
            return elemento == null ? null : Tipo.arreglo(elemento, n.elementos().size());
        }
        if (nodo instanceof NodoTupla n) {
            List<Tipo> componentes = new ArrayList<>();
            for (NodoAST elemento : n.elementos()) {
                Tipo tipo = pista(elemento);
                if (tipo == null) return null;
                componentes.add(tipo);
            }
            return componentes.isEmpty() ? UNIDAD : Tipo.tupla(componentes);
        }
        if (nodo instanceof NodoConversion n) {
            Tipo tipo = new Tipo(n.tipo().lexema());
            return tipoSoportado(tipo) ? tipo : null;
        }
        if (nodo instanceof NodoLiteralStruct n && structs.containsKey(n.nombre().lexema())) {
            return new Tipo(n.nombre().lexema());
        }
        if (nodo instanceof NodoLiteralVariante n && enums.containsKey(n.tipo().lexema())) {
            return new Tipo(n.tipo().lexema());
        }
        if (nodo instanceof NodoVarianteEnum n && enums.containsKey(n.tipo().lexema())) {
            return new Tipo(n.tipo().lexema());
        }
        if (nodo instanceof NodoAccesoCampo n) {
            Tipo objeto = pista(n.objeto());
            while (objeto != null && objeto.referencia()) objeto = objeto.elemento();
            DefStruct definicion = objeto == null ? null : structs.get(objeto.nombre());
            return definicion == null ? null : definicion.campos().get(n.campo().lexema());
        }
        if (nodo instanceof NodoIndice n) {
            Tipo objeto = pista(n.objeto());
            return objeto != null && objeto.arreglo() ? objeto.elemento() : null;
        }
        if (nodo instanceof NodoAccesoTupla n) {
            Tipo objeto = pista(n.objeto());
            if (objeto == null || !objeto.tupla()) return null;
            try {
                int indice = Integer.parseInt(n.indice().lexema());
                return indice >= 0 && indice < objeto.componentes().size()
                    ? objeto.componentes().get(indice) : null;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (nodo instanceof NodoMatch n && !n.brazos().isEmpty()) return pista(n.brazos().get(0).resultado());
        if (nodo instanceof NodoBloque n && n.expresionFinal() != null) return pista(n.expresionFinal());
        return null;
    }

    private Tipo literal(NodoNumero n, Tipo esperado, boolean negativo) {
        TokenFisio token = n.token();
        if (token.categoria() == CategoriaToken.STRING) return STRING;
        if (token.categoria() == CategoriaToken.CHAR) return CHAR;
        if (token.lexema().equals("true") || token.lexema().equals("false")) return BOOL;
        if (token.lexema().contains(".")) {
            Tipo tipo = esperado != null && esperado.decimal() ? esperado : F64;
            double valor = tipo.nombre().equals("f32") ? Float.parseFloat(token.lexema()) : Double.parseDouble(token.lexema());
            if (!Double.isFinite(valor)) error(n, "LITERAL_FUERA_DE_RANGO", "Literal fuera del rango de " + tipo + ".");
            return tipo;
        }
        Tipo tipo = esperado != null && esperado.entero() ? esperado : I32;
        BigInteger valor = new BigInteger(token.lexema());
        if (negativo) valor = valor.negate();
        boolean unsigned = tipo.nombre().startsWith("u");
        int bits = tipo.nombre().endsWith("size") ? 64 : Integer.parseInt(tipo.nombre().substring(1));
        BigInteger max = BigInteger.ONE.shiftLeft(unsigned ? bits : bits - 1).subtract(BigInteger.ONE);
        BigInteger min = unsigned ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
        if (valor.compareTo(min) < 0 || valor.compareTo(max) > 0 || (negativo && unsigned)) {
            error(n, "LITERAL_FUERA_DE_RANGO", "Literal fuera del rango de " + tipo + ".");
        }
        return tipo;
    }

    private Tipo operador(NodoAST nodo, String op, Tipo a, Tipo b) {
        if (a.equals(ERROR) || b.equals(ERROR)) return ERROR;
        if (a.equals(NUNCA) || b.equals(NUNCA)) return NUNCA;
        boolean iguales = a.equals(b);
        String operacion = Set.of("+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=").contains(op)
            ? op.substring(0, op.length() - 1) : op;
        switch (operacion) {
            case "+", "-", "*", "/", "%": if (iguales && a.numerico()) return a; break;
            case "<<", ">>": if (a.entero() && b.entero()) return a; break;
            case "&", "|", "^": if (iguales && (a.entero() || a.equals(BOOL))) return a; break;
            case "&&", "||": if (a.equals(BOOL) && b.equals(BOOL)) return BOOL; break;
            case "==", "!=": if (iguales && (a.numerico() || a.equals(BOOL) || a.equals(STRING) || a.equals(CHAR) || a.equals(UNIDAD))) return BOOL; break;
            case "<", ">", "<=", ">=": if (iguales && (a.numerico() || a.equals(STRING) || a.equals(CHAR))) return BOOL; break;
            default: break;
        }
        error(nodo, "OPERADOR", "El operador '" + op + "' no admite " + a + " y " + b + ".");
        return ERROR;
    }

    private Tipo llamadaAsociada(NodoLlamadaAsociada llamada) {
        DefEnum definicionEnum = enums.get(llamada.tipo().lexema());
        if (definicionEnum != null && definicionEnum.variantes().containsKey(llamada.nombre().lexema())) {
            DefVariante variante = definicionEnum.variantes().get(llamada.nombre().lexema());
            if (variante.forma() != NodoEnum.Forma.TUPLA) {
                for (NodoAST argumento : llamada.argumentos()) expresion(argumento, null);
                error(llamada, "FORMA_VARIANTE", "La variante '" + llamada.tipo().lexema() + "::"
                    + llamada.nombre().lexema() + "' no se construye con argumentos posicionales.");
                return ERROR;
            }
            List<Tipo> esperados = variante.posicionales();
            validarArgumentosTipos(llamada, llamada.argumentos(), esperados,
                llamada.tipo().lexema() + "::" + llamada.nombre().lexema());
            return new Tipo(llamada.tipo().lexema());
        }
        String clave = llamada.tipo().lexema() + "::" + llamada.nombre().lexema();
        Firma firma = metodos.get(clave);
        NodoFuncion metodo = nodosMetodo.get(clave);
        if (firma == null || metodo == null) {
            for (NodoAST argumento : llamada.argumentos()) expresion(argumento, null);
            error(llamada, "FUNCION_ASOCIADA_NO_DECLARADA", "No existe la función asociada '" + clave + "'.");
            return ERROR;
        }
        boolean tieneSelf = !metodo.parametros().isEmpty()
            && metodo.parametros().get(0).nombre().lexema().equals("self");
        if (tieneSelf) {
            error(llamada, "METODO_REQUIERE_RECEPTOR", "'" + clave
                + "' es un método y debe invocarse desde un receptor con '.'.");
            validarArgumentos(llamada, llamada.argumentos(), firma, 1, clave);
            return firma.retorno();
        }
        validarArgumentos(llamada, llamada.argumentos(), firma, 0, clave);
        return firma.retorno();
    }

    private Tipo llamadaMetodo(NodoLlamadaMetodo llamada) {
        Tipo tipoReceptor = expresion(llamada.receptor(), null);
        Tipo base = tipoReceptor;
        while (base.referencia()) base = base.elemento();
        String clave = base.nombre() + "::" + llamada.nombre().lexema();
        Firma firma = metodos.get(clave);
        NodoFuncion metodo = nodosMetodo.get(clave);
        if (firma == null || metodo == null) {
            for (NodoAST argumento : llamada.argumentos()) expresion(argumento, null);
            if (!tipoReceptor.equals(ERROR)) error(llamada, "METODO_NO_DECLARADO",
                "El tipo '" + base + "' no tiene un método llamado '" + llamada.nombre().lexema() + "'.");
            return ERROR;
        }
        boolean tieneSelf = !metodo.parametros().isEmpty()
            && metodo.parametros().get(0).nombre().lexema().equals("self");
        if (!tieneSelf) {
            error(llamada, "FUNCION_ASOCIADA_SIN_RECEPTOR", "'" + clave
                + "' es una función asociada y debe invocarse con '::'.");
            validarArgumentos(llamada, llamada.argumentos(), firma, 0, clave);
            return firma.retorno();
        }
        Tipo esperadoSelf = firma.parametros().get(0);
        Tipo baseEsperada = esperadoSelf.referencia() ? esperadoSelf.elemento() : esperadoSelf;
        if (!tipoReceptor.equals(ERROR) && !base.equals(baseEsperada)) {
            error(llamada.receptor(), "RECEPTOR_TIPO", "El método '" + clave + "' requiere un receptor "
                + baseEsperada + ", pero se encontró " + tipoReceptor + ".");
        }
        if (esperadoSelf.referenciaMutable() && !receptorMutable(llamada.receptor(), tipoReceptor)) {
            error(llamada.receptor(), "RECEPTOR_MUTABLE", "El método '" + clave
                + "' requiere un receptor mutable.");
        }
        validarArgumentos(llamada, llamada.argumentos(), firma, 1, clave);
        return firma.retorno();
    }

    private boolean receptorMutable(NodoAST receptor, Tipo tipo) {
        if (tipo.referenciaMutable()) return true;
        NodoIdentificador raiz = raizAsignacion(receptor);
        Variable variable = raiz == null ? null : buscar(raiz.token().lexema());
        return variable != null && (variable.mutable() || variable.tipo().referenciaMutable());
    }

    private void validarArgumentos(NodoAST llamada, List<NodoAST> argumentos, Firma firma,
            int desplazamiento, String nombre) {
        int esperados = Math.max(0, firma.parametros().size() - desplazamiento);
        if (argumentos.size() != esperados) {
            error(llamada, "ARIDAD", "'" + nombre + "' espera " + esperados
                + " argumento(s), pero recibió " + argumentos.size() + ".");
        }
        for (int i = 0; i < argumentos.size(); i++) {
            Tipo esperado = i + desplazamiento < firma.parametros().size()
                ? firma.parametros().get(i + desplazamiento) : null;
            Tipo actual = expresion(argumentos.get(i), esperado);
            if (esperado != null) exigir(argumentos.get(i), esperado, actual, "ARGUMENTO",
                "Argumento " + (i + 1) + " de '" + nombre + "'");
        }
    }

    private void validarArgumentosTipos(NodoAST llamada, List<NodoAST> argumentos,
            List<Tipo> esperados, String nombre) {
        if (argumentos.size() != esperados.size()) {
            error(llamada, "ARIDAD", "'" + nombre + "' espera " + esperados.size()
                + " argumento(s), pero recibió " + argumentos.size() + ".");
        }
        for (int i = 0; i < argumentos.size(); i++) {
            Tipo esperado = i < esperados.size() ? esperados.get(i) : null;
            Tipo actual = expresion(argumentos.get(i), esperado);
            if (esperado != null) exigir(argumentos.get(i), esperado, actual, "ARGUMENTO",
                "Argumento " + (i + 1) + " de '" + nombre + "'");
        }
    }

    private Tipo llamada(NodoLlamadaFuncion n) {
        DefStruct constructor = structs.get(n.nombre().lexema());
        if (constructor != null) {
            if (!constructor.tupla()) {
                for (NodoAST argumento : n.argumentos()) expresion(argumento, null);
                error(n, "FORMA_STRUCT", "El struct '" + n.nombre().lexema()
                    + "' debe construirse con campos nombrados entre llaves.");
                return ERROR;
            }
            List<Tipo> esperados = new ArrayList<>();
            for (int i = 0; i < constructor.campos().size(); i++)
                esperados.add(constructor.campos().get(String.valueOf(i)));
            validarArgumentosTipos(n, n.argumentos(), esperados, n.nombre().lexema());
            return new Tipo(n.nombre().lexema());
        }
        Firma firma = funciones.get(n.nombre().lexema());
        Variable variable = buscar(n.nombre().lexema());
        if (variable != null) {
            error(n, "NO_INVOCABLE", "'" + n.nombre().lexema() + "' es una variable, no una funcion.");
            firma = null;
        } else if (firma == null) error(n, "FUNCION_NO_DECLARADA", "Funcion no declarada: '" + n.nombre().lexema() + "'.");
        if (firma != null && n.argumentos().size() != firma.parametros().size()) {
            error(n, "ARIDAD", "'" + n.nombre().lexema() + "' espera " + firma.parametros().size() + " argumento(s), pero recibio " + n.argumentos().size() + ".");
        }
        for (int i = 0; i < n.argumentos().size(); i++) {
            Tipo esperado = firma != null && i < firma.parametros().size() ? firma.parametros().get(i) : null;
            Tipo actual = expresion(n.argumentos().get(i), esperado);
            if (esperado != null) exigir(n.argumentos().get(i), esperado, actual, "ARGUMENTO", "Argumento " + (i + 1) + " de '" + n.nombre().lexema() + "'");
        }
        return firma == null ? ERROR : firma.retorno();
    }

    private Tipo macro(NodoInvocacionMacro n) {
        List<Tipo> tipos = new ArrayList<>();
        for (NodoAST argumento : n.argumentos()) tipos.add(expresion(argumento, null));
        if (!n.nombre().lexema().equals("println")) {
            error(n, "MACRO_DESCONOCIDA", "Macro no soportada: '" + n.nombre().lexema() + "!'.");
            return ERROR;
        }
        if (n.argumentos().isEmpty()) return UNIDAD;
        for (int i = 1; i < tipos.size(); i++) {
            Tipo tipo = tipos.get(i);
            if (!tipo.equals(ERROR) && !tipo.equals(NUNCA)
                    && !(tipo.numerico() || tipo.equals(BOOL) || tipo.equals(STRING) || tipo.equals(CHAR))) {
                error(n.argumentos().get(i), "FORMATO_TIPO", "println! no puede mostrar un valor de tipo " + tipo + " con {}.");
            }
        }
        NodoAST formato = n.argumentos().get(0);
        if (!(formato instanceof NodoNumero literal) || literal.token().categoria() != CategoriaToken.STRING) {
            error(formato, "FORMATO", "El primer argumento de println! debe ser una cadena literal de formato.");
            return UNIDAD;
        }
        String texto = literal.token().lexema();
        int huecos = 0;
        for (int i = 1; i < texto.length() - 1; i++) {
            char c = texto.charAt(i);
            if (c != '{' && c != '}') continue;
            if (i + 1 < texto.length() - 1 && texto.charAt(i + 1) == c) { i++; continue; }
            if (c == '{' && i + 1 < texto.length() - 1 && texto.charAt(i + 1) == '}') { huecos++; i++; continue; }
            error(formato, "FORMATO", "Formato invalido o no soportado: usa {}, {{ o }} en println!.");
            return UNIDAD;
        }
        if (huecos != n.argumentos().size() - 1) error(n, "FORMATO_ARIDAD", "println! contiene " + huecos + " marcador(es) {} y recibio " + (n.argumentos().size() - 1) + " valor(es).");
        return UNIDAD;
    }

    private void comprobarCero(NodoAST nodo, String operador, NodoAST divisor) {
        if (!(operador.equals("/") || operador.equals("%") || operador.equals("/=") || operador.equals("%="))) return;
        BigDecimal constante = constante(divisor);
        if (constante != null && constante.signum() == 0) error(nodo, "DIVISION_CERO", "Division o resto entre una expresion constante igual a cero.");
    }

    /** Constantes puras, con truncamiento entero y redondeo del tipo decimal real.
     * No evalúa variables ni llamadas, ni deduce valores después de overflow. */
    private BigDecimal constante(NodoAST nodo) {
        Tipo tipo = tiposExpresion.get(nodo);
        if (tipo != null && !tipo.numerico()) return null;
        BigDecimal valor = null;
        if (nodo instanceof NodoNumero n && n.token().categoria() == CategoriaToken.NUM)
            valor = new BigDecimal(n.token().lexema());
        else if (nodo instanceof NodoAgrupacion n) valor = constante(n.expresion());
        else if (nodo instanceof NodoOperacionUnaria n && n.operador().lexema().equals("-")) {
            var v = constante(n.operando());
            if (v != null) valor = v.negate();
        } else if (nodo instanceof NodoOperacion n) {
            BigDecimal a = constante(n.izquierda()), b = constante(n.derecha());
            if (a == null || b == null || tipo == null) return null;
            String op = n.operador().lexema();
            if ((op.equals("/") || op.equals("%")) && b.signum() == 0) return null;
            if (tipo.decimal()) {
                double resultado;
                if (tipo.nombre().equals("f32")) {
                    float x = a.floatValue(), y = b.floatValue();
                    resultado = switch (op) { case "+" -> x + y; case "-" -> x - y;
                        case "*" -> x * y; case "/" -> x / y; case "%" -> x % y; default -> Double.NaN; };
                } else {
                    double x = a.doubleValue(), y = b.doubleValue();
                    resultado = switch (op) { case "+" -> x + y; case "-" -> x - y;
                        case "*" -> x * y; case "/" -> x / y; case "%" -> x % y; default -> Double.NaN; };
                }
                return Double.isFinite(resultado) ? BigDecimal.valueOf(resultado) : null;
            }
            valor = switch (op) {
                case "+" -> a.add(b); case "-" -> a.subtract(b); case "*" -> a.multiply(b);
                case "/" -> a.divideToIntegralValue(b); case "%" -> a.remainder(b); default -> null;
            };
        }
        if (valor == null || tipo == null) return valor;
        if (tipo.decimal()) {
            double redondeado = tipo.nombre().equals("f32") ? valor.floatValue() : valor.doubleValue();
            return Double.isFinite(redondeado) ? BigDecimal.valueOf(redondeado) : null;
        }
        boolean unsigned = tipo.nombre().startsWith("u");
        int bits = tipo.nombre().endsWith("size") ? 64 : Integer.parseInt(tipo.nombre().substring(1));
        BigInteger max = BigInteger.ONE.shiftLeft(unsigned ? bits : bits - 1).subtract(BigInteger.ONE);
        BigInteger min = unsigned ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
        return valor.compareTo(new BigDecimal(min)) < 0 || valor.compareTo(new BigDecimal(max)) > 0 ? null : valor;
    }

    private boolean esTrue(NodoAST nodo) {
        if (nodo instanceof NodoAgrupacion n) return esTrue(n.expresion());
        return nodo instanceof NodoNumero n && n.token().lexema().equals("true");
    }

    private void comprobarRetorno(NodoAST nodo, Tipo valor, boolean retornoVacio, String contexto) {
        if (retornoActual.equals(ERROR) || valor.equals(ERROR) || valor.equals(NUNCA)) return;
        if (retornoVacio && !retornoActual.equals(UNIDAD)) {
            error(nodo, "RETORNO", "Se encontró un retorno vacío; la función debe devolver un valor de tipo " + retornoActual + ".");
        } else if (retornoActual.equals(UNIDAD) && !valor.equals(UNIDAD)) {
            error(nodo, "RETORNO", "La función no puede devolver un valor porque no tiene tipo de retorno declarado; se encontró " + valor + ".");
        } else exigir(nodo, retornoActual, valor, "RETORNO", contexto);
    }

    private void exigir(NodoAST nodo, Tipo esperado, Tipo actual, String codigo, String contexto) {
        if (esperado.equals(ERROR) || actual.equals(ERROR) || actual.equals(NUNCA) || esperado.equals(actual)) return;
        error(nodo, codigo, contexto + ": se esperaba " + esperado + ", se encontro " + actual + ".");
    }

    private void error(NodoAST nodo, String codigo, String mensaje) {
        errores.add(new ErrorSemantico(codigo, mensaje, nodo.linea(), nodo.columna()));
    }

    private void error(TokenFisio token, String codigo, String mensaje) {
        errores.add(new ErrorSemantico(codigo, mensaje, token.linea(), token.columna()));
    }

    private void error(int linea, int columna, String codigo, String mensaje) {
        errores.add(new ErrorSemantico(codigo, mensaje, linea, columna));
    }
}
