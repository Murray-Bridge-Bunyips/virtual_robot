package org.murraybridgebunyips.bunyipslib;

import androidx.annotation.Nullable;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.PwmControl;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.ServoController;

import org.murraybridgebunyips.bunyipslib.external.TrapezoidProfile;
import org.murraybridgebunyips.bunyipslib.external.units.Measure;
import org.murraybridgebunyips.bunyipslib.external.units.Time;

import static org.murraybridgebunyips.bunyipslib.external.units.Units.Nanoseconds;

/**
 * Extension of the extended {@link Servo} interface that allows for motion profiling via a {@link TrapezoidProfile}.
 * <p>
 * This class serves as a drop-in replacement for the {@link Servo}, similar to {@link Motor} with the {@link DcMotor}.
 *
 * @author Lucas Bubner, 2024
 * @since 5.1.0
 */
public class ProfiledServo implements Servo, PwmControl {
//    protected final ServoControllerEx controller;
//    protected final int port;
//    private final String deviceName;

//    protected Direction direction = Direction.FORWARD;
//    protected double limitPositionMin = MIN_POSITION;
//    protected double limitPositionMax = MAX_POSITION;

    // Virtual robot changes
    private final Servo __VIRTUAL_SERVO;

    @Nullable
    private TrapezoidProfile.Constraints constraints;
    private TrapezoidProfile.State setpoint = new TrapezoidProfile.State();
    private double lastDtSec = -1;

    private double positionDeltaTolerance;
    private double lastPosition;
    private long refreshRateNanos;
    private long lastUpdate;

    /**
     * Set the delta in servo position required to propagate a hardware write.
     *
     * @param magnitude absolute magnitude of delta in servo position, 0/default will disable
     */
    public void setPositionDeltaThreshold(double magnitude) {
        positionDeltaTolerance = Math.abs(magnitude);
    }

    /**
     * Set the refresh rate of the servo that will be a minimum time between hardware writes.
     * Consistent calls to {@link #setPosition(double)} is required for this refresh rate to be effective.
     *
     * @param refreshRate the refresh rate interval, <=0/default will disable
     */
    public void setPositionRefreshRate(Measure<Time> refreshRate) {
        refreshRateNanos = (long) refreshRate.in(Nanoseconds);
    }

    /**
     * Sets the trapezoidal constraints to apply to this servo's positions. These constraints are in units
     * of delta step of position.
     *
     * @param positionConstraints the position velocity and acceleration constraints
     */
    public void setConstraints(@Nullable TrapezoidProfile.Constraints positionConstraints) {
        constraints = positionConstraints;
    }

    /**
     * Return to standard servo controls without a motion profile. This is shorthand for {@code setConstraints(null)}.
     */
    public void disableConstraints() {
        constraints = null;
    }

    /**
     * Wrap a Servo to use with the ProfiledServo class.
     *
     * @param servo the Servo from hardwareMap to use.
     */
    public ProfiledServo(Servo servo) {
//        controller = (ServoControllerEx) servo.getController();
//        port = servo.getPortNumber();
//        deviceName = servo.getDeviceName();
        this.__VIRTUAL_SERVO = servo;
    }

    /**
     * Sets the PWM range limits for the servo
     *
     * @param range the new PWM range limits for the servo
     * @see #getPwmRange()
     */
    @Override
    public void setPwmRange(PwmRange range) {
    }

    /**
     * Returns the current PWM range limits for the servo
     *
     * @return the current PWM range limits for the servo
     * @see #setPwmRange(PwmRange)
     */
    @Override
    public PwmRange getPwmRange() {
        return null;
    }

    /**
     * Individually energizes the PWM for this particular servo.
     *
     * @see #setPwmDisable()
     * @see #isPwmEnabled()
     */
    @Override
    public void setPwmEnable() {
    }

    /**
     * Individually denergizes the PWM for this particular servo
     *
     * @see #setPwmEnable()
     */
    @Override
    public void setPwmDisable() {
    }

    /**
     * Returns whether the PWM is energized for this particular servo
     *
     * @see #setPwmEnable()
     */
    @Override
    public boolean isPwmEnabled() {
        return true;
    }

    /**
     * Returns the underlying servo controller on which this servo is situated.
     *
     * @return the underlying servo controller on which this servo is situated.
     * @see #getPortNumber()
     */
    @Override
    public ServoController getController() {
        return __VIRTUAL_SERVO.getController();
    }

    /**
     * Returns the port number on the underlying servo controller on which this motor is situated.
     *
     * @return the port number on the underlying servo controller on which this motor is situated.
     * @see #getController()
     */
    @Override
    public int getPortNumber() {
        return __VIRTUAL_SERVO.getPortNumber();
    }

    /**
     * Sets the logical direction in which this servo operates.
     *
     * @param direction the direction to set for this servo
     * @see #getDirection()
     * @see Direction
     */
    @Override
    public synchronized void setDirection(Direction direction) {
        __VIRTUAL_SERVO.setDirection(direction);
    }

