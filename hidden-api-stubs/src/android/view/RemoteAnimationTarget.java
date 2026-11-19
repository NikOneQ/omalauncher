package android.view;

import android.app.ActivityManager;
import android.graphics.Point;
import android.graphics.Rect;

/** Compile-time stand-in; see IRemoteAnimationRunner. */
public class RemoteAnimationTarget {
    public static final int MODE_OPENING = 0;
    public static final int MODE_CLOSING = 1;
    public static final int MODE_CHANGING = 2;

    public int taskId;
    public int mode;
    public SurfaceControl leash;
    public boolean isTranslucent;
    public Rect clipRect;
    public Rect localBounds;
    public Rect screenSpaceBounds;
    public Point position;
    public ActivityManager.RunningTaskInfo taskInfo;
    public int windowType;
}
