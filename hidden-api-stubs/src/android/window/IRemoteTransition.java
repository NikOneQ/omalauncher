package android.window;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.view.SurfaceControl;

/** Compile-time stand-in for the framework's hidden AIDL interface; the real class is used at run time. */
public interface IRemoteTransition extends IInterface {
    void startAnimation(IBinder token, TransitionInfo info, SurfaceControl.Transaction t, IRemoteTransitionFinishedCallback finishCallback) throws RemoteException;

    void mergeAnimation(IBinder transition, TransitionInfo info, SurfaceControl.Transaction t, IBinder mergeTarget, IRemoteTransitionFinishedCallback finishCallback) throws RemoteException;

    void takeOverAnimation(IBinder transition, TransitionInfo info, SurfaceControl.Transaction startTransaction, IRemoteTransitionFinishedCallback finishCallback, WindowAnimationState[] states) throws RemoteException;

    void onTransitionConsumed(IBinder transition, boolean aborted) throws RemoteException;

    abstract class Stub extends Binder implements IRemoteTransition {
        public Stub() {
            throw new RuntimeException("stub");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("stub");
        }

        public static IRemoteTransition asInterface(IBinder obj) {
            throw new RuntimeException("stub");
        }
    }
}
