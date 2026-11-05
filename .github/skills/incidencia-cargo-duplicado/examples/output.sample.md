# Salida de ejemplo: cargo posiblemente duplicado

```json
{
	"case_id": "caso-03",
	"service": "tarjetas",
	"error_code": "DUPLICATE_DEBIT_SUSPECTED",
	"correlation_id": "PAY-DEMO-301",
	"first_seen": "2026-11-03T16:00:03Z",
	"last_seen": "2026-11-03T16:00:03Z",
	"occurrences": 1,
	"priority": "P1",
	"summary": "Se detectó una posible duplicidad de débito que requiere conciliación.",
	"impact": "La operación PAY-DEMO-301 tiene dos movimientos asociados: MOV-DEMO-01 y MOV-DEMO-02. El log no confirma el resultado de conciliación ni si corresponde una devolución.",
	"evidence": [
		{
			"file": "caso-03-cargos.log",
			"line": 2,
			"text": "correlation_id=PAY-DEMO-301 event_id=evt-302 code=DEBIT_RECORDED movement_id=MOV-DEMO-01 amount=80.00 currency=PEN"
		},
		{
			"file": "caso-03-cargos.log",
			"line": 3,
			"text": "correlation_id=PAY-DEMO-301 event_id=evt-303 code=DEBIT_RECORDED movement_id=MOV-DEMO-02 amount=80.00 currency=PEN"
		},
		{
			"file": "caso-03-cargos.log",
			"line": 4,
			"text": "correlation_id=PAY-DEMO-301 event_id=evt-304 code=DUPLICATE_DEBIT_SUSPECTED movement_ids=MOV-DEMO-01,MOV-DEMO-02 card=[REDACTADO]"
		},
		{
			"file": "caso-03-cargos.log",
			"line": 6,
			"text": "correlation_id=PAY-DEMO-301 event_id=evt-305 code=RECONCILIATION_PENDING message=\"No hay resultado de conciliacion disponible\""
		}
	],
	"missing_data": [
		"No hay resultado de conciliación disponible para determinar si ambos movimientos corresponden a débitos conciliados o si procede una devolución."
	],
	"recommended_action": "Solicitar a Operaciones la conciliación de MOV-DEMO-01 y MOV-DEMO-02 y revisar el resultado antes de decidir si corresponde una devolución."
}
```

¿Apruebas registrar exactamente esta incidencia? No se registra hasta recibir una aprobación explícita.
