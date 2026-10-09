-- Synthetic demo catalog for the V1 portfolio project: one product, one
-- configuration and its first recipe revision. Fixed ids keep the examples
-- in backend/api.http stable. Price: INR 499.00 = 49900 paise.

INSERT INTO product (id, name)
VALUES ('0d3d0000-0000-4000-8000-000000000001', 'Hex Desk Organizer (demo)');

INSERT INTO product_configuration (id, product_id, code, description, price_minor, currency)
VALUES ('0d3d0000-0000-4000-8000-000000000002',
        '0d3d0000-0000-4000-8000-000000000001',
        'HEX-ORG-PLA-BLACK',
        'Matte black PLA+, 0.20 mm layers, single plate',
        49900,
        'INR');

INSERT INTO recipe_revision (id, product_configuration_id, revision_number,
                             material, layer_height_microns, notes)
VALUES ('0d3d0000-0000-4000-8000-000000000003',
        '0d3d0000-0000-4000-8000-000000000002',
        1,
        'Numakers PLA+ Matte Black',
        200,
        'Synthetic demo recipe. One part, one plate, no supports.');

UPDATE product_configuration
SET current_recipe_revision_id = '0d3d0000-0000-4000-8000-000000000003'
WHERE id = '0d3d0000-0000-4000-8000-000000000002';
