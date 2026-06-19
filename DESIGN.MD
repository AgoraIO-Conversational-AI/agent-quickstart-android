---
# Agora Design System — DESIGN.md
# ================================================
# YAML design tokens followed by free-form design intent description.

colors:
  brand:
    cyan:         "#00C2FF"   # Primary brand cyan — dominant in dark mode UI
    blue:         "#075C9A"   # Deep brand blue — secondary
    cyan-light:   "#A0FAFF"   # Aura tint / gradient accent
    violet:       "#C46FFB"   # Brand violet — Aura / accent
    violet-dark:  "#9443C7"   # Deeper violet shade

  neutral:
    950:  "#0F0F0F"
    900:  "#171717"   # Sidebar background (dark)
    800:  "#2C2C2C"
    700:  "#484848"
    600:  "#626262"
    400:  "#B3B3B3"
    300:  "#D6D6D6"
    200:  "#E7E6E6"   # Default border
    100:  "#F1F0F0"
    50:   "#FCF9F8"   # Warm off-white — light mode background

  semantic:
    light:
      background:         "#FDFBFC"
      foreground:         "#0A0A0A"
      card:               "#FFFFFF"
      primary:            "#00658D"
      primary-foreground: "#FDFBFC"
      muted:              "#F8F3F1"
      muted-foreground:   "#757575"
      border:             "#E7E6E6"
      success:            "#308D2C"
      destructive:        "#E7000B"
      warning:            "#F59E0B"
      sidebar:            "#FDFBFC"
      sidebar-border:     "#E7E6E6"

    dark:
      background:         "#000000"
      foreground:         "#FDFCFB"
      card:               "#0A0A0A"
      primary:            "#00C2FF"
      primary-foreground: "#171717"
      muted:              "#212121"
      muted-foreground:   "#B3B3B3"
      border:             "#383838"
      success:            "#42B03D"
      destructive:        "#DC383E"
      warning:            "#F59E0B"
      sidebar:            "#171717"
      sidebar-border:     "#212121"

  status:
    warning:  "#DCA311"
    error:    "#DE344A"
    success:  "#42B03D"

  chart:
    1: "#F6339A"
    2: "#FF6900"
    3: "#00A6F4"
    4: "#8E51FF"
    5: "#00BC7D"

typography:
  fonts:
    brand:     "Jokker"           # Proprietary — Atipo Foundry. Not on Google Fonts.
    sans:      "Instrument Sans"  # Google Fonts. App UI font.
    mono:      "Space Mono"       # Google Fonts. Code and token display.

  weights:
    regular:   400
    medium:    500
    semibold:  600
    bold:      700

  scale:
    xs:   "0.75rem"    # 12px
    sm:   "0.875rem"   # 14px
    base: "1rem"       # 16px
    lg:   "1.125rem"   # 18px
    xl:   "1.25rem"    # 20px
    2xl:  "1.5rem"     # 24px
    3xl:  "1.875rem"   # 30px
    4xl:  "2.25rem"    # 36px
    brand-display: "164px"  # Used in Brand Guidelines full-bleed slides

  tracking:
    tight:   "-0.01em"
    normal:  "-0.01em"
    wide:    "0.02em"   # Brand running headers

  leading:
    none:    1
    tight:   1.1
    normal:  1.4
    relaxed: 1.5

spacing:
  base: "0.25rem"    # 4px
  scale:
    1:  "0.25rem"    # 4px
    2:  "0.5rem"     # 8px
    3:  "0.75rem"    # 12px
    4:  "1rem"       # 16px
    5:  "1.25rem"    # 20px
    6:  "1.5rem"     # 24px
    8:  "2rem"       # 32px
    10: "2.5rem"     # 40px
    12: "3rem"       # 48px

  layout:
    content-max-width:       "1200px"
    sidebar-expanded:        "241px"
    sidebar-collapsed:       "49px"
    content-horizontal-pad:  "1rem"    # px-4
    section-gap:             "1.25rem" # gap-5

radii:
  sm:   "0.125rem"   # 2px
  md:   "0.375rem"   # 6px
  lg:   "0.5rem"     # 8px  — default --radius
  xl:   "0.75rem"    # 12px
  2xl:  "1rem"       # 16px
  full: "9999px"

shadows:
  2xs: "0 1px 3px 0px hsl(0 0% 0% / 0.05)"
  xs:  "0 1px 3px 0px hsl(0 0% 0% / 0.05)"
  sm:  "0 1px 3px 0px hsl(0 0% 0% / 0.1), 0 1px 2px -1px hsl(0 0% 0% / 0.1)"
  md:  "0 1px 3px 0px hsl(0 0% 0% / 0.1), 0 2px 4px -1px hsl(0 0% 0% / 0.1)"
  lg:  "0 1px 3px 0px hsl(0 0% 0% / 0.1), 0 4px 6px -1px hsl(0 0% 0% / 0.1)"
  xl:  "0 1px 3px 0px hsl(0 0% 0% / 0.1), 0 8px 10px -1px hsl(0 0% 0% / 0.1)"

