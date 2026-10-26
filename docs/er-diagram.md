# Entity-relationship diagram

The MagicShroom database schema, as created by the Flyway migrations in
[`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration)
(`V1__application_schema.sql`, `V2__required_value_constraints.sql`, `V3__card_images.sql`).
The database is Supabase PostgreSQL; `auth.users` is managed by Supabase Auth, so only its `id` is shown.

```mermaid
erDiagram
    "auth.users" ||..o| app_users : authenticates
    app_users ||..o{ decks : owns
    decks ||--o{ deck_cards : contains
    cards ||--o{ deck_cards : included_in
    card_images |o..o{ cards : "matches scryfall_oracle_id (no FK)"

    "auth.users" {
        uuid id PK
    }
    app_users {
        bigint id PK
        uuid auth_user_id FK, UK
        text email
        text display_name
        text role "USER or ADMIN"
    }
    decks {
        bigint id PK
        bigint user_id FK
        varchar(100) name
    }
    deck_cards {
        bigint deck_id PK, FK
        bigint card_id PK, FK
        integer quantity "Greater than zero"
        text section
    }
    cards {
        bigint id PK
        uuid uuid UK
        text card_name
        text[] color_identity
        text[] colors
        float8 converted_mana_cost
        text mana_cost
        uuid scryfall_oracle_id
        text card_text
        text[] types
        text[] subtypes
        text[] supertypes
        text[] printings
        jsonb foreign_data
        jsonb legalities
        jsonb purchase_urls
        jsonb rulings
        text[] color_indicator
        integer edhrec_rank
        float8 face_converted_mana_cost
        text hand
        boolean has_no_deck_limit
        boolean is_reserved
        text layout
        jsonb leadership_skills
        text life
        text loyalty
        integer mtg_arena_id
        integer mtgo_foil_id
        integer mtgo_id
        text[] names
        text power
        text side
        text toughness
        text card_type
    }
    card_images {
        uuid oracle_id PK
        uuid scryfall_card_id
        text set_code
        jsonb faces
        timestamptz synced_at
    }
```

## Relationships and delete rules

| Relationship | Cardinality | On delete |
|---|---|---|
| `auth.users` → `app_users` | A Supabase login has at most one application user (`auth_user_id` is unique). | Deleting the login **cascades** to the application user. |
| `app_users` → `decks` | A user owns zero or more decks. | Deleting a user **cascades** to their decks. |
| `decks` → `deck_cards` | A deck contains zero or more card entries. | Deleting a deck **cascades** to its entries. |
| `cards` → `deck_cards` | A card can appear in many decks; `(deck_id, card_id)` is the primary key, so once per deck. | **Restricted**: a card used by a deck cannot be deleted (the API answers `409 Conflict`). |
| `card_images` ↔ `cards` | Matched on `scryfall_oracle_id`; no foreign key, because image metadata is imported separately. | Independent. |

## Constraints worth knowing

- `app_users.role` is `USER` or `ADMIN`; new users start as `USER`.
- `deck_cards.quantity` must be greater than zero; `section` is one of `MAINBOARD`, `SIDEBOARD`, `COMMANDER`, `MAYBEBOARD`.
- V2 rejects blank user, card, and deck names and JSONB card details that are not arrays.
- The `anon` and `authenticated` Supabase roles have no direct access to these tables; only the API's
  server-side connection reads and writes them.
