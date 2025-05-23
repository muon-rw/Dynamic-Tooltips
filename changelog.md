## 0.5.0
- Added Mod Menu config support - **Slight changes to the config structure, you may need to reapply config changes you've made**  
- Fix a similar total-merged-value issue from 0.4.1 for Attack Range
- Fix Attack Range tooltip's base value not respecting a player's modified base entity interaction range, if it was modified via the `attribute base set` command
- Made the keybind (Shift to Expand Tooltip) modifiable. 
- Minor internal cleanup

## 0.4.1 
- Fix an inconsistency with modifier ordering that led to slightly incorrect total merged values

## 0.4.0
- Fix incorrect calculation of Attack Range for weapons that have WeaponAttributes *and* entity interaction range modifiers
- More intelligently merge modifiers for gear that contains both HAND *and* MAINHAND/OFFHAND modifiers 
- Made Attribute sentiment overrides (color based on whether positive/negative modifiers for the attribute are red/blue) configurable

## 0.3.0
- Configurable Settings! *Now requires Forge Config API Port*
- Toggle appending the usability hint (Shift to expand tooltip line)
- Toggle appending Block Interaction Range to relevant items
- Toggle determining whether to collapse Enchantment Descriptions on gear by default
- Allow configurable color of enchantment names/descriptions
- Refactored logic for Block Interaction range tooltip (Thanks Daedelus!)

## 0.2.0
- Fix Better Combat two-handed weapons not having the "Two-Handed" tooltip line
- Color enchantment names based on their relation to the maximum enchantment value

## 0.1.0
- Initial Release
