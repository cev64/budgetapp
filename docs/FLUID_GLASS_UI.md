# Fluid Glass UI: Style and Motion Guide

A drop-in guide for building interfaces that feel **calm, tactile, glassy and fluid**. It is framework-agnostic: plain CSS plus small vanilla-JS helpers that work in React, Vue, Svelte or plain HTML.

Every value here comes from a production app and was tuned by eye on desktop, Android and iOS. **Copy the values exactly.** The feel comes from the details.

> **The feel in one sentence:** a bright, quiet surface where everything answers you. Selections *glide*, new things *settle in*, moved things *travel from where they were*, changes *tint green or red*, and floating chrome is *frosted glass*. Nothing blinks, jumps or snaps.

**How to use this file:** read §1–§3 once. Then copy §4 (tokens), §5 (base CSS) and §6 (motion kit) into the project. Build components from §7–§9. Before shipping, check §10 (pitfalls) and §11 (checklist).

---

## 1. Principles

1. **Calm at rest, lively on interaction.** Static screens are white, flat and still. Motion happens only in response to the user or to data changing.
2. **Continuity over teleporting.** If something changes place, animate it *from where it was to where it is*, so the eye never has to re-find it.
3. **Two curves, used everywhere.** One ease-out for movement and one gentle spring for "arriving". Most of the consistency comes from this rule.
4. **Glass is for floating layers only.** Use it for sticky headers, pinned toolbars, menus, toasts and modal backdrops. Never use it on base content.
5. **Every press is felt.** Clickable things scale down slightly on `:active`. Hover changes colour or background only, never layout.
6. **Nothing jumps.** Reserve space, freeze the footprints of sticky elements, keep the scrollbar gutter stable, and make hand-offs between animated copies and real elements invisible.
7. **Exits are quicker than entrances.** Open generously and close briskly.
8. **Reduced motion is respected.** Every animation is optional, and the UI must read perfectly without it.

---

## 2. The two curves

```css
--ease:   cubic-bezier(.22, 1, .36, 1);     /* fast start, long soft landing: all movement */
--spring: cubic-bezier(.34, 1.4, .64, 1);   /* slight overshoot, then settle: arrivals, pops, opens */
```
In JavaScript (for the Web Animations API): `const EASE = 'cubic-bezier(.22,1,.36,1)'`.

| Use | Duration | Curve |
|---|---|---|
| Press feedback (`:active` scale) | .15–.18s | ease |
| Hover colour or background | .2–.25s | ease |
| Gliding indicators, progress bars | .45–.6s | ease |
| Things appearing (panels, cards, lists) | .4–.55s | ease |
| Arrivals: modal, toast, menu, pop-in | .45–.6s | **spring** |
| Row reorders (FLIP) | .65s | ease |
| Travel between containers | .6–.7s | ease |
| Change tint (green/red/blue wash) | 1.4s | ease-out |
| Exits | about 60% of the entrance | ease |

---

## 3. Glass recipes

The rich look comes from **saturate > 1** alongside the blur, and from **navy-tinted shadows** (`rgba(8,32,79,…)`) rather than grey ones. Always include the `-webkit-` twin.

| Layer | Background | Filter | Border | Shadow |
|---|---|---|---|---|
| Sticky top bar | `rgba(255,255,255,.92)` | `blur(8px) saturate(1.4)` | bottom `1px var(--line)` | none at rest; once scrolled, `0 8px 24px rgba(8,32,79,.07)` and hide the border |
| Pinned toolbar card | `rgba(255,255,255,.86)` | `blur(18px) saturate(1.7)` | `1px rgba(8,32,79,.07)` | `0 10px 30px rgba(8,32,79,.12), 0 1px 3px rgba(8,32,79,.06)` |
| Menu / popover | `rgba(255,255,255,.9)` | `blur(18px) saturate(1.7)` | `1px rgba(8,32,79,.08)` | `0 18px 44px rgba(8,32,79,.16), 0 2px 8px rgba(8,32,79,.06)` |
| Toast (dark glass) | `rgba(8,32,79,.86)` | `blur(14px) saturate(1.6)` | none | `0 12px 32px rgba(16,24,40,.14)` |
| Modal backdrop | `rgba(8,32,79,.28)` | `blur(8px) saturate(1.2)` | n/a | n/a |
| Modal sheet | `rgba(255,255,255,.96)`, radius 18px | none | none | `0 24px 64px rgba(8,32,79,.22), 0 2px 6px rgba(8,32,79,.06)` |

---

## 4. Tokens

Brand colours are placeholders you can swap. Keep the names and the structure.

