package io.github.paulem.fjc.threads;

/**
 * From <a href="https://stackoverflow.com/questions/702415/how-to-know-if-other-threads-have-finished">StackOverflow</a>
 */
public interface ThreadCompleteListener {
    void onThreadComplete(final Thread thread);
}