package org.jerkar.api.function;

/**
 * A mutable container for {@link Runnable}. From this object you can replace the underlying {@link Runnable} or
 * chain it with other ones.
 */
public class JkRunnables implements Runnable {

    private Runnable runnable;

    /**
     * Creates a {@link JkRunnables} delegating to the single specified {@link Runnable}.
     */
    public static JkRunnables of(Runnable runnable) {
        return new JkRunnables(runnable);
    }

    /**
     * Creates a {@link JkRunnables} delegating to a no-op runnable.
     */
    public static JkRunnables noOp() {
        return of(() -> {});
    }

    private JkRunnables(Runnable runnable) {
        this.runnable = runnable;
    }

    /**
     * Set the specified {@link Runnable} as the unique underlying {@link Runnable} for this container.
     */
    public JkRunnables set(Runnable runnable) {
        this.runnable = runnable;
        return this;
    }

    /**
     * Chains this underlying {@link Runnable} with the specified one. The specified runnable will
     * be executed at the end.
     */
    public JkRunnables chain(Runnable runnable) {
        this.runnable = () -> {this.runnable.run(); runnable.run();};
        return this;
    }

    /**
     * Chains this specified {@link Runnable} with the underlying one. The specified runnable will
     * be executed at the begining.
     */
    public JkRunnables chainAtBegining(Runnable runnable) {
        this.runnable = () -> {this.runnable.run(); runnable.run();};
        return this;
    }


    @Override
    public void run() {
        runnable.run();
    }




}