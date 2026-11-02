# CP-07 · Spike: fuentes de resultados de loterías colombianas

> Fecha: 2026-11-02. Spike de investigación: **no hay cambios de código ni de esquema** en este CP.
> No soy abogado. Lo legal está para decidir con criterio, no como asesoría.

## 1. Recomendación (resumen)

1. **Fuente de verdad = ingreso manual con evidencia** (ADR-08 se confirma). Ninguna fuente automática que encontré es oficial, documentada y estable.
2. **Automatización solo como sugerencia** (CP-10, opcional): leer únicamente el sitio oficial de la lotería elegida, comparar con una segunda fuente y **dejar el resultado en estado "por verificar"**; nunca publicar ni sortear solo. Si falla, se cae a manual.
3. **No usar agregadores como fuente de verdad** ni scrapearlos (no son oficiales, algunos actualizan "al día siguiente", y no revisé sus términos de uso).
4. **Hallazgo legal que afecta el producto** (sección 4): el Decreto 1486 de 2024 cambió el régimen de rifas. Hay que decidir si esto es demostración o producto real **antes** de vender boletos.

## 2. Fuentes evaluadas

| Fuente | Qué es | Pros | Contras | Veredicto |
|---|---|---|---|---|
| **Sitio oficial de cada lotería** (ej. `loteriadebogota.com`) | Fuente primaria; publica minutos después del sorteo | Es el dato oficial | ~15–20 sitios con formatos distintos; **no encontré API pública documentada**; HTML frágil; términos de uso sin revisar | Evidencia para el manual; base de CP-10 |
| **Transmisión y acta del sorteo** | Los sorteos de lotería son de interés público y se transmiten en vivo por canales públicos (Ley 643/2001 art. 20, citado en Acuerdo CNJSA 541/2020) | Autoridad máxima; sirve de evidencia | No es un feed de datos | Evidencia (enlace/captura) |
| **Prensa** (El Tiempo, Caracol, El País, Blu Radio) | Publican número y serie el mismo día | Rápida, fácil de citar | Sin API; contenido protegido; ellos mismos remiten al acta oficial | Solo enlace de evidencia secundaria |
| **Agregadores** (loterias.com.co, resultadosloteriascol.com, loteriaya, etc.) | Reúnen 19–60+ loterías | Cobertura amplia; alguno ofrece CSV | **Se declaran no oficiales**; uno dice actualizar "al día siguiente"; términos sin revisar; pueden desaparecer o equivocarse | Descartar como fuente; útil como contraste humano |
| **Scrapers de terceros** (Apify, GitHub) | Servicios de extracción | — | No encontré ninguno para loterías colombianas; costo y dependencia | Descartar |
| **Acuerdos anuales del CNJSA** (Diario Oficial) | Cronograma oficial de sorteos (día, hora) | Calendario autoritativo | No trae resultados | Fuente del catálogo de días/horas |

## 3. Qué implica para CP-08 y CP-09

Datos reales vistos en las fuentes que cambian el diseño previsto:

