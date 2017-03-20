package cell2D;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.newdawn.slick.AppGameContainer;
import org.newdawn.slick.Color;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.Input;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.command.BasicCommand;
import org.newdawn.slick.command.Command;
import org.newdawn.slick.command.Control;
import org.newdawn.slick.command.ControllerButtonControl;
import org.newdawn.slick.command.ControllerDirectionControl;
import org.newdawn.slick.command.InputProvider;
import org.newdawn.slick.command.InputProviderListener;
import org.newdawn.slick.command.KeyControl;
import org.newdawn.slick.command.MouseButtonControl;
import org.newdawn.slick.state.BasicGameState;
import org.newdawn.slick.state.StateBasedGame;
import org.newdawn.slick.state.transition.Transition;

public abstract class CellGame {
    
    private static enum CommandState {
        NOTHELD, PRESSED, HELD, RELEASED, TAPPED, UNTAPPED
    }
    
    private static final Color transparent = new Color(0, 0, 0, 0);
    private static final int transparentInt = colorToInt(transparent);
    
    private boolean closeRequested = false;
    private final StateBasedGame game;
    private final Map<Integer,CellGameState> states = new HashMap<>();
    private CellGameState currentState = null;
    private boolean negativeIDsOffLimits = false;
    private boolean initialized = false;
    private InputProvider provider = null;
    private Input input = null;
    private int commandToBind = -1;
    private Command[] commands;
    private CommandState[] commandStates;
    private CommandState[] commandChanges;
    private int adjustedMouseX = 0;
    private int newMouseX = 0;
    private int adjustedMouseY = 0;
    private int newMouseY = 0;
    private int mouseWheel = 0;
    private int newMouseWheel = 0;
    private String typingString = null;
    private int maxTypingStringLength = 0;
    private String typedString = null;
    private int fps;
    private double msPerFrame;
    private double msToRun;
    private final DisplayMode[] displayModes;
    private int screenWidth;
    private int screenHeight;
    private double scaleFactor;
    private double effectiveScaleFactor = 1;
    private int screenXOffset = 0;
    private int screenYOffset = 0;
    private boolean fullscreen;
    private boolean updateScreen = true;
    private Image loadingImage = null;
    private boolean loadingScreenRenderedOnce = false;
    private final Map<String,Filter> filters = new HashMap<>();
    private final Map<String,Sprite> sprites = new HashMap<>();
    private final Map<String,SpriteSheet> spriteSheets = new HashMap<>();
    private final Map<String,Animation> animations = new HashMap<>();
    private final Map<String,Sound> sounds = new HashMap<>();
    private final Map<String,Music> musics = new HashMap<>();
    private MusicInstance currentMusic = new MusicInstance(null, 0, 0, false);
    private final SortedMap<Integer,MusicInstance> musicStack = new TreeMap<>();
    private boolean stackOverridden = false;
    private boolean musicPaused = false;
    private float musicPosition = 0;
    private int musicFadeType = 0;
    private double fadeStartVolume = 0;
    private double fadeEndVolume = 0;
    private double fadeDuration = 0;
    private double msFading = 0;
    
    public CellGame(String gamename, int numCommands, int fps,
            int screenWidth, int screenHeight, double scaleFactor,
            boolean fullscreen, String loadingImagePath) throws SlickException {
        game = new Game(gamename);
        if (numCommands < 0) {
            throw new RuntimeException("Attempted to create a CellGame with a negative number of controls");
        }
        commands = new Command[numCommands];
        commandStates = new CommandState[numCommands];
        commandChanges = new CommandState[numCommands];
        for (int i = 0; i < numCommands; i++) {
            commands[i] = new BasicCommand("Command " + i);
            commandStates[i] = CommandState.NOTHELD;
            commandChanges[i] = CommandState.NOTHELD;
        }
        setFPS(fps);
        msToRun = 0;
        try {
            displayModes = Display.getAvailableDisplayModes();
        } catch (LWJGLException e) {
            throw new RuntimeException(e.toString());
        }
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        setScaleFactor(scaleFactor);
        this.fullscreen = fullscreen;
        if (loadingImagePath != null) {
            loadingImage = new Image(loadingImagePath);
        }
    }
    
    public static final void loadNatives(String path) {
        System.setProperty("java.library.path", path);
        System.setProperty("org.lwjgl.librarypath", new File(path).getAbsolutePath());
    }
    
