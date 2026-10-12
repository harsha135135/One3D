package store.one3d.background;

/**
 * One pass of a background task: claim or look up due work, process it, and
 * return how much was handled. Runs on the scheduler in the application and
 * is called directly by integration tests.
 */
public interface BackgroundJob {

    int runOnce();
}
