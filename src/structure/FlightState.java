package structure;

import structure.quaternion.Quaternion;
import transforms.Vec3D;

public class FlightState {

        public int dirIdx = 0;
        public boolean mid_reached = false;

        /**
         * Interpolation parameter <0;1>
         */
        public double deltaTime = 0.0;

        /**
         * Position control points
         */
        public Vec3D posA;
        public Vec3D posMid;
        public Vec3D posB;

        /**
         * Rotation control points
         */
        public Quaternion rotA;
        public Quaternion rotMid;
        public Quaternion rotB;
}