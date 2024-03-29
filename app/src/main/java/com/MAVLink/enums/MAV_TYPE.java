/* AUTO-GENERATED FILE.  DO NOT MODIFY.
 *
 * This class was automatically generated by the
 * java mavlink generator tool. It should not be modified by hand.
 */

package com.MAVLink.enums;

/**
 * MAVLINK component type reported in HEARTBEAT message. Flight controllers must report the type of the vehicle on which they are mounted (e.g. MAV_TYPE_OCTOROTOR). All other components must report a value appropriate for their type (e.g. a camera must use MAV_TYPE_CAMERA).
 */
public class MAV_TYPE {
   public static final int MAV_TYPE_GENERIC = 0; /* Generic micro air vehicle | */
   public static final int MAV_TYPE_FIXED_WING = 1; /* Fixed wing aircraft. | */
   public static final int MAV_TYPE_QUADROTOR = 2; /* Quadrotor | */
   public static final int MAV_TYPE_ENUM_END = 3; /*  | */
}
            