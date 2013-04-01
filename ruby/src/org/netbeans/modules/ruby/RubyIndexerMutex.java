package org.netbeans.modules.ruby;

/**
 */
public class RubyIndexerMutex {
    private boolean lock;

    public synchronized boolean lock() {
        if (lock) {
            return false;
        }
        lock = true;
        return true;
    }

    public synchronized void release() {
        lock = false;
    }

    public boolean isLocked() {
        return lock;
    }
}
