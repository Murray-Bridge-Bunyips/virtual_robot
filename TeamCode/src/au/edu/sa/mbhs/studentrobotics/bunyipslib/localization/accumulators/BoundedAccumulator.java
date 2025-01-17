package au.edu.sa.mbhs.studentrobotics.bunyipslib.localization.accumulators;

import androidx.annotation.NonNull;

import au.edu.sa.mbhs.studentrobotics.bunyipslib.util.Geometry;
//import com.acmerobotics.dashboard.config.reflection.ReflectionConfig;
import com.acmerobotics.roadrunner.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import au.edu.sa.mbhs.studentrobotics.bunyipslib.external.units.Distance;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.external.units.Measure;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.transforms.Rect;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.util.Field;
import com.qualcomm.robotcore.util.ElapsedTime;

/**
 * Accumulator to define clamping limits on the field.
 *
 * @author Lucas Bubner, 2024
 * @since 6.0.0
 */
public class BoundedAccumulator extends Accumulator {
    /**
     * Maximum bounds (FTC field)
     */
    @NonNull
    public static Rect MAX_BOUNDS = Field.MAX_BOUNDS;

    private final List<Rect> restrictedAreas = new ArrayList<>();
    private final Rect robotBoundingBox;
    private final ElapsedTime velocityInhibitionTimer = new ElapsedTime();
    private final ElapsedTime deltaTime = new ElapsedTime();
    private Vector2d lastPos = Geometry.zeroVec();
    private PoseVelocity2d lastVel = Geometry.zeroVel();

    /**
     * Create a new BoundedAccumulator.
     *
     * @param robotRadius the radius of the robot from the center of localization,
     *                    will be interpreted as a square bounding box
     */
    public BoundedAccumulator(@NonNull Measure<Distance> robotRadius) {
        robotBoundingBox = new Rect(new Vector2d(-robotRadius.magnitude(), -robotRadius.magnitude()),
                new Vector2d(robotRadius.magnitude(), robotRadius.magnitude()), robotRadius.unit());
//        FtcDashboard.getInstance().withConfigRoot(c ->
//                c.putVariable(getClass().getSimpleName(), ReflectionConfig.createVariableFromClass(getClass())));
    }

    /**
     * Set areas on the field that the robot should not be able to move into.
     *
     * @param areas the areas to restrict the robot from moving into
     * @return this
     */
    @NonNull
    public BoundedAccumulator withRestrictedAreas(@NonNull Rect... areas) {
        Collections.addAll(restrictedAreas, areas);
        return this;
    }

    /**
     * Set areas on the field that the robot should not be able to move into.
     *
     * @param seasonField the current season field to restrict the robot from moving into
     * @return this
     */
    @NonNull
    public BoundedAccumulator withRestrictedAreas(@NonNull Field.Season seasonField) {
        restrictedAreas.addAll(seasonField.getRestrictedAreas());
        return this;
    }

    @Override
    public void accumulate(@NonNull Twist2dDual<Time> twist) {
        super.accumulate(twist);

        Pose2d currentPose = pose;
        Rect pos = robotBoundingBox.centeredAt(currentPose.position);

        Rect bounds = Rect.normalise(MAX_BOUNDS);
        if (!bounds.contains(pos)) {
            // Reset the pose to the nearest edge of the bounding box
            Vector2d newPos = new Vector2d(
                    Math.min(Math.max(currentPose.position.x, bounds.point1.x + robotBoundingBox.point2.x), bounds.point2.x - robotBoundingBox.point2.x),
                    Math.min(Math.max(currentPose.position.y, bounds.point1.y + robotBoundingBox.point2.y), bounds.point2.y - robotBoundingBox.point2.y)
            );
            currentPose = new Pose2d(newPos, currentPose.heading);
            velocityInhibitionTimer.reset();
        }

        for (Rect rect : restrictedAreas) {
            Rect area = Rect.normalise(rect);
            if (area.overlaps(pos)) {
                double newX = currentPose.position.x;
                double newY = currentPose.position.y;

                // Recalculate the new position to be the nearest edge of the bounding box
                // Note: Cases where the robot is in the corner of the area perform a bit strangely as
                // they are snapped to the nearest corner of the bounding box
                if (currentPose.position.x < area.point1.x || currentPose.position.x > area.point2.x) {
                    if (currentPose.position.x < area.point1.x) {
                        newX = area.point1.x - robotBoundingBox.point2.x;
                    } else {
                        newX = area.point2.x + robotBoundingBox.point2.x;
                    }
                }

                if (currentPose.position.y < area.point1.y || currentPose.position.y > area.point2.y) {
                    if (currentPose.position.y < area.point1.y) {
                        newY = area.point1.y - robotBoundingBox.point2.y;
                    } else {
                        newY = area.point2.y + robotBoundingBox.point2.y;
                    }
                }

                // Run the new position through the bounds check again for other boxes
                currentPose = new Pose2d(new Vector2d(newX, newY), currentPose.heading);
                pos = robotBoundingBox.centeredAt(currentPose.position);
                velocityInhibitionTimer.reset();
            }
        }

        // Switch to manual velocity calculation based on the adjusted pose
        if (velocityInhibitionTimer.seconds() < 0.5) {
            // Resolutions under 0.1 seconds are too small for floating point calculations
            if (deltaTime.seconds() >= 0.1) {
                // We latch it for 0.5 seconds due to the non-constant nature of the rebounding calculation
                velocity = new PoseVelocity2d(
                        // s'(t) and keep unaffected angular velocity
                        currentPose.position.minus(lastPos).div(deltaTime.seconds()),
                        twist.velocity().value().angVel
                );
                lastVel = velocity;
                lastPos = currentPose.position;
                deltaTime.reset();
            } else {
                // We still need to inhibit the velocity, so we keep the last velocity
                velocity = lastVel;
            }
        }

        pose = currentPose;
    }
}