motion:
  duration:
    fast:    "150ms"
    default: "200ms"
    slow:    "300ms"
  easing:
    default: "ease"
    in-out:  "ease-in-out"
  transitions:
    color:   "color 150ms ease, background-color 150ms ease"
    sidebar: "width 300ms ease-in-out"
    dropdown: "opacity 150ms ease, transform 150ms ease"

elevation:
  sidebar:  1
  dropdown: 10
  modal:    100
---

# Agora — Design Intent

## Brand Identity

Agora is a real-time communications infrastructure company. Its visual identity is built around precision, power, and a sense of luminous energy. The brand feels technical but not cold — it has personality through the **Jokker** typeface and the **Aura** visual motif.

The brand operates across two visual registers:

1. **Brand / Marketing** — dark, high-contrast, rich cyans and purples. Jokker type. Full-bleed imagery. The Aura animated form.
2. **Product / Console** — clean, functional, minimal. Instrument Sans. Warm off-white light mode or pure black dark mode. Lucide icons. shadcn/ui component patterns.

## Color Philosophy

**Cyan is the hero.** `#00C2FF` (dark mode) / `#00658D` (light mode) is the primary interaction color — used for links, active nav items, primary buttons, focus rings, and progress indicators. It is vivid and unmistakably Agora.

**The palette is dualistic.** In dark mode (which Agora's brand guidelines favor), backgrounds are pure black `#000000` with layered near-blacks (`#0A0A0A` for cards, `#171717` for the sidebar). In light mode, the background is a warm off-white `#FDFBFC` — subtly warm, never sterile.

**Violet is the accent.** `#C46FFB` and `#9443C7` appear in the Aura and in data visualization, never as interactive UI elements.

**Neutrals are warm.** The off-white `#FCF9F8` has a warm pinkish cast. Even the near-black `#0F0F0F` reads slightly warm. This warmth differentiates Agora from cold, grey-based tech brands.

## Typography

**Jokker** is the brand's signature typeface. Its geometric construction, elevated x-height, and subtle inktraps give it both legibility and personality. Bold Jokker at large scale (40–164px) is used for section titles and display moments in the brand guidelines deck. Medium Jokker at 20px with `letter-spacing: 0.02em` handles running headers and body copy in brand materials.

In the **product UI**, Instrument Sans handles all text. It is a clean, humanist sans-serif that pairs naturally with Jokker while remaining highly legible at small sizes. Space Mono is used exclusively for code-like content: App IDs, token previews, and monospace data.

## Surfaces & Cards

The console uses a **transparent card pattern** — cards have `background: transparent` with a `border: 1px solid var(--border)`. Card headers use `background: rgba(muted / 0.4)` for a subtle fill that distinguishes the header from the body. Inner data sections use `bg-muted/40` on `rounded-lg` or `rounded-xl` containers.

No heavy drop shadows. No colored borders. No left-accent bars. Borders are the primary elevation signal.

## The Aura

The Aura is Agora's proprietary animated visual form — a layered, fluid, organic shape built from parametric layers. It appears on:
- The homepage hero video (the primary brand touchpoint)
- Marketing collateral and slide decks

Colors are drawn from the brand palette (Cyan, Cyan Light, Violet, dark navy). The Aura has editable parameters: layer count, animation speed, frequency. It is **never used in the product console UI** — it is a brand-layer element only.

## Motion & Interaction

Motion is functional, not decorative. Transitions are fast (150–200ms) and use `ease` or `ease-in-out`. The sidebar uses a width transition at 300ms. Dropdowns use `animate-in fade-in-0 zoom-in-95 slide-in-from-top-2` from Tailwind's animation utilities. Buttons use a subtle `translate-y-px` on `:active` — a 1px press feeling.

Hover states shift backgrounds to `bg-accent` (muted fill). There are no color inversions, scale transforms, or glow effects on hover.

## Iconography

Lucide React is the sole icon library in the product. Icons are always stroke-based (no fill), consistent at 14×16px in navigation, 16px inline with text, 20px for feature icons. The Agora wordmark SVG is used as the in-app logo via `currentColor`.

## Layout Principles

The console layout is a classic sidebar + main content shell. The sidebar is collapsible (241px → 49px) with a smooth width transition. Content has a `max-width: 1200px` centered horizontally with `px-4` padding. Sections use `gap-5` (20px) between major blocks. The layout prioritizes information density without feeling cramped.

## Writing Style

Copy is direct, lowercase-first (sentence case), and addressed to the developer as "you." Numbers are localized. Dates use `dd MMM, yyyy`. CTAs are action verbs: "Create", "View", "Configure", "Add". Empty states are plain and factual. No exclamation points in UI chrome.