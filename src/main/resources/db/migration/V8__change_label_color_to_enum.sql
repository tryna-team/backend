UPDATE labels
SET color = 'GREEN';

ALTER TABLE labels
ALTER COLUMN color TYPE VARCHAR(20);

ALTER TABLE labels
    ALTER COLUMN color SET DEFAULT 'GREEN';

ALTER TABLE labels
    ADD CONSTRAINT ck_labels_color
        CHECK (
            color IN (
                      'GREEN',
                      'BLUE',
                      'APRICOT',
                      'PINK',
                      'YELLOW',
                      'PURPLE'
                )
            );