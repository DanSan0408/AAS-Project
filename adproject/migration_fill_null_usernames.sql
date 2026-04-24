-- Optional data backfill for legacy rows with NULL usernames.
-- Review in staging first, then run in production during a maintenance window.

UPDATE student
SET username = SUBSTRING_INDEX(email, '@', 1)
WHERE username IS NULL;

UPDATE lecturer
SET username = SUBSTRING_INDEX(email, '@', 1)
WHERE username IS NULL;
