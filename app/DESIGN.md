---
name: BetterSaid
colors:
  surface: '#fbf9f4'
  surface-dim: '#dbdad5'
  surface-bright: '#fbf9f4'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3ee'
  surface-container: '#f0eee9'
  surface-container-high: '#eae8e3'
  surface-container-highest: '#e4e2dd'
  on-surface: '#1b1c19'
  on-surface-variant: '#444748'
  inverse-surface: '#30312e'
  inverse-on-surface: '#f2f1ec'
  outline: '#747878'
  outline-variant: '#c4c7c7'
  surface-tint: '#5f5e5e'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#1c1b1b'
  on-primary-container: '#858383'
  inverse-primary: '#c8c6c5'
  secondary: '#4a654e'
  on-secondary: '#ffffff'
  secondary-container: '#c9e8cb'
  on-secondary-container: '#4e6952'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#400102'
  on-tertiary-container: '#c9675c'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e5e2e1'
  primary-fixed-dim: '#c8c6c5'
  on-primary-fixed: '#1c1b1b'
  on-primary-fixed-variant: '#474746'
  secondary-fixed: '#cceace'
  secondary-fixed-dim: '#b0ceb2'
  on-secondary-fixed: '#07200f'
  on-secondary-fixed-variant: '#334d38'
  tertiary-fixed: '#ffdad5'
  tertiary-fixed-dim: '#ffb4aa'
  on-tertiary-fixed: '#400102'
  on-tertiary-fixed-variant: '#7c2c25'
  background: '#fbf9f4'
  on-background: '#1b1c19'
  surface-variant: '#e4e2dd'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  accent-handwritten:
    fontFamily: Bricolage Grotesque
    fontSize: 20px
    fontWeight: '400'
    lineHeight: 28px
  label-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 40px
  container-margin: 20px
  gutter: 16px
---

## Brand & Style

The design system is built on the philosophy of "Emotional Safety for Language Expression." It aims to transform the high-anxiety task of language learning into a meditative, creative journaling experience. The target audience consists of professional and creative adult learners who seek a premium, distraction-free environment that feels more like a sketchbook than a textbook.

The aesthetic blends **Minimalism** with **Tactile/Skeuomorphic** nuances. It utilizes a "Digital Paper" metaphor, where the UI behaves like high-quality stationery. The style is defined by generous whitespace, hand-drawn vector accents that provide a "human" touch, and a sophisticated, non-competitive atmosphere. It avoids the gamified, high-pressure visuals of traditional language apps in favor of a calm, reflective, and supportive interface.

## Colors

The palette is anchored by a soft, off-white background that mimics unbleached paper, reducing eye strain and creating a warm foundation.

- **Primary (Black Ink):** Used for core text and "hand-drawn" structural lines. It is not a pure black, but a deep charcoal that feels like ink.
- **Secondary (Sage Green):** Represents growth, correctness, and safety. Used for positive feedback and progress indicators.
- **Tertiary (Coral):** Used sparingly for gentle corrections and highlighting areas of focus without being punitive.
- **Accents (Sky Blue & Warm Yellow):** Used for creative highlights, categories, and interactive "aha!" moments.

Background surfaces should utilize a very subtle, low-opacity noise texture (2-3% opacity) to enhance the paper-like tactile feel.

## Typography

The typography system uses a dual-font approach to balance professional clarity with creative expression.

**Inter** serves as the functional workhorse, providing high legibility for menus, instructions, and settings. Its neutral, systematic nature keeps the UI grounded and professional.

**Bricolage Grotesque** is used as the "handwritten" surrogate for key linguistic visuals, sentence analysis, and personal notes. Its quirky, characterful forms provide the "Mirror" aspect of the system—reflecting the user's unique voice.

- Use **Inter** for all UI controls and long-form instructional content.
- Use **Bricolage Grotesque** for captured speech strings, highlighted grammar points, and "sketched" annotations.

## Layout & Spacing

This design system employs a **Fluid Grid** model optimized for mobile-first interaction.

- **Mobile (Default):** 4-column grid with 20px side margins. Focus is on a single vertical flow to minimize cognitive load.
- **Desktop/Tablet:** 12-column grid with a max-width container of 1040px to maintain the "journal" aspect ratio.

Spacing follows a strict 8px rhythm. The "Journal feel" is achieved through intentional "Air"—extra-large vertical padding (`xl`) between distinct conversation sessions or thought blocks. Elements should never feel crowded; if in doubt, increase whitespace.

## Elevation & Depth

To maintain the paper-like aesthetic, traditional drop shadows are avoided. Depth is communicated through:

1.  **Tonal Layers:** Using slight variations of the neutral off-white to define "Paper Stacks."
2.  **Hand-Drawn Outlines:** Interactive elements use a 1.5px or 2px solid border (`#1A1A1A`) with a slightly irregular "variable width" feel to mimic ink on paper.
3.  **Soft Inset Shadows:** Only used for input fields to create a "pressed into paper" effect.
4.  **Flat Overlays:** High-priority modals use a solid, 100% opaque background with a thick black border, rather than a blurred background, keeping the look graphic and editorial.

## Shapes

The shape language is "Softly Organic." While based on a `0.5rem` (8px) standard radius, the system encourages the use of **Squiggles** and **Hand-Drawn Enclosures** for highlighting text.

- **Buttons & Cards:** Use a uniform `8px` corner radius.
- **Input Fields:** Use a `12px` radius to feel more approachable.
- **Highlight Strokes:** Use vector-based "circles" around corrected words that look like they were drawn with a fine-liner pen. These should be slightly rotated (1-2 degrees) to avoid mechanical perfection.

## Components

### Buttons
Primary buttons are solid Sage Green or Black with high-contrast text. They feature a 2px "Ink" border. Secondary buttons are outlined only. The "Press" state should physically shift the button down 2px (Removing a faux-bottom-shadow) rather than changing color.

### Cards
Cards are used to group conversation history. They use a slightly lighter background than the main surface or a thin black stroke. They should not have shadows; instead, use "Paper Stacking"—a card behind another card with a 4px offset to show history.

### Inputs
Text inputs for speaking prompts should feel like a line in a notebook. Use a simple bottom-border that glows Sky Blue when active. The cursor should be a soft, blinking "Ink" block.

### Feedback Chips
Small, rounded pills used for grammar categories (e.g., "Tense," "Preposition"). Use light tints of the accent colors (Sage, Coral, Blue) with dark text.

### The Mirror (Special Component)
A central, rounded-square visual area where the user's spoken text appears in **Bricolage Grotesque**. It should be framed by a subtle, hand-sketched vector border that pulses gently when the app is "listening."