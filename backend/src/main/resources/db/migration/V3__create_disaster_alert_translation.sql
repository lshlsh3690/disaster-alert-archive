CREATE TABLE IF NOT EXISTS disaster_alert_translation (
    disaster_alert_id        BIGINT       NOT NULL,
    language_code            VARCHAR(10)  NOT NULL,
    translated_message       TEXT         NOT NULL,
    translated_disaster_type VARCHAR(255),
    translated_region_names  TEXT,
    translated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT pk_disaster_alert_translation
        PRIMARY KEY (disaster_alert_id, language_code),
    CONSTRAINT fk_dat_disaster_alert
        FOREIGN KEY (disaster_alert_id) REFERENCES disaster_alert (disaster_alert_id)
);
