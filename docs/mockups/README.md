# Design mockups

Full-screen mockups of Arro-POS in four different looks, drawn so the shop
owner can point at the one they like. The app currently ships with
**Paper & Ink** (01).

| File | Look | Mood |
| --- | --- | --- |
| `01-paper-and-ink-sell.png` | Paper & Ink — **shipped** | Ink blue on warm paper. Calm all day, easy on the eyes under shop lighting. |
| `02-market-green-sell.png` | Market Green | Deep forest green on a warm off-white. Feels like a grocery. |
| `03-warm-clay-add-item.png` | Warm Clay | Terracotta accents on cream. Friendlier, warmer; also shows the Easy mode wizard. |
| `04-slate-night-sell.png` | Slate Night | A dark counter theme for night shifts. Not implemented yet — the app is light-only today. |

## Applying a look

Every colour in the app comes from one file:
`app/src/main/java/com/example/ui/theme/Color.kt`. Nothing else needs to
change — the names (`BrandPrimary`, `LightBackground`, `StatusGreen`, …) stay
the same, so screens pick up the new palette automatically.

The values for each mockup are:

| Name | 01 Paper & Ink | 02 Market Green | 03 Warm Clay | 04 Slate Night |
| --- | --- | --- | --- | --- |
| `BrandPrimary` | `#FF1F4E79` | `#FF1F6B3B` | `#FF9A4A22` | `#FF7FB3E8` |
| `BrandPrimaryDark` | `#FF163A5A` | `#FF154C29` | `#FF6F3417` | `#FFA9CCF2` |
| `BrandPrimaryLight` | `#FFD6E4F0` | `#FFD8EADF` | `#FFF3DCCE` | `#FF2B3A4C` |
| `BrandAccent` | `#FF9A5B22` | `#FF8A5A12` | `#FF7A3E12` | `#FFE0A03C` |
| `BrandSurface` | `#FFE9F0F7` | `#FFE5F1E8` | `#FFF6E8DE` | `#FF1E2B3A` |
| `LightBackground` | `#FFF7F6F3` | `#FFF4F7F2` | `#FFFAF6F2` | `#FF12161C` |
| `LightSurface` | `#FFFFFFFF` | `#FFFFFFFF` | `#FFFFFFFF` | `#FF1B2129` |
| `LightSurfaceVariant` | `#FFF0EEE9` | `#FFEDF3EA` | `#FFF5EDE6` | `#FF232B35` |
| `LightBorder` | `#FFDCD8D0` | `#FFD6E0D5` | `#FFE6D8CC` | `#FF2E3846` |
| `TextPrimary` | `#FF17191D` | `#FF16211A` | `#FF1F1A17` | `#FFE7ECF2` |
| `TextSecondary` | `#FF4B515C` | `#FF47574C` | `#FF5A5049` | `#FF9AA5B1` |
| `TextMuted` | `#FF8A8F9A` | `#FF7F8C81` | `#FF8D8078` | `#FF6F7B88` |
| `StatusGreen` | `#FF1B7A46` | `#FF1B7A46` | `#FF1B7A46` | `#FF4CAF7D` |
| `StatusAmber` | `#FF96590A` | `#FF96590A` | `#FF96590A` | `#FFE0A03C` |
| `StatusRed` | `#FFB3261E` | `#FFB3261E` | `#FFB3261E` | `#FFE57373` |
| `StatusBlue` | `#FF1D4ED8` | `#FF1D4ED8` | `#FF1D4ED8` | `#FF7FB3E8` |

Two more places follow the same palette:

* `app/src/main/res/values/colors.xml` — the non-Compose colours.
* `app/src/main/res/values/themes.xml` — the status bar and navigation bar.

Slate Night needs one extra change: `Theme.kt` builds a `lightColorScheme`,
so a dark mockup means switching it to `darkColorScheme` and choosing which of
the two the app follows.

## The launcher mark

`docs/logo/kadepos-mark-source.png` is the master mark. Run
`python3 tools/make_launcher_icons.py` (needs Pillow) after replacing it and
every launcher icon is rebuilt from that one file.
