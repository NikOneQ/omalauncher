package android.window;

import android.app.ActivityManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceControl;
import java.util.List;

/** Compile-time stand-in; see IRemoteTransition. */
public class TransitionInfo {
    public int getType() { throw new RuntimeException("stub"); }
    public int getFlags() { throw new RuntimeException("stub"); }
    public List<Change> getChanges() { throw new RuntimeException("stub"); }
    public int getRootCount() { throw new RuntimeException("stub"); }
    public Root getRoot(int i) { throw new RuntimeException("stub"); }

    public static class Change {
        public int getMode() { throw new RuntimeException("stub"); }
        public int getFlags() { throw new RuntimeException("stub"); }
        public SurfaceControl getLeash() { throw new RuntimeException("stub"); }
        public Rect getStartAbsBounds() { throw new RuntimeException("stub"); }
        public Rect getEndAbsBounds() { throw new RuntimeException("stub"); }
        public Point getEndRelOffset() { throw new RuntimeException("stub"); }
        public WindowContainerToken getParent() { throw new RuntimeException("stub"); }
        public ActivityManager.RunningTaskInfo getTaskInfo() { throw new RuntimeException("stub"); }
    }

    public static class Root {
        public SurfaceControl getLeash() { throw new RuntimeException("stub"); }
        public Point getOffset() { throw new RuntimeException("stub"); }
    }
}
