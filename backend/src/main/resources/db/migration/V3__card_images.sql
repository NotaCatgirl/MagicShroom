-- Scryfall image metadata is separate from the unchanged MTGJSON card archive.
create table card_images (
    oracle_id uuid primary key,
    scryfall_card_id uuid not null,
    set_code text not null,
    faces jsonb not null check (jsonb_typeof(faces) = 'array'),
    synced_at timestamptz not null default now()
);

alter table card_images enable row level security;
revoke all on card_images from anon, authenticated;
