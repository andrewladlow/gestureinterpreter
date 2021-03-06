package gestureinterpreter;

import java.io.Serializable;

import com.leapmotion.leap.Vector;

/**
 * Class containing the 3D co-ordinates of a point.
 */
public class Point implements Serializable {
    private static final long serialVersionUID = -7199124440157829270L;
    
    private double x;
    private double y;
    private double z;
    private int ID = 0;

    /**
     * Creates a new point with the given co-ordinates.
     * 
     * @param x The X co-ordinate.
     * @param y The Y co-ordinate.
     * @param z The Z co-ordinate.
     */
    public Point(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a new point from the provided 3D vector.
     * 
     * @param v The 3D vector to obtain co-ordinates from.
     */
    public Point(Vector v) {
        x = v.getX();
        y = v.getY();
        z = v.getZ();
    }

    /**
     * Creates a new point with the given co-ordinates and ID.
     * 
     * @param x The X co-ordinate.
     * @param y The Y co-ordinate.
     * @param z The Z co-ordinate.
     * @param ID The ID of this point.
     */
    public Point(double x, double y, double z, int ID) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.ID = ID;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getID() {
        return ID;
    }
}
