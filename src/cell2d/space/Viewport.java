package cell2d.space;

import cell2d.CellGame;
import cell2d.Frac;

/**
 * <p>A Viewport is a SpaceThinker that represents a rectangular region of the
 * screen through which the space of the SpaceState to which it is assigned can
 * be viewed. The center of a Viewport's rectangular field of view in its
 * SpaceState is marked by a SpaceObject called the Viewport's camera. To render
 * any visuals, including its HUD if it has one, a Viewport must be assigned to
 * a SpaceState through its setViewport() method. To render the region of its
 * SpaceState in its field of view, a Viewport's camera must be assigned to the
 * same SpaceState as it. One pixel in a Viewport's rendering region on the
 * screen is equal to one fracunit in its SpaceState.</p>
 * 
 * <p>HUDs may be assigned to one Viewport each. Only one HUD may be assigned
 * to a given Viewport at once. A Viewport's HUD uses the region of the screen
 * that the Viewport occupies as its rendering region.</p>
 * @author Andrew Heyman
 * @param <T> The type of CellGame that uses the SpaceStates that this Viewport
 * can be assigned to
 */
public class Viewport<T extends CellGame> extends SpaceThinker<T> {
    
    private SpaceObject camera = null;
    private HUD<T> hud;
    private long x1, y1, x2, y2;
    int roundX1, roundY1, roundX2, roundY2, left, right, top, bottom;
    
    /**
     * Creates a new Viewport that occupies the specified region of the screen.
     * @param hud This Viewport's HUD, or null if it should have none
     * @param x1 The x-coordinate in pixels of this Viewport's left edge on the
     * screen
     * @param y1 The y-coordinate in pixels of this Viewport's top edge on the
     * screen
     * @param x2 The x-coordinate in pixels of this Viewport's right edge on the
     * screen
     * @param y2 The y-coordinate in pixels of this Viewport's bottom edge on
     * the screen
     */
    public Viewport(HUD<T> hud, long x1, long y1, long x2, long y2) {
        if (x1 > x2) {
            throw new RuntimeException("Attempted to give a Viewport a negative width");
        }
        if (y1 > y2) {
            throw new RuntimeException("Attempted to give a Viewport a negative height");
        }
        this.hud = (hud == null || hud.getNewGameState() == null ? hud : null);
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        roundX1 = Frac.toInt(x1);
        roundY1 = Frac.toInt(y1);
        roundX2 = Frac.toInt(x2);
        roundY2 = Frac.toInt(y2);
        updateXData();
        updateYData();
    }
    
    private void updateXData() {
        left = -(int)Math.round((roundX1 + roundX2)/2.0);
        right = left + roundX2 - roundX1;
    }
    
    private void updateYData() {
        top = -(int)Math.round((roundY1 + roundY2)/2.0);
        bottom = top + roundY2 - roundY1;
    }
    
    @Override
    public final void addedActions(T game, SpaceState<T> levelState) {
        if (hud != null) {
            levelState.addThinker(hud);
        }
    }
    
    @Override
    public final void removedActions(T game, SpaceState<T> levelState) {
        if (hud != null) {
            levelState.removeThinker(hud);
        }
    }
    
    /**
     * Returns this Viewport's camera, or null if it has none.
     * @return This Viewport's camera
     */
    public final SpaceObject getCamera() {
        return camera;
    }
    
    /**
     * Sets this Viewport's camera to the specified SpaceObject, or to none if
     * the specified SpaceObject is null.
     * @param camera The new camera
     */
    public final void setCamera(SpaceObject camera) {
        this.camera = camera;
    }
    
    /**
     * Returns the HUD that is currently assigned to this Viewport, or null if
     * there is none.
     * @return This Viewport's HUD
     */
    public final HUD<T> getHUD() {
        return hud;
    }
    
    /**
     * Sets the HUD that is assigned to this Viewport to the specified HUD, if
     * it is not already assigned to a SpaceState. If there is already an HUD
     * assigned to this Viewport, it will be removed. If the specified HUD is
     * null, the current HUD will be removed if there is one, but it will not be
     * replaced with anything.
     * @param hud The HUD to add
     * @return Whether the addition occurred
     */
    public final boolean setHUD(HUD<T> hud) {
        if (getNewGameState() == null) {
            if (hud == null || hud.getNewGameState() == null) {
                this.hud = hud;
                return true;
            }
            return false;
        } else if (hud == null || getNewGameState().addThinker(hud)) {
            if (this.hud != null) {
                getNewGameState().removeThinker(this.hud);
            }
            this.hud = hud;
            return true;
        }
        return false;
    }
    
    /**
     * Returns the x-coordinate in pixels of this Viewport's left edge on the
     * screen.
     * @return The x-coordinate in pixels of this Viewport's left edge
     */
    public final long getX1() {
        return x1;
    }
    
