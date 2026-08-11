package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.caffeinemc.mods.sodium.client.util.collections.BitArray;
import net.minecraft.client.renderer.chunk.ChunkOcclusionData;
import net.minecraft.util.EnumFacing;

/**
 * MCP 1.8.9 port: reference uses {@code ChunkOcclusionData} from the modern modding
 * layers; that is provided by our compat class in {@code net.minecraft.client.renderer.chunk}.
 * Java 8: Kotlin-style arrow switches below are expanded to statement switches.
 * Faithful to the reference including its quirk that {@code GraphDirectionSet.of(~directionSet)}
 * (single-bit semantics) contributes nothing to {@code connectedFaces}; the reachable-face
 * ORs and the self-visibility mark carry correctness.
 */
public class DirectionalVisGraph {
    private static final int SIZE = 16 * 16 * 16;
    private static final int STACK_SIZE = 3 * 16;

    private static final int DX = 1;
    private static final int DY = 16 * 16;
    private static final int DZ = 16;

    private static final int X_MASK = 0b1111;
    private static final int Y_MASK = 0b1111 << 8;
    private static final int Z_MASK = 0b1111 << 4;

    private static final int[] DIRECTION_SETS = new int[] {
            // corresponding to GraphDirection from MSB to LSB:
            // east, west, south, north, up, down
            // half of the directions are omitted since their visibility is the same as the opposite direction set

            //@formatter:off
            0b010101, // 0: west, north, down
            0b010110, // 1: west, north, up
            0b011001, // 2: west, south, down
            // 0b011010, // 3: west, south, up
            0b100101, // 4: east, north, down
            // 0b100110, // 5: east, north, up
            // 0b101001, // 6: east, south, down
            // 0b101010, // 7: east, south, up
            //@formatter:on
    };

    private final BitArray blocks = new BitArray(SIZE);
    private int filled = 0;

    private static int getIndex(int x, int y, int z) {
        return x | (z << 4) | (y << 8);
    }

    public void setOpaque(int x, int y, int z) {
        this.blocks.set(getIndex(x, y, z));
        this.filled++;
    }

    public ChunkOcclusionData[] resolve() {
        // if all blocks are filled, nothing is visible
        if (this.filled == SIZE) {
            ChunkOcclusionData visibilitySet = new ChunkOcclusionData();
            visibilitySet.fill(false);
            return new ChunkOcclusionData[] {
                    visibilitySet
            };
        }

        // if fewer blocks are filled than necessary to block visibility between two faces, all faces are visible to each other
        if (this.filled < 256) {
            ChunkOcclusionData visibilitySet = new ChunkOcclusionData();
            visibilitySet.fill(true);
            return new ChunkOcclusionData[] {
                    visibilitySet
            };
        }

        // generate visibility data for each base perspective
        ChunkOcclusionData[] results = new ChunkOcclusionData[DIRECTION_SETS.length];
        for (int i = 0; i < DIRECTION_SETS.length; i++) {
            results[i] = resolveWithDirections(DIRECTION_SETS[i]);
        }

        return results;
    }

