# Fluid Glass UI: Style and Motion Guide (v2)

A drop-in guide for building interfaces that feel **calm, tactile, glassy and fluid**. It is framework-agnostic: plain CSS plus small vanilla-JS helpers that work in React, Vue, Svelte or plain HTML.

**Fluid glass v2** (Budget 1.3.0) replaces the v1 look (white page, glass only on floating layers, accent-soft selection): content now floats on frosted glass over a soft ambient field, controls are quieter fills, selection is a raised thumb, and everything you touch answers with a short spring. The sibling app Bets uses the same language (`docs/FLUID_GLASS_UI.md` in that repo). Brand tokens (`design/tokens.json`), Inter + Barlow Condensed, Lucide 1.75, the category palette and the voice are unchanged. Web is the reference implementation (`web/src/styles/*.css`, `web/src/ui/`); Android matches these values.

> **The feel in one sentence:** a calm, bright field where content floats on frosted glass, and everything you touch answers with a short spring. Selections *glide*, new things *settle in*, moved things *travel from where they were*, and nothing blinks, jumps or snaps.

**How to use this file:** read §1–§3 once. Then copy §4 (tokens), §5 (base CSS) and §6 (motion kit) into the project. Build components from §7–§9. Before shipping, check §10 (pitfalls) and §11 (checklist).

---

## 1. Principles (v2)

1. **Content floats on glass, the page is a soft field.** Cards, tiles, sheets, menus, the nav and the condensed top bar are translucent glass over an ambient backdrop. Nothing sits on flat white.
2. **Separate with space and tone, not lines.** No hard borders or dividers inside cards: a 1px hairline highlight and a diffuse shadow define each surface, and a quiet `--fill` band (not a rule) sets off totals.
3. **One accent per view.** The Add button (and the primary Save) is the accent. Selection is a raised glass thumb in ink, never a blue fill, stripe or bar. Links are ink-2. Secondary calls to action ("Start October") are ghost buttons.
4. **Fewer words.** Rows show what you scan for (item, category, date, amount); details live in the opened sheet. No explanatory captions under toggles, no badge clutter.
5. **Springy, short, interruptible.** 150–350 ms for interactions; springs for anything that arrives or follows a finger. Every animation can be interrupted and is off with reduced motion.
6. **Continuity over teleporting.** If something changes place, animate it from where it was (FLIP, gliding thumbs, rolling numbers).
7. **Colour is never the only signal.** Money keeps its sign (`−$330`), over-budget keeps its icon and words, category symbols differ in shape as well as colour.
8. **Nothing jumps.** Reserve space, keep the scrollbar gutter stable, never animate on first paint.

---

## 2. Curves and durations

```css
--ease:        cubic-bezier(.22, 1, .36, 1);   /* movement, fades, exits */
--spring:      cubic-bezier(.34, 1.4, .64, 1); /* arrivals, pops (sheets, toasts, menus) */
--spring-soft: cubic-bezier(.3, 1.25, .5, 1);  /* thumbs and indicators that slide (seg, nav, switch, month picker) */
```
In JavaScript (for the Web Animations API): `const EASE = 'cubic-bezier(.22,1,.36,1)'`.

| Use | Duration | Curve |
|---|---|---|
| Press (`:active` scale .97; rows .985; icon buttons .94) | 150 ms | ease |
| Hover (tone change; tappable tiles lift −2px) | 200 ms | ease |
| Segmented / nav / month-picker thumb glide, switch thumb | 350 ms | spring-soft |
| Sheet in / out | 350 ms / 220 ms | spring / ease |
| Toast in / out | 350 ms / 200 ms | spring / ease |
| Menu in / out | 350 ms / 200 ms | spring / ease |
| List rows in / move / out (FLIP) | 300 / 350 / 260 ms | ease |
| Number roll | 380 ms | ease |
| Chart line draw-in (once per range); area fades in after it | 700 ms; 500 ms from 350 ms | ease |
| Year chart bars grow in (30 ms stagger) | 550 ms | ease |
| Top bar condense cross-fade | 200 ms | ease |
| Swipe / drag spring-back | 300 ms | spring-soft |
| Swipe slide-out | 220 ms | ease |
| Change tint (green / red / accent wash) | 900 ms | ease-out |
| Ambient backdrop drift | 36 / 44 / 52 s, alternate | ease-in-out |

Reduced motion (web `prefers-reduced-motion`, Android "Remove animations"): no transitions, no draw-in, no drift (the backdrop stays, static), gestures still work but snap.

---

## 3. Ambient backdrop and glass recipes

### 3.1 Ambient backdrop
Fixed behind everything (`z-index: -1`), never scrolls, `pointer-events: none`.

| | Light | Dark |
|---|---|---|
| Base (`--page`) | `#F4F6FB` | `#0A1122` (brand bg) |
| Blob A (top-left, 70vmax) | accent `rgba(16,89,252,.07)` | `rgba(74,130,255,.12)` |
| Blob B (bottom-right, 60vmax) | indigo `rgba(99,102,241,.05)` | `rgba(99,102,241,.08)` |
| Blob C (center-low, 50vmax) | sky `rgba(56,189,248,.04)` | `rgba(56,189,248,.05)` |

Blobs are radial gradients (colour → transparent, `closest-side`), blurred 40px, drifting by up to 6vmax with a 1 → 1.1 scale over 36 / 44 / 52 s (alternate). Android: three `Canvas` radial gradients in a full-screen `Box` behind the scaffold, animated with `infiniteRepeatable(tween(40000), Reverse)`.

### 3.2 Glass
`backdrop-filter: blur(24px) saturate(160%)` (with the `-webkit-` twin) on every glass surface.

