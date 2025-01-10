package ovh.paulem.fjc.threads;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * From <a href="https://stackoverflow.com/questions/702415/how-to-know-if-other-threads-have-finished">StackOverflow</a>
 */
public abstract class NotifierThread extends Thread {
    private final Set<ThreadCompleteListener> listeners
            = new CopyOnWriteArraySet<>();
    private final Set<Runnable> runnableListeners
            = new CopyOnWriteArraySet<>();

    public final void addListener(final ThreadCompleteListener listener) {
        listeners.add(listener);
    }

    public final void addListener(final Runnable runnable) {
        runnableListeners.add(runnable);
    }

    public final void removeListener(final ThreadCompleteListener listener) {
        listeners.remove(listener);
    }

    public final void removeListener(final Runnable runnable) {
        runnableListeners.remove(runnable);
    }

    private void notifyListeners() {
        for (ThreadCompleteListener listener : listeners) {
            listener.onThreadComplete(this);
        }

        for(Runnable runnable : runnableListeners) {
            runnable.run();
        }
    }

    @Override
    public final void run() {
        try {
            doRun();
        } finally {
            notifyListeners();
        }
    }

    public abstract void doRun();
}