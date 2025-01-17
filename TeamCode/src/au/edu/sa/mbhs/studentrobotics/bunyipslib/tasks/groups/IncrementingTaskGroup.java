package au.edu.sa.mbhs.studentrobotics.bunyipslib.tasks.groups;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import au.edu.sa.mbhs.studentrobotics.bunyipslib.tasks.bases.Task;

/**
 * A group of tasks that is similar to a {@link SequentialTaskGroup}, but instead of queueing the next
 * task after the previous one finishes, it queues the next task every time the task group is initialised.
 * <p>
 * The timeout of this group is calculated dynamically, based on the currently incremented task.
 * This task retains information across resets and reinitialisation, wrapping around to the first task
 * when the last task is reached and finished.
 *
 * @author Lucas Bubner, 2024
 * @since 7.0.0
 */
public class IncrementingTaskGroup extends TaskGroup {
    private int taskIndex = -1;

    /**
     * Create a new IncrementingTaskGroup with tasks.
     *
     * @param tasks The tasks to run in sequence, incrementing every time the group is initialised
     */
    public IncrementingTaskGroup(@NonNull Task... tasks) {
        setTasks(Arrays.asList(tasks));
        timeout(this.tasks.get(0).timeout);
    }

    /**
     * Create a new IncrementingTaskGroup with tasks.
     *
     * @param tasks The tasks to run in sequence, incrementing every time the group is initialised
     */
    public IncrementingTaskGroup(@NonNull List<Task> tasks) {
        this(tasks.toArray(new Task[0]));
    }

    @Override
    public final void init() {
        increment();
    }

    @Override
    public final void periodic() {
        Task currentTask = tasks.get(taskIndex);
        executeTask(currentTask);
        named(currentTask.toString());
        timeout(currentTask.timeout);
    }

    @Override
    protected boolean isTaskFinished() {
        return tasks.get(taskIndex).poll();
    }

    /**
     * Increment this task group.
     */
    public void increment() {
        taskIndex++;
        if (taskIndex >= tasks.size()) {
            taskIndex = 0;
        }
    }
}