| Token | Light | Dark | Used by |
|---|---|---|---|
| `--glass` | `rgba(255,255,255,.74)` | `rgba(20,32,56,.72)` | cards, tiles, side rail |
| `--glass-strong` | `rgba(255,255,255,.86)` | `rgba(17,27,48,.88)` | sheets, menus, bottom nav, condensed top bar, tooltips, Sheet tab bar |
| `--glass-hi` (inset top highlight) | `inset 0 1px 0 rgba(255,255,255,.55)` | `inset 0 1px 0 rgba(255,255,255,.08)` | every glass surface |
| `--glass-edge` (hairline ring) | `0 0 0 1px rgba(8,32,79,.06)` | `0 0 0 1px rgba(255,255,255,.06)` | every glass surface |
| `--glass-shadow` | `0 1px 2px rgba(8,32,79,.04), 0 12px 32px -12px rgba(8,32,79,.14)` | `0 1px 2px rgba(0,0,0,.25), 0 16px 40px -14px rgba(0,0,0,.6)` | cards, tiles |
| `--glass-shadow-lg` | `0 24px 64px -16px rgba(8,32,79,.28)` | `0 24px 64px -16px rgba(0,0,0,.7)` | sheets, menus, floating nav, hovered tiles |
| `--glass-solid` | `#F8FAFE` | `#132039` | opaque stand-in for glass (swipe face, pinned void cells, chart dots) |
| `--fill` (quiet control fill) | `rgba(8,32,79,.05)` | `rgba(255,255,255,.06)` | seg track, chips, ghost buttons, inputs, totals bands |
| `--fill-2` (hover / pressed) | `rgba(8,32,79,.08)` | `rgba(255,255,255,.10)` | hover, progress tracks, nav indicator |
| `--thumb` (selected, raised) | `#FFFFFF` | `rgba(255,255,255,.14)` | seg thumb, selected chip / category, month-picker thumb |
| `--thumb-shadow` | `0 1px 2px rgba(8,32,79,.08), 0 4px 12px -2px rgba(8,32,79,.12)` | `0 1px 2px rgba(0,0,0,.3), 0 4px 12px -2px rgba(0,0,0,.4)` | seg thumb, selected chip |
| `--row-sel` / shadow | `#FFFFFF` + glass-hi + thumb-shadow | `rgba(255,255,255,.10)` + glass-hi, edge, `0 4px 12px -2px rgba(0,0,0,.4)` | selected row (Month list in the two-pane layout) |

Other layers: toast = dark glass `rgba(8,32,79,.88)` (light) / `rgba(245,248,255,.94)` (dark), pill radius, `blur(16px) saturate(1.6)`, shadow `0 16px 40px -12px rgba(8,32,79,.35)`. Modal scrim = `rgba(8,32,79,.28)` (light) / `rgba(0,0,0,.5)` (dark) with `blur(6px) saturate(1.2)`, on its own layer so a dragged sheet can fade it.

**Radii:** cards and tiles **20**, sheets **28** (top corners on phones), menus 18, inputs and buttons 12, rows 14, segmented controls, chips, pills, the bottom nav and the Sheet tab bar **999** (pill).

**Fallback:** where `backdrop-filter` is unsupported (`@supports not (backdrop-filter: blur(1px))`), glass becomes the opaque brand `--card` (`#FFFFFF` / `#0A1122`) with the same edge and shadow. Android below API 31 (no `RenderEffect` blur) does the same.

**Contrast (checked on the composited colours, worst case = under the accent blob):** light glass composites to `#F8FAFE`: ink 15.1:1, ink-2 7.4:1, ink-3 4.7:1, good 4.8:1, bad 4.6:1. Dark glass composites to `#132039`: ink 15.3:1, ink-2 10.3:1, ink-3 6.3:1, good 9.3:1, bad 6.9:1. A light `--fill` band composites to `#ECEFF5`, where ink-3 / good / bad fall to 4.2–4.4:1, so text on a band uses the fill-safe variants `--ink-3-fill #5A6984` (4.8:1), `--good-fill #14763F` (4.9:1) and `--bad-fill #C7252A` (4.9:1), the base tones mixed 10% toward ink (dark bands keep the base tones: 5.3:1 and up). Input placeholders use `--ink-3-fill` too. The dark `--thumb` composites to `#343F55` (ink-3 4.1:1), which is why a selected row uses `--row-sel` (`.10`) in dark. The armed swipe underlay uses `--on-accent` text (white on red in light, navy on salmon in dark). All AA for text.

---

## 4. Tokens

Brand tokens come from `design/tokens.json` v2 and are unchanged (`web/src/styles/tokens.css`): `--ink #08204F / #F5F8FF`, `--ink-2 #415373 / #C2CEE2`, `--ink-3 #63718A / #91A2BF`, `--accent #1059FC / #4A82FF`, `--accent-ink`, `--on-accent`, `--good #15803D / #4ADE80`, `--bad #DC2626 / #FF8585`, `--warn`, `--card #FFFFFF / #0A1122`, `--surface`, `--line` (the Sheet grid only), the category palette (`--cat-*`, identical in both themes) and `--series-2`. v2 adds, per theme (values in §3):

```css
:root{
  --page:#F4F6FB; --blob-a:…; --blob-b:…; --blob-c:…;
  --glass:…; --glass-strong:…; --glass-solid:#F8FAFE; --glass-hi:…; --glass-edge:…;
  --glass-shadow:…; --glass-shadow-lg:…; --glass-filter:blur(24px) saturate(160%);
  --fill:…; --fill-2:…; --thumb:…; --thumb-shadow:…; --bar-shadow:0 8px 24px -12px rgba(8,32,79,.18);
  --good-fill:#14763F; --bad-fill:#C7252A; --ink-3-fill:#5A6984; --row-sel:#FFFFFF; --row-sel-shadow:…;
  --spring-soft:cubic-bezier(.3,1.25,.5,1);
  --r-card:20px; --r-sheet:28px; --r-menu:18px; --r-ctl:12px;
}
```
Dark is defined twice, under `@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) }` and under `:root[data-theme="dark"]`, so the Settings theme switch wins over the system setting.

**Colour rules**
- **Selected** = raised `--thumb` with `--thumb-shadow`, ink text, weight 600. Never an accent fill, an accent border or a coloured stripe.
- **Up / gained** is green `21,128,61`; **down / lost** is red `220,38,38`; **arrived** is accent `16,89,252`. These are used as fading washes (alpha .12), never as static fills.
- Solid accent only for the Add button / FAB, the primary Save, the switch "on" track and data marks (progress fill, chart line, expense bars).