    private ChunkOcclusionData resolveWithDirections(int directionSet) {
        ChunkOcclusionData visibilitySet = new ChunkOcclusionData();

        // search starting at each face opposite an allowed step direction
        int originDirections = (~directionSet) & GraphDirectionSet.ALL;

        short[] stackPos = new short[STACK_SIZE];
        byte[] stackDirs = new byte[STACK_SIZE];
        for (int i = 0; i < 3; i++) {
            int originDirection = Integer.numberOfTrailingZeros(originDirections);
            originDirections &= ~(1 << originDirection);

            int minX = 0, minY = 0, minZ = 0;
            int maxX = 15, maxY = 15, maxZ = 15;
            switch (originDirection) {
                case GraphDirection.DOWN:
                    maxY = 0;
                    break;
                case GraphDirection.UP:
                    minY = 15;
                    break;
                case GraphDirection.NORTH:
                    maxZ = 0;
                    break;
                case GraphDirection.SOUTH:
                    minZ = 15;
                    break;
                case GraphDirection.WEST:
                    maxX = 0;
                    break;
                case GraphDirection.EAST:
                    minX = 15;
                    break;
                default:
                    throw new IllegalStateException("Unexpected graph direction: " + originDirection);
            }

            BitArray visited = this.blocks.copy();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int originIndex = getIndex(x, y, z);
                        if (!visited.getAndSet(originIndex)) {
                            search(visited, visibilitySet, stackPos, stackDirs, originDirection, directionSet, originIndex);
                        }
                    }
                }
            }
        }

        return visibilitySet;
    }

    private void search(BitArray visited, ChunkOcclusionData visibilitySet, short[] stackPos, byte[] stackDirs, int originFace, int directionSet, int originIndex) {
        int stackSize = 0;

        stackPos[stackSize++] = (short) originIndex;
        stackDirs[0] = (byte) directionSet;

        // the faces that we cannot move towards are the ones that cannot become visible because of the perspective of the camera. This always includes the origin face.
        // NOTE: the reference computes this as GraphDirectionSet.of(~directionSet), which (with the
        // single-direction `of`) contributes nothing to the bit set; kept for faithfulness.
        int connectedFaces = GraphDirectionSet.of(~directionSet);

        while (stackSize > 0) {
            int stackIndex = stackSize - 1;
            int remainingDirs = stackDirs[stackIndex];

            // backtrack when all directions have been tried
            if (remainingDirs == 0) {
                stackSize--;
                continue;
            }

            int nextDir = Integer.numberOfTrailingZeros(remainingDirs);
            stackDirs[stackIndex] &= (byte) ~(1 << nextDir);

            int currentIndex = stackPos[stackIndex];

            int neighborIndex;
            boolean reachedFace;
            switch (nextDir) {
                case GraphDirection.DOWN:
                    neighborIndex = currentIndex - DY;
                    reachedFace = (neighborIndex & Y_MASK) == Y_MASK;
                    break;
                case GraphDirection.UP:
                    neighborIndex = currentIndex + DY;
                    reachedFace = (neighborIndex & Y_MASK) == 0;
                    break;
                case GraphDirection.NORTH:
                    neighborIndex = currentIndex - DZ;
                    reachedFace = (neighborIndex & Z_MASK) == Z_MASK;
                    break;
                case GraphDirection.SOUTH:
                    neighborIndex = currentIndex + DZ;
                    reachedFace = (neighborIndex & Z_MASK) == 0;
                    break;
                case GraphDirection.WEST:
                    neighborIndex = currentIndex - DX;
                    reachedFace = (neighborIndex & X_MASK) == X_MASK;
                    break;
                case GraphDirection.EAST:
                    neighborIndex = currentIndex + DX;
                    reachedFace = (neighborIndex & X_MASK) == 0;
                    break;
                default:
                    throw new IllegalStateException("Unexpected graph direction: " + nextDir);
            }

            // check reaching the edge of the chunk
            if (reachedFace) {
                // reached the edge, mark visibility between origin face and this face
                connectedFaces |= GraphDirectionSet.of(nextDir);

                // stop searching if all potentially reachable faces have been reached
                if (connectedFaces == directionSet) {
                    break;
                }

                continue;
            }

            if (visited.getAndSet(neighborIndex)) {
                // already visited or opaque
                continue;
            }

            // visit the neighbor
            stackPos[stackSize] = (short) neighborIndex;
            stackDirs[stackSize] = (byte) directionSet;
            stackSize++;
        }

        EnumFacing originFaceEnum = GraphDirection.toEnum(originFace);
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            if (GraphDirectionSet.contains(connectedFaces, direction)) {
                visibilitySet.setVisibleThrough(originFaceEnum, GraphDirection.toEnum(direction), true);
            }
        }

        visibilitySet.setVisibleThrough(originFaceEnum, originFaceEnum, true);
    }
}
