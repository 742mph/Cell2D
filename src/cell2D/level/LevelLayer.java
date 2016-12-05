package cell2D.level;

import cell2D.CellGame;
import org.newdawn.slick.Graphics;

public abstract class LevelLayer extends LevelThinker {
    
    public abstract void renderActions(CellGame game, LevelState levelState,
            Graphics g, double x, double y, int x1, int y1, int x2, int y2);
    
}