-- Odyssey backend: initial schema (PostgreSQL 13+).
-- Generated from app/models.py by scripts/gen_init_sql.py -- regenerate, do not hand-edit.
-- Idempotent: safe to run more than once.

CREATE SCHEMA IF NOT EXISTS "odyssey";

CREATE TABLE IF NOT EXISTS odyssey.trips (
	id UUID DEFAULT gen_random_uuid() NOT NULL, 
	session_id VARCHAR(64) NOT NULL, 
	title TEXT, 
	request JSONB NOT NULL, 
	itinerary JSONB, 
	status VARCHAR(16) DEFAULT 'PENDING' NOT NULL, 
	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT trips_status_check CHECK (status IN ('PENDING','READY','FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_trips_session_created ON odyssey.trips (session_id, created_at);

CREATE TABLE IF NOT EXISTS odyssey.runs (
	id UUID DEFAULT gen_random_uuid() NOT NULL, 
	trip_id UUID NOT NULL, 
	status VARCHAR(16) DEFAULT 'QUEUED' NOT NULL, 
	error JSONB, 
	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
	started_at TIMESTAMP WITH TIME ZONE, 
	finished_at TIMESTAMP WITH TIME ZONE, 
	PRIMARY KEY (id), 
	CONSTRAINT runs_status_check CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')), 
	FOREIGN KEY(trip_id) REFERENCES odyssey.trips (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_runs_trip_id ON odyssey.runs (trip_id);