    /**
     * Returns the current logical direction in which this servo is set as operating.
     *
     * @return the current logical direction in which this servo is set as operating.
     * @see #setDirection(Direction)
     */
    @Override
    public Direction getDirection() {
        return __VIRTUAL_SERVO.getDirection();
    }

    /**
     * Sets the current position of the servo, expressed as a fraction of its available
     * range. If PWM power is enabled for the servo, the servo will attempt to move to
     * the indicated position.
     * <p>
     * <b>Important ProfiledServo Note:</b> Since this class requires continuous update with a motion profile,
     * it is important that this method is being called periodically if one is being used; this is similar
     * to how {@code setPower} has to be called periodically in {@link Motor} to update system controllers.
     *
     * @param targetPosition the position to which the servo should move, a value in the range [0.0, 1.0]
     * @see ServoController#pwmEnable()
     * @see #getPosition()
     */
    @Override
    public synchronized void setPosition(double targetPosition) {
        if (constraints != null) {
            // Apply motion profiling for current target in seconds
            TrapezoidProfile.State goal = new TrapezoidProfile.State(targetPosition, 0);
            TrapezoidProfile profile = new TrapezoidProfile(constraints, goal, setpoint);
            double t = System.nanoTime() / 1.0E9;
            if (lastDtSec == -1) lastDtSec = t;
            setpoint = profile.calculate(t - lastDtSec);
            lastDtSec = t;
            targetPosition = setpoint.position;
        }

        // Apply refresh rate and cache restrictions
        if (refreshRateNanos > 0 && Math.abs(lastUpdate - System.nanoTime()) < refreshRateNanos) {
            return;
        }
        if (Math.abs(lastPosition - targetPosition) < positionDeltaTolerance) {
            return;
        }
        lastUpdate = System.nanoTime();
        lastPosition = targetPosition;

        __VIRTUAL_SERVO.setPosition(targetPosition);
    }

    /**
     * Returns the position to which the servo was last commanded to move. Note that this method
     * does NOT read a position from the servo through any electrical means, as no such electrical
     * mechanism is, generally, available.
     *
     * @return the position to which the servo was last commanded to move, or Double.NaN
     * if no such position is known
     * @see #setPosition(double)
     * @see Double#NaN
     * @see Double#isNaN()
     */
    @Override
    public synchronized double getPosition() {
        return __VIRTUAL_SERVO.getPosition();
    }

    /**
     * Scales the available movement range of the servo to be a subset of its maximum range. Subsequent
     * positioning calls will operate within that subset range. This is useful if your servo has
     * only a limited useful range of movement due to the physical hardware that it is manipulating
     * (as is often the case) but you don't want to have to manually scale and adjust the input
     * to {@link #setPosition(double) setPosition()} each time.
     *
     * <p>For example, if scaleRange(0.2, 0.8) is set; then servo positions will be
     * scaled to fit in that range:<br>
     * setPosition(0.0) scales to 0.2<br>
     * setPosition(1.0) scales to 0.8<br>
     * setPosition(0.5) scales to 0.5<br>
     * setPosition(0.25) scales to 0.35<br>
     * setPosition(0.75) scales to 0.65<br>
     * </p>
     *
     * <p>Note the parameters passed here are relative to the underlying full range of motion of
     * the servo, not its currently scaled range, if any. Thus, scaleRange(0.0, 1.0) will reset
     * the servo to its full range of movement.</p>
     *
     * @param min the lower limit of the servo movement range, a value in the interval [0.0, 1.0]
     * @param max the upper limit of the servo movement range, a value in the interval [0.0, 1.0]
     * @see #setPosition(double)
     */
    @Override
    public synchronized void scaleRange(double min, double max) {
        __VIRTUAL_SERVO.scaleRange(min, max);
    }

    /**
     * Returns an indication of the manufacturer of this device.
     *
     * @return the device's manufacturer
     */
    @Override
    public Manufacturer getManufacturer() {
        return __VIRTUAL_SERVO.getManufacturer();
    }

    /**
     * Returns a string suitable for display to the user as to the type of device.
     * Note that this is a device-type-specific name; it has nothing to do with the
     * name by which a user might have configured the device in a robot configuration.
     *
     * @return device manufacturer and name
     */
    @Override
    public String getDeviceName() {
        return __VIRTUAL_SERVO.getDeviceName();
    }

    /**
     * Get connection information about this device in a human readable format
     *
     * @return connection info
     */
    @Override
    public String getConnectionInfo() {
        return __VIRTUAL_SERVO.getConnectionInfo();
    }

    /**
     * Version
     *
     * @return get the version of this device
     */
    @Override
    public int getVersion() {
        return __VIRTUAL_SERVO.getVersion();
    }

    /**
     * Resets the device's configuration to that which is expected at the beginning of an OpMode.
     * For example, motors will reset the their direction to 'forward'.
     */
    @Override
    public synchronized void resetDeviceConfigurationForOpMode() {
        __VIRTUAL_SERVO.resetDeviceConfigurationForOpMode();
    }

    /**
     * Closes this device
     */
    @Override
    public void close() {
        __VIRTUAL_SERVO.close();
    }
}