**Type:** Inter 16/24 body, antialiased, `tabular-nums lining-nums` on `body`. Micro labels 12/500 uppercase `letter-spacing .6px` ink-3. Card titles 17/600 (−.2px). Display titles Barlow Condensed 40/44 uppercase. Hero numbers 48/52 Inter 600, −1px; tile values 24/32. Sheet titles 22/600.

---

## 5. Base CSS (the essentials; full file: `web/src/styles/base.css`)

```css
html,body{background:var(--page)}
.backdrop{position:fixed;inset:0;z-index:-1;overflow:hidden;pointer-events:none;background:var(--page)}
.backdrop i{position:absolute;border-radius:50%;filter:blur(40px);will-change:transform}
.backdrop .b1{width:70vmax;height:70vmax;left:-22vmax;top:-30vmax;background:radial-gradient(closest-side,var(--blob-a),transparent);animation:drift1 36s ease-in-out infinite alternate}
/* .b2 60vmax bottom-right 44s, .b3 50vmax centre-low 52s */
@keyframes drift1{to{transform:translate(6vmax,4vmax) scale(1.1)}}

/* glass surfaces */
.glass,.card,.glass-card,.glass-bar{background:var(--glass);-webkit-backdrop-filter:var(--glass-filter);backdrop-filter:var(--glass-filter);
  box-shadow:var(--glass-hi),var(--glass-edge),var(--glass-shadow)}
.glass-strong,.glass-card,.glass-bar{background:var(--glass-strong)}
.card{border-radius:var(--r-card)}

/* buttons: primary solid accent, everything else ghost glass */
.btn{font:500 16px/24px var(--body);background:var(--fill);color:var(--ink);border:0;padding:10px 16px;min-height:44px;border-radius:var(--r-ctl);
  transition:background-color .2s var(--ease),transform .15s var(--ease)}
@media(hover:hover){.btn:hover{background:var(--fill-2)}}
.btn:active{transform:scale(.97)}
.btn.primary{background:var(--accent);color:var(--on-accent);box-shadow:0 6px 16px -6px rgba(16,89,252,.45)}
.btn.danger{color:var(--bad)}                /* destructive = ghost with bad text */
.icon-btn{width:44px;height:44px;border-radius:999px;background:none;color:var(--ink-2)}
.icon-btn:active{transform:scale(.94)}
.link{color:var(--ink-2)}                    /* links are ink-2, never accent */

/* segmented control: a raised glass thumb slides to the selection (glideIndicator) */
.seg{display:inline-flex;position:relative;padding:3px;border-radius:999px;background:var(--fill)}
.seg button{position:relative;z-index:1;border:0;background:none;border-radius:999px;padding:6px 14px;min-height:36px;font:500 15px/22px var(--body);color:var(--ink-2)}
.seg button[aria-selected="true"]{color:var(--ink);font-weight:600}
.seg-ind{position:absolute;top:3px;left:0;height:calc(100% - 6px);border-radius:999px;background:var(--thumb);box-shadow:var(--glass-hi),var(--thumb-shadow)}
.seg-ind.ready{transition:transform .35s var(--spring-soft),width .35s var(--spring-soft)}

/* chips and picks: quiet until selected */
.pick{background:var(--fill);border:1.5px solid transparent;border-radius:var(--r-ctl);color:var(--ink-2)}
.pick:active{transform:scale(.96)}
.pick.on{background:var(--thumb);color:var(--ink);font-weight:600;box-shadow:var(--glass-hi),var(--thumb-shadow)}
.pick.chip{border-radius:999px;min-height:34px;padding:6px 14px}   /* + a 44px hit area via ::after */

/* inputs: a fill, no border at rest; focus = 2px ring, 2px offset */
.input{background:var(--fill);border:0;border-radius:var(--r-ctl);padding:10px 14px;min-height:44px}
.input:focus-visible,.input:focus{outline:2px solid var(--focus-ring);outline-offset:2px}
.input::placeholder{color:var(--ink-3-fill)}

/* switch: 48×28 track, 24px thumb, spring-soft slide, stretches to 28px while pressed */
.switch{width:48px;height:28px;border-radius:99px;background:var(--fill-2)}
.switch-knob{position:absolute;top:2px;left:2px;width:24px;height:24px;border-radius:99px;background:#fff;
  transition:transform .35s var(--spring-soft),width .2s var(--ease)}
.switch:active .switch-knob{width:28px}
.switch.on{background:var(--accent)} .switch.on .switch-knob{transform:translateX(20px)}

/* rows: no dividers, 2px rhythm, a fill on hover */
.row{width:calc(100% + 20px);margin:0 -10px;padding:10px;border-radius:14px}
@media(hover:hover){.row:hover{background:var(--fill)}}
.row:active{transform:scale(.985)}
.row.selected{background:var(--row-sel);box-shadow:var(--row-sel-shadow)}
```

---

## 6. Motion kit

### 6.1 Keyframes (CSS)
```css
@keyframes riseIn {from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
@keyframes fadeIn {from{opacity:0}to{opacity:1}}
@keyframes settle {from{opacity:0;transform:translateY(8px) scale(.96)}to{opacity:1;transform:none}}
@keyframes ckPop  {from{opacity:0;transform:scale(.3)}to{opacity:1;transform:scale(1)}}
@keyframes tpIn   {from{opacity:0;transform:scale(.55)}to{opacity:1;transform:none}}
@keyframes bump   {40%{transform:scale(1.08)}to{transform:scale(1)}}
@keyframes pickIn {from{transform:scale(.94)}}
@keyframes draw   {from{stroke-dashoffset:1}to{stroke-dashoffset:0}}   /* with pathLength=1 + stroke-dasharray:1 */
@keyframes barIn  {from{transform:scale(0)}to{transform:none}}         /* scaleY, transform-origin bottom */
@keyframes drift1 {to{transform:translate(6vmax,4vmax) scale(1.1)}}    /* backdrop; drift2/drift3 likewise */
@keyframes rollInUp   {from{opacity:0;transform:translateY(55%);filter:blur(6px)}to{opacity:1;transform:none;filter:none}}
@keyframes rollOutUp  {to{opacity:0;transform:translateY(-55%);filter:blur(6px)}}
@keyframes rollInDown {from{opacity:0;transform:translateY(-55%);filter:blur(6px)}to{opacity:1;transform:none;filter:none}}
@keyframes rollOutDown{to{opacity:0;transform:translateY(55%);filter:blur(6px)}}
@keyframes pulse{50%{opacity:.35}}

.panel.on{animation:riseIn .35s var(--ease) both}                       /* view / tab switch (riseIn: 8px) */
.list.enter>*{animation:riseIn .3s var(--ease) both;                     /* list cascade, capped at 12 */
  animation-delay:calc(min(var(--n,12),12) * 24ms)}
.pick.pop{animation:pickIn .35s var(--spring)}                           /* just selected */
.pick.pop .check{animation:ckPop .5s var(--spring) both}
.num.bump{display:inline-block;animation:bump .4s var(--spring)}         /* a number changed */
.arrive{animation:settle .45s var(--spring) both}                        /* arrival */
.grid.enter>*{animation:tpIn .45s var(--spring) both;                    /* option grid ripples in */
  animation-delay:calc(var(--n) * 28ms + 40ms)}                          /* --n = col + row */
.hchart-line.draw{stroke-dasharray:1;animation:draw .7s var(--ease) both}  /* chart line, once per range */
.hchart-area{animation:fadeIn .5s var(--ease) .35s both}
.live-dot{animation:pulse 1.6s infinite}

@media (prefers-reduced-motion:reduce){*,*::before,*::after{transition:none!important;animation:none!important}}
```

