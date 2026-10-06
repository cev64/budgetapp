# Changelog

## 1.3.0 — Fluid glass v2

### Changed
- Web: every screen sits on a soft ambient backdrop (three slow-drifting blobs, static with reduced
  motion); cards, tiles, sheets, menus, tooltips, the nav and the collapsed top bar are frosted glass
  (blur + saturation, hairline highlight, soft navy shadow) with an opaque fallback. Spec values in
  `docs/FLUID_GLASS_UI.md` (now v2).
- Web navigation: under 600px a detached floating glass pill nav with a gliding indicator and the round
  Add beside it; a floating glass rail / side nav on wider windows. Each screen has a large title that
  scrolls under a 52px top bar, which then turns to glass with a compact title.
- Web controls: segmented controls (month tabs, net worth ranges, years, theme, Sheet tabs) with a
  raised thumb that slides on a soft spring; the month picker's selection glides across the grid;
  springy switches that stretch while pressed; pill chips; quiet fill buttons and inputs with a focus
  ring; press-scale on everything tappable.
- Web interactions: swipe a transaction left to delete it (arms with a haptic tick, Undo toast);
  deleting from the transaction sheet also shows Undo instead of a confirm; drag a phone sheet down to
  dismiss it; the net worth line and sparklines draw in, the Year bars grow in and both charts scrub
  with a glass tooltip; shorter number rolls and list animations.
- Web cleanup: no dividers inside cards (totals sit on a quiet band), selection is a raised thumb
  instead of a blue tint, transaction rows are item over "● Category · date" instead of a pill, no
  captions under toggles ("Closed: summary uses actuals", "counts toward liquid assets" and the tracking
  hint are gone), ghost buttons for secondary calls to action. Text stays WCAG AA on the composited
  glass in both themes. The Sheet grid stays dense and opaque; only its chrome is glass. Version 1.3.0.
- Android: ambient backdrop (three slow-drifting soft blobs, static with "Remove animations"); cards,
  tiles, sheet, menus and tooltips on translucent glass with a hairline highlight and soft shadow; real
  backdrop blur (Haze, Android 12+) behind the floating nav, the rail, the collapsed top bar and the Add
  sheet; opaque glass fallback below Android 12.
- Android navigation: a floating glass pill bar with a sliding indicator and the round Add beside it on
  the cover screen; a floating glass rail (76dp, 220dp with the lockup on wide windows) with a sliding
  indicator on the inner screen. The top bar is transparent over a large title and turns to glass with
  a compact title once you scroll.
- Android controls: segmented controls with a raised sliding thumb (spring), springy switches that
  stretch while pressed, pill chips, quiet fill buttons and inputs with a focus ring, press-scale on
  everything tappable, shorter number rolls.
- Android interactions: swipe a transaction left to delete it (haptic tick when armed, Undo toast);
  drag the Add sheet down to dismiss it; the net-worth chart draws in, then scrubs with a glass tooltip
  and haptic ticks; the Year bar chart now scrubs month by month too.
- Android cleanup: no dividers inside cards, no captions under toggles, one quiet meta line instead of
  badge rows (accounts, category detail), selection shown as a soft fill instead of blue. The Sheet
  grid stays a dense opaque card; only its title and tab bar are glass. Version 1.3.0.

## 1.2.0

### Added
- **Sheet view**: a fifth tab that lays the budget out exactly like the original Google Sheet (Summary +
  month tabs, the same rows and ledger columns), editable where the sheet had typed values. On the folded
  phone it asks you to unfold and switches to the sheet in place when you do; on the web it needs a window
  at least 600px wide. Spec: `docs/SHEET_VIEW.md`.

## 1.1.0

### Changed
- Year page: the headline is now **leftover vs plan** (signed, green/red, with projected and planned
  leftover, a progress bar and a collapsed "By month" dropdown whose months add up to the year figure). Annualized savings moved
  to a smaller Savings rate card below the table. Same on web and Android; Claude's year summary leads
  with it too.

## 1.0.0

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
