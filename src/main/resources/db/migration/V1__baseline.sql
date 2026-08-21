-- BuddyDrop baseline schema.
-- Types chosen to validate identically on PostgreSQL (production) and H2 (dev/test):
-- uuid, bigint/integer, varchar(n), and timestamp with time zone (Instant / TIMESTAMP_UTC).

create table app_user (
    id            uuid                     primary key,
    email         varchar(320)             not null,
    quota_bytes   bigint                   not null,
    created_at    timestamp with time zone not null,
    last_login_at timestamp with time zone,
    constraint uq_app_user_email unique (email)
);

create table magic_token (
    id          uuid                     primary key,
    user_id     uuid                     not null,
    token_hash  varchar(64)              not null,
    expires_at  timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at  timestamp with time zone not null,
    constraint uq_magic_token_hash unique (token_hash),
    constraint fk_magic_token_user foreign key (user_id) references app_user (id) on delete cascade
);
create index ix_magic_token_hash on magic_token (token_hash);

create table stored_file (
    id            uuid                     primary key,
    owner_id      uuid                     not null,
    s3_key        varchar(1024)            not null,
    original_name varchar(1024)            not null,
    content_type  varchar(255),
    size_bytes    bigint                   not null,
    status        varchar(16)              not null,
    created_at    timestamp with time zone not null,
    constraint uq_stored_file_key unique (s3_key),
    constraint fk_stored_file_owner foreign key (owner_id) references app_user (id) on delete cascade
);
create index ix_stored_file_owner on stored_file (owner_id);

create table share_link (
    id             uuid                     primary key,
    file_id        uuid                     not null,
    token_hash     varchar(64)              not null,
    expires_at     timestamp with time zone,
    max_downloads  integer,
    download_count integer                  not null,
    password_hash  varchar(100),
    created_at     timestamp with time zone not null,
    constraint uq_share_link_file unique (file_id),
    constraint uq_share_link_hash unique (token_hash),
    constraint fk_share_link_file foreign key (file_id) references stored_file (id) on delete cascade
);
create index ix_share_link_hash on share_link (token_hash);
