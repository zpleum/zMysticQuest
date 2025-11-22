# MysticQuest

MysticQuest is a Minecraft Spigot plugin (1.21+) that adds mysterious NPCs who randomly approach players and give them secret quests.

## Features

- **Mysterious NPCs**: NPCs will randomly appear near players and approach them with quest offers
- **Interactive Quest Offers**: 
  - NPCs lock player's view when in conversation
  - Players can accept or reject quests via /accept or /reject commands
  - Animated story text appears in the ActionBar when accepting
  - NPCs dramatically walk backwards into darkness when rejected
- **Secret Quests**: Players receive "secret quest scrolls" with various objectives
- **Multiple Quest Types**: Includes kill quests, collection quests, and exploration quests
- **XP System**: Complete quests to earn Mystic XP, which acts as a currency for rewards
- **Reward Tiers**: Unlock special rewards by accumulating enough Mystic XP
- **Notifications**: Hear special sounds and see particles when NPCs are nearby
- **Fully Configurable**: All aspects of the plugin can be configured in multiple config files

## Configuration Files

The plugin uses multiple configuration files to make customization easy:

- **config.yml**: Main settings for NPCs, quests, and the XP system
- **quests.yml**: Configure different quest types and their rewards
- **npcs.yml**: Configure NPC types, appearance, their stories and which quests they offer
- **rewards.yml**: Configure reward tiers and what players get when they unlock them
- **messages.yml**: Customize all messages that players see

## Commands

- **/accept**: Accept a quest offer from an NPC
- **/reject**: Reject a quest offer from an NPC
- **/mysticquest info**: View your active quests and Mystic XP
- **/mysticquest reload**: Reload the plugin configuration (Admin)
- **/mysticquest spawnnpc <type>**: Spawn a quest NPC (Admin)
- **/mysticquest resetxp <player>**: Reset a player's Mystic XP (Admin)

## Permissions

- **mysticquest.admin**: Access to admin commands

## Installation

1. Download the plugin JAR file
2. Place it in your server's plugins folder
3. Restart your server
4. Edit the configuration files to customize the plugin

## Requirements

- Spigot/Paper 1.21+
- Java 17+

## NPC Interaction

1. NPCs will randomly spawn near players and approach them
2. When an NPC gets close, the player's view will lock onto the NPC
3. The NPC will offer a quest through the ActionBar
4. The player can type `/accept` or `/reject` to respond
5. If accepted:
   - The NPC will tell a story with animated text in the ActionBar
   - The player will receive a quest scroll
   - The NPC will disappear in a puff of smoke
6. If rejected:
   - The NPC will back away dramatically into the darkness
   - The player will be freed from the interaction

## Example Quest Types

### Kill Quests
Players must kill a specific number of certain mob types.

### Collection Quests
Players must collect specific items.

### Exploration Quests
Players must find specific biomes or structures.

## Adding Custom Quests

To add your own quests, edit the `quests.yml` file. 

Example:
```yaml
my_custom_quest:
  type: KILL
  name: "Skeleton Hunter"
  description: "Hunt down 15 skeletons"
  entity: "SKELETON"
  amount: 15
  xp: 150
```

## Adding Custom NPCs

To add your own NPC types with custom stories, edit the `npcs.yml` file.

Example:
```yaml
my_custom_npc:
  name: "Treasure Hunter"
  skin: "eyJ0ZXh0dXJlcyI6..."
  quest-types:
    - my_custom_quest
    - collect_diamonds
  story: "I've been searching for ancient treasures across the land. Your help would be most valuable in my quest. Will you join me?"
``` 