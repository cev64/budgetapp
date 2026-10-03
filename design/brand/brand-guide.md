# Budget — compact brand guide

A bright, quiet surface where everything answers you.

## Identity

The **split ledger B** pairs two geometric bands: expected above, actual below. The larger lower bowl gives a subtle forward movement without implying guaranteed financial growth. Two counters and a 2-unit gap preserve the identity in a single colour. The mark is a fixed identity, never a live progress indicator.

Three short naming alternatives: **Tally**, **Month**, **Sum**. These are creative suggestions; name, domain, and trademark availability have not been checked. Every delivered asset uses Budget.

## Logo usage

The mark uses a 24×24 viewBox. Visible bounds: x=4–20.5, y=2–22; visible height H=20 units. Two filled paths, no strokes, filters, text, or raster data. Rounded outer bowls contrast with the precise flat ledger edges.

- Clear space: **0.25H** on every side of the visible mark or complete lockup. Internal SVG whitespace does not satisfy this requirement by itself. At a 24px mark canvas, add 5px around the visible shape.
- Minimum mark canvas: **16px**. Preferred standalone UI size: 24–32px. At 16px, the split is 1.33px and counters are 2px tall. Do not further thin either feature.
- Minimum horizontal lockup: **109px wide**, equivalent to a wordmark size of approximately 22.4px. Preferred web header: 136×36px. Keep the viewBox aspect ratio.
- Light: navy upper band / blue lower band. Dark: near-white upper band / lighter blue lower band, as in the dark lockup. In constrained single-colour contexts, tint the mono asset to an appropriate foreground token.
- App tile: white mark on #1059FC; retain a full square background. The operating system applies its mask.
- Do use supplied path geometry, uniform scaling, accessible names (e.g. “Budget home”), and sufficient contrast.
- Do not stretch, rotate, redraw the bowls, close the split, use shadows or gradients inside the logo, add outlines, use the category palette for the brand, or place it on busy imagery.

Wordmark: **Inter**, weight **600**, optical size **32**, title case, **−0.02em** letter spacing. Master font size 28px and baseline y=28 on a 136×36 canvas. The supplied lockups are outlined from Inter, so there is no runtime font dependency and spacing is already baked into the paths. Never apply CSS letter spacing to the outlined assets.

## Colour

Core light colours are retained. Dark text/status colours are purpose-built counterparts. “Hex” below is the light value; the adjacent dark value is its exact replacement.

| Name | Hex / light | Dark | Role |
|---|---|---|---|
| ink | #08204F | #F5F8FF | Primary text |
| ink-2 | #415373 | #C2CEE2 | Secondary text |
| ink-3 | #63718A | #91A2BF | Metadata / micro-labels |
| surface | #FFFFFF | #0A1122 | Page / card surface |
| surface-2 | #F6F8FC | #131F35 | Recessed surface |
| line | #DCE3EE | #2A3A55 | Decorative dividers only |
| accent | #1059FC | #4A82FF | Action / active state |
| accent-ink | #FFFFFF | #0A1122 | Text on accent fills |
| accent-soft | #EDF3FF | #172B52 | Selected / quiet accent surface |
| good | #15803D | #4ADE80 | Positive status |
| bad | #DC2626 | #FF8585 | Negative status |

Pressed accent: light **#0A45CC**, dark **#7BA3FF**. White on the light pressed colour is 7.68:1 (calculate from exact tokens if changing it). Use `accent-ink` for button labels: white in light mode, navy in dark mode. White on #4A82FF is insufficient for normal-sized text; do not keep white button labels when switching to the dark accent.

Contrast uses WCAG relative luminance against the listed opaque page surface. Values are rounded to two decimals; transparency changes the effective result.

| Text token | On #FFFFFF | Dark token on #0A1122 |
|---|---|---|
| ink | 15.79:1 | 17.70:1 |
| ink-2 | 7.75:1 | 11.84:1 |
| ink-3 | 4.93:1 | 7.28:1 |
| accent | 5.45:1 | 5.31:1 |
| good | 5.02:1 | 10.80:1 |
| bad | 4.83:1 | 8.01:1 |

Accent-ink on accent: **5.45:1** light, **5.31:1** dark. All listed primary, secondary, metadata, action, and status text pairs exceed 4.5:1 on their page surfaces. Decorative `line` is not a focus ring or required control boundary: use ink-3 for those boundaries and accent for focus, with a 2px ring and 2px offset. Include text or an icon for every status.

### Categories

The same ten colours work in both themes for charts, meaningful swatches, and graphical objects: each exceeds 3:1 on both specified surfaces. **They are not a ten-colour text palette**; some fail 4.5:1 on white. Use ink for labels and accessible text names. No ten-colour palette alone can promise distinguishability for every colour-vision deficiency: the paired symbol is mandatory in legends and corresponding series. Prefer labelled rows or bars over a ten-slice pie. For line charts, add symbols at data points, direct labels, and distinct dash patterns; do not rely on red/green to encode good/bad.

