package com.lody.virtual.client.hook.patchs.am;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.WeakHashMap;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.env.SpecialWidgetList;
import com.lody.virtual.client.hook.base.Hook;
import com.lody.virtual.client.hook.utils.HookUtils;
import com.lody.virtual.helper.utils.Reflect;

import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * @author Lody
 * @see android.app.IActivityManager#registerReceiver(IApplicationThread,
 *      String, IIntentReceiver, IntentFilter, String, int)
 */
/* package */ class Hook_RegisterReceiver extends Hook {

	private static final int IDX_IIntentReceiver = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
			? 2
			: 1;

	private static final int IDX_RequiredPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
			? 4
			: 3;
	private static final int IDX_IntentFilter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
			? 3
			: 2;

	private WeakHashMap<IBinder, IIntentReceiver.Stub> mProxyIIntentReceiver = new WeakHashMap<>();

	@Override
	public String getName() {
		return "registerReceiver";
	}

	@Override
	public Object onHook(Object who, Method method, Object... args) throws Throwable {

		HookUtils.replaceFirstAppPkg(args);

		String permission = VirtualCore.getPermissionBroadcast();

		Class<?> permType = method.getParameterTypes()[IDX_RequiredPermission];
		if (permType == String.class) {
			args[IDX_RequiredPermission] = permission;
		} else if (permType == String[].class) {
			args[IDX_RequiredPermission] = new String[]{permission};
		}
		IntentFilter filter = (IntentFilter) args[IDX_IntentFilter];
		modifyIntentFilter(filter);
		if (args.length > IDX_IIntentReceiver && IIntentReceiver.class.isInstance(args[IDX_IIntentReceiver])) {
			final IIntentReceiver old = (IIntentReceiver) args[IDX_IIntentReceiver];
			// 防止重复代理
			if (!ProxyIIntentReceiver.class.isInstance(old)) {

				IBinder token = old.asBinder();
				IIntentReceiver.Stub proxyIIntentReceiver = mProxyIIntentReceiver.get(token);
				if (proxyIIntentReceiver == null) {
					proxyIIntentReceiver = new ProxyIIntentReceiver(old);
					mProxyIIntentReceiver.put(token, proxyIIntentReceiver);
				}
				try {
					WeakReference WeakReference_mDispatcher = Reflect.on(old).get("mDispatcher");
					Object mDispatcher = WeakReference_mDispatcher.get();
					Reflect.on(mDispatcher).set("mIIntentReceiver", proxyIIntentReceiver);
					args[IDX_IIntentReceiver] = proxyIIntentReceiver;
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
		return method.invoke(who, args);
	}

	private void modifyIntentFilter(IntentFilter filter) {
		if (filter != null) {
			List<String> actions = Reflect.on(filter).get("mActions");
			List<String> newActions = new ArrayList<>();
			ListIterator<String> iterator = actions.listIterator();
			while (iterator.hasNext()) {
				String action = iterator.next();
				if (SpecialWidgetList.isActionInBlackList(action)) {
					iterator.remove();
				}
				String newAction = SpecialWidgetList.modifyAction(action);
				if (newAction != null) {
					iterator.remove();
					newActions.add(newAction);
				}
			}
			actions.addAll(newActions);
		}
	}

	@Override
	public boolean isEnable() {
		return isAppProcess();
	}

	private static class ProxyIIntentReceiver extends IIntentReceiver.Stub {
		IIntentReceiver old;

		ProxyIIntentReceiver(IIntentReceiver old) {
			this.old = old;
		}

		@Override
		public void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered,
				boolean sticky, int sendingUser) throws RemoteException {
			try {
				String action = intent.getAction();
				String oldAction = SpecialWidgetList.restoreAction(action);
				if (oldAction != null) {
					intent.setAction(oldAction);
				}
				ComponentName oldComponent = VirtualCore.getOriginComponentName(action);
				if (oldComponent != null) {
					intent.setComponent(oldComponent);
					intent.setAction(null);
				}
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
					old.performReceive(intent, resultCode, data, extras, ordered, sticky, sendingUser);
				} else {
					Method performReceive = old.getClass().getDeclaredMethod("performReceive", Intent.class, int.class,
							String.class, Bundle.class, boolean.class, boolean.class);
					performReceive.setAccessible(true);
					performReceive.invoke(old, intent, resultCode, data, extras, ordered, sticky);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		// @Override
		public void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered,
				boolean sticky) throws android.os.RemoteException {
			this.performReceive(intent, resultCode, data, extras, ordered, sticky, 0);
		}
	}
}