- **`numero_mayor` debe ser texto (`VARCHAR(4)`), no entero.** Hay resultados como `0377` y `0339`; un `INT` perdería el cero y rompería "últimos N dígitos".
- **Número de sorteo** (ej. #2865): lo publican todas las fuentes. Propongo agregar `numero_sorteo` a `resultados_loteria` con `UNIQUE (loteria_id, numero_sorteo)`: es la clave natural para que CP-10 sea idempotente.
- **La serie (3 dígitos) no entra al sorteo de la rifa.** Las boletas de rifa no pueden tener serie (Decreto 1486/2024, art. 2.7.3.2). Se guarda por evidencia, no se usa para decidir.
- **El día de la semana no basta.** El cronograma dice que si el sorteo cae en festivo, la lotería puede jugar ese día, otro día de la misma semana, o no jugar (Acuerdo CNJSA 565/2020); además hay sorteos extraordinarios. Propongo: `dia_semana`/`hora_sorteo` en `loterias` son **valores por defecto**; la rifa guarda su `fecha_sorteo` explícita; y el sistema debe contemplar "no hubo sorteo" → la rifa se reprograma y se avisa a compradores (el decreto exige informar el aplazamiento).
- **Corrección, no borrado:** `estado` (`VIGENTE` / `ANULADO`) en el resultado, y bloquear cambios si ya se sorteó una rifa con él. Todo con `auditoria_eventos` (quién, cuándo, evidencia).
- **Dos personas:** `cargado_por` ≠ `verificado_por` antes de poder usar el resultado en un sorteo. Es barato y evita que un error de dedo adjudique un premio.
- **Horarios de referencia** (indicativos, de agregadores y de acuerdos viejos; confirmar con el CNJSA vigente): lun Cundinamarca/Tolima · mar Huila · mié Meta/Valle · jue Bogotá/Quindío · vie Medellín/Risaralda · sáb Cauca/Boyacá; entre las 21:30 y 23:00. **No los siembres como verdad sin verificar.**

## 4. Hallazgo legal (decidir antes de vender)

El **Decreto 1486 del 13-dic-2024** (Min. Hacienda) modifica el régimen de rifas. Lo leído del texto, que mi copia tiene OCR dañado en partes y conviene confirmarlo en el original:

- Quien organiza (**gestor**) debe operar **a través de un operador**, persona jurídica autorizada, con integración a una plataforma que permita el control de la autoridad. Hay un periodo de transición de 12 meses que corre desde la entrada en vigencia de los requerimientos técnicos de Coljuegos (**no verifiqué si ya se expidieron**).
- Premios **en especie**: se prohíben rifas con **dinero** y de **bienes usados**; boletas **sin series** ni fraccionadas.
- **Máximo 2 rifas al mes** por gestor; las permanentes están prohibidas.
- Derechos de explotación del **14 %** de los ingresos brutos; el plan de premios debe ser **≥ 40 %** de la emisión; garantía (póliza) por el valor del plan de premios.
- El sorteo se cursa contra una **lotería tradicional** (con autorización de esa lotería) o, como alternativa, un **generador de números aleatorios** operado por el operador. El `ALEATORIO` actual (SecureRandom) **no equivale** a ese GNA legal.

Consecuencias para el producto: (a) si esto va a vender con dinero real, hace falta un operador autorizado y la autorización previa; (b) el campo de autorización de la rifa pasa de informativo a **requisito de publicación**; (c) validar "máx. 2 rifas/mes" y "premio en especie" podría ser regla de negocio. **No implementé nada de esto**: es decisión tuya. Para la materia, basta dejar claro que es demostración.

## 5. Decisiones que necesito de ti

1. ¿Demostración académica o producto real? (define si la sección 4 se vuelve código).
2. ¿Agrego `numero_sorteo`, `estado` y `verificado_por` a la V3 (CP-08)? Lo recomiendo.
3. ¿El CP-10 (ingesta) se queda como opcional o lo sacamos del plan?

## Fuentes consultadas
- Decreto 1486/2024: <https://www.cijuf.org.co/sites/cijuf.org.co/files/normatividad/2024/DECRETO%201486%20DE%2013%20DE%20DICIEMBRE%20DE%202024.pdf>
- Requisitos de rifas nacionales, Coljuegos: <https://www.coljuegos.gov.co/descargar.php?id=200057>
- Acuerdo CNJSA 541/2020: <https://normograma.mintic.gov.co/docs/pdf/acuerdo_cnjuegosazar_0541_2020.pdf>
- Acuerdo CNJSA 565/2020: <https://www.ambitojuridico.com/sites/default/files/BancoMedios/Archivos/acuerdo-566-20.pdf>
- Agregadores revisados: <https://resultadosloteriascol.com/>, <https://loterias.com.co/>, <https://www.loteriaya.com.co/loterias>
- Ejemplos de resultados con cero a la izquierda: <https://www.elpais.com.co/loterias/loteria-de-bogota-de-este-jueves-22-de-enero-del-2026-resultados-oficiales-2240.html>
