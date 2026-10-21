# Fast, large matches in Backpack Blitz — 1.9.40

## Goal

The round lasts 60 seconds. The tutorial says that matching three or more gems
scores points, while larger matches create bonuses. The bot therefore first
maximizes how many gems the current swap clears.

## Move selection order

1. Evaluate every adjacent horizontal and vertical swap that creates a match.
2. Prefer the move that clears the most gems immediately.
3. For equally sized matches, evaluate created/activated bonuses, cascades from
   existing tiles, and possible cascades after refill.
4. Simulate random refills only for moves with the largest immediate clear.
   This prevents a random cascade estimate from choosing a smaller match over a
   larger one; skip unnecessary simulations when one larger match clearly wins.

## Limitations

This heuristic prefers a larger match in one swap. It cannot observe the game's
actual score increase and does not guarantee the global maximum score.
Recognition, animation waits, and screenshot intervals are unchanged. A full
60-second phone round cannot be reproduced by a JVM build; actual score and move
count need to be checked in games on a device.
