# Where everything lives

One line per requirement: what the shop owner does, where to find it, and how
they get there. Line numbers were correct when this was written — if one has
drifted, search the file for the symbol named in the last column.

## Section 1 — Add a product, two modes

| Requirement | File | Symbol |
|---|---|---|
| Easy / Normal switch, shared state | `app/src/main/java/com/example/ui/screens/products/ProductEditScreen.kt:112` | `ProductEditMode` |
| Unit chips (Piece…Meter) | same file `:104` | `UNIT_CHIPS` |
| Step 1 name & unit | same file `:559` | `NameAndUnitStep` |
| Step 2 first options + prices | same file `:633` | `FirstOptionsStep` |
| Step 3 split again, per-option sizes | same file `:870` | `SplitAgainStep` |
| Step 4 review & save | same file `:1003` | `ReviewStep` |
| Normal mode (one dense form) | same file `:1195` | `NormalMode` |
| Combination price/stock table | same file `:2293` | `CombinationTable` |
| Save parent + one line per combination | `app/src/main/java/com/example/ui/viewmodel/PosViewModel.kt:994` | `saveProduct` |
| Keep child rows in step with the options | same file | `reconcileVariantChildren` |
| Storage grammar (no migration needed) | `app/src/main/java/com/example/data/model/ProductOptions.kt:50` | `ProductOptions` |
| Price/stock engine, `=` combination lines | `app/src/main/java/com/example/data/model/VariantModels.kt:139` | `buildCombinations` |
| Honest one-line card summary | same file `:227` | `summary` |
| Remove several items in one go | `app/src/main/java/com/example/ui/viewmodel/PosViewModel.kt:1202` | `archiveProducts` |
| Selection mode + confirm step | `app/src/main/java/com/example/ui/screens/products/ProductsScreen.kt:261` | `ProductsScreen` |