```css
:root{
  --bg:#FFFFFF;
  --surface:#F7F8FA;     /* subtle fills, hover */
  --surface-2:#EEF1F5;   /* control tracks, ghost hover, chips */
  --line:#E4E7EC;        /* borders, dividers */
  --line-2:#D5DAE1;      /* stronger borders, dashed placeholders */
  --ink:#08204F;         /* primary text: navy, never pure black */
  --ink-2:#475467;       /* secondary text */
  --ink-3:#8A94A6;       /* labels, meta, placeholders */
  --accent:#1059FC;
  --accent-ink:#0A45CC;  /* accent text on light fills, primary hover */
  --accent-soft:#EDF3FF; /* selected fills */
  --good:#15803D;
  --bad:#DC2626;
  --radius:12px;         /* cards */
  --radius-sm:8px;       /* buttons, inputs */
  --shadow:0 1px 2px rgba(16,24,40,.05);
  --shadow-lg:0 12px 32px rgba(16,24,40,.14);
  --body:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
  --display:'Barlow Condensed','Arial Narrow',sans-serif;   /* big uppercase headings only */
  --ease:cubic-bezier(.22,1,.36,1);
  --spring:cubic-bezier(.34,1.4,.64,1);
}
```

**Colour rules**
- **Selected** means `--accent-soft` fill, an `--accent` border or `inset 3px 0 0 var(--accent)` bar, and `--accent-ink` text. Use a solid accent fill only for primary buttons and small toggles.
- Unselected siblings of a selection fade to `opacity:.45–.5`.
- **Up / gained** is green `21,128,61`; **down / lost** is red `220,38,38`; **arrived** is accent `16,89,252`. These are used as fading washes, never as static fills.

**Type:** Inter 14px/1.45, antialiased, and `font-variant-numeric:tabular-nums` on `body` so changing numbers never jiggle. Micro-labels are 10.5–11px, weight 700, UPPERCASE, `letter-spacing:.06–.1em`, in `--ink-3`. Build hierarchy with weight and colour before size.

---

## 5. Base CSS (copy as-is)

