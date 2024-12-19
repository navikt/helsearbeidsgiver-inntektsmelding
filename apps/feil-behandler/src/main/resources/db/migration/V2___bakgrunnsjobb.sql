create table bakgrunnsjobb
(
    jobb_id      uuid unique not null,
    type         text        not null,
    behandlet    timestamp,
    opprettet    timestamp   not null,

    status       text        not null,
    kjoeretid    timestamp   not null,

    forsoek      int         not null default 0,
    maks_forsoek int         not null,
    data         jsonb
);
