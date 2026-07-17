---
version: alpha
name: ListenStudy Quiet Reader
description: A calm, premium reading surface with an anchored audio dock and uncompromised Korean text legibility.
colors:
  primary: "#3D5A50"
  onPrimary: "#FFFFFF"
  primaryContainer: "#DCE8E1"
  onPrimaryContainer: "#18372E"
  background: "#F7F5F0"
  surface: "#FFFFFC"
  surfaceVariant: "#F0EEE8"
  textPrimary: "#1C1B18"
  textSecondary: "#5F5D57"
  outline: "#77746D"
  outlineSubtle: "#E1DED6"
  readingCurrent: "#F2E7C9"
  onReadingCurrent: "#5B4515"
  success: "#2F6B4F"
  warning: "#7A570B"
  error: "#B3261E"
  errorContainer: "#FFF1F0"
typography:
  screenTitle:
    fontFamily: Android Sans Serif
    fontSize: 24px
    fontWeight: 600
    lineHeight: 32px
    letterSpacing: "-0.1px"
  sectionTitle:
    fontFamily: Android Sans Serif
    fontSize: 18px
    fontWeight: 600
    lineHeight: 26px
  readingBody:
    fontFamily: Android Sans Serif
    fontSize: 18px
    fontWeight: 400
    lineHeight: 30px
    letterSpacing: "0px"
  readingCurrent:
    fontFamily: Android Sans Serif
    fontSize: 18px
    fontWeight: 500
    lineHeight: 30px
    letterSpacing: "0px"
  body:
    fontFamily: Android Sans Serif
    fontSize: 16px
    fontWeight: 400
    lineHeight: 25px
  metadata:
    fontFamily: Android Sans Serif
    fontSize: 14px
    fontWeight: 400
    lineHeight: 21px
  label:
    fontFamily: Android Sans Serif
    fontSize: 15px
    fontWeight: 600
    lineHeight: 22px
rounded:
  small: 10px
  medium: 14px
  large: 20px
  dock: 28px
  pill: 999px
spacing:
  xxs: 2px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  xxl: 32px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
    typography: "{typography.label}"
    rounded: "{rounded.small}"
    padding: 16px
    height: 48px
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "{rounded.small}"
    padding: 16px
    height: 48px
  reader-surface:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.textPrimary}"
    typography: "{typography.readingBody}"
    rounded: "{rounded.large}"
    padding: 20px
  reader-current-sentence:
    backgroundColor: "{colors.readingCurrent}"
    textColor: "{colors.onReadingCurrent}"
    typography: "{typography.readingCurrent}"
    rounded: "{rounded.small}"
    padding: 12px
  player-dock:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.textPrimary}"
    rounded: "{rounded.dock}"
    padding: 16px
  card-flat:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.textPrimary}"
    rounded: "{rounded.medium}"
    padding: 16px
---

## Overview

ListenStudy is a reading-first product, not a control dashboard. Its primary surface is a **focused reader**; audio controls form a stable secondary layer and technical settings remain visually quiet. The visual direction is “warm paper, restrained sage, precise controls.” Premium quality comes from hierarchy, rhythm, and accessibility rather than gradients, glass effects, excessive shadows, or decorative cards.

The product must feel calm during long Korean reading sessions. The document title and text own the screen. The brand name appears mainly in launch, library, and identity surfaces—not as the largest title on every reader screen.

## Colors

- Use warm neutral `background` and ivory `surface` to reduce the sterile feeling of pure white while preserving strong text contrast.
- `primary` is a low-saturation deep sage used only for key actions, progress, focus, and selection.
- `readingCurrent` is a parchment highlight. Pair it only with `onReadingCurrent`; never use it for warnings.
- Standard text pairs:
  - `textPrimary` on `background` or `surface`
  - `textSecondary` on `background` or `surface`
  - `onPrimary` on `primary`
- Status colors must be accompanied by an icon or explicit label. Never communicate state by color alone.
- Avoid bright blue, yellow highlighter blocks, pure-black body text, and pure-white full-screen backgrounds.
- Dynamic color is off by default so the reader contrast and current-sentence treatment remain predictable.