```css
*{box-sizing:border-box}
html{scrollbar-gutter:stable;-webkit-tap-highlight-color:transparent;-webkit-text-size-adjust:100%}
@supports not (scrollbar-gutter:stable){html{overflow-y:scroll}}
body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.45 var(--body);
  -webkit-font-smoothing:antialiased;font-variant-numeric:tabular-nums}
button{font-family:inherit}
button,[role="tab"]{touch-action:manipulation}
button{-webkit-touch-callout:none;user-select:none;-webkit-user-select:none}
[hidden]{display:none!important}
:focus:not(:focus-visible){outline:none}
:focus-visible{outline:2px solid var(--accent);outline-offset:2px}

/* buttons */
.btn{font:500 13px var(--body);background:#fff;color:var(--ink-2);border:1px solid var(--line-2);
  padding:6px 12px;border-radius:var(--radius-sm);white-space:nowrap;cursor:pointer;
  transition:background-color .2s var(--ease),color .2s var(--ease),border-color .2s var(--ease),transform .18s var(--ease),opacity .2s}
@media(hover:hover){.btn:hover{color:var(--ink);background:var(--surface)}}
.btn:active{transform:scale(.97)}
.btn:disabled{opacity:.55}
.btn.primary{background:var(--accent);border-color:var(--accent);color:#fff}
@media(hover:hover){.btn.primary:hover{background:var(--accent-ink)}}
.btn.ghost{background:none;border-color:transparent}
@media(hover:hover){.btn.ghost:hover{background:var(--surface-2)}}
.btn.danger{color:var(--bad);border-color:#F3C1C1}
@media(hover:hover){.btn.danger:hover{background:#FEF2F2}}

/* cards: base content, never glass */
.card{background:#fff;border:1px solid var(--line);border-radius:var(--radius);box-shadow:var(--shadow)}

/* segmented control / tabs: one white pill glides to the selection (see glideIndicator) */
.seg{display:inline-flex;position:relative;gap:2px;padding:3px;border-radius:10px;background:var(--surface-2)}
.seg button{position:relative;z-index:1;background:none;border:0;border-radius:8px;padding:6px 14px;cursor:pointer;
  font:600 13px var(--body);color:var(--ink-2);transition:color .25s var(--ease),transform .18s var(--ease)}
.seg button[aria-pressed="true"],.seg button[aria-selected="true"]{color:var(--ink)}
.seg button:active{transform:scale(.96)}
.seg-ind{position:absolute;top:3px;left:0;height:calc(100% - 6px);border-radius:8px;background:#fff;pointer-events:none;
  box-shadow:0 1px 3px rgba(16,24,40,.12),0 1px 1px rgba(16,24,40,.04);will-change:transform,width}
.seg-ind.ready{transition:transform .45s var(--ease),width .45s var(--ease)}

/* selectable tile (a "pick") */
.pick{background:var(--surface);border:1.5px solid transparent;border-radius:10px;padding:8px 12px;cursor:pointer;
  transition:background-color .25s var(--ease),border-color .25s var(--ease),opacity .3s var(--ease),transform .18s var(--ease)}
@media(hover:hover){.pick:hover{background:var(--surface-2)}}
.pick:active{transform:scale(.97)}
.pick.on{background:var(--accent-soft);border-color:var(--accent);color:var(--accent-ink)}
.pick.off{opacity:.5}

/* pills */
.pill{font:600 12px var(--body);padding:3px 9px;border-radius:99px;background:var(--surface-2);color:var(--ink-2)}
.pill.accent{background:var(--accent-soft);color:var(--accent-ink)}

/* glass layers */
.glass-bar{background:rgba(255,255,255,.92);-webkit-backdrop-filter:blur(8px) saturate(1.4);backdrop-filter:blur(8px) saturate(1.4)}
.glass-card{background:rgba(255,255,255,.86);-webkit-backdrop-filter:blur(18px) saturate(1.7);backdrop-filter:blur(18px) saturate(1.7);
  border:1px solid rgba(8,32,79,.07);box-shadow:0 10px 30px rgba(8,32,79,.12),0 1px 3px rgba(8,32,79,.06)}
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
@keyframes bump   {40%{transform:scale(1.12)}to{transform:scale(1)}}
@keyframes pickIn {from{background-color:var(--surface);border-color:transparent;transform:scale(.985)}}
@keyframes rollInUp   {from{opacity:0;transform:translateY(55%);filter:blur(6px)}to{opacity:1;transform:none;filter:none}}
@keyframes rollOutUp  {to{opacity:0;transform:translateY(-55%);filter:blur(6px)}}
@keyframes rollInDown {from{opacity:0;transform:translateY(-55%);filter:blur(6px)}to{opacity:1;transform:none;filter:none}}
@keyframes rollOutDown{to{opacity:0;transform:translateY(55%);filter:blur(6px)}}
@keyframes pulse{50%{opacity:.35}}

.panel.on{animation:riseIn .42s var(--ease) both}                       /* view / tab switch */
.list.enter>*{animation:riseIn .5s var(--ease) both;                     /* list cascade, capped at 12 */
  animation-delay:calc(min(var(--n,12),12) * 28ms)}
.pick.pop{animation:pickIn .4s var(--ease)}                              /* just selected */
.pick.pop .check{animation:ckPop .5s var(--spring) both}
.num.bump{display:inline-block;animation:bump .45s var(--spring)}        /* a number changed */
.arrive{animation:settle .6s var(--spring) both}                         /* celebratory arrival */
.grid.enter>*{animation:tpIn .55s var(--spring) both;                    /* option grid ripples in */
  animation-delay:calc(var(--n) * 32ms + 60ms)}                          /* --n = col + row */
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
export function tint(el, rgb, {delay=0, a=.16, duration=1400}={}){
  if(REDUCE.matches) return;
  el.animate([{backgroundColor:`rgba(${rgb},${a})`},{backgroundColor:`rgba(${rgb},0)`}],{duration,easing:'ease-out',delay});
}

/* one indicator that glides to the selected item (segmented controls, tabs, grids) */
export function glideIndicator(box, {selector='[aria-selected="true"],[aria-pressed="true"]', cls='seg-ind'}={}){
  let ind = box.querySelector(`:scope > .${cls}`);
  if(!ind){ ind = document.createElement('span'); ind.className = cls; box.prepend(ind); }
  const on = box.querySelector(selector); if(!on) return;
  ind.style.width = on.offsetWidth + 'px';
  ind.style.transform = `translateX(${on.offsetLeft}px)`;
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
    animation:`rollOut${dir==='up'?'Up':'Down'} .38s var(--ease) both`});
  Object.assign(n.style, {display:'inline-block', animation:`rollIn${dir==='up'?'Up':'Down'} .5s var(--ease) both`});
  el.append(o, n);
  clearTimeout(el._rt); el._rt = setTimeout(()=>{ if(el.dataset.t === text) el.textContent = text; }, 650);
}
```

---

## 7. Signature interactions