### 6.2 Helpers (JS)
```js
export const EASE = 'cubic-bezier(.22,1,.36,1)';
export const REDUCE = matchMedia('(prefers-reduced-motion: reduce)');
export const TINT = {up:'21,128,61', down:'220,38,38', accent:'16,89,252'};

/* restart a CSS animation class (e.g. 'bump' on a changed number) */
export function replay(el, cls){ el.classList.remove(cls); void el.offsetWidth; el.classList.add(cls); }

/* fading colour wash */
export function tint(el, rgb, {delay=0, a=.12, duration=900}={}){
  if(REDUCE.matches) return;
  el.animate([{backgroundColor:`rgba(${rgb},${a})`},{backgroundColor:`rgba(${rgb},0)`}],{duration,easing:'ease-out',delay});
}

/* one indicator that glides to the selected item (segmented controls, nav, grids) */
export function glideIndicator(box, {selector='[aria-selected="true"],[aria-pressed="true"],[aria-current="page"]', cls='seg-ind', vertical=false, grid=false}={}){
  let ind = box.querySelector(`:scope > .${cls}`);
  if(!ind){ ind = document.createElement('span'); ind.className = cls; box.prepend(ind); }
  const on = box.querySelector(selector); if(!on){ ind.style.opacity = '0'; return; }
  ind.style.opacity = '';
  if(grid){            /* both axes: the month-picker thumb takes the cell's box */
    ind.style.width = on.offsetWidth + 'px'; ind.style.height = on.offsetHeight + 'px';
    ind.style.transform = `translate(${on.offsetLeft}px,${on.offsetTop}px)`;
  } else if(vertical){ /* side nav */
    ind.style.height = on.offsetHeight + 'px'; ind.style.transform = `translateY(${on.offsetTop}px)`;
  } else {
    ind.style.width = on.offsetWidth + 'px'; ind.style.transform = `translateX(${on.offsetLeft}px)`;
  }
  // enable the transition only after the first placement, so nothing slides in on page load
  if(!ind.classList.contains('ready')) requestAnimationFrame(()=>requestAnimationFrame(()=>ind.classList.add('ready')));
}
// call on selection change, on resize, and after document.fonts.ready

/* text that rolls to its new value in the direction of travel ('up' = next, 'down' = previous) */
export function rollText(el, text, dir='up'){
  const old = el.dataset.t; if(old === text) return; el.dataset.t = text;
  if(old == null || REDUCE.matches){ el.textContent = text; return; }
  el.style.position = 'relative'; el.style.display = 'inline-block';
  el.innerHTML = '';
  const o = Object.assign(document.createElement('span'), {textContent: old});
  const n = Object.assign(document.createElement('span'), {textContent: text});
  Object.assign(o.style, {position:'absolute', left:0, top:0, whiteSpace:'nowrap', pointerEvents:'none',
    animation:`rollOut${dir==='up'?'Up':'Down'} .3s var(--ease) both`});
  Object.assign(n.style, {display:'inline-block', animation:`rollIn${dir==='up'?'Up':'Down'} .38s var(--ease) both`});
  el.append(o, n);
  clearTimeout(el._rt); el._rt = setTimeout(()=>{ if(el.dataset.t === text) el.textContent = text; }, 450);
}
```

---

## 7. Signature interactions

### 7.1 FLIP reorder with direction colour (lists, tables, rankings)
Rows slide from their old position to their new one, **wash green if they moved up and red if they moved down**, fade in (accent wash) when they enter, and sink away in red when they leave.

