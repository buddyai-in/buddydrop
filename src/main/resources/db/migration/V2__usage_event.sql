-- Per-user usage log for rate limiting uploads and downloads across rolling hour/day/month windows.
-- Public share downloads are recorded against the file owner. Rows are purged past the widest window.

create table usage_event (
    id         uuid                     primary key,
    user_id    uuid                     not null,
    kind       varchar(16)              not null,
    created_at timestamp with time zone not null,
    constraint fk_usage_event_user foreign key (user_id) references app_user (id) on delete cascade
);

create index ix_usage_event_lookup on usage_event (user_id, kind, created_at);
