import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Optional;
import java.util.*;

import processing.core.*;

public final class VirtualWorld extends PApplet
{
    private static final int TIMER_ACTION_PERIOD = 100;

    private static final int VIEW_WIDTH = 640;
    private static final int VIEW_HEIGHT = 480;
    private static final int TILE_WIDTH = 32;
    private static final int TILE_HEIGHT = 32;
    private static final int WORLD_WIDTH_SCALE = 2;
    private static final int WORLD_HEIGHT_SCALE = 2;

    private static final int VIEW_COLS = VIEW_WIDTH / TILE_WIDTH;
    private static final int VIEW_ROWS = VIEW_HEIGHT / TILE_HEIGHT;
    private static final int WORLD_COLS = VIEW_COLS * WORLD_WIDTH_SCALE;
    private static final int WORLD_ROWS = VIEW_ROWS * WORLD_HEIGHT_SCALE;

    private static final String IMAGE_LIST_FILE_NAME = "imagelist";
    private static final String DEFAULT_IMAGE_NAME = "background_default";
    private static final int DEFAULT_IMAGE_COLOR = 0x808080;

    private static String LOAD_FILE_NAME = "world.sav";

    private static final String FAST_FLAG = "-fast";
    private static final String FASTER_FLAG = "-faster";
    private static final String FASTEST_FLAG = "-fastest";
    private static final double FAST_SCALE = 0.5;
    private static final double FASTER_SCALE = 0.25;
    private static final double FASTEST_SCALE = 0.10;

    private static double timeScale = 1.0;

    private ImageStore imageStore;
    private WorldModel world;
    private WorldView view;
    private static EventScheduler scheduler;
    private Player player;
    private long nextTime;
    private int playerCheckCount;

    public static EventScheduler getScheduler() {
        return scheduler;
    }

    public void settings() {
        size(VIEW_WIDTH, VIEW_HEIGHT);
    }

    /*
       Processing entry point for "sketch" setup.
    */
    public void setup() {
        this.imageStore = new ImageStore(
                createImageColored(TILE_WIDTH, TILE_HEIGHT,
                                   DEFAULT_IMAGE_COLOR));
        this.world = new WorldModel(WORLD_ROWS, WORLD_COLS,
                                    createDefaultBackground(imageStore));
        this.view = new WorldView(VIEW_ROWS, VIEW_COLS, this, world, TILE_WIDTH,
                                  TILE_HEIGHT);
        this.scheduler = new EventScheduler(timeScale);

        loadImages(IMAGE_LIST_FILE_NAME, imageStore, this);
        loadWorld(world, LOAD_FILE_NAME, imageStore);

        scheduleActions(world, scheduler, imageStore);

        nextTime = System.currentTimeMillis() + TIMER_ACTION_PERIOD;
    }

    public void draw() {
        long time = System.currentTimeMillis();
        if (time >= nextTime) {
            this.scheduler.updateOnTime(time);
            nextTime = time + TIMER_ACTION_PERIOD;
        }

        view.drawViewport();
    }

    // Just for debugging and for P5
    // Be sure to refactor this method as appropriate
    public void mousePressed() {
        Point pressed = mouseToPoint(mouseX, mouseY);
        System.out.println("CLICK! " + pressed.x + ", " + pressed.y);

        List<Point> squareClicked = pointsRadius(pressed);

        // okay so, background has multiple different tile types, this only checks if the tile is pure grass
        // it wont spawn infected tile on a flower tile, so we need to change this to also change the flower tiles
        // maybe we can check if the point has a background class instead of checking if they are the same same tile type? idk
        for (Point p : squareClicked) {
            Optional<PImage> currentImage = world.getBackgroundImage(p);

            if ( (currentImage.equals(world.getBackgroundImage(new Point(0,0)))) ||
                    ( (!(currentImage.equals(world.getBackgroundImage(new Point(0,8))))) &&
                            (!(currentImage.equals(world.getBackgroundImage(new Point (1,1)))))))
            {
                world.setBackground(p, new Background("infectedgrass", imageStore.getImageList("infectedgrass")));
            } else if (currentImage.equals(world.getBackgroundImage(new Point (1,1)))) {
                world.setBackground(p, new Background("infectedflowers", imageStore.getImageList("infectedflowers")));
            }
        }

        world.setBackground(pressed , new Background("infectedgrass", imageStore.getImageList("infectedgrass")));
        // only spawns wyvern if point clicked isnt occupied by entity
        if (!world.isOccupied(pressed)) {
            System.out.println(pressed);
            if ((pressed.x + pressed.y) % 2 == 0) {
                Wyvern wyvernentity = new Wyvern("wyvern_" + pressed.x + "_" + pressed.y, pressed, imageStore.getImageList("wyvern"), 4, 5, 0, 0);
                world.addEntity(wyvernentity);
                wyvernentity.scheduleActions(scheduler, world, imageStore);
            } else {
                DUDE_NOT_FULL dudenentity = new DUDE_NOT_FULL("DUDE_NOT_FULL_" + pressed.x + "_" + pressed.y, pressed, imageStore.getImageList("dude"), 5, 0,0,2,5,5);
                world.addEntity(dudenentity);
                dudenentity.scheduleActions(scheduler, world, imageStore);
            }
        }

        Optional<Entity> entityOptional = world.getOccupant(pressed);
        if (entityOptional.isPresent())
        {
            Entity entity = entityOptional.get();
            System.out.println(entity.getId() + ": " + entity.getClass().getName() + " : " + entity.getHealth());
        }

    }

