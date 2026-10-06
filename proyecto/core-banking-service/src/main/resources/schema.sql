CREATE TABLE
    IF NOT EXISTS transacciones_procesadas (
        id INTEGER PRIMARY KEY,
        fecha DATE NOT NULL,
        monto NUMERIC(14, 2) NOT NULL,
        tipo VARCHAR(20) NOT NULL
    );

CREATE TABLE
    IF NOT EXISTS intereses_mensuales (
        cuenta_id INTEGER PRIMARY KEY,
        nombre VARCHAR(100) NOT NULL,
        tipo VARCHAR(20) NOT NULL,
        saldo_inicial NUMERIC(14, 2) NOT NULL,
        tasa NUMERIC(8, 4) NOT NULL,
        saldo_final NUMERIC(14, 2) NOT NULL
    );

CREATE TABLE
    IF NOT EXISTS movimientos_anuales (
        movimiento_id SERIAL PRIMARY KEY,
        cuenta_id INTEGER NOT NULL,
        fecha DATE NOT NULL,
        transaccion VARCHAR(30) NOT NULL,
        monto NUMERIC(14, 2) NOT NULL,
        descripcion VARCHAR(150) NOT NULL,
        UNIQUE (cuenta_id, fecha, transaccion, monto, descripcion)
    );

CREATE TABLE
    IF NOT EXISTS resumen_transacciones_diarias (
        resumen_id SERIAL PRIMARY KEY,
        job_execution_id BIGINT NOT NULL,
        registros_leidos INTEGER NOT NULL,
        registros_procesados INTEGER NOT NULL,
        registros_escritos INTEGER NOT NULL,
        registros_omitidos INTEGER NOT NULL,
        estado VARCHAR(20) NOT NULL
    );
CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS withdrawal_transactions (
    transaction_id UUID PRIMARY KEY,
    account_id INTEGER NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    status VARCHAR(12) NOT NULL,
    final_balance NUMERIC(14, 2),
    rejection_reason VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS banking_events (
    event_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id INTEGER NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL
);
