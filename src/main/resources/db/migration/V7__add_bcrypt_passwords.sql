ALTER TABLE app_users ADD COLUMN bcrypt_hash VARCHAR(60);

ALTER TABLE app_users ALTER COLUMN password_salt DROP NOT NULL;
ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE app_users ALTER COLUMN hash_iterations DROP NOT NULL;

ALTER TABLE app_users ADD CONSTRAINT chk_app_users_password_format CHECK (
    (bcrypt_hash IS NOT NULL
        AND password_salt IS NULL
        AND password_hash IS NULL
        AND hash_iterations IS NULL)
    OR
    (bcrypt_hash IS NULL
        AND password_salt IS NOT NULL
        AND password_hash IS NOT NULL
        AND hash_iterations IS NOT NULL)
);