### 7.1 FLIP reorder with direction colour (lists, tables, rankings)
Rows slide from their old position to their new one, **wash green if they moved up and red if they moved down**, fade in green when they enter, and sink away in red when they leave.

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
      el.animate([{opacity:0,transform:'translateY(6px)'},{opacity:1,transform:'none'}],{duration:450,easing:EASE});
      tint(el, TINT.up); return;
    }
    const dy = was.top - el.getBoundingClientRect().top;
    if(Math.abs(dy) > 1){
      el.animate([{transform:`translateY(${dy}px)`},{transform:'none'}],{duration:650,easing:EASE,fill:'backwards'});
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
               {opacity:0,transform:'translateY(14px)',backgroundColor:`rgba(${TINT.down},0)`}],
              {duration:900,easing:EASE}).onfinish = () => g.remove();
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
.menu{position:absolute;top:calc(100% + 8px);right:0;z-index:40;min-width:200px;padding:6px;border-radius:14px;
  background:rgba(255,255,255,.9);-webkit-backdrop-filter:blur(18px) saturate(1.7);backdrop-filter:blur(18px) saturate(1.7);
  border:1px solid rgba(8,32,79,.08);box-shadow:0 18px 44px rgba(8,32,79,.16),0 2px 8px rgba(8,32,79,.06);
  transform-origin:top right;opacity:0;visibility:hidden;pointer-events:none;transform:translateY(-6px) scale(.86);filter:blur(3px);
  transition:opacity .16s var(--ease),transform .2s var(--ease),filter .16s var(--ease),visibility 0s linear .2s}
.menu.open{opacity:1;visibility:visible;pointer-events:auto;transform:none;filter:none;
  transition:opacity .22s var(--ease),transform .5s var(--spring),filter .3s var(--ease),visibility 0s}
.menu.open>*{animation:piIn .4s var(--ease) both;animation-delay:calc(var(--i) * 45ms + 70ms)}
@keyframes piIn{from{opacity:0;transform:translateY(-5px)}to{opacity:1;transform:none}}
.menu-item{display:flex;width:100%;padding:10px 12px;border-radius:9px;border:0;background:none;text-align:left;
  font:500 14px var(--body);color:var(--ink);cursor:pointer;transition:background-color .15s var(--ease),transform .15s var(--ease)}
@media(hover:hover){.menu-item:hover{background:var(--surface-2)}}
.menu-item:active{transform:scale(.98)}
.menu-item.warn{color:var(--bad)}
.menu-sep{height:1px;background:var(--line);margin:4px 8px}
```
- Give each item `style="--i:N"` so the items stagger in.
- While the menu is open, the trigger keeps its hover look (`[aria-expanded="true"]`).
- Keep the menu on screen: measure it with transitions off (a `.measure` class) and nudge it in by 12px if it overflows.
- Close it on outside click, on Escape, and after choosing an item.

### 7.4 Modal and toast
```css
.modal{position:fixed;inset:0;z-index:50;display:flex;align-items:center;justify-content:center;padding:20px;
  background:rgba(8,32,79,.28);-webkit-backdrop-filter:blur(8px) saturate(1.2);backdrop-filter:blur(8px) saturate(1.2);
  opacity:0;transition:opacity .3s var(--ease)}
.modal.open{opacity:1}
.sheet{background:rgba(255,255,255,.96);border-radius:18px;max-width:420px;width:100%;padding:22px 24px 20px;
  box-shadow:0 24px 64px rgba(8,32,79,.22),0 2px 6px rgba(8,32,79,.06);
  transform:translateY(14px) scale(.96);opacity:0;transition:transform .45s var(--spring),opacity .3s var(--ease)}
.modal.open .sheet{transform:none;opacity:1}
@media(max-width:480px){.sheet .actions{flex-direction:column-reverse}.sheet .actions .btn{width:100%;padding:11px;font-size:15px}}

.toast{position:fixed;left:50%;bottom:calc(26px + env(safe-area-inset-bottom));z-index:60;pointer-events:none;
  transform:translate(-50%,16px) scale(.96);opacity:0;
  background:rgba(8,32,79,.86);-webkit-backdrop-filter:blur(14px) saturate(1.6);backdrop-filter:blur(14px) saturate(1.6);
  color:#fff;border-radius:10px;padding:10px 18px;font-size:13.5px;box-shadow:0 12px 32px rgba(16,24,40,.14);
  transition:opacity .3s var(--ease),transform .45s var(--spring)}
