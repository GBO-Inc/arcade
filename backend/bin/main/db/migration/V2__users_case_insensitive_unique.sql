-- "Alice" and "alice" are the same account. The V1 UNIQUE constraints are case-sensitive,
-- so these indexes enforce uniqueness on the lower-case value. Lookups use lower() too,
-- which lets them use these indexes.
CREATE UNIQUE INDEX users_username_lower_key ON users (LOWER(username));
CREATE UNIQUE INDEX users_email_lower_key ON users (LOWER(email));
