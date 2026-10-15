-- V1 already marks application identity, names, card lists, and card details NOT NULL.
-- Require meaningful names and array-shaped JSONB without changing optional card fields.
alter table app_users
    add constraint app_users_email_not_blank check (btrim(email) <> ''),
    add constraint app_users_display_name_not_blank check (btrim(display_name) <> '');

alter table cards
    add constraint cards_name_not_blank check (btrim(card_name) <> ''),
    add constraint cards_foreign_data_array check (jsonb_typeof(foreign_data) = 'array'),
    add constraint cards_legalities_array check (jsonb_typeof(legalities) = 'array'),
    add constraint cards_purchase_urls_array check (jsonb_typeof(purchase_urls) = 'array'),
    add constraint cards_rulings_array check (jsonb_typeof(rulings) = 'array'),
    add constraint cards_mana_value_nonnegative check (converted_mana_cost >= 0);

alter table decks
    add constraint decks_name_not_blank check (btrim(name) <> '');
