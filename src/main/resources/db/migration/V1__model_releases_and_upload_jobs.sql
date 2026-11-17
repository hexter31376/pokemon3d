CREATE TABLE model_release (
    id UUID PRIMARY KEY,
    request_key VARCHAR(200) NOT NULL UNIQUE,
    dataset_version VARCHAR(200) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    expected_count INTEGER NOT NULL CHECK (expected_count > 0),
    base_revision BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('UPLOADING', 'READY', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE model_active_release (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    release_id UUID REFERENCES model_release(id),
    revision BIGINT NOT NULL DEFAULT 0
);
INSERT INTO model_active_release(singleton, revision) VALUES (1, 0);
CREATE TABLE model_upload_job (
    id UUID PRIMARY KEY,
    release_id UUID NOT NULL REFERENCES model_release(id),
    model_id VARCHAR(200) NOT NULL,
    metadata_json TEXT NOT NULL,
    source_path TEXT NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(200),
    UNIQUE(release_id, model_id)
);
CREATE INDEX model_upload_job_available ON model_upload_job(state, available_at, lease_until);
CREATE TABLE model_metadata (
    id UUID PRIMARY KEY,
    release_id UUID NOT NULL REFERENCES model_release(id),
    model_id VARCHAR(200) NOT NULL,
    pokedex_number INTEGER NOT NULL CHECK (pokedex_number > 0),
    metadata_json TEXT NOT NULL,
    UNIQUE(release_id, model_id)
);
CREATE INDEX model_metadata_pokedex ON model_metadata(release_id, pokedex_number);
