ALTER TABLE auths ADD COLUMN oauth_refresh_token VARCHAR(512);
ALTER TABLE auths ADD COLUMN granted_scopes VARCHAR(1000);