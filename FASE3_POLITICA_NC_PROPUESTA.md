# FASE2-017 — Política propuesta, pendiente de decisión funcional

**REQUIERE DECISIÓN FUNCIONAL. No implementada.** La protección acumulada es una decisión de integridad del producto; no se presenta como una regla acumulativa SUNAT demostrada por los documentos disponibles.

## Propuesta antes de cambiar código

- Original identificado por ID y RUC del emisor, conservando vínculo, cliente, moneda y tasa histórica. No recalcular el original con la tasa vigente de empresa.
- Motivos monetarios actualmente soportados: 01/04/05/06/07/08/09. Cada NC monetaria consume/reserva su total calculado. El motivo **03 (corrección de descripción) no consume saldo monetario**, aunque el XML tenga importes informativos.
- Saldo disponible = total histórico del original − NC monetarias aceptadas − reservas de NC monetarias pendientes/inciertas. Nunca exceder el saldo ni aceptar saldo negativo por carreras.
- NC aceptada/aceptada con observaciones: consumo definitivo. Estados pendientes de envío/resumen, ENVIANDO, EN_RESUMEN e inciertos: reserva mientras su resultado pueda cambiar. Errores técnicos no prueban rechazo fiscal y no liberan automáticamente.
- NC individual RECHAZADO con CDR válido: liberar sólo si el flujo impide reenviar esa misma NC y la política de corrección está confirmada. Un **RC rechazado no equivale por sí solo a cancelar cada NC**: las NC siguen EN_RESUMEN y pueden corregirse/reincorporarse; conservar su reserva.
- No liberar inciertos por timeout ni ampliar plazos. Una cancelación técnica segura/reconciliación futura debe tener acción explícita, evidencia y atomicidad; no inventar un estado fiscal nuevo en esta corrección.
- Bloquear el original por RUC/ID en la misma transacción corta de emisión/idempotencia, comprobar acumulados y registrar reserva/vínculo + nueva NC conjuntamente. Dos emisores concurrentes no pueden aprobar ambos el mismo saldo. SOAP fuera de esa transacción.
- Convertir reserva a consumo o liberar bajo el mismo control del original, sólo una vez; no perder reserva por un fallo entre actualización del estado y del saldo. Idempotencia no duplica reservas.

## Decisiones que el código actual no determina

1. Aprobar esta política de reserva frente a errores técnicos/RC rechazados, y definir qué acción puede cancelar definitivamente una NC no aceptada sin posibilidad de reenvío.
2. Tras NC parcial, los motivos 01/06 actualmente exigen el importe original completo. Propuesta conservadora: rechazarlos mientras haya NC monetarias aceptadas/reservadas; no inventar una devolución total por “saldo restante” ni modificar su XML/regla existente sin decisión explícita.
3. Confirmar si el límite acumulado debe aplicarse sólo al total o también separadamente a bases/IGV y cantidades por ítem, especialmente bonificación y descuentos. No deducirlo de una regla individual de comparación con el original.
4. Plan para datos históricos: revisar NC previas y motivos antes de introducir protección. No backfill, reescritura de comprobantes ni liberación masiva automática.

Al no poder deducir una política completa, FASE2-017 permanece pendiente. Se permite continuar los bloques independientes de validación/SOAP/límites/firma/esquema. No se ha cambiado emisión ni saldo por esta propuesta.
