# BakaShop

A Paper `1.21.11` shop plugin that uses EssentialsX economy balances.

## Build

```powershell
mvn package
```

The plugin jar is created at:

```text
target/BakaShop-1.0.0.jar
```

## Install

Put these jars in your Paper server's `plugins` folder:

- `EssentialsX`
- `BakaShop-1.0.0.jar`

Then restart the server.

## Commands

- `/shop` or `/bakashop` opens the shop.
- `/sell` opens a sell GUI. Put items in it, then click the green pane in the bottom-right or close the GUI to sell them.
- `/bakashop reload` reloads `plugins/BakaShop/config.yml`.
- `/amethystpick wall` makes the Amethyst Pickaxe mine a vertical 3x3 wall.
- `/amethystpick floor` makes the Amethyst Pickaxe mine a flat 3x3 floor.
- `/editshop additem <category> <material> <buy> <sell>` adds an item and reloads the shop.
- `/editshop removeitem <category> page<page>.items.<slot>` removes an item and reloads the shop.
- `/editshop edititem <category> page<page>.items.<slot> <buy|sell> set <price>` edits a price and reloads the shop.
- `/editshop removecoins <player> <amount>` removes EssentialsX money from a player.
- `/editshop addcoins <player> <amount>` adds EssentialsX money to a player.
- `/editshop addshells <player> <count>` gives shells using the configured shell give command.
- `/editshop removeshells <player> <count>` removes shells using the configured shell take command.

Examples:

```text
/editshop additem Blocks Moss 100 50
/editshop removeitem Blocks page2.items.4
/editshop edititem Blocks page2.items.4 sell set 1
/editshop edititem blocks page2.items.4 buy set 5
/editshop removecoins Steve 100
/editshop addcoins Steve 100
/editshop addshells Steve 25
/editshop removeshells Steve 25
```

`Moss` is accepted as `MOSS_BLOCK`. Item paths use the visible category page and inventory slot number, from `0` to `44`.

## Permissions

- `bakashop.use` defaults to everyone.
- `bakashop.reload` defaults to operators.
- `bakashop.edit` defaults to operators.
- `bakashop.sell` defaults to everyone.

## Config

Edit `plugins/BakaShop/config.yml` after the plugin starts once.

If `config.yml` is invalid and no valid categories/items can load, the plugin backs it up as `config.invalid-<timestamp>.yml`, restores the bundled default config, and reloads automatically.

The main menu is built from `categories`. Each category has a tab icon and nested shop items:

```yaml
categories:
  blocks:
    slot: 10
    icon: MOSS_BLOCK
    currency: money
    title: "&a&lBLOCK"
    menu-title: "&0BLOCK SHOP"
    lore:
      - "&7SERVER SHOP"
      - ""
      - "&9Information:"
      - "&a¦ &7Browse and &apurchase"
      - "&a¦ &abuilding &7blocks"
      - ""
      - "&e▻ &lCLICK &eto Browse"
    items:
      stone:
        slot: 10
        material: STONE
        amount: 16
        buy: 20.0
        sell: 5.0
        name: "&7Stone"
        lore:
          - "&7Left click: buy for &e%buy%"
          - "&7Right click: sell for &e%sell%"
          - "&7Shift click trades 64x."
```

Left click buys, right click sells, and shift-click trades 64 times the configured `amount`.

Buying opens an amount selector first. The default amount is `1`; red panes decrease, green panes increase, and the yellow pane two rows above the preview item sets the amount to `64`. The amount cannot go below `1`.

Categories can use EssentialsX money, item currency, or a PlaceholderAPI-backed currency. For shell shops:

```yaml
categories:
  shells:
    currency: placeholder
    currency-placeholder: "%shells_short%"
    currency-take-command: "shells remove %player% %amount%"
    currency-give-command: "shells give %player% %amount%"
    currency-singular: "%shells_short%"
    currency-plural: "%shells_short%"
```

The default `spawner` and `shells` tabs read `%shells_short%` through PlaceholderAPI for balance checks and are buy-only. Buying runs `currency-take-command`; shell items are not sellable through right-click or `/sell`. Other tabs use EssentialsX money and can be sold.

## Amethyst Tools

- `Amethyst Pickaxe`: mines a 3x3 area. Use `/amethystpick wall` or `/amethystpick floor`.
- `AmethystAxe`: vein-mines connected logs, across every log type.

## Virtual Spawners

The spawner tab includes skeleton, creeper, zombie, cow, chicken, spider, and iron golem spawners. Placed shop spawners do not spawn mobs. They gather drops and XP over time.

Virtual spawner drops are calculated with Looting 3-style luck.

Right-click a placed virtual spawner:

- Experience bottle: shows stored XP.
- Moss block: collects stored drops and XP.

Virtual spawner data is saved in `plugins/BakaShop/spawners.yml`.
