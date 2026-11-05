# Valor Weapons (Forge 1.20.1 / 47.3.0)

Item: `valor_weapons:thunder_lord_fist` - Thunder Lord's Fist

## Behaviour
- Damage = 100% `minecraft:generic.attack_damage` + 20% `valor_items:magic_damage`, dealt as MAGIC damage (ignores armor).
- Target within `meleeRange` (default 3.5 blocks): melee hit, no projectile.
- Target farther away: homing Thunder Bolt (same damage). It only hits its intended target and fizzles if the target dies/disappears.
- If `valor_items:magic_damage` does not exist (mod missing), the magic part counts as 0.
- Balance values: `config/valor_weapons-common.toml` (physicalScale, magicScale, meleeRange, projectileSpeed, projectileMaxAge).
- Base weapon stats (damage bonus +6, attack speed -1.6, durability 1800): see `ThunderLordFistItem` / `ModItems`.

## Better Combat
`src/main/resources/data/valor_weapons/weapon_attributes/thunder_lord_fist.json`
- Inherits `bettercombat:fist` (animations/combo/sounds) and only raises `attack_range` so Better Combat can
  register hits on far targets. Lower/raise `attack_range` to change how far the bolt can reach.
- Better Combat is an optional dependency (not required to launch).

## Models
- In hand: `models/item/thunder_fist.json` (texture `textures/item/thunder_fist.png`)
- Inventory (GUI): 2D `textures/item/fist_of_thunderlord.png`
- `models/item/thunder_lord_fist.json` combines them with `forge:separate_transforms`.
- The original Blockbench model used 90 degree element rotations, which vanilla 1.20.1 refuses to load;
  they were baked into plain coordinates (geometry and UVs are equivalent).

## Testing
`gradlew runClient`, then put Better Combat (+ its dependencies, e.g. PlayerAnimator) and your valor_items mod
into `run/mods`. `/give @p valor_weapons:thunder_lord_fist`
