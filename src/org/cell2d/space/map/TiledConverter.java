package org.cell2d.space.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cell2d.Animatable;
import org.cell2d.Animation;
import org.cell2d.CellGame;
import org.cell2d.Color;
import org.cell2d.Filter;
import org.cell2d.Frac;
import org.cell2d.Sprite;
import org.cell2d.SpriteSheet;
import org.cell2d.space.SpaceState;
import org.tiledreader.TiledImage;
import org.tiledreader.TiledMap;
import org.tiledreader.TiledReader;
import org.tiledreader.TiledResource;
import org.tiledreader.TiledTile;
import org.tiledreader.TiledTileset;

/**
 * <p>The TiledConverter class, along with the TiledArea class, allow Cell2D
 * games to incorporate information from files created with the map editor
 * <a href="https://www.mapeditor.org/">Tiled</a>. They accomplish this by
 * interfacing with the <a href="https://github.com/AlexHeyman/TiledReader">
 * TiledReader</a> library, which is a dependency of Cell2D. The TiledConverter
 * class cannot be instantiated; instead, it contains static methods for
 * converting data structures from the TiledReader library into data structures
 * native to Cell2D.</p>
 * 
 * <p>The TiledConverter class stores pointers to the Sprites and Animations
 * corresponding to the TiledTilesets it has converted, as well as to the
 * TiledAreas corresponding to the TiledMaps they have been constructed from.
 * The TiledConverter class will use these pointers to return the very same
 * Sprites and Animations if asked to convert the same TiledTileset multiple
 * times. This is mainly to ensure that, if multiple TiledMaps reference the
 * same TiledTileset, the TiledTileset will not be wastefully converted and
 * stored in memory multiple times. However, the TiledConverter class also
 * contains static methods that can be called manually to remove these pointers.
 * Removing the pointers to no-longer-needed Sprites, Animations, and TiledAreas
 * is necessary to make those objects vulnerable to the Java garbage collector.
 * (Keep in mind, however, that unloading the Sprites is also necessary to fully
 * free the memory they take up.)</p>
 * 
 * <p>Tiled stores the durations of tile animation frames in milliseconds, but
 * Cell2D stores the durations of Animation frames in fracunits. The
 * TiledConverter class converts milliseconds to fracunits using a consistent
 * rate, which by default is one fracunit for every 16 milliseconds. The
 * TiledConverter also contains static methods for getting and setting this
 * conversion rate.</p>
 * @see TiledArea
 * @author Alex Heyman
 */
public final class TiledConverter {
    
    private TiledConverter() {}
    
    private static class AssetData {
        
        private Object asset = null;
        private final Set<TiledResource> referToThis = new HashSet<>();
        private final Set<TiledResource> referencedByThis = new HashSet<>();
        
    }
    
    private static final Map<TiledResource,AssetData> assets = new HashMap<>();
    
    private static void ensureReference(TiledResource referer, TiledResource referent) {
        assets.get(referer).referencedByThis.add(referent);
        assets.get(referent).referToThis.add(referer);
    }
    
    private static final Map<TiledTile,Animatable> tilesToAnimatables = new HashMap<>();
    private static long fracunitsPerMS = Frac.UNIT/16;
    
    /**
     * Returns the TiledConverter class' milliseconds-to-fracunits conversion
     * rate, in fracunits per millisecond.
     * @return The TiledConverter class' milliseconds-to-fracunits conversion
     * rate
     */
    public static long getFracunitsPerMS() {
        return fracunitsPerMS;
    }
    
    /**
     * Sets the TiledConverter class' milliseconds-to-fracunits conversion rate
     * to the specified value.
     * @param fracunitsPerMS The new conversion rate, in fracunits per
     * millisecond
     */
    public static void setFracunitsPerMS(long fracunitsPerMS) {
        if (fracunitsPerMS <= 0) {
            throw new RuntimeException("Attempted to set TiledConverter's fracunits-per-millisecond rate to"
                    + " a non-positive value (about " + Frac.toDouble(fracunitsPerMS) + " fracunits)");
        }
        TiledConverter.fracunitsPerMS = fracunitsPerMS;
    }
    
