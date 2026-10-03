# Changelog

## 1.0.0 (unreleased)

### Added
- Supabase backend: schema with row-level security, sync triggers, realtime, sign-up seeding of the
  14 default categories, nightly net worth snapshots (pg_cron) and an on-demand snapshot RPC.
- Android app (Kotlin, Compose, Room): Home, Month (budget, transactions, category detail, closed
  months, new month), Year, Net worth (history chart, accounts, IOU ledger), Settings; Fold-aware
  layouts; offline-first sync; Glance widget; "Add expense" shortcut and `budget://add`; JSON backup.
- Web app (React + Vite PWA): the same screens, live updates, demo mode, JSON backup, and connector
  tokens for the AI assistant.
- `budget-mcp` remote MCP server (Supabase Edge Function) with 16 tools so Claude can add transactions,
  set budgets and actuals, close months and report summaries and net worth history.
- Brand kit (split-ledger "B" mark, adaptive/monochrome launcher icons, splash, favicon) and shared
  design tokens.
- Spreadsheet converter (`tools/xlsx_to_backup.py`) and a reference calculator that reproduces every
  Summary number of the original sheet.

### Changed
- Money of $1,000 or more is displayed in whole dollars.

### Removed
- Meal plans (the spreadsheet's Food tab is out of scope).

### Fixed
- Backup import no longer duplicates categories when run before the first load finishes; the database
  now rejects duplicate live category names.