| Category | Hex, both themes | Symbol | On white | On navy |
|---|---|---|---|---|
| housing | #2978C9 | circle | 4.54:1 | 4.14:1 |
| food | #C96523 | square | 3.91:1 | 4.81:1 |
| transport | #178879 | triangle | 4.34:1 | 4.33:1 |
| fun | #A65DA8 | diamond | 4.42:1 | 4.26:1 |
| subscriptions | #A87914 | plus | 3.88:1 | 4.85:1 |
| health | #C65172 | cross | 4.36:1 | 4.32:1 |
| roth | #6B75C6 | ring | 4.21:1 | 4.47:1 |
| 401k | #648631 | square-ring | 4.20:1 | 4.48:1 |
| brokerage | #B46B51 | triangle-ring | 4.07:1 | 4.63:1 |
| hsa | #778190 | diamond-ring | 3.94:1 | 4.77:1 |

Assignments are stable across months. Add labels/tooltips and screen-reader descriptions with category, value, and unit. Small symbols should be at least 12px; symbol outlines use the category colour at 1.75px. For adjacent regions without white/navy between them, add a 2px surface-coloured separator: the background contrast figures do not assert contrast between two category colours. In Android, build equivalents with Canvas; on web, render SVG symbols.

## Typography

Inter is the UI family. Barlow Condensed is reserved for uppercase display headings. Bundle licensed font files in both apps; do not depend on network font loading. Fallbacks: Inter → system sans-serif; Barlow Condensed → condensed system sans-serif. Font licenses must accompany redistributed fonts. This package outlines the logo and does not redistribute font binaries.

All sizes below are px on web, sp on Android; line heights are px/sp and letter spacing is px at the stated size. Android Compose letterSpacing can use `(tracking/size).em`. Use the same weight values on both platforms.

| Role | Family | Weight | Size / line height | Tracking |
|---|---|---|---|---|
| display | Barlow Condensed | 600 | 40/44 | +0.80 |
| title | Inter | 600 | 24/30 | -0.48 |
| body | Inter | 400 | 16/24 | +0.00 |
| numbers | Inter | 600 | 32/40 | -0.32 |
| micro | Inter | 600 | 12/16 | +0.72 |
| wordmark | Inter | 600 | 28/36 | -0.56 |

- Display and micro roles: uppercase; title and body: sentence case. Use body 500 for controls and selected navigation. Body text stays at 16; 12 is for short micro-labels only.
- Numbers: enable tabular, lining numerals (`font-feature-settings: "tnum" 1, "lnum" 1`; Compose `fontFeatureSettings = "tnum, lnum"`). Right-align comparable currency columns. Display negatives as “−$42.00”; do not communicate sign only with colour.
- Inter numeric role is 32/40 for hero totals; use 16/24 at weight 500 for table values with the same features. Currency symbols and decimals share the numeric baseline.
- Respect Android font scaling and browser zoom; allow wrapping. Cover-screen text must not shrink below these values to fit the inner-screen layout. Switch layout based on available width, keeping the tokens unchanged.

## Surfaces and iconography

Radius: cards 12, controls 8, sheets 24, floating bars 24, pills 999 (clamped to half the rendered height); px/web and dp/Android. A card is a solid surface first. Floating bars may use 88% surface over a backdrop with 20px blur on web, provided contrast is checked on the composited result. On Android, use an opaque surface fallback when blur is unavailable. Maintain navy ink and a single saturated blue action accent.

Lucide-style icons: 24×24 design grid, **1.75px/dp** stroke at 24px/dp, round line caps and joins, no mixed filled icons. In web set `strokeWidth={1.75}`. The brand mark is filled geometry and is intentionally distinct from navigation icon strokes. Icon controls retain at least 48dp Android / 44px web hit targets even when their visible icon is 24.

## Voice and tone

Short, factual, calm. Describe what happened and the useful next step. No celebration of spending, blame, scare language, investment promises, or forced cheerfulness.

| Situation | Exact UI string |
|---|---|
| Empty state | Set your first category to start this month’s budget. |
| Success toast | Expense saved. Your budget is up to date. |
| Over-budget warning | Food is $42 over budget. Review your recent expenses. |
| Month closed | September is closed. Your totals are saved. |
| Sign-in headline | A clear view of your money. |

Only show “saved” or “closed” after persistence succeeds; the strings specify product intent, not a new backend behaviour.

## Engineering handoff

