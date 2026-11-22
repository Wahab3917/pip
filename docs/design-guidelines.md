# Pip — Design Guidelines

Nov 1, 2026 · Abdul Wahab

This is the single source of truth for how Pip looks, sounds, and speaks. It's a working draft meant to be edited freely. The main agent spec stays design-free, so nothing here blocks the build; when this doc is final, a design pass applies it through the app's theme layer.

> **As built (2026-11-22):** applied in the design pass, with these decisions on top: no categories,
> reminders, filter chips, Plant / Let go or evening nudge (Pip only saves ideas for now; Plant / Let go
> return with connectors); the note's bottom sheet opens a full-screen editor for editing; Hey Pip's
> sensitivity and battery setting live on a separate Settings page; the logo is "Sunflower Seed" by Amethyst
> Studio (Noun Project, CC BY 3.0), a paper seed with deep-green lines, instead of the speech-bubble seed below.
> See CLAUDE.md → "Design pass".

## Design principles

- **Invisible by default.** Most captures happen with the screen off. The app is for reviewing, and its best state is closed.
- **A pocket notebook, not an app.** Warm paper, ink-black text, one green. No dashboards, no stats, no shadows.
- **Your words come first.** Thoughts are set in a serif because they're yours; the interface around them stays quiet.
- **One idea everywhere.** The seed shape is the logo, the dot of the i, and the capture button.
- **Sound is the interface.** The three cues matter more than any screen and get the most design care.

## Voice and copy

Pip's language comes from the seed story: thoughts are **caught**, wait in your **pocket**, and are either **planted** or **let go**. The name and wordmark are lowercase in the logo, like wait, what?; in sentences it's "Pip".

| Word | Meaning | Where it shows |
| --- | --- | --- |
| Catch | Pip records a thought | "Pip caught that" after every capture |
| Pocket | Thoughts you haven't dealt with yet | The home screen's title |
| Plant | Send a thought to a connected app (Mindly first) | Swipe right on a thought |
| Let go | Archive a thought you don't need | Swipe left on a thought |
| Missed | Pip heard the wake word but no thought | The low tick sound |

**Copy samples**

- Listening notification: "Pip is listening" · action: "Pause for 1 hour"
- After a capture: "Pip caught that"
- Transcription running: "Writing it down…"
- Failure: "Pip couldn't write that down. The recording is safe."
- Reminder: "You asked Pip to remind you" + the thought
- Evening nudge (optional): "3 thoughts in your pocket"
- Empty pocket: "Nothing in your pocket. Say "Hey Pip" whenever something comes up."
- After planting: "Planted in Mindly · connects to 2 things"

**Tone rules**

- Confirmations are six words or fewer.
- No exclamation marks, no emoji, and never the word "AI".
- Pip refers to itself by name, never "I". It's a little companion, not a chatbot.
- Errors always say what's safe before what went wrong.

## Sound and haptics

The three cues share one timbre, a soft wooden pluck like a kalimba, so they sound like one family. The app ships with placeholder tones; these are the targets for the final sounds.

| Cue | Meaning | Notes | Length | Haptic |
| --- | --- | --- | --- | --- |
| Listening | "Go ahead" | Two rising notes, a fifth apart | ~180 ms | One short tap |
| Caught | "Got it" | The same two notes, falling and settling | ~250 ms | Two soft taps |
| Missed | "Didn't catch that" | One low, muted tick | ~100 ms | One dull tap |

- No cue is louder than a normal notification; Pip should never startle anyone nearby.
- In silent or vibrate mode, haptics only.

## Logo

The mark is a single seed: an almond shape whose pointed tip curves out like the tail of a speech bubble. Seed and voice in one shape.

- **Wordmark:** "pip" in lowercase, where the dot of the i is the seed in Sprout green.
- **App icon:** the Paper-colored seed on a Sprout square, plus a monochrome version for Android's themed icons.
- **Capture button:** the same seed inside a soft Sprout circle.

A sketch, not final art: it shows the one-seed idea and the Pocket screen's rough layout. Colors in the sketch are approximate; the Color section is the source of truth.

## Color

