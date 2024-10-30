package au.edu.sa.mbhs.studentrobotics.bunyipslib.subsystems.drive;

import static au.edu.sa.mbhs.studentrobotics.bunyipslib.external.units.Units.Inches;
import static au.edu.sa.mbhs.studentrobotics.bunyipslib.external.units.Units.InchesPerSecond;
import static au.edu.sa.mbhs.studentrobotics.bunyipslib.external.units.Units.Radians;
import static au.edu.sa.mbhs.studentrobotics.bunyipslib.external.units.Units.RadiansPerSecond;
import static au.edu.sa.mbhs.studentrobotics.bunyipslib.external.units.Units.Seconds;

import androidx.annotation.NonNull;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.roadrunner.AccelConstraint;
import com.acmerobotics.roadrunner.Actions;
import com.acmerobotics.roadrunner.AngularVelConstraint;
import com.acmerobotics.roadrunner.Arclength;
import com.acmerobotics.roadrunner.DisplacementTrajectory;
import com.acmerobotics.roadrunner.DualNum;
import com.acmerobotics.roadrunner.HolonomicController;
import com.acmerobotics.roadrunner.MecanumKinematics;
import com.acmerobotics.roadrunner.MinVelConstraint;
import com.acmerobotics.roadrunner.MotorFeedforward;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.Pose2dDual;
import com.acmerobotics.roadrunner.PoseVelocity2d;
import com.acmerobotics.roadrunner.PoseVelocity2dDual;
import com.acmerobotics.roadrunner.ProfileAccelConstraint;
import com.acmerobotics.roadrunner.ProfileParams;
import com.acmerobotics.roadrunner.Time;
import com.acmerobotics.roadrunner.TimeTrajectory;
import com.acmerobotics.roadrunner.TimeTurn;
import com.acmerobotics.roadrunner.TrajectoryBuilderParams;
import com.acmerobotics.roadrunner.TurnConstraints;
import com.acmerobotics.roadrunner.VelConstraint;
//import com.acmerobotics.roadrunner.ftc.DownsampledWriter;
//import com.acmerobotics.roadrunner.ftc.FlightRecorder;
import com.acmerobotics.roadrunner.ftc.LazyImu;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.ElapsedTime;

import java.util.Arrays;
import java.util.List;

import au.edu.sa.mbhs.studentrobotics.bunyipslib.BunyipsSubsystem;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.RobotConfig;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.external.Mathf;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.localization.Localizer;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.localization.MecanumLocalizer;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.localization.accumulators.Accumulator;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.RoadRunnerDrive;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.messages.DriveCommandMessage;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.messages.MecanumCommandMessage;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.messages.PoseMessage;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.parameters.Constants;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.parameters.DriveModel;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.parameters.ErrorThresholds;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.parameters.MecanumGains;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.roadrunner.parameters.MotionProfile;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.tasks.bases.Task;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.util.Dashboard;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.util.Geometry;
import au.edu.sa.mbhs.studentrobotics.bunyipslib.util.Storage;

/**
 * This class is the standard Mecanum drive class that controls a set of four mecanum wheels while
 * integrating the RoadRunner v1.0 library in BunyipsLib.
 *
 * @since 6.0.0
 */
