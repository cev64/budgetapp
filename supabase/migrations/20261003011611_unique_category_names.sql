-- A user can never have two live categories with the same name (case/whitespace-insensitive).
-- Backup import, the MCP server and the add-transaction pickers all match categories by name,
-- so duplicates would split a month's numbers across two rows. Tombstoned rows are exempt.
create unique index categories_user_name_live_uniq
  on public.categories (user_id, lower(btrim(name)))
  where not deleted;