**Path:** bottom tab **Items** → **+ Add item** (or tap a card's **Edit**).

**Clearing out the starter list:** the starter catalogue is a guess, so a shop
will always see a few things it has never stocked. **Items** → **Select items**
puts the list into a ticking mode; **Tick all** / **Untick all** work across
whatever is currently listed, and **Remove** takes the whole ticked batch out
after one confirmation. Removed items are *archived*, not deleted — they leave
the list and the Sell tab, but old bills that mention them still add up, so the
stock ledger and the sales history stay honest.

## Section 2 — Adaptive sell / add-to-cart

| Requirement | File | Symbol |
|---|---|---|
| Picker that adapts to the item | `app/src/main/java/com/example/ui/screens/sell/SellScreen.kt:1177` | `VariantPickerDialog` |
| Choice tile, greyed when sold out | same file `:1476` | `OptionTile` |
| Per-combination stock deduction | `app/src/main/java/com/example/data/db/PosDao.kt:339` | `completeSaleTransaction` |

| A note written on the bill | `app/src/main/java/com/example/ui/screens/sell/SellScreen.kt:2683` | `BillNoteDialog` |

**Path:** bottom tab **Sell** → tap a product. An item with no options goes
straight onto the bill; one with options opens the picker.
**New path:** **Items** → **Add to Cart** on an item with options now jumps to
the Sell tab with that item's picker already open
(`PosViewModel.openVariantPickerOnSellTab`).
**Bill note:** the **Note** chip next to the current bill. The note is saved
with the sale and cleared when the cart is cleared, so it never leaks onto the
next bill.

## Section 3 — Provider access + Cloud & Backup

| Requirement | File | Symbol |
|---|---|---|
| Tap ×10 then long-press | `app/src/main/java/com/example/ui/screens/more/SettingsConfigurationScreen.kt:64` | `PROVIDER_TAP_TARGET` |
| Cloud & Backup card (Owner/Manager) | same file `:1190` | `CloudAndBackupCard` |
| Cloud & Backup hub tile | `app/src/main/java/com/example/ui/screens/more/MoreManagementHubScreen.kt:303` | hub tile |
| Full cloud screen | `app/src/main/java/com/example/ui/screens/more/CloudBackupScreen.kt:47` | `CloudBackupScreen` |
| Owner's on/off switch | `app/src/main/java/com/example/data/cloud/CloudSettings.kt` | `ownerBackupEnabled` |

**Path:** **More → Shop details and receipt** (or **Settings → Data & System
Durability**) → tap the "100% Offline-First" card ten times, then press and
hold. Once the provider has allowed it, **More → Cloud & Backup** appears for
Owner and Manager only.

## Section 4 — Logo, theme, mockups

| Item | Path |
|---|---|
| Master mark | `docs/logo/kadepos-mark-source.png` |
| Launcher icons (built from the master) | `app/src/main/res/mipmap-*/ic_launcher.png` and `ic_launcher_round.png` |
| Adaptive foreground / background / monochrome | `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`, `app/src/main/res/drawable/ic_launcher_background.xml`, `app/src/main/res/drawable/ic_launcher_monochrome.xml` |
| Icon generator | `tools/make_launcher_icons.py` |
| Palette (paper & ink, shipped) | `app/src/main/java/com/example/ui/theme/Color.kt:26` |
| XML colours / status bar | `app/src/main/res/values/colors.xml`, `app/src/main/res/values/themes.xml` |
| Mockups in four themes | `docs/mockups/01-paper-and-ink-sell.png`, `02-market-green-sell.png`, `03-warm-clay-add-item.png`, `04-slate-night-sell.png` |
| Colour values for each theme | `docs/mockups/README.md` |

## Section 5 — Onboarding validation

| Requirement | File | Symbol |
|---|---|---|
| Country + local number field | `app/src/main/java/com/example/ui/components/PhoneField.kt:61` | `PhoneField` |
| All 232 countries, searchable | same file `:152` | `CountryPickerDialog` |
| Country list, default Sri Lanka +94 | `app/src/main/java/com/example/ui/util/CountryCodes.kt:23` | `CountryCodes` |
| Leading-0 and 9-digit rules | same file `:335` | `PhoneValidator` |
| Name / shop-name rules + flow | `app/src/main/java/com/example/ui/screens/onboarding/OnboardingFlow.kt` | `validateOwnerName`, `validateShopName` |

**Path:** first launch, or **More → Shop details and receipt → Setup Wizard**.

## Section 6 — Password & keyboard UX

| Requirement | File | Symbol |
|---|---|---|
| Focus rises to mid-screen | `app/src/main/java/com/example/ui/components/FormFields.kt:71` | `FocusRiser` |
| Eye button on every secret field | same file `:113` | `AppTextField(isSecret = …)` |
| Window resizes instead of panning | `app/src/main/AndroidManifest.xml:41` | `adjustResize` |

## Section 7 — Confirmation toast

| Requirement | File | Symbol |
|---|---|---|
| Confirmation pill, middle of screen | `app/src/main/java/com/example/MainActivity.kt:336` | `Alignment.Center` |
| Error banner, top, tap to dismiss | same file `:285` | `Alignment.TopCenter` |
| ~1 second for confirmations | `app/src/main/java/com/example/ui/viewmodel/PosViewModel.kt:590` | `showMessage` |
| 5 seconds for errors | same file `:604` | `showAlert` |

## Starter catalogue

Thirteen shop types, fifty items each — **650 items, no name used twice**
(`tools/generate_catalog.py`).

| Shop type | Key |
|---|---|
| Grocery & Provisions | `GROCERY` |
| Takeaway & Cafe | `FOOD_CAFE` |
| Pharmacy | `PHARMACY` |
| Clothing & Tailoring | `CLOTHING` |
| Phones & Electronics | `ELECTRONICS` |
| Books & Stationery | `STATIONERY` |
| Salon & Spa | `SALON` |
| Mobile & Device Repair | `REPAIR` |
| Flower & Gift Shop | `FLOWERS` |
| Shoe & Footwear Store | `SHOES` |
| Hardware & Building Supplies | `HARDWARE` |
| Toys, Gifts & Fancy Goods | `TOYS_GIFTS` |
| Sports & Fitness | `SPORTS` |

**Do not hand-edit `data/model/ProductCatalogPresets.kt`** — it is generated.
Edit `tools/generate_catalog.py` and re-run it:

```
python3 tools/generate_catalog.py
```

The generator refuses to write unless every type has exactly fifty items and no
product name repeats, so adding a shop type is safe by construction. Picking a
type during setup needs no code change either: `ShopTypeStep`
(`ui/screens/onboarding/OnboardingFlow.kt:393`) just walks
`ProductCatalogPresets.shopTypes`.

## Units everywhere

`ProductEntity.unit` is set on the Add/Edit screen and read by:
`ProductsScreen.kt` (cards), `SellScreen.kt` (grid, picker, cart lines),
`InventoryScreen.kt` (stock), `CurrencyUtils.kt:222` (`ReceiptItemData.unit`,
printed as `2 Kg x 250.00`).

## Database

- Version: `CURRENT_DB_VERSION = 7` — `app/src/main/java/com/example/data/db/PosDatabase.kt:32`
- Migrations: an unbroken chain `1→2→3→4→5→6→7` — `data/db/Migrations.kt:304` (`ALL_MIGRATIONS`)
- **No entity changed.** Per-option sizes and per-combination stock live in the
  existing `products.variants` TEXT column, so no migration was needed and no
  existing shop's data is touched.
- `ownerBackupEnabled` is app preference state (DataStore), not a database
  column — see `data/cloud/CloudSettings.kt`.

Known gap: `app/schemas/` has no exported JSON files yet. Room needs them to
verify migrations at build time; the first machine with a JDK and the Android
SDK that builds the project will write them, and they should be committed (see
`app/schemas/README.md`). Until then migrations are checked by reading, not by
Room.

## Tests

`app/src/test/java/com/example/ProductOptionsTest.kt` — the biryani priced by
rice and portion, the trouser where each colour has its own sizes, the
encode/decode round-trip with per-combination stock, legacy formats, the phone
rules and the country list.

Run with:

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Tools

`tools/check_sources.py` parses every Kotlin file with tree-sitter to check
brace balance and flag symbols used from another package without an import. It
exists because Gradle cannot run in every environment.

```
python3 tools/check_sources.py
```
