create table clinic (
    id bigint generated always as identity primary key,
    code varchar(30) not null unique,
    name varchar(120) not null unique,
    specialty varchar(60) not null
);

create table patient (
    id bigint generated always as identity primary key,
    mrn varchar(20) not null unique,
    first_name varchar(80) not null,
    last_name varchar(80) not null,
    date_of_birth date not null
);

create table referral (
    id bigint generated always as identity primary key,
    patient_id bigint not null references patient(id),
    clinic_id bigint not null references clinic(id),
    referring_gp varchar(120) not null,
    external_ref varchar(64) unique,
    source text not null check (source in ('JSON', 'XML', 'MANUAL')),
    clinical_notes text not null,
    raw_message jsonb,
    status text not null check (status in ('RECEIVED', 'AI_SUGGESTED', 'TRIAGED', 'BOOKED', 'REJECTED')),
    ai_summary text,
    suggested_priority text check (suggested_priority in ('URGENT', 'SOON', 'ROUTINE')),
    suggestion_source text check (suggestion_source in ('AI', 'RULE_BASED_FALLBACK')),
    suggestion_confidence numeric(3,2) check (suggestion_confidence between 0 and 1),
    prompt_version varchar(20),
    red_flags jsonb,
    confirmed_priority text check (confirmed_priority in ('URGENT', 'SOON', 'ROUTINE')),
    priority_rank smallint generated always as (
        case confirmed_priority
            when 'URGENT' then 1
            when 'SOON' then 2
            when 'ROUTINE' then 3
        end
    ) stored,
    triage_note text,
    triaged_by varchar(60),
    triaged_at timestamptz,
    received_at timestamptz not null,
    version bigint not null default 0
);

create index referral_waiting_list_idx on referral (clinic_id, priority_rank, received_at)
    where status = 'TRIAGED';
create index referral_status_idx on referral (status);
create index referral_patient_id_idx on referral (patient_id);
create index referral_clinic_id_idx on referral (clinic_id);

create table appointment (
    id bigint generated always as identity primary key,
    referral_id bigint not null unique references referral(id),
    starts_at timestamptz not null,
    status text not null check (status in ('BOOKED', 'CANCELLED'))
);

create table dead_letter_message (
    id bigint generated always as identity primary key,
    source text not null check (source in ('JSON', 'XML')),
    payload text not null,
    error text not null,
    received_at timestamptz not null
);

create index dead_letter_message_received_at_idx on dead_letter_message (received_at desc);
