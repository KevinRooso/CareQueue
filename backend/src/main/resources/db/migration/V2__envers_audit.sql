create table revinfo (
    rev bigint generated always as identity primary key,
    revtstmp timestamptz not null,
    username varchar(60) not null
);

create table referral_aud (
    id bigint not null,
    rev bigint not null references revinfo(rev),
    revtype smallint,
    patient_id bigint,
    clinic_id bigint,
    referring_gp varchar(120),
    external_ref varchar(64),
    source text,
    status text,
    ai_summary text,
    suggested_priority text,
    suggestion_source text,
    suggestion_confidence numeric(3,2),
    prompt_version varchar(20),
    red_flags jsonb,
    confirmed_priority text,
    triage_note text,
    triaged_by varchar(60),
    triaged_at timestamptz,
    received_at timestamptz,
    primary key (id, rev)
);

create index referral_aud_rev_idx on referral_aud (rev);

create table appointment_aud (
    id bigint not null,
    rev bigint not null references revinfo(rev),
    revtype smallint,
    referral_id bigint,
    starts_at timestamptz,
    status text,
    primary key (id, rev)
);

create index appointment_aud_rev_idx on appointment_aud (rev);
