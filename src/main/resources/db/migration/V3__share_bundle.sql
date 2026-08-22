-- Multi-file share bundles: one public link that downloads several files as a single ZIP.

create table share_bundle (
    id             uuid                     primary key,
    owner_id       uuid                     not null,
    token_hash     varchar(64)              not null,
    expires_at     timestamp with time zone,
    max_downloads  integer,
    download_count integer                  not null,
    password_hash  varchar(100),
    created_at     timestamp with time zone not null,
    constraint uq_share_bundle_hash unique (token_hash),
    constraint fk_share_bundle_owner foreign key (owner_id) references app_user (id) on delete cascade
);
create index ix_share_bundle_hash on share_bundle (token_hash);

create table share_bundle_file (
    bundle_id uuid not null,
    file_id   uuid not null,
    primary key (bundle_id, file_id),
    constraint fk_sbf_bundle foreign key (bundle_id) references share_bundle (id) on delete cascade,
    constraint fk_sbf_file foreign key (file_id) references stored_file (id) on delete cascade
);