    static <T extends CellGame, U extends SpaceState<T,U,?>> void addArea(TiledArea<T,U> area) {
        TiledMap map = area.getMap();
        if (assets.containsKey(map)) {
            removeAssets(map, false, false);
        }
        AssetData data = new AssetData();
        assets.put(map, data);
        data.asset = area;
        //The TiledArea constructor ensures that by now, all of the map's
        //tilesets have been converted and are present in the assets map
        for (TiledTileset tileset : map.getTilesets()) {
            ensureReference(map, tileset);
        }
    }
    
    /**
     * Converts the specified TiledTileset, if it has not already been
     * converted, and returns an Iterable of the Sprites corresponding to all of
     * the TiledTileset's TiledTiles. If the TiledTileset is a single-image
     * tileset, the Iterable will be a SpriteSheet with the same grid layout as
     * the TiledTileset. If the TiledTileset is instead an image collection
     * tileset, the Iterable will be a List of Sprites, given in order from
     * lowest to highest local ID of the TiledTiles to which they correspond.
     * @param tileset The TiledTileset whose corresponding Sprites are to be
     * returned
     * @param filters If the TiledTileset has not yet been converted, this
     * parameter determines the Set of Filters that should have an effect on the
     * TiledTileset's Sprites when applied to them with draw(). If this is null,
     * no Filters will have an effect.
     * @param load If the TiledTileset has not yet been converted and this
     * parameter is true, all of the TiledTileset's Sprites will be loaded upon
     * their creation.
     * @return An Iterable of the TiledTileset's Sprites
     */
    public static Iterable<Sprite> getSprites(TiledTileset tileset, Set<Filter> filters, boolean load) {
        AssetData data = assets.get(tileset);
        if (data == null) {
            data = new AssetData();
            assets.put(tileset, data);
            Map<TiledTile,Sprite> tilesToSprites = new HashMap<>();
            TiledImage image = tileset.getImage();
            if (image == null) {
                //Tileset is an image collection tileset
                List<Sprite> spriteList = new ArrayList<>(tileset.getTiles().size());
                data.asset = spriteList;
                for (TiledTile tile : tileset.getTiles()) {
                    image = tile.getImage();
                    java.awt.Color transColor = image.getTransColor();
                    Sprite sprite = new Sprite(image.getSource(),
                            -tileset.getTileOffsetX(), -tileset.getTileOffsetY(),
                            (transColor == null ? null : new Color(transColor)), filters, load);
                    spriteList.add(sprite);
                    tilesToSprites.put(tile, sprite);
                }
            } else {
                //Tileset is a single-image tileset
                java.awt.Color transColor = image.getTransColor();
                SpriteSheet spriteSheet = new SpriteSheet(image.getSource(), tileset.getWidth(),
                        tileset.getHeight(), tileset.getTileWidth(), tileset.getTileHeight(),
                        tileset.getSpacing(), tileset.getMargin(),
                        -tileset.getTileOffsetX(), -tileset.getTileOffsetY(),
                        (transColor == null ? null : new Color(transColor)), filters, load);
                data.asset = spriteSheet;
                for (int x = 0; x < tileset.getWidth(); x++) {
                    for (int y = 0; y < tileset.getHeight(); y++) {
                        tilesToSprites.put(tileset.getTile(x, y), spriteSheet.getSprite(x, y));
                    }
                }
            }
            for (Map.Entry<TiledTile,Sprite> entry : tilesToSprites.entrySet()) {
                TiledTile tile = entry.getKey();
                int numFrames = tile.getNumAnimationFrames();
                if (numFrames == 0) {
                    tilesToAnimatables.put(tile, entry.getValue());
                } else {
                    Animatable[] frames = new Animatable[numFrames];
                    long[] frameDurations = new long[numFrames];
                    for (int i = 0; i < numFrames; i++) {
                        frames[i] = tilesToSprites.get(tile.getAnimationFrame(i));
                        frameDurations[i] = tile.getAnimationFrameDuration(i)*fracunitsPerMS;
                    }
                    tilesToAnimatables.put(tile, new Animation(frames, frameDurations));
                }
            }
        }
        return (Iterable<Sprite>)(data.asset);
    }
    
