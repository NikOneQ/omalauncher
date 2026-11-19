package android.view;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/** Compile-time stand-in for the framework's hidden AIDL interface; the real class is used at run time. */
public interface IRemoteAnimationRunner extends IInterface {
    void onAnimationStart(int transit, RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps, IRemoteAnimationFinishedCallback finishedCallback) throws RemoteException;

    void onAnimationCancelled() throws RemoteException;

    abstract class Stub extends Binder implements IRemoteAnimationRunner {
        public Stub() {
            throw new RuntimeException("stub");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("stub");
        }
    }
}
