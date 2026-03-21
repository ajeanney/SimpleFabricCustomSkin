# SimpleFabricCustomSkin

Hand out skins from local PNG files on a Fabric server: an operator gives a player a skin from the `skins` folder, and it shows up immediately, survives restarts, and is handed back when cleared.

It supports:

- Instant in-game skin refresh (no relog required).
- Restoring a player's original personal skin on clear.
- Persistence across disconnects and server restarts.
- Filename autocompletion in the command.

## Commands

All commands require operator-level permission.

- Set skin:
  - `/skin set <player> <filename_without_png>`
- Clear skin:
  - `/skin clear <player>`
  - `/skin clear` (self when run as a player)

When using `/skin set`, `<filename_without_png>` is suggested from files in the skins folder.

## Skin files

Place PNG skins in:

- `skins/` in the server working directory.

Requirements:

- File extension: `.png`
- Resolution: `64x64` or `64x32`

Example:

- File: `skins/knight.png`
- Command: `/skin set Steve knight`

## Persistence

Assignments are stored in:

- `config/simplefabriccustomskin-player-skins.json`

On player join, saved skins are reapplied automatically.

## Technical behavior

- Set:
  - Uploads/derives texture data from the local PNG.
  - Applies the texture to the target profile.
  - Sends refresh packets so changes are visible immediately.
- Clear:
  - Removes the custom assignment.
  - Restores the cached original player skin texture.

## Development

Build:

```bash
./gradlew build
```

Run GameTests:

```bash
./gradlew runGameTest
```