public class MecanumDrive extends BunyipsSubsystem implements RoadRunnerDrive {
    private final MecanumKinematics kinematics;
    private final TurnConstraints defaultTurnConstraints;
    private final VelConstraint defaultVelConstraint;
    private final AccelConstraint defaultAccelConstraint;
    private final VoltageSensor voltageSensor;
    // Unfortunately we have to expose lazyImu directly on both RR drives as they are required for tuning,
    // the LazyImu interface is not exposed on the localizers as they should be constructed by simply calling .get()
    // if a LazyImu were to be used regardless
    private final LazyImu lazyImu;
    private final DcMotorEx leftFront;
    private final DcMotorEx leftBack;
    private final DcMotorEx rightBack;
    private final DcMotorEx rightFront;
    private final DriveModel model;
    private final MotionProfile profile;
//    private final DownsampledWriter targetPoseWriter = new DownsampledWriter("TARGET_POSE", 50_000_000);
//    private final DownsampledWriter driveCommandWriter = new DownsampledWriter("DRIVE_COMMAND", 50_000_000);
//    private final DownsampledWriter mecanumCommandWriter = new DownsampledWriter("MECANUM_COMMAND", 50_000_000);
    /**
     * Gains used for holonomic drive control.
     */
    @NonNull
    public MecanumGains gains;
    private Localizer localizer;
    private Accumulator accumulator;
    private ErrorThresholds errorThresholds = ErrorThresholds.DEFAULT;
    private volatile double leftFrontPower;
    private volatile double leftBackPower;
    private volatile double rightBackPower;
    private volatile double rightFrontPower;

