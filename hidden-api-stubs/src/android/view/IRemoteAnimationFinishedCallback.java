package android.view;

import android.os.IInterface;
import android.os.RemoteException;

/** Compile-time stand-in; see IRemoteAnimationRunner. */
public interface IRemoteAnimationFinishedCallback extends IInterface {
    void onAnimationFinished() throws RemoteException;
}