    // returns list of points in a square radius of the pressed point
    private List<Point> pointsRadius(Point p) {
        List<Point> points = new ArrayList<>();
        points.add(new Point(p.x, p.y + 1));
        points.add(new Point(p.x,p.y-1));
        points.add(new Point(p.x - 1, p.y));
        points.add(new Point(p.x + 1, p.y));
        points.add(new Point(p.x, p.y + 2));
        points.add(new Point(p.x,p.y-2));
        points.add(new Point(p.x - 2, p.y));
        points.add(new Point(p.x + 2, p.y));
        points.add(new Point(p.x - 1, p.y + 1));
        points.add(new Point(p.x - 1, p.y -1));
        points.add(new Point(p.x + 1, p.y + 1));
        points.add(new Point(p.x + 1, p.y -1));
        return points;
    }

    private Point mouseToPoint(int x, int y)
    {
        return view.getViewport().viewportToWorld(mouseX/TILE_WIDTH, mouseY/TILE_HEIGHT);
    }
    public void keyPressed() {
        int dx = 0;
        int dy = 0;
        // FOR WASD MOVEMENT OF A SINGLE PLAYER ONLY
        if (keyPressed)
        {
            // create new player (maximum of 1 per world) when 0 is pressed
            if (key == '0' && playerCheckCount == 0)
            {
                player = new Player("player", new Point(20, 1), imageStore.getImageList("playerD"), 4,5,0,0 );
                world.addEntity(player);
                player.scheduleActions(scheduler, world, imageStore);
                playerCheckCount++;
            }
            if (key == 't' && playerCheckCount == 1 || key == 'T' && playerCheckCount==1)
            {
                player.executeActivity(world, imageStore, scheduler);
            }
            // move up with w (only works when there is an existing player)
           if(key == 'w' && playerCheckCount == 1|| key == 'W' && playerCheckCount == 1)
           {
               dy = -1;
               Point newPos = player.nextPositionPlayer(world, 1);
               player.setImages(imageStore.getImageList("playerU"));
               // only shifts view if the player moves (else it wont move world camera)
               if (!player.getPosition().equals(newPos))
               {
                   view.shiftView(dx, dy);
               }
               world.moveEntity(player, newPos);
           }
            // move up with s (only works when there is an existing player)
            if(key == 's' && playerCheckCount == 1|| key == 'S' && playerCheckCount == 1)
            {
                dy = 1;
                Point newPos = player.nextPositionPlayer(world, 2);
                player.setImages(imageStore.getImageList("playerD"));
                // only shifts view if the player moves (else it wont move world camera)
                if (!player.getPosition().equals(newPos))
                {
                    view.shiftView(dx, dy);
                }
                world.moveEntity(player, newPos);
            }
            // move up with w (only works when there is an existing player)
            if(key == 'a' && playerCheckCount == 1|| key == 'A' && playerCheckCount == 1)
            {
                dx = -1;
                Point newPos = player.nextPositionPlayer(world, 3);
                player.setImages(imageStore.getImageList("playerL"));
                if (!player.getPosition().equals(newPos))
                {
                    view.shiftView(dx, dy);
                }
                world.moveEntity(player, newPos);
            }
            // move up with w (only works when there is an existing player)
            if(key == 'd' && playerCheckCount == 1 || key == 'D' && playerCheckCount == 1)
            {
                dx = 1;
                Point newPos = player.nextPositionPlayer(world, 4);
                player.setImages(imageStore.getImageList("playerR"));
                if (!player.getPosition().equals(newPos))
                {
                    view.shiftView(dx, dy);
                }
                world.moveEntity(player, newPos);
            }

        }
        if (key == CODED) {


            switch (keyCode) {
                case UP:
                    dy = -1;
                    break;
                case DOWN:
                    dy = 1;
                    break;
                case LEFT:
                    dx = -1;
                    break;
                case RIGHT:
                    dx = 1;
                    break;
            }

            view.shiftView(dx, dy);
        }
    }

    public static Background createDefaultBackground(ImageStore imageStore) {
        return new Background(DEFAULT_IMAGE_NAME,
                              imageStore.getImageList(DEFAULT_IMAGE_NAME));
    }

    public static PImage createImageColored(int width, int height, int color) {
        PImage img = new PImage(width, height, RGB);
        img.loadPixels();
        for (int i = 0; i < img.pixels.length; i++) {
            img.pixels[i] = color;
        }
        img.updatePixels();
        return img;
    }

    static void loadImages(
            String filename, ImageStore imageStore, PApplet screen)
    {
        try {
            Scanner in = new Scanner(new File(filename));
            imageStore.loadImages(in, screen);
        }
        catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
        }
    }

    public static void loadWorld(
            WorldModel world, String filename, ImageStore imageStore)
    {
        try {
            Scanner in = new Scanner(new File(filename));
            world.load(in, imageStore);
            // text for instructions on world
        }
        catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
        }
    }

    public static void scheduleActions(
            WorldModel world, EventScheduler scheduler, ImageStore imageStore)
    {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof AnimatedEntity)
            {
                ((AnimatedEntity)entity).scheduleActions(scheduler, world, imageStore);
            }
        }
    }

    public static void parseCommandLine(String[] args) {
        if (args.length > 1)
        {
            if (args[0].equals("file"))
            {

            }
        }
        for (String arg : args) {
            switch (arg) {
                case FAST_FLAG:
                    timeScale = Math.min(FAST_SCALE, timeScale);
                    break;
                case FASTER_FLAG:
                    timeScale = Math.min(FASTER_SCALE, timeScale);
                    break;
                case FASTEST_FLAG:
                    timeScale = Math.min(FASTEST_SCALE, timeScale);
                    break;
            }
        }
    }

    public static void main(String[] args) {
        parseCommandLine(args);
        PApplet.main(VirtualWorld.class);
    }
}
