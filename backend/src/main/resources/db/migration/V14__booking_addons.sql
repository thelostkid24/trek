-- Add-ons a trekker can take per seat: insurance, bag offloading, transport. Prices are set per trek by an admin
-- (offloading already had one); a booking freezes the prices it was held at. The trek fee and the add-ons stay
-- apart on the booking so the guide share and the charity share are worked out on the trek fee only.
-- See docs/TRD.md §6 and §7.6.
ALTER TABLE tracks
    ADD COLUMN insurance_price_paise BIGINT CHECK (insurance_price_paise > 0),
    ADD COLUMN transport_price_paise BIGINT CHECK (transport_price_paise > 0);

ALTER TABLE bookings
    ADD COLUMN insurance_seats        INT    NOT NULL DEFAULT 0,
    ADD COLUMN insurance_price_paise  BIGINT CHECK (insurance_price_paise > 0),
    ADD COLUMN offloading_seats       INT    NOT NULL DEFAULT 0,
    ADD COLUMN offloading_price_paise BIGINT CHECK (offloading_price_paise > 0),
    ADD COLUMN transport_seats        INT    NOT NULL DEFAULT 0,
    ADD COLUMN transport_price_paise  BIGINT CHECK (transport_price_paise > 0),
    ADD COLUMN addons_paise           BIGINT NOT NULL DEFAULT 0;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_addon_seats CHECK (
        insurance_seats BETWEEN 0 AND seats
            AND offloading_seats BETWEEN 0 AND seats
            AND transport_seats BETWEEN 0 AND seats),
    ADD CONSTRAINT bookings_addon_prices CHECK (
        (insurance_seats = 0 OR insurance_price_paise IS NOT NULL)
            AND (offloading_seats = 0 OR offloading_price_paise IS NOT NULL)
            AND (transport_seats = 0 OR transport_price_paise IS NOT NULL)),
    ADD CONSTRAINT bookings_addons_total CHECK (addons_paise =
        insurance_seats * COALESCE(insurance_price_paise, 0)
            + offloading_seats * COALESCE(offloading_price_paise, 0)
            + transport_seats * COALESCE(transport_price_paise, 0));

-- The amount charged is the trek fee plus the add-ons.
ALTER TABLE bookings DROP CONSTRAINT bookings_amount;
ALTER TABLE bookings ADD CONSTRAINT bookings_amount CHECK (amount_paise = price_paise_per_seat * seats + addons_paise);