    /**
     * Sets the x-coordinate in pixels of this Viewport's left edge on the
     * screen to the specified value, if doing so would not cause this
     * Viewport's width to be negative.
     * @param x1 The new x-coordinate in pixels of this Viewport's left edge
     * @return Whether the change occurred
     */
    public final boolean setX1(long x1) {
        if (x1 <= x2) {
            this.x1 = x1;
            roundX1 = Frac.toInt(x1);
            updateXData();
            return true;
        }
        return false;
    }
    
    /**
     * Returns the y-coordinate in pixels of this Viewport's top edge on the
     * screen.
     * @return The y-coordinate in pixels of this Viewport's top edge
     */
    public final long getY1() {
        return y1;
    }
    
    /**
     * Sets the y-coordinate in pixels of this Viewport's top edge on the screen
     * to the specified value, if doing so would not cause this Viewport's
     * height to be negative.
     * @param y1 The new y-coordinate in pixels of this Viewport's top edge
     * @return Whether the change occurred
     */
    public final boolean setY1(long y1) {
        if (y1 <= y2) {
            this.y1 = y1;
            roundY1 = Frac.toInt(y1);
            updateYData();
            return true;
        }
        return false;
    }
    
    /**
     * Returns the x-coordinate in pixels of this Viewport's right edge on the
     * screen.
     * @return The x-coordinate in pixels of this Viewport's right edge
     */
    public final long getX2() {
        return x2;
    }
    
    /**
     * Sets the x-coordinate in pixels of this Viewport's right edge on the
     * screen to the specified value, if doing so would not cause this
     * Viewport's width to be negative.
     * @param x2 The new x-coordinate in pixels of this Viewport's right edge
     * @return Whether the change occurred
     */
    public final boolean setX2(long x2) {
        if (x1 <= x2) {
            this.x2 = x2;
            roundX2 = Frac.toInt(x2);
            updateXData();
            return true;
        }
        return false;
    }
    
    /**
     * Returns the y-coordinate in pixels of this Viewport's bottom edge on the
     * screen.
     * @return The y-coordinate in pixels of this Viewport's bottom edge
     */
    public final long getY2() {
        return y2;
    }
    
    /**
     * Sets the y-coordinate in pixels of this Viewport's bottom edge on the
     * screen to the specified value, if doing so would not cause this
     * Viewport's height to be negative.
     * @param y2 The new y-coordinate in pixels of this Viewport's bottom edge
     * @return Whether the change occurred
     */
    public final boolean setY2(long y2) {
        if (y1 <= y2) {
            this.y2 = y2;
            roundY2 = Frac.toInt(y2);
            updateYData();
            return true;
        }
        return false;
    }
    
    /**
     * Returns this Viewport's width in pixels on the screen.
     * @return This Viewport's width in pixels
     */
    public final long getWidth() {
        return right - left;
    }
    
    /**
     * Returns this Viewport's height in pixels on the screen.
     * @return This Viewport's height in pixels
     */
    public final long getHeight() {
        return bottom - top;
    }
    
    /**
     * Returns the x-coordinate in pixels of this Viewport's rendering region's
     * actual left edge on the screen.
     * @return The x-coordinate in pixels of this Viewport's rendering region's
     * actual left edge
     */
    public final int getLeftEdge() {
        return Frac.toInt(camera.getCenterX()) + left;
    }
    
    /**
     * Returns the x-coordinate in pixels of this Viewport's rendering region's
     * actual right edge on the screen.
     * @return The x-coordinate in pixels of this Viewport's rendering region's
     * actual right edge
     */
    public final int getRightEdge() {
        return Frac.toInt(camera.getCenterX()) + right;
    }
    
    /**
     * Returns the y-coordinate in pixels of this Viewport's rendering region's
     * actual top edge on the screen.
     * @return The y-coordinate in pixels of this Viewport's rendering region's
     * actual top edge
     */
    public final int getTopEdge() {
        return Frac.toInt(camera.getCenterY()) + top;
    }
    
    /**
     * Returns the y-coordinate in pixels of this Viewport's rendering region's
     * actual bottom edge on the screen.
     * @return The y-coordinate in pixels of this Viewport's rendering region's
     * actual bottom edge
     */
    public final int getBottomEdge() {
        return Frac.toInt(camera.getCenterY()) + bottom;
    }
    
    /**
     * Returns whether any part of the specified rectangular region is visible
     * through this Viewport.
     * @param x1 The x-coordinate of the region's left edge
     * @param y1 The y-coordinate of the region's top edge
     * @param x2 The x-coordinate of the region's right edge
     * @param y2 The y-coordinate of the region's bottom edge
     * @return Whether the specified rectangular region is visible through this
     * Viewport
     */
    public final boolean rectangleIsVisible(long x1, long y1, long x2, long y2) {
        if (camera != null && camera.newState == getNewGameState()) {
            int centerX = Frac.toInt(camera.getCenterX());
            int centerY = Frac.toInt(camera.getCenterY());
            return Frac.toInt(x1) < centerX + right && Frac.toInt(x2) > centerX + left
                    && Frac.toInt(y1) < centerY + bottom && Frac.toInt(y2) > centerY + top;
        }
        return false;
    }
    
}
