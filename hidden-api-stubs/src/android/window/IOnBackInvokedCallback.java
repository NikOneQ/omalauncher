package android.window;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/** Compile-time stand-in; see IRemoteTransition. */
public interface IOnBackInvokedCallback extends IInterface {
    void onBackStarted(BackMotionEvent backMotionEvent) throws RemoteException;

    void onBackProgressed(BackMotionEvent backMotionEvent) throws RemoteException;

    void onBackCancelled() throws RemoteException;

    void onBackInvoked() throws RemoteException;

    void setTriggerBack(boolean triggerBack) throws RemoteException;

    void setHandoffHandler(IBackAnimationHandoffHandler handler) throws RemoteException;

    abstract class Stub extends Binder implements IOnBackInvokedCallback {
        public Stub() {
            throw new RuntimeException("stub");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("stub");
        }
    }
}