.toast.on{opacity:1;transform:translate(-50%,0) scale(1)}
```
- **Modal:** un-hide it, then add `.open` two animation frames later so the transition runs. Default focus goes to the safe button, and a destructive confirm button is solid `--bad`. Wrap it in a Promise-returning `ask({title, body, yes, no})`.
- **Toast:** auto-hides after 2.6s; a new toast replaces the current one.

### 7.5 Pinned toolbar that collapses into a glass card
The control row is `position:sticky` under the header. When it pins, it turns into a floating `.glass-card`: it moves down 8px, tightens its padding (`--spring`, .5s), and colours fade in (`--ease`, .35s).
- Detect pinning with an **IntersectionObserver on a 0-height sentinel** placed just above the toolbar, with `rootMargin: -${headerHeight+1}px 0 0 0`. Don't use scroll listeners.
- **Freeze the footprint:** set the sticky wrapper's `height` to the open card's height, and let the card shrink inside it, so pinning never moves the page. To re-measure while pinned, turn transitions off (a `.measuring` class), un-pin, measure, re-pin, and force a reflow.
- A context label (the current item's name) fades in when pinned: `opacity 0 → 1`, `translateY(8px) scale(.94) → none`, `blur(4px) → none`, with a .08s delay.

### 7.6 Compact control that expands into a full picker, inside the same card
```css
.expand{display:grid;grid-template-rows:0fr;transition:grid-template-rows .5s var(--spring)}
.expand>div{overflow:hidden;min-height:0}
.open .expand{grid-template-rows:1fr}
.expand .content{opacity:0;transform:translateY(-8px) scale(.98);filter:blur(4px);transform-origin:top center;
  transition:opacity .2s var(--ease),transform .3s var(--ease),filter .25s var(--ease)}
.open .expand .content{opacity:1;transform:none;filter:none;
  transition:opacity .35s var(--ease) .05s,transform .55s var(--spring) .05s,filter .4s var(--ease) .05s}
.chev{transition:transform .45s var(--spring),color .25s var(--ease)}
.open .chev{transform:rotate(180deg);color:var(--accent)}
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
- **Progress bars:** `transition:width .6s var(--ease)`. Stacked segments sit on a `--surface-2` track, 8px high, radius 99px.
- **Progress rings (SVG):** use `pathLength="100"` with `stroke-dasharray:100`, and animate `stroke-dashoffset .8s var(--ease)`.
- **Icon or avatar swap:** `ckPop .55s var(--spring)`.
- **Haptics (Android):** `navigator.vibrate` with ~4ms for a wheel tick, 6ms for a tap, 9ms for a selection, and `[12,60,24]` for a celebration. Only use it when `(pointer:coarse)` matches.

---

## 8. Layout rules

- Use `minmax(0,1fr)`, never plain `1fr`, so long text ellipsizes instead of stretching the grid.
- Use **container queries** for components that live in variable-width columns. They drop secondary text (records, long names) as they narrow instead of wrapping.
- Spacing scale: 4 / 6 / 8 / 10 / 14 / 18 / 28px. Cards in a stack are 10px apart; sections are 26–48px apart.
- Side rails or ads in wide margins: let the main column take whatever the rails leave, capped at its maximum (`--wrap:min(1240px, calc(100vw - 400px))`), so there's never dead space outside the rails.
- Reserve the size of anything that loads later (ads, images, charts) before it arrives.

---

## 9. Accessibility

- Use real `<button>`s. Use `aria-pressed` / `aria-selected` for toggles and tabs, `aria-expanded` + `aria-controls` for disclosure, and `role="menu"` / `menuitem` for menus.
- `:focus-visible` rings are always visible for keyboard users and never appear on click.
- For round items, use a double ring instead of an outline: `box-shadow:0 0 0 2px #fff,0 0 0 4px var(--accent)`.
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

---

## 11. Ship checklist

- [ ] Only tokens are used: no hard-coded colours, radii, shadows or curves.
- [ ] Only `transform`, `opacity`, `filter`, colours, box-shadow and `grid-template-rows` are animated. Width and height are animated only on `position:fixed` copies.
- [ ] Every interactive element has hover (inside `@media(hover:hover)`), `:active` scale, `:focus-visible` and no tap highlight.
- [ ] Glass appears only on floating layers, with saturate > 1 and `-webkit-` twins.
- [ ] Moves animate from their old position. Hand-offs between copies and real elements are invisible.
- [ ] Up/down changes wash green/red, and arrivals wash accent.
- [ ] Tested on iOS Safari (a real device or a WebKit build), not just Chrome: scroll snapping, the ends of scrollers, sticky elements and blur.
- [ ] Reduced motion leaves a fully usable UI.
- [ ] No layout shift on load, on pinning, when switching tabs or when async content arrives.