    public static final void startGame(CellGame game) throws SlickException {
        AppGameContainer container = new AppGameContainer(game.game);
        game.updateScreen(container);
        container.setTargetFrameRate(game.getFPS());
        container.setShowFPS(false);
        container.start();
    }
    
    private static int colorToInt(Color color) {
        return (color.getAlphaByte() << 24) | (color.getRedByte() << 16) | (color.getGreenByte() << 8) | color.getBlueByte();
    }
    
    private static Color intToColor(int color) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF);
    }
    
    static final GameImage getTransparentImage(String path, Color transColor) throws SlickException {
        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(new File(path));
        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        if (bufferedImage.getType() != BufferedImage.TYPE_4BYTE_ABGR) {
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            java.awt.Graphics bufferedGraphics = newImage.getGraphics();
            bufferedGraphics.drawImage(bufferedImage, 0, 0, null);
            bufferedGraphics.dispose();
            bufferedImage = newImage;
        }
        Image image;
        if (transColor == null) {
            image = new Image(path);
        } else {
            image = new Image(width, height);
            Graphics graphics = image.getGraphics();
            Color color;
            int transR = transColor.getRedByte();
            int transG = transColor.getGreenByte();
            int transB = transColor.getBlueByte();
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    color = intToColor(bufferedImage.getRGB(x, y));
                    if (color.getRedByte() == transR
                            && color.getGreenByte() == transG
                            && color.getBlueByte() == transB) {
                        color = transparent;
                        bufferedImage.setRGB(x, y, transparentInt);
                    }
                    graphics.setColor(color);
                    graphics.fillRect(x, y, 1, 1);
                }
            }
            graphics.flush();
        }
        image.setFilter(Image.FILTER_NEAREST);
        return new GameImage(image, bufferedImage);
    }
    
    static final GameImage getRecoloredImage(BufferedImage bufferedImage, Map<Color,Color> colorMap) throws SlickException {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        java.awt.Graphics bufferedGraphics = newImage.getGraphics();
        bufferedGraphics.drawImage(bufferedImage, 0, 0, null);
        bufferedGraphics.dispose();
        Image image = new Image(width, height);
        image.setFilter(Image.FILTER_NEAREST);
        Graphics graphics = image.getGraphics();
        int size = colorMap.size();
        int[] oldR = new int[size];
        int[] oldG = new int[size];
        int[] oldB = new int[size];
        int[] newR = new int[size];
        int[] newG = new int[size];
        int[] newB = new int[size];
        int i = 0;
        Color key, value;
        for (Map.Entry<Color,Color> entry : colorMap.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            oldR[i] = key.getRedByte();
            oldG[i] = key.getGreenByte();
            oldB[i] = key.getBlueByte();
            newR[i] = value.getRedByte();
            newG[i] = value.getGreenByte();
            newB[i] = value.getBlueByte();
            i++;
        }
        Color color;
        int colorR, colorG, colorB;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                color = intToColor(newImage.getRGB(x, y));
                colorR = color.getRedByte();
                colorG = color.getGreenByte();
                colorB = color.getBlueByte();
                for (int j = 0; j < size; j++) {
                    if (oldR[j] == colorR && oldG[j] == colorG && oldB[j] == colorB) {
                        color = new Color(newR[j], newG[j], newB[j], color.getAlphaByte());
                        newImage.setRGB(x, y, colorToInt(color));
                        break;
                    }
                }
                graphics.setColor(color);
                graphics.fillRect(x, y, 1, 1);
            }
        }
        graphics.flush();
        return new GameImage(image, newImage);
    }
    
    static final GameImage getRecoloredImage(BufferedImage bufferedImage, Color newColor) throws SlickException {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        java.awt.Graphics bufferedGraphics = newImage.getGraphics();
        bufferedGraphics.drawImage(bufferedImage, 0, 0, null);
        bufferedGraphics.dispose();
        Image image = new Image(width, height);
        image.setFilter(Image.FILTER_NEAREST);
        Graphics graphics = image.getGraphics();
        int newColorR = newColor.getRedByte();
        int newColorG = newColor.getGreenByte();
        int newColorB = newColor.getBlueByte();
        Color color;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                color = new Color(newColorR, newColorG, newColorB, (newImage.getRGB(x, y) >> 24) & 0xFF);
                newImage.setRGB(x, y, colorToInt(color));
                graphics.setColor(color);
                graphics.fillRect(x, y, 1, 1);
            }
        }
        graphics.flush();
        return new GameImage(image, newImage);
    }
    
    private void updateScreen(GameContainer container) throws SlickException {
        updateScreen = false;
        if (container instanceof AppGameContainer) {
            AppGameContainer appContainer = (AppGameContainer)container;
            if (fullscreen) {
                int wastedArea = -1;
                int newWidth = -1;
                int newHeight = -1;
                double newScale = -1;
                int newXOffset = -1;
                int newYOffset = -1;
                double screenRatio = ((double)screenHeight)/screenWidth;
                for (int i = 0; i < displayModes.length; i++) {
                    int modeWidth = displayModes[i].getWidth();
                    int modeHeight = displayModes[i].getHeight();
                    if (modeWidth < screenWidth || modeHeight < screenHeight) {
                        continue;
                    }
                    double modeScale;
                    int modeXOffset = 0;
                    int modeYOffset = 0;
                    if (((double)modeHeight)/modeWidth > screenRatio) {
                        modeScale = ((double)modeWidth)/screenWidth;
                        modeYOffset = (int)((modeHeight/modeScale - screenHeight)/2);
                    } else {
                        modeScale = ((double)modeHeight)/screenHeight;
                        modeXOffset = (int)((modeWidth/modeScale - screenWidth)/2);
                    }
                    int modeArea = modeWidth*modeHeight - (int)(screenWidth*screenHeight*modeScale*modeScale);
                    if (modeArea < wastedArea || wastedArea == -1) {
                        wastedArea = modeArea;
                        newWidth = modeWidth;
                        newHeight = modeHeight;
                        newScale = modeScale;
                        newXOffset = modeXOffset;
                        newYOffset = modeYOffset;
                    }
                }
                if (wastedArea != -1) {
                    effectiveScaleFactor = newScale;
                    screenXOffset = newXOffset;
                    screenYOffset = newYOffset;
                    appContainer.setDisplayMode(newWidth, newHeight, true);
                    return;
                }
            }
            effectiveScaleFactor = scaleFactor;
            screenXOffset = 0;
            screenYOffset = 0;
            appContainer.setDisplayMode((int)(screenWidth*scaleFactor), (int)(screenHeight*scaleFactor), false);
            return;
        }
        double screenRatio = ((double)screenHeight)/screenWidth;
        int containerWidth = container.getWidth();
        int containerHeight = container.getHeight();
        if (((double)containerHeight)/containerWidth > screenRatio) {
            effectiveScaleFactor = ((double)containerWidth)/screenWidth;
            screenXOffset = 0;
            screenYOffset = (int)((containerHeight/effectiveScaleFactor - screenHeight)/2);
            return;
        }
        effectiveScaleFactor = ((double)containerHeight)/screenHeight;
        screenXOffset = (int)((containerWidth/effectiveScaleFactor - screenWidth)/2);
        screenYOffset = 0;
    }
    
    public final void close() {
        closeRequested = true;
    }
    
    private class Game extends StateBasedGame implements InputProviderListener {
        
        private Game(String name) {
            super(name);
        }
        
        @Override
        public final void initStatesList(GameContainer container) throws SlickException {
            new LoadingState();
            negativeIDsOffLimits = true;
        }
        
        @Override
        public final void preUpdateState(GameContainer container, int delta) {}
        
        @Override
        public final void postUpdateState(GameContainer container, int delta) throws SlickException {
            if (loadingScreenRenderedOnce) {
                if (initialized) {
                    double timeElapsed = Math.min(delta, msPerFrame);
                    msToRun += timeElapsed;
                    if (currentMusic.music != null && !musicPaused) {
                        if (musicFadeType != 0) {
                            msFading = Math.min(msFading + timeElapsed, fadeDuration);
                            if (msFading == fadeDuration) {
                                currentMusic.music.setVolume(fadeEndVolume);
                                if (musicFadeType == 2) {
                                    currentMusic.music.stop();
                                }
                                musicFadeType = 0;
                            } else {
                                currentMusic.music.setVolume(fadeStartVolume + (msFading/fadeDuration)*(fadeEndVolume - fadeStartVolume));
                            }
                        }
                        if (!currentMusic.music.isPlaying()) {
                            stopMusic();
                        }
                    }
                    if (msToRun >= msPerFrame) {
                        for (int i = 0; i < commandChanges.length; i++) {
                            commandStates[i] = commandChanges[i];
                            if (commandChanges[i] == CommandState.PRESSED
                                    || commandChanges[i] == CommandState.UNTAPPED) {
                                commandChanges[i] = CommandState.HELD;
                            } else if (commandChanges[i] == CommandState.RELEASED
                                    || commandChanges[i] == CommandState.TAPPED) {
                                commandChanges[i] = CommandState.NOTHELD;
                            }
                        }
                        adjustedMouseX = Math.min(Math.max((int)(newMouseX/effectiveScaleFactor) - screenXOffset, 0), screenWidth - 1);
                        adjustedMouseY = Math.min(Math.max((int)(newMouseY/effectiveScaleFactor) - screenYOffset, 0), screenHeight - 1);
                        mouseWheel = newMouseWheel;
                        newMouseWheel = 0;
                        CellGame.this.getCurrentState().doFrame();
                        msToRun -= msPerFrame;
                        if (closeRequested) {
                            container.exit();
                        } else if (updateScreen) {
                            updateScreen(container);
                        }
                    }
                } else {
                    provider = new InputProvider(container.getInput());
                    provider.addListener(this);
                    input = new Input(container.getScreenHeight());
                    newMouseX = input.getMouseX();
                    newMouseY = input.getMouseY();
                    initActions();
                    initialized = true;
                }
            }
        }
        
        @Override
        public final void preRenderState(GameContainer container, Graphics g) {
            float scale = (float)effectiveScaleFactor;
            g.scale(scale, scale);
            g.setWorldClip(screenXOffset, screenYOffset, screenWidth, screenHeight);
        }
        
        @Override
        public final void postRenderState(GameContainer container, Graphics g) throws SlickException {
            if (initialized) {
                renderActions(g, screenXOffset, screenYOffset, screenXOffset + screenWidth, screenYOffset + screenHeight);
            }
            g.clearWorldClip();
        }
        
        @Override
        public final void mouseMoved(int oldx, int oldy, int newx, int newy) {
            newMouseX = newx;
            newMouseY = newy;
        }
        
        @Override
        public final void mouseWheelMoved(int delta) {
            newMouseWheel += delta;
        }
        
        @Override
        public final void keyPressed(int key, char c) {
            if (typingString != null) {
                if (key == Input.KEY_ESCAPE) {
                    cancelTypingToString();
                } else if (key == Input.KEY_BACK) {
                    if (typingString.length() > 0) {
                        char toDelete = typingString.charAt(typingString.length() - 1);
                        typingString = typingString.substring(0, typingString.length() - 1);
                        CellGame.this.getCurrentState().charDeleted(toDelete);
                    }
                } else if (key == Input.KEY_DELETE) {
                    String s = typingString;
                    typingString = "";
                    CellGame.this.getCurrentState().stringDeleted(s);
                } else if (key == Input.KEY_ENTER) {
                    finishTypingToString();
                } else if (c != '\u0000' && typingString.length() < maxTypingStringLength) {
                    typingString += c;
                    CellGame.this.getCurrentState().charTyped(c);
                }
            } else if (commandToBind >= 0) {
                finishBindingToCommand(new KeyControl(key));
            }
        }
        
        @Override
        public final void mouseClicked(int button, int x, int y, int clickCount) {
            if (commandToBind >= 0) {
                finishBindingToCommand(new MouseButtonControl(button));
            }
        }
        
        @Override
        public final void controllerUpPressed(int controller) {
            if (commandToBind >= 0) {
                finishBindingToCommand(new ControllerDirectionControl(controller, ControllerDirectionControl.UP));
            }
        }
        
        @Override
        public final void controllerDownPressed(int controller) {
            if (commandToBind >= 0) {
                finishBindingToCommand(new ControllerDirectionControl(controller, ControllerDirectionControl.DOWN));
            }
        }
        
        @Override
        public final void controllerLeftPressed(int controller) {
            if (commandToBind >= 0) {
                finishBindingToCommand(new ControllerDirectionControl(controller, ControllerDirectionControl.LEFT));
            }
        }
        
        @Override
        public final void controllerRightPressed(int controller) {
            if (commandToBind >= 0) {
                finishBindingToCommand(new ControllerDirectionControl(controller, ControllerDirectionControl.RIGHT));
            }
        }
        
        @Override
        public final void controllerButtonPressed(int controller, int button) {
            if (commandToBind >= 0) {
                finishBindingToCommand(new ControllerButtonControl(controller, button));
            }
        }
        
        @Override
        public final void controlPressed(Command command) {
            if (commandToBind >= 0 || typingString != null) {
                return;
            }
            int i = Arrays.asList(commands).indexOf(command);
            if (i >= 0) {
                if (commandChanges[i] == CommandState.NOTHELD
                        || commandChanges[i] == CommandState.TAPPED) {
                    commandChanges[i] = CommandState.PRESSED;
                } else if (commandChanges[i] == CommandState.RELEASED) {
                    commandChanges[i] = CommandState.UNTAPPED;
                }
            }
        }
        
        @Override
        public final void controlReleased(Command command) {
            if (commandToBind >= 0 || typingString != null) {
                return;
            }
            int i = Arrays.asList(commands).indexOf(command);
            if (i >= 0) {
                if (commandChanges[i] == CommandState.HELD
                        || commandChanges[i] == CommandState.UNTAPPED) {
                    commandChanges[i] = CommandState.RELEASED;
                } else if (commandChanges[i] == CommandState.PRESSED) {
                    commandChanges[i] = CommandState.TAPPED;
                }
            }
        }
        
    }
    
    private class State extends BasicGameState {
        
        private final CellGameState state;
        
        private State(CellGameState state) {
            this.state = state;
        }
        
        @Override
        public int getID() {
            return state.getID();
        }
        
        @Override
        public final void init(GameContainer container, StateBasedGame game) throws SlickException {}
        
        @Override
        public final void update(GameContainer container, StateBasedGame game, int delta) throws SlickException {}
        
        @Override
        public final void render(GameContainer container, StateBasedGame game, Graphics g) throws SlickException {
            state.renderActions(CellGame.this, g, screenXOffset, screenYOffset, screenXOffset + screenWidth, screenYOffset + screenHeight);
        }
        
        @Override
        public final void enter(GameContainer container, StateBasedGame game) {
            state.active = true;
            state.enteredActions(CellGame.this);
        }
        
        @Override
        public final void leave(GameContainer container, StateBasedGame game) {
            state.leftActions(CellGame.this);
            state.active = false;
        }
        
    }
    
    private class LoadingState extends cell2D.BasicGameState {
        
        private LoadingState() {
            super(CellGame.this, -2);
        }
        
        @Override
        public final void renderActions(CellGame game, Graphics g, int x1, int y1, int x2, int y2) {
            if (loadingImage != null) {
                loadingImage.draw((x1 + x2 - loadingImage.getWidth())/2, (y1 + y2 - loadingImage.getHeight())/2);
            }
            loadingScreenRenderedOnce = true;
        }
        
    }
    
    public final CellGameState getState(int id) {
        return (id < 0 ? null : states.get(id));
    }
    
    public final CellGameState getCurrentState() {
        return currentState;
    }
    
    public final int getCurrentStateID() {
        return currentState.getID();
    }
    
    final void addState(CellGameState state) {
        int id = state.getID();
        if (id < 0 && negativeIDsOffLimits) {
            throw new RuntimeException("Attempted to add a CellGameState with negative ID " + id);
        }
        states.put(id, state);
        game.addState(new State(state));
    }
    
    public final void enterState(int id) {
        enterState(id, null, null);
    }
    
    public final void enterState(int id, Transition leave, Transition enter) {
        if (id < 0 && negativeIDsOffLimits) {
            throw new RuntimeException("Attempted to enter a CellGameState with negative ID " + id);
        }
        currentState = states.get(id);
        game.enterState(id, leave, enter);
    }
    
    public abstract void initActions() throws SlickException;
    
    public void renderActions(Graphics g, int x1, int y1, int x2, int y2) {}
    
    private void finishBindingToCommand(Control control) {
        bindControl(commandToBind, control);
        commandToBind = -1;
    }
    
    public final List<Control> getControlsFor(int commandNum) {
        if (commandNum < 0 || commandNum >= commands.length) {
            throw new RuntimeException("Attempted to get the controls for nonexistent command number " + commandNum);
        }
        return (provider == null ? new ArrayList<>() : provider.getControlsFor(commands[commandNum]));
    }
    
    public final void bindControl(int commandNum, Control control) {
        if (commandNum < 0 || commandNum >= commands.length) {
            throw new RuntimeException("Attempted to bind nonexistent command number " + commandNum);
        }
        if (provider != null) {
            provider.bindCommand(control, commands[commandNum]);
        }
    }
    
    public final void unbindControl(Control control) {
        if (provider != null) {
            provider.unbindCommand(control);
        }
    }
    
    public final void clearControls(int commandNum) {
        if (commandNum < 0 || commandNum >= commands.length) {
            throw new RuntimeException("Attempted to clear nonexistent command number " + commandNum);
        }
        if (provider != null) {
            provider.clearCommand(commands[commandNum]);
        }
    }
    
    public final int getBindingCommand() {
        return commandToBind;
    }
    
    public final void waitToBindToCommand(int commandNum) {
        if (commandNum < 0 || commandNum >= commands.length) {
            throw new RuntimeException("Attempted to begin waiting to bind nonexistent command number " + commandNum);
        }
        if (typingString != null) {
            throw new RuntimeException("Attempted to begin waiting to bind command number " + commandNum + " while already typing to a String");
        }
        commandToBind = commandNum;
        for (int i = 0; i < commandChanges.length; i++) {
            commandChanges[i] = CommandState.NOTHELD;
        }
    }
    
    public final void cancelBindToCommand() {
        commandToBind = -1;
    }
    
    public final boolean commandPressed(int commandNum) {
        return commandStates[commandNum] == CommandState.PRESSED
                || commandStates[commandNum] == CommandState.TAPPED
                || commandStates[commandNum] == CommandState.UNTAPPED;
    }
    
    public final boolean commandHeld(int commandNum) {
        return commandStates[commandNum] == CommandState.PRESSED
                || commandStates[commandNum] == CommandState.HELD
                || commandStates[commandNum] == CommandState.TAPPED;
    }
    
    public final boolean commandReleased(int commandNum) {
        return commandStates[commandNum] == CommandState.RELEASED
                || commandStates[commandNum] == CommandState.TAPPED
                || commandStates[commandNum] == CommandState.UNTAPPED;
    }
    
    public final int getMouseX() {
        return adjustedMouseX;
    }
    
    public final int getMouseY() {
        return adjustedMouseY;
    }
    
    public final int getMouseWheelMoved() {
        return mouseWheel;
    }
    
    public final String getTypingString() {
        return typingString;
    }
    
    public final String getTypedString() {
        String s = typedString;
        typedString = null;
        return s;
    }
    
    public final void beginTypingToString(String initialString, int maxLength) {
        if (maxLength <= 0) {
            throw new RuntimeException("Attempted to begin typing to a String with non-positive maximum length " + maxLength);
        }
        if (commandToBind >= 0) {
            throw new RuntimeException("Attempted to begin typing to a String while already binding to command number " + commandToBind);
        }
        if (initialString == null) {
            initialString = "";
        }
        if (initialString.length() > maxLength) {
            initialString = initialString.substring(0, maxLength);
        }
        typingString = initialString;
        maxTypingStringLength = maxLength;
        typedString = null;
        for (int i = 0; i < commandChanges.length; i++) {
            commandChanges[i] = CommandState.NOTHELD;
        }
        getCurrentState().stringBegan(initialString);
    }
    
    public final void beginTypingToString(int maxLength) {
        beginTypingToString("", maxLength);
    }
    
    public final void finishTypingToString() {
        if (typingString != null) {
            typedString = typingString;
            typingString = null;
            maxTypingStringLength = 0;
            getCurrentState().stringTyped(typedString);
        }
    }
    
    public final void cancelTypingToString() {
        if (typingString != null) {
            String s = typingString;
            typingString = null;
            maxTypingStringLength = 0;
            typedString = null;
            getCurrentState().stringCanceled(s);
        }
    }
    
    public final int getFPS() {
        return fps;
    }
    
    public final void setFPS(int fps) {
        if (fps <= 0) {
            throw new RuntimeException("Attempted to run a CellGame at a non-positive FPS");
        }
        this.fps = fps;
        msPerFrame = 1000/((double)fps);
    }
    
    public final int getScreenWidth() {
        return screenWidth;
    }
    
    public final void setScreenWidth(int screenWidth) {
        if (screenWidth <= 0) {
            throw new RuntimeException("Attempted to give a CellGame a non-positive screen width");
        }
        this.screenWidth = screenWidth;
        updateScreen = true;
    }
    
    public final int getScreenHeight() {
        return screenHeight;
    }
    
    public final void setScreenHeight(int screenHeight) {
        if (screenHeight <= 0) {
            throw new RuntimeException("Attempted to give a CellGame a non-positive screen height");
        }
        this.screenHeight = screenHeight;
        updateScreen = true;
    }
    
    public final double getScaleFactor() {
        return scaleFactor;
    }
    
    public final void setScaleFactor(double scaleFactor) {
        if (scaleFactor <= 0) {
            throw new RuntimeException("Attempted to give a CellGame a non-positive scale factor");
        }
        this.scaleFactor = scaleFactor;
        updateScreen = true;
    }
    
    public final boolean isFullscreen() {
        return fullscreen;
    }
    
    public final void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        updateScreen = true;
    }
    
    public final void add(String name, Filter filter) {
        if (name == null) {
            throw new RuntimeException("Attempted to add a Filter with a null name");
        }
        if (filters.put(name, filter) != null) {
            throw new RuntimeException("Attempted to add multiple Filters with the name " + name);
        }
    }
    
    public final void add(String name, Sprite sprite) {
        if (name == null) {
            throw new RuntimeException("Attempted to add a Sprite with a null name");
        }
        if (sprites.put(name, sprite) != null) {
            throw new RuntimeException("Attempted to add multiple Sprites with the name " + name);
        }
    }
    
    public final void add(String name, SpriteSheet spriteSheet) {
        if (name == null) {
            throw new RuntimeException("Attempted to add a SpriteSheet with a null name");
        }
        if (spriteSheets.put(name, spriteSheet) != null) {
            throw new RuntimeException("Attempted to add multiple SpriteSheets with the name " + name);
        }
    }
    
    public final void add(String name, Animation animation) {
        if (name == null) {
            throw new RuntimeException("Attempted to add an Animation with a null name");
        }
        if (animations.put(name, animation) != null) {
            throw new RuntimeException("Attempted to add multiple Animations with the name " + name);
        }
    }
    
    public final void add(String name, Sound sound) throws SlickException {
        if (name == null) {
            throw new RuntimeException("Attempted to add a Sound with a null name");
        }
        if (sounds.put(name, sound) != null) {
            throw new RuntimeException("Attempted to add multiple Sounds with the name " + name);
        }
    }
    
    public final void add(String name, Music music) throws SlickException {
        if (name == null) {
            throw new RuntimeException("Attempted to add a music track with a null name");
        }
        if (musics.put(name, music) != null) {
            throw new RuntimeException("Attempted to add multiple music tracks with the name " + name);
        }
    }
    
    public final Filter getFilter(String name) {
        return filters.get(name);
    }
    
    public final Sprite getSprite(String name) {
        return sprites.get(name);
    }
    
    public final SpriteSheet getSpriteSheet(String name) {
        return spriteSheets.get(name);
    }
    
    public final Animation getAnimation(String name) {
        return animations.get(name);
    }
    
    public final Sound getSound(String name) {
        return sounds.get(name);
    }
    
    public final Music getMusic(String name) {
        return musics.get(name);
    }
    
    private class MusicInstance {
        
        private final Music music;
        private final double pitch;
        private double volume;
        private final boolean loop;
        
        private MusicInstance(Music music, double pitch, double volume, boolean loop) {
            this.music = music;
            this.pitch = pitch;
            this.volume = volume;
            this.loop = loop;
        }
        
    }
    
    public final Music getCurrentMusic() {
        return currentMusic.music;
    }
    
    public final Music getMusic(int priority) {
        MusicInstance instance = musicStack.get(priority);
        return (instance == null ? null : instance.music);
    }
    
    private void changeMusic(MusicInstance instance) {
        if (!musicPaused) {
            if (currentMusic.music != null) {
                currentMusic.music.stop();
            }
            if (instance.music != null) {
                if (instance.loop) {
                    instance.music.loop(instance.pitch, instance.volume);
                } else {
                    instance.music.play(instance.pitch, instance.volume);
                }
            }
        }
        currentMusic = instance;
        musicPosition = 0;
        musicFadeType = 0;
    }
    
    private void startMusic(Music music, double pitch, double volume, boolean loop) {
        if (music == null) {
            stopMusic();
            return;
        }
        if (musicFadeType == 2 && !musicStack.isEmpty() && !stackOverridden) {
            musicStack.remove(musicStack.lastKey());
        }
        changeMusic(new MusicInstance(music, pitch, volume, loop));
        stackOverridden = true;
    }
    
    private void addMusicToStack(int priority, Music music, double pitch, double volume, boolean loop) {
        if (music == null) {
            stopMusic(priority);
            return;
        }
        MusicInstance instance = new MusicInstance(music, pitch, volume, loop);
        if ((musicStack.isEmpty() || priority >= musicStack.lastKey()) && !stackOverridden) {
            if (musicFadeType == 2 && !musicStack.isEmpty() && priority > musicStack.lastKey()) {
                musicStack.remove(musicStack.lastKey());
            }
            changeMusic(instance);
        }
        musicStack.put(priority, instance);
    }
    
    public final void playMusic(Music music) {
        startMusic(music, 1, 1, false);
    }
    
    public final void playMusic(Music music, double pitch, double volume) {
        startMusic(music, pitch, volume, false);
    }
    
    public final void playMusic(int priority, Music music) {
        addMusicToStack(priority, music, 1, 1, false);
    }
    
    public final void playMusic(int priority, Music music, double pitch, double volume) {
        addMusicToStack(priority, music, pitch, volume, false);
    }
    
    public final void loopMusic(Music music) {
        startMusic(music, 1, 1, true);
    }
    
    public final void loopMusic(Music music, double pitch, double volume) {
        startMusic(music, pitch, volume, true);
    }
    
    public final void loopMusic(int priority, Music music) {
        addMusicToStack(priority, music, 1, 1, true);
    }
    
    public final void loopMusic(int priority, Music music, double pitch, double volume) {
        addMusicToStack(priority, music, pitch, volume, true);
    }
    
    public final void stopMusic() {
        if (musicStack.isEmpty()) {
            changeMusic(new MusicInstance(null, 0, 0, false));
            stackOverridden = false;
            return;
        }
        if (!stackOverridden) {
            musicStack.remove(musicStack.lastKey());
            if (musicStack.isEmpty()) {
                changeMusic(new MusicInstance(null, 0, 0, false));
                return;
            }
        }
        changeMusic(musicStack.get(musicStack.lastKey()));
        stackOverridden = false;
    }
    
    public final void stopMusic(Music music) {
        if (currentMusic.music != null && currentMusic.music == music) {
            stopMusic();
        }
    }
    
    public final void stopMusic(int priority) {
        if (musicStack.isEmpty()) {
            return;
        }
        if (priority == musicStack.lastKey() && !stackOverridden) {
            stopMusic();
        } else {
            musicStack.remove(priority);
        }
    }
    
    public final void stopMusic(int priority, Music music) {
        if (musicStack.isEmpty()) {
            return;
        }
        MusicInstance instance = musicStack.get(priority);
        if (instance != null && instance.music != null && instance.music == music) {
            if (priority == musicStack.lastKey() && !stackOverridden) {
                stopMusic();
            } else {
                musicStack.remove(priority);
            }
        }
    }
    
    public final boolean musicIsPaused() {
        return musicPaused;
    }
    
    public final void pauseMusic() {
        if (currentMusic.music != null && !musicPaused) {
            if (currentMusic.music != null) {
                musicPosition = currentMusic.music.getPosition();
                currentMusic.music.stop();
            }
            musicPaused = true;
        }
    }
    
    public final void resumeMusic() {
        if (currentMusic.music != null && musicPaused) {
            if (currentMusic.music != null) {
                if (currentMusic.loop) {
                    currentMusic.music.loop(currentMusic.pitch, currentMusic.music.getVolume());
                } else {
                    currentMusic.music.play(currentMusic.pitch, currentMusic.music.getVolume());
                }
                currentMusic.music.setPosition(musicPosition);
            }
            musicPaused = false;
        }
    }
    
    public final double getMusicPosition() {
        return (currentMusic.music == null ? 0 : currentMusic.music.getPosition());
    }
    
    public final void setMusicPosition(double position) {
        if (currentMusic.music != null) {
            currentMusic.music.setPosition(position);
        }
    }
    
    public final double getMusicVolume() {
        return (currentMusic.music == null ? 0 : currentMusic.music.getVolume());
    }
    
    public final void setMusicVolume(double volume) {
        if (currentMusic.music != null) {
            currentMusic.volume = volume;
            if (currentMusic.music != null) {
                currentMusic.music.setVolume(volume);
                musicFadeType = 0;
            }
        }
    }
    
    public final void fadeMusicVolume(double volume, double duration) {
        if (currentMusic.music != null) {
            if (currentMusic.music != null) {
                musicFadeType = 1;
                fadeStartVolume = currentMusic.music.getVolume();
                fadeEndVolume = volume;
                fadeDuration = duration*1000;
                msFading = 0;
            }
            currentMusic.volume = volume;
        }
    }
    
    public final void fadeMusicOut(double duration) {
        if (currentMusic.music != null) {
            if (currentMusic.music == null) {
                stopMusic();
            } else {
                musicFadeType = 2;
                fadeStartVolume = currentMusic.music.getVolume();
                fadeEndVolume = 0;
                fadeDuration = duration*1000;
                msFading = 0;
            }
        }
    }
    
}
