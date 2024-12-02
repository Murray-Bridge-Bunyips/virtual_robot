package au.edu.sa.mbhs.studentrobotics.bunyipslib.tasks.bases

import java.util.function.BooleanSupplier

/**
 * Dynamic builder pattern implementation for [Task] instances.
 *
 * @author Lucas Bubner, 2024
 * @since 6.1.0
 */
class DynamicTask : Task() {
    private var init = {}
    private var loop = {}
    private var until = { false }
    private var finish = {}
    private var interrupt = {}
    private var reset = {}

    /**
     * Runs once when the task is initialised.
     */
    infix fun init(onInitialise: Runnable) = apply { init = { onInitialise.run() } }

    /**
     * Runs periodically while the task is active.
     */
    infix fun periodic(periodic: Runnable) = apply { loop = { periodic.run() } }

    /**
     * Returning true will end the task.
     */
    infix fun isFinished(isTaskFinished: BooleanSupplier) = apply { until = { isTaskFinished.asBoolean } }

    /**
     * Runs once when the task is finished.
     */
    infix fun onFinish(onFinish: Runnable) = apply { finish = { onFinish.run() } }

    /**
     * Runs when the task is interrupted (finished but not by [onFinish]).
     */
    infix fun onInterrupt(onInterrupt: Runnable) = apply { interrupt = { onInterrupt.run() } }

    /**
     * Runs when the task is reset to its initial state.
     */
    infix fun onReset(onReset: Runnable) = apply { reset = { onReset.run() } }

    override fun init() {
        init.invoke()
    }

    override fun periodic() {
        loop.invoke()
    }

    override fun isTaskFinished(): Boolean {
        return until.invoke()
    }

    override fun onFinish() {
        finish.invoke()
    }

    override fun onInterrupt() {
        interrupt.invoke()
    }

    override fun onReset() {
        reset.invoke()
    }
}