- SVG files use fills only; the standalone logo has two even-odd paths. React can use `<img>` or inline SVG; if inline JSX, convert `fill-rule` to `fillRule`. Provide alt text; if a visible adjacent label repeats the brand, mark the image decorative.
- Android does not consume these SVGs directly as adaptive icon resources. Bonus VectorDrawable XML equivalents are provided. Put the three layer XML files in `res/drawable`. Put an adaptive icon XML in `res/mipmap-anydpi-v26` referencing background and foreground; add the monochrome reference in the v33 resource. A v26 resource omits the monochrome element. Build legacy mipmaps with Android Studio Image Asset from the supplied square icon. These are source assets, not a compiled Android resource bundle.
- Adaptive foreground visible bounds: **x=31.725–76.275, y=27–81**, strictly inside [21,87]². The monochrome file shares that geometry, has black fills and a transparent background, and is meant for system tinting; no background shape belongs in its layer.
- Web PNGs: 1024×1024 and 512×512, full opaque blue square, no baked corners. In both, mark bounds are x=29.375%–70.625%, y=25%–75%. Farthest bounding-box corner is **32.41%** of canvas width from centre, within the maskable 40%-radius safe circle. Declare the 512 image with `purpose: "maskable"`; the 1024 square with `purpose: "any"`.
- Splash: transparent 288 canvas; visible bounds x=87.9–200.1, y=76–212. Farthest bounding-box corner is **88.16** from centre, within the 96-radius safe circle. Use a themed surface behind it. Android 12+ uses a drawable resource; convert/import the paths to VectorDrawable. This asset is for the 288dp no-icon-background configuration; if using the 240dp icon-with-background setup, rescale using that setup’s safe circle.
- Favicon: blue square with white split B, 24-unit viewBox rendered at 16px. Keep its 2-unit split; never use the detailed wordmark as a favicon.
- Preview is a contextual mockup. Geometry, tokens, SVGs, XMLs, and PNGs are the engineering source of truth.

## Tokens

`tokens.json` is the machine-readable master. Category order matches the table. `size`, `lineHeight`, and `letterSpacing` are numeric px/sp values; radius values are px/dp. Both surface and surface-2 are opaque hex colours. The optional glass treatment is an implementation recipe, not a replacement for these tokens.

```json
{
  "color": {
    "light": {
      "ink": "#08204F",
      "ink-2": "#415373",
      "ink-3": "#63718A",
      "surface": "#FFFFFF",
      "surface-2": "#F6F8FC",
      "line": "#DCE3EE",
      "accent": "#1059FC",
      "accent-ink": "#FFFFFF",
      "accent-soft": "#EDF3FF",
      "good": "#15803D",
      "bad": "#DC2626"
    },
    "dark": {
      "ink": "#F5F8FF",
      "ink-2": "#C2CEE2",
      "ink-3": "#91A2BF",
      "surface": "#0A1122",
      "surface-2": "#131F35",
      "line": "#2A3A55",
      "accent": "#4A82FF",
      "accent-ink": "#0A1122",
      "accent-soft": "#172B52",
      "good": "#4ADE80",
      "bad": "#FF8585"
    },
    "categoryPalette": [
      {
        "id": "housing",
        "hex": "#2978C9",
        "symbol": "circle"
      },
      {
        "id": "food",
        "hex": "#C96523",
        "symbol": "square"
      },
      {
        "id": "transport",
        "hex": "#178879",
        "symbol": "triangle"
      },
      {
        "id": "fun",
        "hex": "#A65DA8",
        "symbol": "diamond"
      },
      {
        "id": "subscriptions",
        "hex": "#A87914",
        "symbol": "plus"
      },
      {
        "id": "health",
        "hex": "#C65172",
        "symbol": "cross"
      },
      {
        "id": "roth",
        "hex": "#6B75C6",
        "symbol": "ring"
      },
      {
        "id": "401k",
        "hex": "#648631",
        "symbol": "square-ring"
      },
      {
        "id": "brokerage",
        "hex": "#B46B51",
        "symbol": "triangle-ring"
      },
      {
        "id": "hsa",
        "hex": "#778190",
        "symbol": "diamond-ring"
      }
    ],
    "accentPressed": {
      "light": "#0A45CC",
      "dark": "#7BA3FF"
    }
  },
  "type": {
    "display": {
      "family": "Barlow Condensed",
      "weight": 600,
      "size": 40,
      "lineHeight": 44,
      "letterSpacing": 0.8,
      "case": "uppercase"
    },
    "title": {
      "family": "Inter",
      "weight": 600,
      "size": 24,
      "lineHeight": 30,
      "letterSpacing": -0.48
    },
    "body": {
      "family": "Inter",
      "weight": 400,
      "size": 16,
      "lineHeight": 24,
      "letterSpacing": 0
    },
    "numbers": {
      "family": "Inter",
      "weight": 600,
      "size": 32,
      "lineHeight": 40,
      "letterSpacing": -0.32,
      "fontFeatureSettings": "\"tnum\" 1, \"lnum\" 1"
    },
    "micro": {
      "family": "Inter",
      "weight": 600,
      "size": 12,
      "lineHeight": 16,
      "letterSpacing": 0.72,
      "case": "uppercase"
    },
    "wordmark": {
      "family": "Inter",
      "opticalSize": 32,
      "weight": 600,
      "size": 28,
      "letterSpacing": -0.56
    }
  },
  "radius": {
    "card": 12,
    "control": 8,
    "sheet": 24,
    "floatingBar": 24,
    "pill": 999
  }
}
```