## Typography

- Android system sans is intentional for reliable Korean glyph coverage, fast startup, and user font-scale compatibility. A bundled brand font may be considered later only after Korean fallback and APK size validation.
- Reading text defaults to `18sp / 30sp`; user-adjustable presets should eventually cover 16, 18, 20, and 22sp.
- Current sentences use Medium rather than Bold to avoid changing line wraps during playback.
- Use SemiBold sparingly for screen titles, section titles, and primary labels. Avoid repeated ExtraBold.
- Metadata must never be smaller than 14sp in core flows.
- All text must reflow at 200% font scale without clipping inside fixed-height containers.

## Layout

- Base grid: 4dp; primary rhythm: 8dp.
- Phone horizontal padding: 16dp. Expanded layouts: 24–32dp.
- Reader content max width: 720dp, centered on tablets.
- Interactive targets: minimum 48×48dp. The play/pause control is 56dp.
- Prefer one clear top-bar action plus overflow over three compact text buttons.
- Reader composition:
  1. document title top bar,
  2. compact reading progress,
  3. dominant reading surface,
  4. anchored playback dock.
- Library composition: title + import action, then a low-elevation list. Deletion belongs in item overflow.
- Settings composition: playback/voice first, device engine second, Cloud advanced settings third, storage last.
- Voice picker uses one scrollable result list; do not stack multiple fixed-height LazyColumns.

## Elevation & Depth

- Default cards are flat with a 1dp subtle outline.
- Reader surface may use 0–1dp elevation.
- Playback dock is the only persistent floating surface and may use 3–6dp shadow elevation.
- Selection uses container color and outline, not increased elevation.
- Avoid applying shadows to every list item or settings section.

## Shapes

- Controls: 10dp.
- List/options/settings cards: 14dp.
- Reader paper: 20dp.
- Floating player dock and modal sheet: 28dp.
- Pills and progress tracks: fully rounded.
- Do not invent new radii per component; use the four semantic levels.

## Components

- `LsScaffold`: owns background, safe drawing insets, system-bar appearance, and optional bottom dock.
- `LsTopBar`: navigation, document/screen title, one primary action, and overflow.
- `LsButton`/`LsIconButton`: enforce 48dp minimum target and clear enabled/loading/destructive states.
- `LsCard`: flat by default, subtle outline, 16dp internal padding.
- `LsReaderSentence`: 18sp/30sp, minimum 48dp hit area, selected semantics, explicit click label.
- `LsPlayerDock`: progress, previous/play/next always visible; speed and voice use a secondary expandable layer.
- `LsStatusPill`: icon + label + semantic state; color is secondary.
- `LsSettingRow`: label, supporting text, current value, and one trailing action.
- `LsOptionRow`: whole-row selection with radio semantics; preview remains a separate 48dp action.
- `LsFeedbackBanner`: feedback sits next to the initiating action and supports live-region semantics.
- `LsEmptyState`: concise title, one sentence, one primary action.

Motion durations: fast 100ms, standard 180ms, emphasized 280ms. Respect reduced-motion settings; do not pulse playback state or animate decoration continuously.

## Do's and Don'ts

### Do

- Make the document title and reading text visually dominant.
- Keep previous/play/next available without expanding the dock.
- Use spacing and typography before adding cards or colors.
- Validate at 320dp width, landscape, 200% font scale, dark theme, and TalkBack.
- Keep progress visible as both a bar and readable percentage/position.
- Keep destructive actions separated and confirmed or undoable.

### Don't

- Do not use 34dp controls or tiny text buttons for primary navigation.
- Do not repeat app name, status, speed, and progress in multiple layers.
- Do not hardcode `Color(...)`, arbitrary radii, or arbitrary elevation in screen code.
- Do not show engine package names or Cloud implementation details in the default flow.
- Do not use fixed-height nested lists in bottom sheets.
- Do not force-scroll while the user is manually reading elsewhere.
- Do not add gradients, glassmorphism, ornamental icons, or equal-weight card grids.