```js
/* before the data changes */
export function snapshot(root){
  if(!root || !root.offsetParent || REDUCE.matches) return null;
  const m = new Map();
  root.querySelectorAll('[data-k]').forEach(el => m.set(el.dataset.k, {top: el.getBoundingClientRect().top, el}));
  return m;
}
/* after re-rendering */
export function flip(root, before){
  if(!before) return;
  const now = new Set();
  root.querySelectorAll('[data-k]').forEach(el=>{
    now.add(el.dataset.k);
    const was = before.get(el.dataset.k);
    if(!was){
      el.animate([{opacity:0,transform:'translateY(6px)'},{opacity:1,transform:'none'}],{duration:300,easing:EASE});
      tint(el, TINT.accent); return;
    }
    const dy = was.top - el.getBoundingClientRect().top;
    if(Math.abs(dy) > 1){
      el.animate([{transform:`translateY(${dy}px)`},{transform:'none'}],{duration:350,easing:EASE,fill:'backwards'});
      tint(el, dy > 0 ? TINT.up : TINT.down);
    }
  });
  // left the list: the old row sinks away in red (root must be position:relative)
  const box = root.getBoundingClientRect();
  before.forEach(({top, el}, k)=>{
    if(now.has(k)) return;
    const g = el.cloneNode(true); g.removeAttribute('data-k');
    Object.assign(g.style,{position:'absolute',left:0,right:0,top:(top-box.top)+'px',pointerEvents:'none',zIndex:1});
    root.appendChild(g);
    g.animate([{opacity:1,transform:'none',backgroundColor:`rgba(${TINT.down},.18)`},
               {opacity:0,transform:'translateY(10px) scale(.98)',backgroundColor:`rgba(${TINT.down},0)`}],
              {duration:260,easing:EASE}).onfinish = () => g.remove();
  });
}
```
- Give every row a stable `data-k` (the item's id).
- If the list is hidden when data changes, remember its order and replay the moves (with a 140ms delay) the next time it's shown.

### 7.2 Travel between containers: the seamless flight
An item moves to another container (the next round, another column, another list). **A copy of it flies from the old spot to the new one and lands invisibly.** Most implementations get this wrong: the copy looks different from the real element, so it "snaps" or "flashes" on landing. These rules make the landing seamless:

1. **Copy the destination element, not the source.** Take it *after* re-rendering, so the flying copy shows exactly what the real element will show.
2. **Freeze its responsive layout.** Container queries and parent-dependent styles stop applying once the copy is moved to `<body>`. Copy the computed `display` of every descendant onto the clone, so hidden parts stay hidden and swapped labels stay swapped.
3. **Fixed size for the whole flight.** Never animate width or height, because the text would reflow mid-air.
4. **Outline with `box-shadow`, not `border`.** A border shifts the content 1px compared with the real element.
5. **Melt at the end.** Over the last ~15%, fade the outline and shadow to nothing and morph the corners to exactly what the real element has in place. Then swap copy and real element in the same frame.

```js
function freezeDisplay(src, dst){
  dst.style.display = getComputedStyle(src).display;
  [...src.children].forEach((c, i) => dst.children[i] && freezeDisplay(c, dst.children[i]));
}
/* el: the real element in its new place (after re-render); from: its old DOMRect */
export function fly(el, from, {lift = true, index = 0, radius = 10, endRadius = '0px'} = {}){
  if(REDUCE.matches) return;
  const to = el.getBoundingClientRect();
  const g = el.cloneNode(true);
  freezeDisplay(el, g);
  g.removeAttribute('id'); g.removeAttribute('data-k');
  Object.assign(g.style, {position:'fixed', zIndex:60, margin:0, pointerEvents:'none', transition:'none',
    left:to.left+'px', top:to.top+'px', width:to.width+'px', height:to.height+'px',
    background:getComputedStyle(el).backgroundColor === 'rgba(0, 0, 0, 0)' ? '#fff' : '', border:'none'});
  document.body.appendChild(g);
  el.style.visibility = 'hidden';
  const dx = from.left - to.left, dy = from.top - to.top;
  const up = lift ? Math.min(20, 8 + Math.abs(dx) * .05) : 0;
  const sh = (ring, y, blur, a) => `0 0 0 1px rgba(8,32,79,${ring}), 0 ${y}px ${blur}px rgba(8,32,79,${a})`;
  const r = radius + 'px';
  const anim = g.animate([
    {transform:`translate(${dx}px,${dy}px)`, boxShadow:sh(.1,1,2,.06), borderRadius:r},
    {transform:`translate(${dx*.5}px,${dy*.5-up}px) scale(1.03)`, boxShadow:sh(.08,12,28,.18), borderRadius:r, offset:.5},
    {transform:'translate(0,0) scale(1)', boxShadow:sh(.07,3,8,.08), borderRadius:r, offset:.85},
    {transform:'none', boxShadow:sh(0,0,0,0), borderRadius:endRadius}          // melts into place
  ], {duration: lift ? 700 : 600, easing: EASE, delay: index * 40, fill:'backwards'});
  let done = false;
  const land = () => { if(done) return; done = true; el.style.visibility = ''; g.remove(); };
  anim.onfinish = land; anim.oncancel = land;
}
```
**Wiring it up:** before re-rendering, snapshot the rects of keyed items (`data-k="container:id"`). After re-rendering:
- An item that is new to its container but existed in another one flies from there (`lift:true`).
- An item that only moved inside its container glides (`lift:false`).
- An item with no source fades in (`fadeIn .35s`).
- Stagger simultaneous moves by 40ms.
- For `endRadius`, pass the corners the element has in place. For example, the first row of a rounded card is `'9px 9px 0 0'` (the card radius minus its border).

### 7.3 Menus and popovers
```css
.menu{position:absolute;top:calc(100% + 8px);right:0;z-index:40;min-width:210px;padding:6px;border-radius:var(--r-menu);
  background:var(--glass-strong);-webkit-backdrop-filter:var(--glass-filter);backdrop-filter:var(--glass-filter);
  box-shadow:var(--glass-hi),var(--glass-edge),var(--glass-shadow-lg);
  transform-origin:top right;opacity:0;visibility:hidden;pointer-events:none;transform:translateY(-6px) scale(.9);filter:blur(2px);
  transition:opacity .16s var(--ease),transform .2s var(--ease),filter .16s var(--ease),visibility 0s linear .2s}
.menu.open{opacity:1;visibility:visible;pointer-events:auto;transform:none;filter:none;
  transition:opacity .2s var(--ease),transform .35s var(--spring),filter .25s var(--ease),visibility 0s}
.menu.open>*{animation:piIn .3s var(--ease) both;animation-delay:calc(var(--i) * 30ms + 50ms)}
.menu-item{display:flex;width:100%;min-height:44px;padding:10px 12px;border-radius:12px;border:0;background:none;font:500 16px/24px var(--body);color:var(--ink)}
@media(hover:hover){.menu-item:hover{background:var(--fill)}}
.menu-item:active{transform:scale(.98)}
.menu-item.warn{color:var(--bad)}
.menu-sep{height:1px;background:var(--fill-2);margin:4px 8px}
```
- Give each item `style="--i:N"` so the items stagger in.
- While the menu is open, the trigger keeps its hover look (`[aria-expanded="true"]`).
- Keep the menu on screen: measure it with transitions off (a `.measure` class) and nudge it in by 12px if it overflows.
- Close it on outside click, on Escape, and after choosing an item.

### 7.4 Sheets, drag-to-dismiss and toast
```css
.modal{position:fixed;inset:0;z-index:50;display:flex;align-items:center;justify-content:center;padding:20px;isolation:isolate;
  opacity:0;transition:opacity .25s var(--ease)}
.modal::before{content:'';position:absolute;inset:0;z-index:-1;opacity:var(--scrim-o,1);   /* scrim on its own layer */
  background:var(--scrim);-webkit-backdrop-filter:blur(6px) saturate(1.2);backdrop-filter:blur(6px) saturate(1.2)}
.modal.open{opacity:1}
.sheet{background:var(--glass-strong);-webkit-backdrop-filter:var(--glass-filter);backdrop-filter:var(--glass-filter);
  border-radius:var(--r-sheet);max-width:440px;width:100%;padding:22px 24px 20px;
  box-shadow:var(--glass-hi),var(--glass-edge),var(--glass-shadow-lg);
  transform:translateY(16px) scale(.96);opacity:0;transition:transform .35s var(--spring),opacity .25s var(--ease)}
.modal:not(.open) .sheet{transition:transform .22s var(--ease),opacity .18s var(--ease)}
.modal.open .sheet{transform:none;opacity:1}
.sheet.dragging{transition:none!important}
@media(max-width:599px){                       /* phones: a bottom sheet with a grabber */
  .modal{align-items:flex-end;padding:0}
  .sheet{max-width:none;border-radius:var(--r-sheet) var(--r-sheet) 0 0;padding:8px 18px calc(16px + env(safe-area-inset-bottom));transform:translateY(48px)}
  .sheet.flung{transition:transform .2s var(--ease)!important}
  .grabber{display:block;width:38px;height:5px;border-radius:99px;background:var(--fill-2);margin:0 auto 10px}
}
.sheet-head .sheet-close{width:34px;height:34px;border-radius:999px;background:var(--fill)}   /* 44px hit area via ::after */

.toast{position:fixed;left:50%;bottom:calc(26px + env(safe-area-inset-bottom));z-index:60;width:max-content;
  transform:translate(-50%,16px) scale(.96);opacity:0;border-radius:999px;padding:12px 20px;
  background:var(--toast);color:var(--on-toast);-webkit-backdrop-filter:blur(16px) saturate(1.6);backdrop-filter:blur(16px) saturate(1.6);
  box-shadow:0 16px 40px -12px rgba(8,32,79,.35);transition:opacity .2s var(--ease),transform .35s var(--spring)}
.toast.on{opacity:1;transform:translate(-50%,0) scale(1)}
.toast-action{border-radius:999px;padding:8px 14px;font-weight:600;background:rgba(255,255,255,.14)}   /* "Undo" */
body:has(.dock) .toast{bottom:calc(88px + env(safe-area-inset-bottom))}     /* above the floating dock */
```
- **Sheet:** un-hide it, then add `.open` two animation frames later so the transition runs. Escape and a scrim click close it. Default focus goes to the first `[data-autofocus]` field, else the sheet.
- **Drag-to-dismiss (phones):** the grabber and header (`.sheet-grab`, `touch-action:none`) follow the finger 1:1 down; dragging up rubber-bands (`−sqrt(|dy|)·4`). The scrim fades as you pull (`--scrim-o = max(.15, 1 − dy/360)`). Release past 120px or faster than 0.6 px/ms flings it out (`translateY(110%)`, 200 ms) and closes; otherwise it springs back. Buttons, links and inputs inside the header don't start a drag. Pure maths: `web/src/ui/gesture.ts` (tested).
- **Confirm:** `ask({title, body, yes, no, danger})` returns a Promise; a destructive confirm is solid `--bad` with `--on-accent` text. Deleting a transaction does **not** confirm: it deletes and shows an Undo toast (5 s).
- **Toast:** auto-hides after 2.6s (5s with an action); a new toast replaces the current one.

### 7.5 Chrome: collapsing top bar, floating dock, glass rail
- **Top bar:** sticky, 52px tall. At rest it is transparent and the page shows a large title block (micro label 12/500 ink-3 + Barlow display title 40/44). A 1px sentinel at the bottom of the title block is watched by an **IntersectionObserver** with `rootMargin: -52px 0 0 0`; when it passes under the bar, the bar's `::before` layer fades to `--glass-strong` + blur with `--bar-shadow` and a compact title (Inter 17/600) fades in (200 ms). Actions (sync dot or "Demo data" pill, the wide-screen Add, Settings) stay right. Don't use scroll listeners.
- **Bottom dock (< 600px):** a detached floating glass pill (`--glass-strong`, radius 999, height 60, `--glass-shadow-lg`), `bottom: 12px + safe area`, with Budget's five destinations (Home · Month · Year · Net worth · Sheet; icon 20 + label 12/600). Items size to their label (`flex: 1 1 auto`) so "Net worth" never truncates at 360–390px. A `--fill-2` pill indicator glides between items (spring-soft 350 ms). The round Add (56px, accent, `0 10px 24px -6px rgba(16,89,252,.45)`) floats beside it with a 10px gap. Toasts sit above both.
- **Side rail (≥ 600px):** a floating glass panel inset 12px from the top, left and bottom, radius 24: 76px wide (600–1023, Add on top, icon over label) or 220px with the logo lockup (≥ 1024). The Sheet route always uses the 76px rail. Active item = gliding `--fill-2` pill with ink 600 text; inactive ink-2.

### 7.6 Compact control that expands into a full picker, inside the same card
```css
.expand{display:grid;grid-template-rows:0fr;transition:grid-template-rows .4s var(--spring-soft)}
.expand>div{overflow:hidden;min-height:0}
.open .expand{grid-template-rows:1fr}
.expand .content{opacity:0;transform:translateY(-6px);transform-origin:top center;
  transition:opacity .18s var(--ease),transform .25s var(--ease)}
.open .expand .content{opacity:1;transform:none;transition:opacity .3s var(--ease) .05s,transform .4s var(--spring-soft) .05s}
.chev{transition:transform .35s var(--spring-soft),color .25s var(--ease)}
.open .chev{transform:rotate(180deg);color:var(--ink)}
```
- Animating `0fr → 1fr` grows the panel to its natural height without measuring.
- Replay the grid ripple (`.grid.enter`, see §6.1) each time the picker opens.
- The trigger should be a real `<button>` with `width:100%` if it needs to centre its content. A bare button shrinks to fit its content.
- Measure a gliding indicator inside a scaled or animating container with **layout offsets** (`offsetLeft/Top/Width/Height`), not `getBoundingClientRect`.

### 7.7 Snap wheel (iOS-picker-style horizontal selector)
A horizontal row of items. The item under the centre line is magnified and gets selected when the swipe settles, and a disc glides to it.

```css
.wheel{overflow-x:auto;overflow-y:hidden;scrollbar-width:none;overscroll-behavior-x:contain;scroll-snap-type:x mandatory}
.wheel::-webkit-scrollbar{display:none}
.wheel-track{display:flex;position:relative;width:max-content;min-width:100%}
/* end space: REAL spacer items, never padding (see pitfalls). --edge set in px from JS */
.wheel-track::before,.wheel-track::after{content:'';flex:0 0 var(--edge);width:var(--edge)}
.wheel-item{flex:0 0 58px;width:58px;scroll-snap-align:center}   /* explicit width too */
.wheel.scrolling .disc{transition:transform .22s var(--ease)}   /* keeps up while moving; taps use the spring */
```
```js
const setEdge = () => wheel.clientWidth && wheel.style.setProperty('--edge', (wheel.clientWidth/2 - 29) + 'px');
new ResizeObserver(setEdge).observe(wheel);

// magnify toward the centre line and return the centred item
function lens(){
  const r = wheel.getBoundingClientRect(), cx = r.left + r.width/2; let best, bd = Infinity;
  items.forEach(c=>{ const b = c.getBoundingClientRect(), d = Math.abs(b.left + b.width/2 - cx);
    c.style.setProperty('--s', (1 + .2*Math.exp(-d*d/(2*58*58))).toFixed(3));        // scale
    c.style.setProperty('--o', (.4 + .6*Math.exp(-d*d/(2*120*120))).toFixed(3));      // opacity
    if(d < bd){ bd = d; best = c; } });
  return best;
}

// "settled" only when the wheel has really stopped: don't trust scrollend alone (see pitfalls)
let settleT, touching = false, userScroll = false;
function settleWhenStill(n = 0){
  clearTimeout(settleT);
  settleT = setTimeout(()=>{ const x = wheel.scrollLeft;
    requestAnimationFrame(()=>requestAnimationFrame(()=>{
      if(touching) return;
      if(Math.abs(wheel.scrollLeft - x) > .5 && n < 20) settleWhenStill(n + 1); else settle();
    })); }, n ? 80 : 180);
}
wheel.addEventListener('touchstart', ()=>{ touching = true; userScroll = true; clearTimeout(settleT); }, {passive:true});
wheel.addEventListener('touchend',   ()=>{ touching = false; settleWhenStill(); }, {passive:true});
wheel.addEventListener('scroll', ()=>{ wheel.classList.add('scrolling'); /* rAF: lens(), move disc to centred item */
  if(!touching) settleWhenStill(); }, {passive:true});
if('onscrollend' in window) wheel.addEventListener('scrollend', settle);   // settling twice is harmless
function settle(){
  wheel.classList.remove('scrolling');
  const c = lens(); const was = userScroll; userScroll = false; if(!c) return;
  if(was) select(c);                      // the user scrolled: select the centred item
  else if(c !== selected) centre(selected);   // otherwise the selection comes back to the centre line
}
```
- **Desktop version:** all items fit on one row, with Dock-style magnification under the pointer: `scale = 1 + .3*exp(-d²/(2·50²))`. Show edge fades (`mask-image` gradients) and arrow buttons only when the row overflows, and map the vertical wheel to horizontal scroll.
- The selection disc sits on the item, centred with `left:calc(var(--size)/-2)`, so it stays true while sizes animate.

### 7.8 Small touches
- **A number changes:** `replay(el,'bump')`. Skip this on the first render.
- **Progress bars:** `transition:width .6s var(--ease)`, on a `--fill-2` track, 8px (6px thin), radius 99px; an over-budget tail in `--bad` sits 2px after the fill. The Home stacked bar is 10px with 3px gaps between rounded segments.
- **Progress rings (SVG):** use `pathLength="100"` with `stroke-dasharray:100`, and animate `stroke-dashoffset .8s var(--ease)`.
- **Icon or avatar swap:** `ckPop .55s var(--spring)`.
- **Haptics (Android):** `navigator.vibrate` with ~4ms for a wheel tick, 6ms for a tap, 9ms for a selection, and `[12,60,24]` for a celebration. Only use it when `(pointer:coarse)` matches.

### 7.9 Swipe to delete (transaction rows, touch)
Every transaction row (Home "Recent", Month → Transactions, category detail) can be swiped left on touch. Mouse and pen are ignored: they open the row's sheet, which has Delete.
- Direction locks after 8px of travel, horizontal only when `|dx| > 1.2·|dy|` (vertical scrolling is never stolen; the face has `touch-action: pan-y`).
- The row follows the finger over a `--bad` 12% underlay that is only as wide as the gap (it never peeks round the row's corners). The action **arms at 96px or 30% of the row** (whichever is smaller, at least 48px): the underlay turns solid `--bad` with `--on-accent` text, the trash icon + "Delete" scale up (spring) and a haptic tick fires. Past the threshold the row follows at 35% (rubber band). The direction without an action barely gives (`sqrt`).
- Release armed: the row slides out (220 ms ease), the transaction is deleted and a toast offers **Undo** (re-saves the same row). Otherwise it springs back (300 ms spring-soft). A click right after a drag is swallowed.

### 7.10 Charts
- **Net worth history:** a monotone-cubic accent line (2.25px) that draws in once per range (`pathLength=1`, `draw` 700 ms; the chart is keyed by range), the 14% → 0 accent area fading in after it, a `--fill-2` baseline and a dashed zero line when the series crosses $0. Scrubbing (pointer or finger, `touch-action: pan-y`) shows a hairline, a dot ringed in `--thumb` and a `--glass-strong` tooltip (date, value, the Super liquid overlay value). Tile sparklines draw in the same way.
- **Year spending chart:** expense bars grow from the baseline with a 30 ms stagger (open months at 45% opacity), budget marks fade in, the leftover line draws in. Scrubbing anywhere over the plot picks the nearest month: its slot tints `--fill`, a hairline marks it and a glass tooltip lists Spent / Budget / Leftover (projected for open months). The legend stays put.

### 7.11 Month picker thumb
The "Go to month" sheet's 12-month grid sits on a `--fill` tray (radius 22). The current month is on a raised `--thumb` (radius 16) that is one element gliding in **both axes** (`glideIndicator(…, {grid:true})`, spring-soft 350 ms). Tapping a month glides the thumb there first, then (200 ms later) the sheet closes and the month opens.

---

## 8. Layout rules

- Use `minmax(0,1fr)`, never plain `1fr`, so long text ellipsizes instead of stretching the grid.
- Use **container queries** for components that live in variable-width columns. They drop secondary text (records, long names) as they narrow instead of wrapping.
- Spacing scale: 4 / 6 / 8 / 10 / 12 / 16 / 22 / 28px. Cards in a stack are 12px apart; card padding 16/18 (heroes 22); rows use a 2px rhythm instead of dividers.
- Side rails or ads in wide margins: let the main column take whatever the rails leave, capped at its maximum (`--wrap:min(1240px, calc(100vw - 400px))`), so there's never dead space outside the rails.
- Reserve the size of anything that loads later (ads, images, charts) before it arrives.

---

## 9. Accessibility

- Use real `<button>`s. Use `aria-pressed` / `aria-selected` for toggles and tabs, `aria-expanded` + `aria-controls` for disclosure, and `role="menu"` / `menuitem` for menus.
- `:focus-visible` rings are always visible for keyboard users and never appear on click.
- For round items, use a double ring instead of an outline: `box-shadow:0 0 0 2px var(--page),0 0 0 4px var(--focus-ring)`.
- Text contrast is checked on the **composited** glass (§3.2), worst case under the accent blob; text on `--fill` bands uses the fill-safe tones.
- Small controls keep a 44px hit area (`::after`) without growing visually (chips, seg buttons, switches, the sheet close button, card-head buttons).
- Escape closes any floating layer. Reduced motion skips every animation, both the CSS kill-switch and an early `return` in every JS helper.

---

## 10. Pitfalls (each of these shipped as a real bug)

1. **Percentage padding on a `max-content` element.** Safari and Firefox count `%` padding as 0 when sizing the element, and with `border-box` the real padding then eats the content. **Use px values, set from JS.**
2. **End padding in a scroll container.** If the children overflow their track, padding past them is *not* scrollable, so the last item can't reach the centre or the end. Safari can size a flex track from its items' *content* rather than their `flex-basis`, which causes exactly this. **Use real spacer items (`::before`/`::after`), and give items an explicit `width` as well as `flex-basis`.**
3. **Trusting `scrollend` or a fixed timer on iOS.** Safari doesn't reliably fire `scrollend`, and momentum or snapping can pause for more than 250ms mid-glide. **Settle only when the position is unchanged over two frames**, and afterwards always make the visual selection and the scroll position agree.
4. **Flying copy that differs from the real element.** Missing fields, different truncation, a border instead of a shadow, or animated width all cause a visible snap on landing. **Clone the destination, freeze computed `display`, keep a fixed size, and melt the outline and corners at the end** (§7.2).
5. **Clones lose container-query and parent-scoped styles** once moved to `<body>`. Freeze what matters (display, sizes such as logo width and height) inline.
6. **Measuring inside something that's animating.** `getBoundingClientRect` includes transforms, so pop-in scales give wrong sizes. **Use offset properties** for indicators inside animating containers.
7. **Indicators sliding in on page load.** Only add the transition class after the first placement (double `requestAnimationFrame`).
8. **Buttons don't stretch.** A `<button>` with `display:flex` shrinks to fit its content, so give it `width:100%` when it should fill a row or centre its content.
9. **Re-rendering big chunks while animating.** Re-render only the parts that changed, and keep ads and other heavy slots outside re-rendered regions.
10. **Animating on plain re-renders.** Snapshot → change → animate only when something actually moved or changed. Never animate on first load, except the page's initial `riseIn`.
11. **Service worker caching.** Bump the service worker's cache version whenever the app shell changes, and use network-first for page loads, so users get fixes.
12. **A scroll container in the way of `position: sticky` and shadows.** A wrapper with `overflow` set (even `overflow-y: auto` from an unrelated rule with the same class name) clips the glass shadow into a hard band and makes a sticky bar stick to the wrapper, not the viewport. Keep glass shadows' ancestors `overflow: visible`.
13. **Tinted bands eat contrast.** A `--fill` band on light glass drops ink-3 / good / bad below 4.5:1; use the fill-safe tones (§3.2). Likewise a white label on the dark-theme `--bad` (#FF8585) fails: use `--on-accent`.

---

## 11. Ship checklist

- [ ] Only tokens are used: no hard-coded colours, radii, shadows or curves.
- [ ] Only `transform`, `opacity`, `filter`, colours, box-shadow and `grid-template-rows` are animated. Width and height are animated only on `position:fixed` copies.
- [ ] Every interactive element has hover (inside `@media(hover:hover)`), `:active` scale, `:focus-visible` and no tap highlight.
- [ ] Every surface is glass over the ambient field (`--glass` / `--glass-strong`, glass-hi + edge + shadow), with `-webkit-` twins and the opaque fallback. The Sheet grid cells stay opaque.
- [ ] No borders or dividers inside cards; selection is a raised thumb, never an accent fill or a coloured stripe.
- [ ] Contrast checked on the composited colours in both themes (§3.2).
- [ ] Moves animate from their old position. Hand-offs between copies and real elements are invisible.
- [ ] Up/down changes wash green/red, and arrivals wash accent.
- [ ] Tested on iOS Safari (a real device or a WebKit build), not just Chrome: scroll snapping, the ends of scrollers, sticky elements and blur.
- [ ] Reduced motion leaves a fully usable UI.
- [ ] No layout shift on load, on pinning, when switching tabs or when async content arrives.