    /**
     * Converts the TiledTileset to which the specified TiledTile belongs, if it
     * has not already been converted, and returns the Animatable representation
     * of the specified TiledTile. If the TiledTile is an animated tile, the
     * returned object will be an Animation whose frames are the Sprites
     * corresponding to the TiledTile frames of the original tile animation. If
     * the specified TiledTile is not animated, the returned object will simply
     * be the static Sprite corresponding to the TiledTile.
     * @param tile The TiledTile for which the representative Animatable is to
     * be returned
     * @param filters If the TiledTile's TiledTileset has not yet been
     * converted, this parameter determines the Set of Filters that should have
     * an effect on the TiledTileset's Sprites when applied to them with draw().
     * If this is null, no Filters will have an effect.
     * @param load If the TiledTile's TiledTileset has not yet been converted
     * and this parameter is true, all of the TiledTileset's Sprites will be
     * loaded upon their creation.
     * @return The Animatable representation of the specified TiledTile
     */
    public static Animatable getAnimatable(TiledTile tile, Set<Filter> filters, boolean load) {
        TiledTileset tileset = tile.getTileset();
        getSprites(tileset, filters, load);
        return tilesToAnimatables.get(tile);
    }
    
    /**
     * Removes the TiledConverter class' pointer to the assets converted from
     * the specified resource, if it has been converted before.
     * @param resource The resource to forget about
     * @param cleanUp If true, also remove the pointers to all of the assets
     * referenced by the assets from the specified resource, and not referenced
     * by any of the other assets that the TiledConverter class still points to.
     * This parameter applies recursively, so if the removal of any of these
     * "orphaned" assets causes more assets to be orphaned, those will be
     * removed as well.
     * @param removeResources If true, also remove the TiledReader class'
     * pointer to the specified resource, if that pointer exists. Note that the
     * removal is based on the resource's source path rather than its identity
     * as an Object, and so if the resource from that same path has already been
     * removed from and re-read by the TiledReader class, the TiledReader class
     * <i>will</i> remove its pointer to the new resource. The cleanUp parameter
     * applies here as well; if it is true, the TiledReader class will also
     * clean up its orphaned resources.
     * @return Whether the specified resource had been converted before this
     * method was called, and hence whether the removal occurred
     */
    public static boolean removeAssets(TiledResource resource, boolean cleanUp, boolean removeResources) {
        AssetData data = assets.get(resource);
        if (data == null) {
            return false;
        }
        for (TiledResource refererResource : data.referToThis) {
            assets.get(refererResource).referencedByThis.remove(resource);
        }
        if (cleanUp) {
            List<TiledResource> orphanedResources = new ArrayList<>();
            for (TiledResource referencedResource : data.referencedByThis) {
                AssetData referencedData = assets.get(referencedResource);
                referencedData.referToThis.remove(resource);
                if (referencedData.referToThis.isEmpty()) {
                    orphanedResources.add(referencedResource);
                }
            }
            for (TiledResource orphanedResource : orphanedResources) {
                removeAssets(orphanedResource, true, removeResources);
            }
        } else {
            for (TiledResource referencedResource : data.referencedByThis) {
                assets.get(referencedResource).referToThis.remove(resource);
            }
        }
        assets.remove(resource);
        if (resource instanceof TiledTileset) {
            tilesToAnimatables.keySet().removeAll(((TiledTileset)resource).getTiles());
        }
        if (removeResources && resource.getPath() != null) {
            TiledReader.removeResource(resource.getPath(), cleanUp);
        }
        return true;
    }
    
    /**
     * Removes all of the TiledConverter class' pointers to the assets that have
     * been converted from Tiled resources.
     */
    public static void clearAssets() {
        assets.clear();
        tilesToAnimatables.clear();
    }
    
}