    /**
     * Create a new MecanumDrive.
     *
     * @param driveModel           the drive model parameters
     * @param motionProfile        the motion profile parameters
     * @param mecanumGains         the mecanum gains parameters
     * @param leftFront            the front left motor
     * @param leftBack             the back left motor
     * @param rightBack            the back right motor
     * @param rightFront           the front right motor
     * @param lazyImu              the LazyImu instance to use, see the {@code getLazyImu} method of {@link RobotConfig} to construct this
     * @param voltageSensorMapping the voltage sensor mapping for the robot as returned by {@code hardwareMap.voltageSensor}
     * @param startPose            the starting pose of the robot
     */
    public MecanumDrive(@NonNull DriveModel driveModel, @NonNull MotionProfile motionProfile, @NonNull MecanumGains mecanumGains, @NonNull DcMotor leftFront, @NonNull DcMotor leftBack, @NonNull DcMotor rightBack, @NonNull DcMotor rightFront, @NonNull LazyImu lazyImu, @NonNull HardwareMap.DeviceMapping<VoltageSensor> voltageSensorMapping, @NonNull Pose2d startPose) {
        accumulator = new Accumulator(startPose);

        gains = mecanumGains;
        model = driveModel;
        profile = motionProfile;

        kinematics = new MecanumKinematics(driveModel.inPerTick * driveModel.trackWidthTicks, driveModel.inPerTick / driveModel.lateralInPerTick);
        defaultTurnConstraints = new TurnConstraints(motionProfile.maxAngVel, -motionProfile.maxAngAccel, motionProfile.maxAngAccel);
        defaultVelConstraint = new MinVelConstraint(Arrays.asList(
                kinematics.new WheelVelConstraint(motionProfile.maxWheelVel),
                new AngularVelConstraint(motionProfile.maxAngVel)
        ));
        defaultAccelConstraint = new ProfileAccelConstraint(motionProfile.minProfileAccel, motionProfile.maxProfileAccel);

        voltageSensor = voltageSensorMapping.iterator().next();

//        FlightRecorder.write("MECANUM_GAINS", mecanumGains);
//        FlightRecorder.write("MECANUM_DRIVE_MODEL", driveModel);
//        FlightRecorder.write("MECANUM_PROFILE", motionProfile);

        if (assertParamsNotNull(driveModel, motionProfile, mecanumGains, leftFront, leftBack, rightBack, rightFront, lazyImu, voltageSensorMapping, startPose)) {
            leftFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            leftBack.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            rightBack.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            rightFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        this.leftFront = (DcMotorEx) leftFront;
        this.leftBack = (DcMotorEx) leftBack;
        this.rightBack = (DcMotorEx) rightBack;
        this.rightFront = (DcMotorEx) rightFront;
        this.lazyImu = lazyImu;
    }

    /**
     * Create a new MecanumDrive that will start at the last known pose.
     *
     * @param driveModel           the drive model parameters
     * @param motionProfile        the motion profile parameters
     * @param mecanumGains         the mecanum gains parameters
     * @param leftFront            the front left motor
     * @param leftBack             the back left motor
     * @param rightBack            the back right motor
     * @param rightFront           the front right motor
     * @param lazyImu              the LazyImu instance to use, see the {@code getLazyImu} method of {@link RobotConfig} to construct this
     * @param voltageSensorMapping the voltage sensor mapping for the robot as returned by {@code hardwareMap.voltageSensor}
     */
    public MecanumDrive(@NonNull DriveModel driveModel, @NonNull MotionProfile motionProfile, @NonNull MecanumGains mecanumGains, @NonNull DcMotor leftFront, @NonNull DcMotor leftBack, @NonNull DcMotor rightBack, @NonNull DcMotor rightFront, @NonNull LazyImu lazyImu, @NonNull HardwareMap.DeviceMapping<VoltageSensor> voltageSensorMapping) {
        this(driveModel, motionProfile, mecanumGains, leftFront, leftBack, rightBack, rightFront, lazyImu, voltageSensorMapping, Storage.memory().lastKnownPosition);
    }

    /**
     * Set the Localizer this drive instance should use.
     * If not specified, this will be a {@link MecanumLocalizer}.
     *
     * @param localizer the localizer to use
     * @return this
     */
    @NonNull
    @Override
    public MecanumDrive withLocalizer(@NonNull Localizer localizer) {
        this.localizer = localizer;
        return this;
    }

    @NonNull
    @Override
    public Localizer getLocalizer() {
        if (localizer == null) {
            localizer = new MecanumLocalizer(model, leftFront, leftBack, rightBack, rightFront, lazyImu.get());
        }
        return localizer;
    }

    /**
     * Set the pose accumulator this drive instance should use.
     * If not specified, the default {@link Accumulator} will be used.
     *
     * @param accumulator the new accumulator to use
     * @return this
     */
    @NonNull
    @Override
    public MecanumDrive withAccumulator(@NonNull Accumulator accumulator) {
        this.accumulator.copyTo(accumulator);
        this.accumulator = accumulator;
        return this;
    }

    @NonNull
    @Override
    public Accumulator getAccumulator() {
        return accumulator;
    }

    /**
     * Set a new set of error thresholds for this drive instance to use when running trajectories.
     *
     * @param errorThresholds the new error thresholds to use
     * @return this
     */
    @NonNull
    public MecanumDrive withErrorThresholds(@NonNull ErrorThresholds errorThresholds) {
        this.errorThresholds = errorThresholds;
        return this;
    }

    /**
     * Set the raw power of each motor on the drive.
     *
     * @param leftFrontPower  the power of the front left motor
     * @param leftBackPower   the power of the back left motor
     * @param rightBackPower  the power of the back right motor
     * @param rightFrontPower the power of the front right motor
     */
    public void setMotorPowers(double leftFrontPower, double leftBackPower, double rightBackPower, double rightFrontPower) {
        this.leftFrontPower = leftFrontPower;
        this.leftBackPower = leftBackPower;
        this.rightBackPower = rightBackPower;
        this.rightFrontPower = rightFrontPower;
    }

    /**
     * Get the raw power of each motor on the drive.
     *
     * @return the power of each motor on the drive, in the order of left front, left back, right back, right front
     */
    @NonNull
    public double[] getMotorPowers() {
        return new double[]{leftFrontPower, leftBackPower, rightBackPower, rightFrontPower};
    }

    @Override
    public void setPower(@NonNull PoseVelocity2d target) {
        MecanumKinematics.WheelVelocities<Time> wheelVels = new MecanumKinematics(1)
                .inverse(PoseVelocity2dDual.constant(target, 1));

        double maxPowerMag = 1;
        for (DualNum<Time> power : wheelVels.all()) {
            maxPowerMag = Math.max(maxPowerMag, power.value());
        }

        leftFrontPower = wheelVels.leftFront.get(0) / maxPowerMag;
        leftBackPower = wheelVels.leftBack.get(0) / maxPowerMag;
        rightBackPower = wheelVels.rightBack.get(0) / maxPowerMag;
        rightFrontPower = wheelVels.rightFront.get(0) / maxPowerMag;
    }

    /**
     * Calculate the accumulated pose from an internal localizer.
     *
     * @return the current pose estimate of the drive
     */
    @Override
    @NonNull
    public Pose2d getPose() {
        return accumulator.getPose();
    }

    /**
     * Reset the accumulated pose estimate to this pose. Further updates from an internal localizer will continue
     * from this supplied pose estimate.
     *
     * @param newPose the new pose to continue localization from
     */
    @Override
    public void setPose(@NonNull Pose2d newPose) {
        accumulator.setPose(newPose);
    }

    /**
     * Calculate the first derivative of the accumulated pose from an internal localizer.
     *
     * @return the current pose velocity of the drive as of the last update
     */
    @Override
    @NonNull
    public PoseVelocity2d getVelocity() {
        return accumulator.getVelocity();
    }

    /**
     * This method will dispatch new motor powers and run one iteration of the localizer to update the pose estimates.
     * It will also update the pose history for drawing on the dashboard.
     */
    @Override
    public void periodic() {
        if (localizer == null) {
            localizer = new MecanumLocalizer(model, leftFront, leftBack, rightBack, rightFront, lazyImu.get());
        }

        accumulator.accumulate(localizer.update());

        Pose2d pose = accumulator.getPose();
        PoseVelocity2d poseVel = accumulator.getVelocity();
        opMode(o -> o.telemetry.add("Localizer: X:%in(%/s) Y:%in(%/s) %deg(%/s)",
                Mathf.round(pose.position.x, 1),
                Mathf.round(poseVel.linearVel.x, 1),
                Mathf.round(pose.position.y, 1),
                Mathf.round(poseVel.linearVel.y, 1),
                Mathf.round(Math.toDegrees(pose.heading.toDouble()), 1),
                Mathf.round(Math.toDegrees(poseVel.angVel), 1)
        ).color("gray"));

        leftFront.setPower(leftFrontPower);
        leftBack.setPower(leftBackPower);
        rightBack.setPower(rightBackPower);
        rightFront.setPower(rightFrontPower);

        opMode(o -> o.telemetry.add("%: FL:%\\% %, BL:%\\% %, BR:%\\% %, FR:%\\% %", this,
                Math.round(Math.min(100, Math.abs(leftFrontPower * 100))), leftFrontPower >= 0 ? "↑" : "↓",
                Math.round(Math.min(100, Math.abs(leftBackPower * 100))), leftBackPower >= 0 ? "↑" : "↓",
                Math.round(Math.min(100, Math.abs(rightBackPower * 100))), rightBackPower >= 0 ? "↑" : "↓",
                Math.round(Math.min(100, Math.abs(rightFrontPower * 100))), rightFrontPower >= 0 ? "↑" : "↓"
        ));
    }

    @Override
    protected void onDisable() {
        leftFront.setPower(0);
        leftBack.setPower(0);
        rightBack.setPower(0);
        rightFront.setPower(0);
    }


    @NonNull
    @Override
    public Constants getConstants() {
        return new Constants(
                model,
                profile,
                TurnTask::new,
                gains.pathFollowingEnabled ? FollowDisplacementTrajectoryTask::new : FollowTimedTrajectoryTask::new,
                new TrajectoryBuilderParams(
                        1.0e-6,
                        new ProfileParams(
                                0.25, 0.1, 1.0e-2
                        )
                ),
                0, defaultTurnConstraints,
                defaultVelConstraint, defaultAccelConstraint
        );
    }

    /**
     * A task that will execute a RoadRunner trajectory on a Mecanum drive using a path follower approach.
     */
    public final class FollowDisplacementTrajectoryTask extends Task {
        private final DisplacementTrajectory displacementTrajectory;
        private final double[] xPoints, yPoints;
        private Pose2d error = Geometry.zeroPose();
        private ElapsedTime stab;
        private PoseVelocity2d robotVelRobot = Geometry.zeroVel();
        private double s;

        /**
         * Create a new FollowDisplacementTrajectoryTask.
         *
         * @param t the trajectory to follow with a path follower
         */
        public FollowDisplacementTrajectoryTask(@NonNull TimeTrajectory t) {
            displacementTrajectory = new DisplacementTrajectory(t.path, t.profile.dispProfile);
            List<Double> disps = com.acmerobotics.roadrunner.Math.range(
                    0, t.path.length(),
                    Math.max(2, (int) Math.ceil(t.path.length() / 2)));
            xPoints = new double[disps.size()];
            yPoints = new double[disps.size()];
            for (int i = 0; i < disps.size(); i++) {
                Pose2d p = t.path.get(disps.get(i), 1).value();
                xPoints[i] = p.position.x;
                yPoints[i] = p.position.y;
            }
        }

        @Override
        protected void periodic() {
            fieldOverlay.setStroke("#4CAF507A");
            fieldOverlay.setStrokeWidth(1);
            fieldOverlay.strokePolyline(xPoints, yPoints);

            Pose2d actualPose = accumulator.getPose();
            robotVelRobot = accumulator.getVelocity();

            s = displacementTrajectory.project(actualPose.position, s);
            // Internally reparameterises from respect to displacement to respect to time
            Pose2dDual<Time> txWorldTarget = displacementTrajectory.get(s); // TODO: check projection
//            targetPoseWriter.write(new PoseMessage(txWorldTarget.value()));

            PoseVelocity2dDual<Time> feedbackCommand = new HolonomicController(
                    gains.axialGain, gains.lateralGain, gains.headingGain,
                    gains.axialVelGain, gains.lateralVelGain, gains.headingVelGain
            )
                    .compute(txWorldTarget, actualPose, robotVelRobot);
//            driveCommandWriter.write(new DriveCommandMessage(feedbackCommand));

            double voltage = voltageSensor.getVoltage();

            // TODO: calculate max feedforward power
            Pose2dDual<Arclength> displacement = displacementTrajectory.path.get(s, 3);

            MecanumKinematics.WheelVelocities<Time> wheelVels = kinematics.inverse(feedbackCommand);
            MotorFeedforward feedforward = new MotorFeedforward(profile.kS,
                    profile.kV / model.inPerTick, profile.kA / model.inPerTick);
            leftFrontPower = feedforward.compute(wheelVels.leftFront) / voltage;
            leftBackPower = feedforward.compute(wheelVels.leftBack) / voltage;
            rightBackPower = feedforward.compute(wheelVels.rightBack) / voltage;
            rightFrontPower = feedforward.compute(wheelVels.rightFront) / voltage;
//            mecanumCommandWriter.write(new MecanumCommandMessage(
//                    voltage, leftFrontPower, leftBackPower, rightBackPower, rightFrontPower
//            ));

            error = txWorldTarget.value().minusExp(actualPose);
            p.put("xError", error.position.x);
            p.put("yError", error.position.y);
            p.put("headingError (deg)", Math.toDegrees(error.heading.toDouble()));

            Canvas c = p.fieldOverlay();
            c.setStrokeWidth(1);

            c.setStroke("#4CAF50");
            Dashboard.drawRobot(c, txWorldTarget.value());

            c.setStroke("#4CAF50FF");
            c.setStrokeWidth(1);
            c.strokePolyline(xPoints, yPoints);
        }

        @Override
        protected boolean isTaskFinished() {
            boolean displacement = s >= displacementTrajectory.length();
            if (displacement && stab == null) {
                stab = new ElapsedTime();
            } else if (!displacement) {
                stab = null;
            }
            boolean stablilised = stab != null &&
                    ((error.position.norm() < errorThresholds.getMaxTranslationalError().in(Inches)
                    && robotVelRobot.linearVel.norm() < errorThresholds.getMinVelStab().in(InchesPerSecond)
                    && error.heading.toDouble() < errorThresholds.getMaxAngularError().in(Radians)
                    && robotVelRobot.angVel < errorThresholds.getMinAngVelStab().in(RadiansPerSecond))
                    || stab.seconds() > errorThresholds.getStabilizationTimeout().in(Seconds));
            return displacement && stablilised;
        }

        @Override
        protected void onFinish() {
            leftFrontPower = 0;
            leftBackPower = 0;
            rightBackPower = 0;
            rightFrontPower = 0;
        }
    }

    /**
     * A task that will execute a time-based RoadRunner trajectory on a Mecanum drive.
     */
    public final class FollowTimedTrajectoryTask extends Task {
        private final TimeTrajectory timeTrajectory;
        private final double[] xPoints, yPoints;
        private Pose2d error = Geometry.zeroPose();
        private PoseVelocity2d robotVelRobot = Geometry.zeroVel();
        private double beginTs = -1;
        private double t;

        /**
         * Create a new FollowTimedTrajectoryTask.
         *
         * @param t the trajectory to follow with a time-based approach (default)
         */
        public FollowTimedTrajectoryTask(@NonNull TimeTrajectory t) {
            timeTrajectory = t;

            List<Double> disps = com.acmerobotics.roadrunner.Math.range(
                    0, t.path.length(),
                    Math.max(2, (int) Math.ceil(t.path.length() / 2)));
            xPoints = new double[disps.size()];
            yPoints = new double[disps.size()];
            for (int i = 0; i < disps.size(); i++) {
                Pose2d p = t.path.get(disps.get(i), 1).value();
                xPoints[i] = p.position.x;
                yPoints[i] = p.position.y;
            }

            withTimeout(Seconds.of(t.duration).plus(errorThresholds.getStabilizationTimeout()));
        }

        @Override
        protected void periodic() {
            fieldOverlay.setStroke("#4CAF507A");
            fieldOverlay.setStrokeWidth(1);
            fieldOverlay.strokePolyline(xPoints, yPoints);

            if (beginTs < 0) {
                beginTs = Actions.now();
                t = 0;
            } else {
                t = Actions.now() - beginTs;
            }

            Pose2dDual<Time> txWorldTarget = timeTrajectory.get(t);
//            targetPoseWriter.write(new PoseMessage(txWorldTarget.value()));

            robotVelRobot = accumulator.getVelocity();

            PoseVelocity2dDual<Time> command = new HolonomicController(
                    gains.axialGain, gains.lateralGain, gains.headingGain,
                    gains.axialVelGain, gains.lateralVelGain, gains.headingVelGain
            )
                    .compute(txWorldTarget, accumulator.getPose(), robotVelRobot);
//            driveCommandWriter.write(new DriveCommandMessage(command));

            MecanumKinematics.WheelVelocities<Time> wheelVels = kinematics.inverse(command);
            double voltage = voltageSensor.getVoltage();

            MotorFeedforward feedforward = new MotorFeedforward(
                    profile.kS, profile.kV / model.inPerTick, profile.kA / model.inPerTick);
            leftFrontPower = feedforward.compute(wheelVels.leftFront) / voltage;
            leftBackPower = feedforward.compute(wheelVels.leftBack) / voltage;
            rightBackPower = feedforward.compute(wheelVels.rightBack) / voltage;
            rightFrontPower = feedforward.compute(wheelVels.rightFront) / voltage;
//            mecanumCommandWriter.write(new MecanumCommandMessage(
//                    voltage, leftFrontPower, leftBackPower, rightBackPower, rightFrontPower
//            ));

            error = txWorldTarget.value().minusExp(accumulator.getPose());
            p.put("xError", error.position.x);
            p.put("yError", error.position.y);
            p.put("headingError (deg)", Math.toDegrees(error.heading.toDouble()));

            Canvas c = p.fieldOverlay();
            c.setStrokeWidth(1);

            c.setStroke("#4CAF50");
            Dashboard.drawRobot(c, txWorldTarget.value());

            c.setStroke("#4CAF50FF");
            c.setStrokeWidth(1);
            c.strokePolyline(xPoints, yPoints);
        }

        @Override
        protected boolean isTaskFinished() {
            return t >= timeTrajectory.duration
                    && error.position.norm() < errorThresholds.getMaxTranslationalError().in(Inches)
                    && robotVelRobot.linearVel.norm() < errorThresholds.getMinVelStab().in(InchesPerSecond)
                    && error.heading.toDouble() < errorThresholds.getMaxAngularError().in(Radians)
                    && robotVelRobot.angVel < errorThresholds.getMinAngVelStab().in(RadiansPerSecond);
        }

        @Override
        protected void onFinish() {
            leftFrontPower = 0;
            leftBackPower = 0;
            rightBackPower = 0;
            rightFrontPower = 0;
        }
    }

    /**
     * A task that will execute a motion-profiled turn on a Mecanum drive using RoadRunner.
     */
    public final class TurnTask extends Task {
        private final TimeTurn turn;
        private Pose2d error = Geometry.zeroPose();
        private PoseVelocity2d robotVelRobot = Geometry.zeroVel();
        private double beginTs = -1;
        private double t;

        /**
         * Create a new TurnTask.
         *
         * @param turn the turn to execute
         */
        public TurnTask(@NonNull TimeTurn turn) {
            this.turn = turn;
            withTimeout(Seconds.of(turn.duration).plus(errorThresholds.getStabilizationTimeout()));
        }

        @Override
        protected void periodic() {
            fieldOverlay.setStroke("#7C4DFF7A");
            fieldOverlay.fillCircle(turn.beginPose.position.x, turn.beginPose.position.y, 2);

            if (beginTs < 0) {
                beginTs = Actions.now();
                t = 0;
            } else {
                t = Actions.now() - beginTs;
            }

            Pose2dDual<Time> txWorldTarget = turn.get(t);
//            targetPoseWriter.write(new PoseMessage(txWorldTarget.value()));

            robotVelRobot = accumulator.getVelocity();

            PoseVelocity2dDual<Time> command = new HolonomicController(
                    gains.axialGain, gains.lateralGain, gains.headingGain,
                    gains.axialVelGain, gains.lateralVelGain, gains.headingVelGain
            )
                    .compute(txWorldTarget, accumulator.getPose(), robotVelRobot);
//            driveCommandWriter.write(new DriveCommandMessage(command));

            MecanumKinematics.WheelVelocities<Time> wheelVels = kinematics.inverse(command);
            double voltage = voltageSensor.getVoltage();
            MotorFeedforward feedforward = new MotorFeedforward(
                    profile.kS, profile.kV / model.inPerTick, profile.kA / model.inPerTick);
            leftFrontPower = feedforward.compute(wheelVels.leftFront) / voltage;
            leftBackPower = feedforward.compute(wheelVels.leftBack) / voltage;
            rightBackPower = feedforward.compute(wheelVels.rightBack) / voltage;
            rightFrontPower = feedforward.compute(wheelVels.rightFront) / voltage;
//            mecanumCommandWriter.write(new MecanumCommandMessage(
//                    voltage, leftFrontPower, leftBackPower, rightBackPower, rightFrontPower
//            ));

            error = txWorldTarget.value().minusExp(accumulator.getPose());
            p.put("headingError (deg)", Math.toDegrees(error.heading.toDouble()));

            Canvas c = p.fieldOverlay();
            c.setStrokeWidth(1);

            c.setStroke("#4CAF50");
            Dashboard.drawRobot(c, txWorldTarget.value());

            c.setStroke("#7C4DFFFF");
            c.fillCircle(turn.beginPose.position.x, turn.beginPose.position.y, 2);
        }

        @Override
        protected boolean isTaskFinished() {
            return t >= turn.duration
                    && error.heading.toDouble() < errorThresholds.getMaxAngularError().in(Radians)
                    && robotVelRobot.angVel < errorThresholds.getMinAngVelStab().in(RadiansPerSecond);
        }

        @Override
        protected void onFinish() {
            leftFrontPower = 0;
            leftBackPower = 0;
            rightBackPower = 0;
            rightFrontPower = 0;
        }
    }
}
