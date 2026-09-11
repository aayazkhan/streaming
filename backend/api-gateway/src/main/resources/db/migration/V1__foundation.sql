CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(32) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    password_hash TEXT NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(200) NOT NULL,
    token_hash CHAR(43) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_id_idx ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS refresh_tokens_active_idx ON refresh_tokens(token_hash) WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(40) NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('STANDARD', 'KIDS')),
    language VARCHAR(16) NOT NULL,
    subtitle_language VARCHAR(16),
    avatar_key VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS profiles_user_id_idx ON profiles(user_id);

CREATE TABLE IF NOT EXISTS contents (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES contents(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL CHECK (type IN ('MOVIE', 'SERIES', 'SEASON', 'EPISODE', 'TRAILER', 'SHORT')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    access_tier VARCHAR(16) NOT NULL DEFAULT 'FREE' CHECK (access_tier IN ('FREE', 'PREMIUM')),
    title VARCHAR(300) NOT NULL,
    synopsis TEXT,
    poster_key VARCHAR(500),
    backdrop_key VARCHAR(500),
    release_year INTEGER,
    release_date DATE,
    duration_seconds INTEGER,
    season_number INTEGER,
    episode_number INTEGER,
    rating NUMERIC(3, 1),
    popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    trending_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    featured_rank INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS contents_public_created_idx ON contents(status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS contents_public_release_idx ON contents(status, release_date DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS contents_public_popular_idx ON contents(status, popularity_score DESC);
CREATE INDEX IF NOT EXISTS contents_parent_idx ON contents(parent_id);
CREATE INDEX IF NOT EXISTS contents_search_idx ON contents USING GIN (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(synopsis, '')));

CREATE TABLE IF NOT EXISTS genres (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS content_genres (
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (content_id, genre_id)
);

CREATE TABLE IF NOT EXISTS content_people (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    role VARCHAR(80) NOT NULL,
    character_name VARCHAR(200),
    billing_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS content_people_content_idx ON content_people(content_id, billing_order);

CREATE TABLE IF NOT EXISTS content_audio_tracks (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    language VARCHAR(16) NOT NULL,
    label VARCHAR(80) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS content_subtitles (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    language VARCHAR(16) NOT NULL,
    label VARCHAR(80) NOT NULL,
    uri VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS media_assets (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL UNIQUE REFERENCES contents(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('CREATED', 'UPLOADING', 'UPLOADED', 'VALIDATING', 'QUEUED', 'PROCESSING', 'PACKAGING', 'READY', 'FAILED', 'CANCELLED')),
    source_key VARCHAR(500) NOT NULL,
    hls_manifest_key VARCHAR(500),
    dash_manifest_key VARCHAR(500),
    thumbnail_key VARCHAR(500),
    preview_key VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE media_assets DROP CONSTRAINT IF EXISTS media_assets_status_check;
ALTER TABLE media_assets ADD CONSTRAINT media_assets_status_check CHECK (status IN ('CREATED', 'UPLOADING', 'UPLOADED', 'VALIDATING', 'QUEUED', 'PROCESSING', 'PACKAGING', 'READY', 'FAILED', 'CANCELLED'));
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS pipeline_stage VARCHAR(32);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS stage_progress_percent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS failure_code VARCHAR(200);

CREATE TABLE IF NOT EXISTS media_uploads (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    expected_size_bytes BIGINT NOT NULL CHECK (expected_size_bytes > 0),
    checksum_sha256 CHAR(64),
    status VARCHAR(16) NOT NULL CHECK (status IN ('REQUESTED', 'UPLOADING', 'UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    multipart_upload_id VARCHAR(300),
    part_size_bytes BIGINT
);

ALTER TABLE media_uploads ADD COLUMN IF NOT EXISTS multipart_upload_id VARCHAR(300);
ALTER TABLE media_uploads ADD COLUMN IF NOT EXISTS part_size_bytes BIGINT;

CREATE INDEX IF NOT EXISTS media_uploads_content_idx ON media_uploads(content_id, created_at DESC);

CREATE TABLE IF NOT EXISTS media_jobs (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    source_key VARCHAR(500) NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('ACCEPTED', 'UPLOADING', 'TRANSCODING', 'PACKAGING', 'SUBTITLES', 'THUMBNAILS', 'READY', 'FAILED', 'CANCELLED')),
    profiles JSONB NOT NULL,
    failure_code VARCHAR(200),
    hls_manifest_key VARCHAR(500),
    dash_manifest_key VARCHAR(500),
    poster_key VARCHAR(500),
    preview_key VARCHAR(500),
    subtitle_keys JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(300) NOT NULL UNIQUE,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    progress_percent INTEGER NOT NULL DEFAULT 0 CHECK (progress_percent BETWEEN 0 AND 100),
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    worker_id VARCHAR(200),
    heartbeat_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS media_jobs_claim_idx ON media_jobs(state, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS search_history (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query VARCHAR(120) NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, query)
);

CREATE INDEX IF NOT EXISTS search_history_user_idx ON search_history(user_id, searched_at DESC);

CREATE TABLE IF NOT EXISTS watchlist (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, content_id)
);

CREATE INDEX IF NOT EXISTS watchlist_user_idx ON watchlist(user_id, added_at DESC);

CREATE TABLE IF NOT EXISTS playback_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS playback_sessions_user_idx ON playback_sessions(user_id, started_at DESC);

CREATE TABLE IF NOT EXISTS watch_progress (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES playback_sessions(id) ON DELETE CASCADE,
    position_seconds BIGINT NOT NULL DEFAULT 0,
    duration_seconds BIGINT,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, profile_id, content_id)
);

CREATE INDEX IF NOT EXISTS watch_progress_user_idx ON watch_progress(user_id, profile_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS playback_events (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES playback_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    position_seconds BIGINT,
    quality VARCHAR(32),
    error_code VARCHAR(120),
    occurred_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS playback_events_session_idx ON playback_events(session_id, occurred_at);