Warm paper, ink-black text, and one seedling green. If Mindly has its own brand green, swap it in for Sprout so the two apps read as siblings.

| Token | Light | Dark | Used for |
| --- | --- | --- | --- |
| Paper | #F6F3EC | #161513 | Background |
| Card | #FBF9F4 | #201F1C | Thought cards, sheets |
| Ink | #1D1C1A | #EDE9E1 | Thought text, titles |
| Graphite | #6B6760 | #9C978D | Timestamps, secondary text |
| Hairline | #E4DFD5 | #2E2C28 | Dividers, card borders |
| Sprout | #3E8E5E | #6CC08A | The seed, listening state, Idea, Plant |
| Amber | #C98A2B | #E0A94F | Reminders |
| Clay | #B4553F | #D9785F | Missed, errors |

Categories are small colored dots, never filled backgrounds: Idea is Sprout, Reminder is Amber, Task is Ink, Shopping is Graphite, and Thought has no dot. Dark mode is warm charcoal, never pure black.

## Typography, icons and shapes

| Role | Font | Size | Notes |
| --- | --- | --- | --- |
| Thought text | Newsreader | 19sp, line height 1.4 | Free on Google Fonts; up to 3 lines on a card |
| Interface | Instrument Sans | 15sp | Buttons, chips, settings |
| Metadata | Instrument Sans | 12sp | Tabular numbers for times |

- Both fonts are bundled in the app so they work offline.
- Icons: rounded Material Symbols at a 1.75 stroke.
- Cards: 16dp corners and a hairline border instead of shadows.
- Spacing on an 8dp grid.

## Motion

- **Listening:** the seed breathes, scaling gently with your voice level.
- **Caught:** the seed drops into the pocket in about 200 ms.
- **Everything else:** a simple fade.
- All motion respects Android's reduce-motion setting.

## Screen layouts

The main spec defines what each screen does; this section defines how it looks.

**Pocket (home)**

- Top bar: the small "pip" wordmark on the left; a status pill on the right (Sprout dot + "listening", or Graphite + "paused").
- Filter chips under the top bar; the selected chip gets a Sprout tint.
- Thought cards grouped by day, with quiet day labels (Today, Yesterday, then dates).
- The seed capture button sits centered in the thumb zone at the bottom.

**Thought card**

- Thought text in Newsreader, up to 3 lines.
- One metadata line in Graphite: time · category dot and label · reminder chip in Amber · "in Mindly" once planted.
- While transcribing: a soft shimmer bar with "Writing it down…" and a play button on the right.

**Swipe actions.** Swiping right reveals a Sprout background with the seed icon (Plant); swiping left reveals a Graphite background (Let go).

**Detail sheet.** A bottom sheet on Card color. Large editable serif text at the top, the waveform player under it, then category and reminder rows.

**Capture overlay.** Full-screen Paper background: the seed breathing in the center, "Listening…" under it, a thin live waveform, and "Tap to finish" near the bottom.

**Onboarding.** One idea per screen, the seed illustration at the top, a single primary button at the bottom.

**Settings.** Plain grouped list, no icons beside rows except the seed for the Listening group.

## Handoff to code

The app is built with neutral placeholders behind a small theme layer, so applying this doc means changing values in a handful of files, not rewriting screens.

| What this doc defines | Where it lands in the app | Placeholder until the design pass |
| --- | --- | --- |
| Color tokens (Paper, Card, Ink, Graphite, Hairline, Sprout, Amber, Clay) | `ui/theme/PipColors.kt` | Material 3 defaults |
| Type roles (thought, body, meta) | `ui/theme/PipType.kt` + `res/font` | System font |
| Shapes and spacing | `ui/theme/PipShapes.kt` | Material 3 defaults |
| The three cues | `res/raw/cue_listening`, `cue_caught`, `cue_missed` | Simple generated tones |
| All copy | `res/values/strings.xml` | Plain working text |
| Logo and app icon | `res/drawable` + adaptive icon | Default Android icon |
| Motion | `ui/theme/PipMotion.kt` | No animation |

Screens only ever read from these names, never hard-coded values. When this doc is final, the design pass is: update these files, then polish each screen's layout to match Screen layouts.
