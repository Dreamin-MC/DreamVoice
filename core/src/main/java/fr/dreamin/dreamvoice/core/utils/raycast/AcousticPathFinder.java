package fr.dreamin.dreamvoice.core.utils.raycast;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ultra-fast bounded 3D BFS pathfinder through air, open doors, and apertures.
 * Finds acoustic propagation paths around walls and through doorways.
 */
public final class AcousticPathFinder {

  private static final int MAX_VISITED_NODES = 280;
  private static final int[][] NEIGHBORS = {
    { 1, 0, 0 }, { -1, 0, 0 },
    { 0, 1, 0 }, { 0, -1, 0 },
    { 0, 0, 1 }, { 0, 0, -1 }
  };

  public record AcousticPath(boolean found, double pathLength, @NotNull List<Location> waypoints) {
    public static final AcousticPath NONE = new AcousticPath(false, 0.0, Collections.emptyList());
  }

  public static @NotNull AcousticPath findAirPath(
    final @NotNull Location startLoc,
    final @NotNull Location targetLoc,
    final double maxPathDistance
  ) {
    final var world = startLoc.getWorld();
    if (world == null || !world.equals(targetLoc.getWorld()))
      return AcousticPath.NONE;

    final var startBlockX = startLoc.getBlockX();
    final var startBlockY = startLoc.getBlockY();
    final var startBlockZ = startLoc.getBlockZ();

    final var targetBlockX = targetLoc.getBlockX();
    final var targetBlockY = targetLoc.getBlockY();
    final var targetBlockZ = targetLoc.getBlockZ();

    if (startBlockX == targetBlockX && startBlockY == targetBlockY && startBlockZ == targetBlockZ) {
      return new AcousticPath(true, startLoc.distance(targetLoc), List.of(startLoc, targetLoc));
    }

    final var startNode = new Node(startBlockX, startBlockY, startBlockZ);
    final var targetNode = new Node(targetBlockX, targetBlockY, targetBlockZ);

    final var queue = new ArrayDeque<Node>();
    final var visited = new HashSet<Node>();
    final var parentMap = new HashMap<Node, Node>();
    final var costMap = new HashMap<Node, Double>();

    queue.add(startNode);
    visited.add(startNode);
    costMap.put(startNode, 0.0);

    var nodesExplored = 0;
    Node reachedNode = null;

    final var maxDistSq = maxPathDistance * maxPathDistance;

    while (!queue.isEmpty() && nodesExplored < MAX_VISITED_NODES) {
      final var current = queue.poll();
      nodesExplored++;

      final var currentCost = costMap.get(current);

      // Check if we can directly reach the target from current position
      if (current.equals(targetNode) || current.distanceSq(targetNode) <= 2.25) {
        reachedNode = current;
        break;
      }

      for (final var offset : NEIGHBORS) {
        final var nx = current.x + offset[0];
        final var ny = current.y + offset[1];
        final var nz = current.z + offset[2];

        final var nextNode = new Node(nx, ny, nz);
        if (visited.contains(nextNode))
          continue;

        final var newDist = currentCost + 1.0;
        if (newDist * newDist > maxDistSq)
          continue;

        if (!isAcousticallyPassable(world, nx, ny, nz))
          continue;

        visited.add(nextNode);
        parentMap.put(nextNode, current);
        costMap.put(nextNode, newDist);
        queue.add(nextNode);
      }
    }

    if (reachedNode == null)
      return AcousticPath.NONE;

    // Reconstruct waypoint path
    final var pathNodes = new ArrayList<Node>();
    var curr = reachedNode;
    while (curr != null) {
      pathNodes.add(curr);
      curr = parentMap.get(curr);
    }
    Collections.reverse(pathNodes);

    final var waypoints = new ArrayList<Location>();
    waypoints.add(startLoc.clone());
    for (final var n : pathNodes) {
      waypoints.add(new Location(world, n.x + 0.5, n.y + 0.5, n.z + 0.5));
    }
    waypoints.add(targetLoc.clone());

    var totalLen = 0.0;
    for (int i = 0; i < waypoints.size() - 1; i++) {
      totalLen += waypoints.get(i).distance(waypoints.get(i + 1));
    }

    return new AcousticPath(true, totalLen, waypoints);
  }

  public static boolean isAcousticallyPassable(final @NotNull World world, final int x, final int y, final int z) {
    if (y < world.getMinHeight() || y >= world.getMaxHeight())
      return false;

    final var block = world.getBlockAt(x, y, z);
    final var type = block.getType();

    if (type.isAir())
      return true;

    // Doors, Trapdoors, and Fence Gates: passable if OPEN
    final var data = block.getBlockData();
    if (data instanceof Openable openable) {
      return openable.isOpen();
    }

    // Glass, carpets, bars, signs, banners, torches, non-solid blocks
    if (type == Material.GLASS || type == Material.GLASS_PANE || type == Material.IRON_BARS)
      return false; // Solid structure even if transparent

    return !type.isSolid() || !type.isOccluding();
  }

  private static final class Node {
    final int x, y, z;

    Node(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }

    double distanceSq(Node other) {
      final var dx = this.x - other.x;
      final var dy = this.y - other.y;
      final var dz = this.z - other.z;
      return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Node node)) return false;
      return x == node.x && y == node.y && z == node.z;
    }

    @Override
    public int hashCode() {
      return Objects.hash(x, y, z);
    }
  }

}
