DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_users WHERE bcrypt_hash IS NULL) THEN
        RAISE EXCEPTION 'Cannot remove legacy password columns while users without BCrypt hashes exist';
    END IF;
END $$;

ALTER TABLE app_users DROP CONSTRAINT chk_app_users_password_format;
ALTER TABLE app_users DROP CONSTRAINT chk_app_users_hash_iterations;

ALTER TABLE app_users ALTER COLUMN bcrypt_hash SET NOT NULL;

ALTER TABLE app_users DROP COLUMN password_salt;
ALTER TABLE app_users DROP COLUMN password_hash;
ALTER TABLE app_users DROP COLUMN hash_iterations;
