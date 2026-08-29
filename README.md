# MMCE Flip

A Minecraft 1.12.2 mod that allows flipping controllers from
[Modular Machinery: Community Edition](https://www.curseforge.com/minecraft/mc-mods/modularmachinery-community-edition) (MMCE)
in any direction.

## Features

### Full controller orientation control

Machine controllers support all 24 cube orientations: the face can point to
any of the six directions, and the structure can be spun around that face in
90 degree steps.

- Sneak-right-click a controller to cycle its facing through north, east,
  south, west, up and down.
- Sneak-right-click while holding a stick to cycle the spin around the
  current facing (0, 90, 180, 270 degrees).
- Sneak-scroll with an empty hand while looking at a controller to spin
  it: scrolling forward adds 90 degrees, scrolling backward removes 90.

The structure re-forms around the controller in the chosen orientation, so
machines can be built into floors and ceilings and rotated to match any
placement.

### Limitations

Machines with expandable structures do not form at tilted or spun
orientations.

## License

MIT, see [LICENSE](LICENSE).
